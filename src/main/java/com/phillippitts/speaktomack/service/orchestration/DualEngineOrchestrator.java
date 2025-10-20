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
import com.phillippitts.speaktomack.service.reconcile.TranscriptReconciler;
import com.phillippitts.speaktomack.config.reconcile.ReconciliationProperties;
import com.phillippitts.speaktomack.service.stt.SttEngine;
import com.phillippitts.speaktomack.service.stt.parallel.ParallelSttService;
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

    // Engine name constants
    private static final String ENGINE_VOSK = "vosk";
    private static final String ENGINE_WHISPER = "whisper";
    private static final String ENGINE_RECONCILED = "reconciled";

    // Timeout value indicating "use service default"
    private static final long USE_DEFAULT_TIMEOUT = 0L;

    private final AudioCaptureService captureService;
    private final SttEngine vosk;
    private final SttEngine whisper;
    private final SttEngineWatchdog watchdog;
    private final OrchestrationProperties props;
    private final ApplicationEventPublisher publisher;

    // Optional Phase 4 components
    private final ParallelSttService parallel;
    private final TranscriptReconciler reconciler;
    private final ReconciliationProperties recProps;

    private final Object lock = new Object();
    private UUID activeSession;

    public DualEngineOrchestrator(AudioCaptureService captureService,
                                  SttEngine vosk,
                                  SttEngine whisper,
                                  SttEngineWatchdog watchdog,
                                  OrchestrationProperties props,
                                  ApplicationEventPublisher publisher) {
        this(captureService, vosk, whisper, watchdog, props, publisher, null, null, null);
    }

    public DualEngineOrchestrator(AudioCaptureService captureService,
                                  SttEngine vosk,
                                  SttEngine whisper,
                                  SttEngineWatchdog watchdog,
                                  OrchestrationProperties props,
                                  ApplicationEventPublisher publisher,
                                  ParallelSttService parallel,
                                  TranscriptReconciler reconciler,
                                  ReconciliationProperties recProps) {
        this.captureService = Objects.requireNonNull(captureService);
        this.vosk = Objects.requireNonNull(vosk);
        this.whisper = Objects.requireNonNull(whisper);
        this.watchdog = Objects.requireNonNull(watchdog);
        this.props = Objects.requireNonNull(props);
        this.publisher = Objects.requireNonNull(publisher);
        this.parallel = parallel;
        this.reconciler = reconciler;
        this.recProps = recProps;
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
        UUID session = stopCaptureSession();
        if (session == null) {
            return; // No active session
        }

        byte[] pcm = finalizeCaptureSession(session);
        if (pcm == null) {
            return; // Capture failed
        }

        // If reconciliation is enabled and services are present, run both engines and reconcile
        if (isReconciliationEnabled()) {
            transcribeWithReconciliation(pcm);
        } else {
            transcribeWithSingleEngine(pcm);
        }
    }

    /**
     * Stops the active capture session and returns the session ID.
     *
     * @return session ID if an active session exists, null otherwise
     */
    private UUID stopCaptureSession() {
        synchronized (lock) {
            UUID session = activeSession;
            if (session == null) {
                LOG.debug("No active capture session on release; ignoring");
                return null;
            }
            captureService.stopSession(session);
            activeSession = null;
            return session;
        }
    }

    /**
     * Reads captured audio data from the session and handles cleanup on failure.
     *
     * @param session the capture session ID
     * @return PCM audio data, or null if finalization failed
     */
    private byte[] finalizeCaptureSession(UUID session) {
        try {
            return captureService.readAll(session);
        } catch (Exception e) {
            LOG.warn("Failed to finalize capture session {}: {}", session, e.toString());
            captureService.cancelSession(session);
            return null;
        }
    }

    /**
     * Checks if reconciliation mode is enabled with all required dependencies.
     *
     * @return true if reconciliation is enabled and all services are available
     */
    private boolean isReconciliationEnabled() {
        return recProps != null && recProps.isEnabled() && parallel != null && reconciler != null;
    }

    /**
     * Transcribes audio using both engines in parallel and reconciles the results.
     *
     * @param pcm PCM audio data to transcribe
     */
    private void transcribeWithReconciliation(byte[] pcm) {
        try {
            long startTime = System.nanoTime();
            var pair = parallel.transcribeBoth(pcm, USE_DEFAULT_TIMEOUT);
            TranscriptionResult result = reconciler.reconcile(pair.vosk(), pair.whisper());
            String strategy = String.valueOf(recProps.getStrategy());
            logTranscriptionSuccess(ENGINE_RECONCILED, startTime, result.text().length(), strategy);
            publisher.publishEvent(new TranscriptionCompletedEvent(result, Instant.now(), ENGINE_RECONCILED));
        } catch (TranscriptionException te) {
            LOG.warn("Reconciled transcription failed: {}", te.getMessage());
        } catch (RuntimeException re) {
            LOG.error("Unexpected error during reconciled transcription", re);
        }
    }

    /**
     * Transcribes audio using a single engine selected based on watchdog state.
     *
     * @param pcm PCM audio data to transcribe
     */
    private void transcribeWithSingleEngine(byte[] pcm) {
        SttEngine engine = selectEngine(); // May throw TranscriptionException if both engines unavailable

        try {
            long startTime = System.nanoTime();
            TranscriptionResult result = engine.transcribe(pcm);
            logTranscriptionSuccess(engine.getEngineName(), startTime, result.text().length(), null);
            publisher.publishEvent(new TranscriptionCompletedEvent(result, Instant.now(), engine.getEngineName()));
        } catch (TranscriptionException te) {
            LOG.warn("Transcription failed: {}", te.getMessage());
        } catch (RuntimeException re) {
            LOG.error("Unexpected error during transcription", re);
        }
    }

    /**
     * Logs successful transcription with timing and metadata.
     *
     * @param engineName name of the engine used
     * @param startTimeNanos start time in nanoseconds
     * @param textLength length of transcribed text
     * @param strategy reconciliation strategy (nullable for single-engine)
     */
    private void logTranscriptionSuccess(String engineName, long startTimeNanos, int textLength, String strategy) {
        long durationMs = (System.nanoTime() - startTimeNanos) / 1_000_000L;
        if (strategy != null) {
            LOG.info("Reconciled transcription in {} ms (strategy={}, chars={})", durationMs, strategy, textLength);
        } else {
            LOG.info("Transcription completed by {} in {} ms (chars={})", engineName, durationMs, textLength);
        }
    }

    /**
     * Handles audio capture errors (e.g., microphone permission denied, device unavailable).
     *
     * <p>Cancels the active session if one exists. Phase 4.2 will add user notification.
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
        // Phase 4.2: Publish user notification event for UI display
    }

    /**
     * Selects the best available engine based on primary preference and health status.
     *
     * @return selected STT engine
     * @throws TranscriptionException if both engines are unavailable
     */
    private SttEngine selectEngine() {
        PrimaryEngine primary = props.getPrimaryEngine();
        boolean voskReady = watchdog.isEngineEnabled(ENGINE_VOSK) && vosk.isHealthy();
        boolean whisperReady = watchdog.isEngineEnabled(ENGINE_WHISPER) && whisper.isHealthy();

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

        // Both engines unavailable - construct detailed error message
        String errorMsg = String.format(
                "Both engines unavailable (vosk.enabled=%s, vosk.healthy=%s, whisper.enabled=%s, whisper.healthy=%s)",
                watchdog.isEngineEnabled(ENGINE_VOSK),
                vosk.isHealthy(),
                watchdog.isEngineEnabled(ENGINE_WHISPER),
                whisper.isHealthy()
        );
        throw new TranscriptionException(errorMsg);
    }
}
