package com.phillippitts.speaktomack.service.orchestration;

import com.phillippitts.speaktomack.config.orchestration.OrchestrationProperties;
import com.phillippitts.speaktomack.config.orchestration.OrchestrationProperties.PrimaryEngine;
import com.phillippitts.speaktomack.domain.TranscriptionResult;
import com.phillippitts.speaktomack.exception.TranscriptionException;
import com.phillippitts.speaktomack.service.audio.capture.AudioCaptureService;
import com.phillippitts.speaktomack.service.audio.capture.CaptureErrorEvent;
import com.phillippitts.speaktomack.service.hotkey.event.HotkeyPressedEvent;
import com.phillippitts.speaktomack.service.hotkey.event.HotkeyReleasedEvent;
import com.phillippitts.speaktomack.service.orchestration.event.TranscriptionCompletedEvent;
import com.phillippitts.speaktomack.service.stt.SttEngine;
import com.phillippitts.speaktomack.service.stt.watchdog.SttEngineWatchdog;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Orchestrates push-to-talk: start capture on press, transcribe on release,
 * route to the appropriate engine based on watchdog state and primary-engine config.
 *
 * Not annotated as @Component to avoid ambiguity; see OrchestrationConfig for bean wiring.
 */
public final class DualEngineOrchestrator {

    private static final Logger LOG = LogManager.getLogger(DualEngineOrchestrator.class);

    private final AudioCaptureService captureService;
    private final SttEngine vosk;
    private final SttEngine whisper;
    private final SttEngineWatchdog watchdog;
    private final OrchestrationProperties props;
    private final ApplicationEventPublisher publisher;

    private final Object lock = new Object();
    private UUID activeSession;

    public DualEngineOrchestrator(AudioCaptureService captureService,
                                  SttEngine vosk,
                                  SttEngine whisper,
                                  SttEngineWatchdog watchdog,
                                  OrchestrationProperties props,
                                  ApplicationEventPublisher publisher) {
        this.captureService = Objects.requireNonNull(captureService);
        this.vosk = Objects.requireNonNull(vosk);
        this.whisper = Objects.requireNonNull(whisper);
        this.watchdog = Objects.requireNonNull(watchdog);
        this.props = Objects.requireNonNull(props);
        this.publisher = Objects.requireNonNull(publisher);
    }

    @EventListener
    public void onHotkeyPressed(HotkeyPressedEvent evt) {
        synchronized (lock) {
            if (activeSession != null) {
                LOG.debug("Capture already active (session={})", activeSession);
                return; // Ignore duplicate presses
            }
            activeSession = captureService.startSession();
            LOG.info("Capture session started at {} (session={})", Instant.now(), activeSession);
        }
    }

    @EventListener
    public void onHotkeyReleased(HotkeyReleasedEvent evt) {
        UUID session;
        synchronized (lock) {
            session = activeSession;
            if (session == null) {
                LOG.debug("No active capture session on release; ignoring");
                return;
            }
            captureService.stopSession(session);
            activeSession = null;
        }

        byte[] pcm = null;
        try {
            pcm = captureService.readAll(session);
        } catch (Exception e) {
            LOG.warn("Failed to finalize capture session {}: {}", session, e.toString());
            captureService.cancelSession(session); // Explicit cleanup on failure
            return;
        }

        SttEngine engine;
        try {
            engine = selectEngine();
        } catch (TranscriptionException te) {
            pcm = null; // Help GC before rethrowing
            throw te; // Propagate "both engines unavailable" to caller
        }

        try {
            long t0 = System.nanoTime();
            TranscriptionResult result = engine.transcribe(pcm);
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            LOG.info("Transcription completed by {} in {} ms (chars={})",
                    engine.getEngineName(), ms, result.text().length());
            publisher.publishEvent(new TranscriptionCompletedEvent(result, Instant.now(), engine.getEngineName()));
        } catch (TranscriptionException te) {
            LOG.warn("Transcription failed: {}", te.getMessage());
        } catch (RuntimeException re) {
            LOG.error("Unexpected error during transcription", re);
        } finally {
            pcm = null; // Help GC reclaim potentially large buffer (up to ~2MB for 60s audio)
        }
    }

    /**
     * Handles audio capture errors (e.g., microphone permission denied, device unavailable).
     *
     * <p>Cancels the active session if one exists. Task 3.6 will add user notification.
     *
     * @param event capture error event with reason and timestamp
     */
    @EventListener
    public void onCaptureError(CaptureErrorEvent event) {
        synchronized (lock) {
            if (activeSession != null) {
                LOG.warn("Capture error during session {}: {}", activeSession, event.reason());
                captureService.cancelSession(activeSession);
                activeSession = null;
            } else {
                LOG.debug("Capture error when no active session: {}", event.reason());
            }
        }
        // Task 3.6 will add user notification event publishing here
    }

    private SttEngine selectEngine() {
        PrimaryEngine primary = props.getPrimaryEngine();
        boolean voskReady = watchdog.isEngineEnabled("vosk") && vosk.isHealthy();
        boolean whisperReady = watchdog.isEngineEnabled("whisper") && whisper.isHealthy();

        if (primary == PrimaryEngine.VOSK) {
            if (voskReady) {
                return vosk;
            }
            if (whisperReady) {
                return whisper;
            }
        } else { // primary whisper
            if (whisperReady) {
                return whisper;
            }
            if (voskReady) {
                return vosk;
            }
        }
        throw new TranscriptionException(
                "Both engines unavailable (vosk.enabled=" + watchdog.isEngineEnabled("vosk")
                + ", vosk.healthy=" + vosk.isHealthy()
                + ", whisper.enabled=" + watchdog.isEngineEnabled("whisper")
                + ", whisper.healthy=" + whisper.isHealthy() + ")");
    }
}
