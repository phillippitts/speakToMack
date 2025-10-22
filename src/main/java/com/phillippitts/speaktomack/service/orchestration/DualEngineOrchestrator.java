package com.phillippitts.speaktomack.service.orchestration;

import com.phillippitts.speaktomack.config.properties.HotkeyProperties;
import com.phillippitts.speaktomack.service.audio.capture.CaptureErrorEvent;
import com.phillippitts.speaktomack.service.hotkey.event.HotkeyPressedEvent;
import com.phillippitts.speaktomack.service.hotkey.event.HotkeyReleasedEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;

import java.util.Objects;
import java.util.UUID;

/**
 * Orchestrates push-to-talk or toggle-mode workflow: audio capture on hotkey, transcription on completion.
 *
 * <p>This orchestrator coordinates the complete speech-to-text pipeline:
 * <ol>
 *   <li><b>Hotkey Press:</b> Starts audio capture (push-to-talk) or toggles recording (toggle mode)</li>
 *   <li><b>Hotkey Release:</b> Stops capture and transcribes (push-to-talk mode only)</li>
 *   <li><b>Engine Selection:</b> Uses
 *       {@link com.phillippitts.speaktomack.service.stt.watchdog.SttEngineWatchdog} to choose
 *       healthy engine based on primary preference</li>
 *   <li><b>Transcription:</b> Supports both single-engine and dual-engine reconciliation modes</li>
 *   <li><b>Event Publishing:</b> Emits
 *       {@link com.phillippitts.speaktomack.service.orchestration.event.TranscriptionCompletedEvent}
 *       for downstream processing</li>
 * </ol>
 *
 * <p><b>Capture Modes:</b>
 * <ul>
 *   <li><b>Push-to-Talk (Default):</b> Press hotkey to start recording, release to stop and transcribe.</li>
 *   <li><b>Toggle Mode:</b> First press starts recording, second press stops and transcribes. Release is ignored.</li>
 * </ul>
 *
 * <p><b>Transcription Modes:</b>
 * <ul>
 *   <li><b>Single-Engine Mode (Default):</b> Uses one engine (Vosk or Whisper) based on primary
 *       preference and health status. Falls back to secondary if primary is unhealthy.</li>
 *   <li><b>Reconciliation Mode (Phase 4):</b> When enabled, runs both engines in parallel and
 *       reconciles results using configurable strategy (SIMPLE, CONFIDENCE, or OVERLAP).</li>
 * </ul>
 *
 * <p><b>Thread Safety:</b> This class uses synchronization on {@code lock} to protect the
 * {@code activeSession} field. Multiple hotkey events can be received concurrently, but only
 * one capture session can be active at a time. Duplicate presses during active sessions are
 * ignored to prevent audio corruption.
 *
 * <p><b>Error Handling:</b> Transcription failures are logged but do not crash the application.
 * The orchestrator remains ready for the next hotkey press. Capture errors trigger session
 * cancellation via {@link #onCaptureError(CaptureErrorEvent)}.
 *
 * <p><b>Smart Reconciliation Failure Semantics:</b> When running in single-engine mode and the
 * selected engine is Vosk with a confidence score below the configured threshold, this orchestrator
 * upgrades the current transcription to dual-engine reconciliation for improved accuracy. If that
 * reconciliation attempt itself fails (throws
 * {@link com.phillippitts.speaktomack.exception.TranscriptionException} or any unexpected
 * runtime exception), the orchestrator will:
 * <ul>
 *   <li>Record a failure metric for the "reconciled" engine</li>
 *   <li>Publish a
 *       {@link com.phillippitts.speaktomack.service.orchestration.event.TranscriptionCompletedEvent}
 *       carrying an <b>empty</b> text result (no characters will be typed)</li>
 *   <li><b>Not</b> fall back to the original low-confidence single-engine result</li>
 * </ul>
 * This behavior favors correctness (avoiding possibly wrong text) over availability. Downstream
 * consumers should treat an empty text result as a no-op while still updating any UI state
 * (e.g., ending a "transcribing" spinner).
 *
 * <p><b>Configuration:</b> Not annotated as {@code @Component} to avoid ambiguity; see
 * {@link com.phillippitts.speaktomack.config.orchestration.OrchestrationConfig} for bean wiring.
 *
 * @see HotkeyPressedEvent
 * @see HotkeyReleasedEvent
 * @see com.phillippitts.speaktomack.service.orchestration.event.TranscriptionCompletedEvent
 * @see com.phillippitts.speaktomack.service.stt.watchdog.SttEngineWatchdog
 * @see com.phillippitts.speaktomack.service.reconcile.TranscriptReconciler
 * @since 1.0
 */
public class DualEngineOrchestrator {

    private static final Logger LOG = LogManager.getLogger(DualEngineOrchestrator.class);

    private final CaptureOrchestrator captureOrchestrator;
    private final TranscriptionOrchestrator transcriptionOrchestrator;
    private final HotkeyProperties hotkeyProps;

    // Active capture session tracking
    private UUID activeSessionId;

    /**
     * Constructs a DualEngineOrchestrator.
     * Package-private to enforce use of DualEngineOrchestratorBuilder for construction.
     *
     * <p>This streamlined constructor has only 3 parameters after extracting CaptureOrchestrator
     * (Phase 1) and TranscriptionOrchestrator (Phase 2). The orchestrator now focuses solely on
     * coordinating hotkey events with capture and transcription workflows.
     *
     * @param captureOrchestrator orchestrator for audio capture lifecycle management
     * @param transcriptionOrchestrator orchestrator for transcription execution and result publishing
     * @param hotkeyProps hotkey configuration (toggle mode, etc.)
     * @throws NullPointerException if any parameter is null
     * @see CaptureOrchestrator
     * @see TranscriptionOrchestrator
     * @see DualEngineOrchestratorBuilder
     * @since 1.1
     */
    DualEngineOrchestrator(CaptureOrchestrator captureOrchestrator,
                           TranscriptionOrchestrator transcriptionOrchestrator,
                           HotkeyProperties hotkeyProps) {
        this.captureOrchestrator = Objects.requireNonNull(captureOrchestrator, "captureOrchestrator");
        this.transcriptionOrchestrator = Objects.requireNonNull(transcriptionOrchestrator,
                "transcriptionOrchestrator");
        this.hotkeyProps = Objects.requireNonNull(hotkeyProps, "hotkeyProps");
    }

    /**
     * Handles hotkey press events by starting or stopping audio capture, depending on mode.
     *
     * <p>This method runs asynchronously on the {@code eventExecutor} thread pool to prevent
     * blocking Spring's event bus. Transcription work (in toggle mode) is offloaded from the
     * event publishing thread.
     *
     * <p><b>Push-to-Talk Mode (toggleMode=false):</b> Starts a new capture session and records
     * the session ID for later retrieval on release.
     *
     * <p><b>Toggle Mode (toggleMode=true):</b> If no session is active, starts a new capture
     * session. If a session is already active, stops it and initiates transcription (same as
     * release in push-to-talk mode).
     *
     * <p><b>Thread Safety:</b> Synchronized on {@code lock} to ensure only one session can
     * be active at a time. Duplicate presses during an active session are handled based on mode.
     *
     * <p><b>Performance:</b> Session start completes in &lt;5ms. Transcription (toggle mode)
     * may take 1-5 seconds but runs on a dedicated thread pool without blocking event delivery.
     *
     * @param evt the hotkey pressed event with timestamp
     * @see HotkeyPressedEvent
     * @see CaptureOrchestrator#startCapture()
     */
    @EventListener
    @Async("eventExecutor")
    public void onHotkeyPressed(HotkeyPressedEvent evt) {
        if (captureOrchestrator.isCapturing()) {
            // In toggle mode, pressing again stops the capture
            if (hotkeyProps.isToggleMode()) {
                LOG.info("Toggle mode: stopping capture on second press (session={})", activeSessionId);
                processToggleStop();
            } else {
                LOG.debug("Capture already active (session={})", activeSessionId);
            }
            return;
        }

        UUID sessionId = captureOrchestrator.startCapture();
        if (sessionId != null) {
            activeSessionId = sessionId;
            LOG.info("Capture session started at {} (session={}, toggleMode={})",
                     evt.at(), sessionId, hotkeyProps.isToggleMode());
        } else {
            LOG.warn("Failed to start capture session, another session already active");
        }
    }

    /**
     * Processes toggle mode stop: stops capture and initiates transcription.
     */
    private void processToggleStop() {
        if (activeSessionId == null) {
            LOG.warn("processToggleStop called but no active session");
            return;
        }

        byte[] pcm = captureOrchestrator.stopCapture(activeSessionId);
        activeSessionId = null;

        if (pcm == null) {
            LOG.warn("Failed to retrieve audio data from capture session");
            return; // Capture failed
        }

        transcriptionOrchestrator.transcribe(pcm);
    }

    /**
     * Handles hotkey release events by stopping capture and initiating transcription.
     *
     * <p>This method runs asynchronously on the {@code eventExecutor} thread pool to prevent
     * blocking Spring's event bus during CPU-intensive transcription work.
     *
     * <p><b>Push-to-Talk Mode (toggleMode=false):</b> Performs the following workflow:
     * <ol>
     *   <li>Stops the active capture session (synchronized)</li>
     *   <li>Retrieves captured PCM audio data</li>
     *   <li>Selects transcription mode (single-engine vs. reconciliation)</li>
     *   <li>Transcribes audio using selected mode</li>
     *   <li>Publishes
 *       {@link com.phillippitts.speaktomack.service.orchestration.event.TranscriptionCompletedEvent}
 *       on success</li>
     * </ol>
     *
     * <p><b>Toggle Mode (toggleMode=true):</b> Ignored. In toggle mode, capture is stopped
     * by the second press event, not by release.
     *
     * <p><b>Error Handling:</b> If no active session exists or audio retrieval fails, the
     * method returns early without attempting transcription. Transcription failures are logged
     * but do not crash the application.
     *
     * <p><b>Performance:</b> Transcription is CPU-intensive and may take 1-5 seconds depending
     * on audio length and engine choice. Running asynchronously prevents blocking other event
     * listeners and maintains UI responsiveness.
     *
     * @param evt the hotkey released event with timestamp
     * @see HotkeyReleasedEvent
     * @see CaptureOrchestrator#stopCapture(UUID)
     * @see com.phillippitts.speaktomack.service.orchestration.event.TranscriptionCompletedEvent
     */
    @EventListener
    @Async("eventExecutor")
    public void onHotkeyReleased(HotkeyReleasedEvent evt) {
        // In toggle mode, release events are ignored
        if (hotkeyProps.isToggleMode()) {
            LOG.debug("Toggle mode: ignoring release event");
            return;
        }

        if (activeSessionId == null) {
            LOG.debug("No active capture session on release; ignoring");
            return;
        }

        byte[] pcm = captureOrchestrator.stopCapture(activeSessionId);
        activeSessionId = null;

        if (pcm == null) {
            LOG.warn("Failed to retrieve audio data from capture session");
            return; // Capture failed
        }

        transcriptionOrchestrator.transcribe(pcm);
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
        if (activeSessionId != null) {
            LOG.warn("Capture error during session {}: {}", activeSessionId, event.reason());
            captureOrchestrator.cancelCapture(activeSessionId);
            activeSessionId = null;
        } else {
            LOG.debug("Capture error when no active session: {}", event.reason());
        }
        // Phase 4.2: Publish user notification event for UI display
    }
}
