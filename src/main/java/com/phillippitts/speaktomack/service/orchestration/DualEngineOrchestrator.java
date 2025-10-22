package com.phillippitts.speaktomack.service.orchestration;

import com.phillippitts.speaktomack.config.properties.OrchestrationProperties;
import com.phillippitts.speaktomack.config.properties.HotkeyProperties;
import com.phillippitts.speaktomack.domain.TranscriptionResult;
import com.phillippitts.speaktomack.exception.TranscriptionException;
import com.phillippitts.speaktomack.service.audio.capture.AudioCaptureService;
import com.phillippitts.speaktomack.service.audio.capture.CaptureErrorEvent;
import com.phillippitts.speaktomack.service.hotkey.event.HotkeyPressedEvent;
import com.phillippitts.speaktomack.service.hotkey.event.HotkeyReleasedEvent;
import com.phillippitts.speaktomack.service.orchestration.event.TranscriptionCompletedEvent;
import com.phillippitts.speaktomack.service.reconcile.TranscriptReconciler;
import com.phillippitts.speaktomack.config.properties.ReconciliationProperties;
import com.phillippitts.speaktomack.service.stt.SttEngine;
import com.phillippitts.speaktomack.service.stt.SttEngineNames;
import com.phillippitts.speaktomack.service.stt.parallel.ParallelSttService;
import com.phillippitts.speaktomack.service.stt.watchdog.SttEngineWatchdog;
import com.phillippitts.speaktomack.util.TimeUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Orchestrates push-to-talk or toggle-mode workflow: audio capture on hotkey, transcription on completion.
 *
 * <p>This orchestrator coordinates the complete speech-to-text pipeline:
 * <ol>
 *   <li><b>Hotkey Press:</b> Starts audio capture (push-to-talk) or toggles recording (toggle mode)</li>
 *   <li><b>Hotkey Release:</b> Stops capture and transcribes (push-to-talk mode only)</li>
 *   <li><b>Engine Selection:</b> Uses {@link SttEngineWatchdog} to choose healthy engine
 *       based on primary preference</li>
 *   <li><b>Transcription:</b> Supports both single-engine and dual-engine reconciliation modes</li>
 *   <li><b>Event Publishing:</b> Emits {@link TranscriptionCompletedEvent} for downstream processing</li>
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
 * reconciliation attempt itself fails (throws {@link TranscriptionException} or any unexpected
 * runtime exception), the orchestrator will:
 * <ul>
 *   <li>Record a failure metric for the "reconciled" engine</li>
 *   <li>Publish a {@link TranscriptionCompletedEvent} carrying an <b>empty</b> text result
 *       (no characters will be typed)</li>
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
 * @see TranscriptionCompletedEvent
 * @see SttEngineWatchdog
 * @see TranscriptReconciler
 * @since 1.0
 */
public class DualEngineOrchestrator {

    private static final Logger LOG = LogManager.getLogger(DualEngineOrchestrator.class);

    // Engine name constants
    private static final String ENGINE_RECONCILED = "reconciled";

    // Timeout value indicating "use service default"
    private static final long USE_DEFAULT_TIMEOUT = 0L;

    private final AudioCaptureService captureService;
    private final SttEngine vosk;
    private final SttEngine whisper;
    private final SttEngineWatchdog watchdog;
    private final OrchestrationProperties props;
    private final HotkeyProperties hotkeyProps;
    private final ApplicationEventPublisher publisher;

    // Optional Phase 4 components
    private final ParallelSttService parallel;
    private final TranscriptReconciler reconciler;
    private final ReconciliationProperties recProps;

    // State machines and services for managing complex state and logic
    private final CaptureStateMachine captureStateMachine;
    private final EngineSelectionStrategy engineSelector;
    private final TimingCoordinator timingCoordinator;
    private final TranscriptionMetricsPublisher metricsPublisher;

    /**
     * Constructs a DualEngineOrchestrator in single-engine mode (no reconciliation).
     *
     * <p>This constructor is used when reconciliation is disabled. The orchestrator will
     * select one engine per transcription based on primary preference and health status.
     *
     * @param captureService service for audio capture lifecycle management
     * @param vosk Vosk STT engine instance
     * @param whisper Whisper STT engine instance
     * @param watchdog engine health monitor and enablement controller
     * @param props orchestration configuration (primary engine preference)
     * @param hotkeyProps hotkey configuration (toggle mode, etc.)
     * @param publisher Spring event publisher for transcription results
     * @throws NullPointerException if any parameter is null
     * @see #DualEngineOrchestrator(AudioCaptureService, SttEngine, SttEngine, SttEngineWatchdog,
     *      OrchestrationProperties, HotkeyProperties, ApplicationEventPublisher, ParallelSttService,
     *      TranscriptReconciler, ReconciliationProperties)
     */
    // CHECKSTYLE.OFF: ParameterNumber - Builder pattern used for construction
    public DualEngineOrchestrator(AudioCaptureService captureService,
                                  SttEngine vosk,
                                  SttEngine whisper,
                                  SttEngineWatchdog watchdog,
                                  OrchestrationProperties props,
                                  HotkeyProperties hotkeyProps,
                                  ApplicationEventPublisher publisher,
                                  CaptureStateMachine captureStateMachine,
                                  EngineSelectionStrategy engineSelector,
                                  TimingCoordinator timingCoordinator,
                                  TranscriptionMetricsPublisher metricsPublisher) {
        this(captureService, vosk, whisper, watchdog, props, hotkeyProps, publisher, null, null, null,
                captureStateMachine, engineSelector, timingCoordinator, metricsPublisher);
    }
    // CHECKSTYLE.ON: ParameterNumber

    /**
     * Constructs a DualEngineOrchestrator with optional reconciliation and metrics support.
     * Package-private to enforce use of DualEngineOrchestratorBuilder for construction.
     *
     * <p>This constructor has 14 parameters, which exceeds checkstyle's 10-parameter limit.
     * The suppression is justified because:
     * <ul>
     *   <li>Constructor is package-private, not part of public API</li>
     *   <li>Only accessed via DualEngineOrchestratorBuilder (builder pattern)</li>
     *   <li>All parameters represent required dependencies or state machine components</li>
     *   <li>Further parameter reduction would require nested parameter objects,
     *       adding complexity without improving readability</li>
     * </ul>
     *
     * <p>When all Phase 4 parameters ({@code parallel}, {@code reconciler}, {@code recProps})
     * are provided and {@code recProps.isEnabled() == true}, the orchestrator runs both engines
     * in parallel and reconciles their results using the configured strategy.
     *
     * <p>If any Phase 4 parameter is {@code null} or reconciliation is disabled, the orchestrator
     * falls back to single-engine mode (same behavior as the 7-parameter constructor).
     *
     * @param captureService service for audio capture lifecycle management
     * @param vosk Vosk STT engine instance
     * @param whisper Whisper STT engine instance
     * @param watchdog engine health monitor and enablement controller
     * @param props orchestration configuration (primary engine preference)
     * @param hotkeyProps hotkey configuration (toggle mode, etc.)
     * @param publisher Spring event publisher for transcription results
     * @param parallel service for running both engines in parallel (nullable)
     * @param reconciler strategy for merging dual-engine results (nullable)
     * @param recProps reconciliation configuration and enablement flag (nullable)
     * @param metricsPublisher metrics publishing service
     * @throws NullPointerException if any required parameter is null (Phase 4 params are optional)
     * @see ParallelSttService
     * @see TranscriptReconciler
     * @see ReconciliationProperties
     * @see DualEngineOrchestratorBuilder
     */
    // CHECKSTYLE.OFF: ParameterNumber - Package-private constructor only used by builder
    DualEngineOrchestrator(AudioCaptureService captureService,
                           SttEngine vosk,
                           SttEngine whisper,
                           SttEngineWatchdog watchdog,
                           OrchestrationProperties props,
                           HotkeyProperties hotkeyProps,
                           ApplicationEventPublisher publisher,
                           ParallelSttService parallel,
                           TranscriptReconciler reconciler,
                           ReconciliationProperties recProps,
                           CaptureStateMachine captureStateMachine,
                           EngineSelectionStrategy engineSelector,
                           TimingCoordinator timingCoordinator,
                           TranscriptionMetricsPublisher metricsPublisher) {
        this.captureService = Objects.requireNonNull(captureService);
        this.vosk = Objects.requireNonNull(vosk);
        this.whisper = Objects.requireNonNull(whisper);
        this.watchdog = Objects.requireNonNull(watchdog);
        this.props = Objects.requireNonNull(props);
        this.hotkeyProps = Objects.requireNonNull(hotkeyProps);
        this.publisher = Objects.requireNonNull(publisher);
        this.parallel = parallel;
        this.reconciler = reconciler;
        this.recProps = recProps;
        this.captureStateMachine = Objects.requireNonNull(captureStateMachine);
        this.engineSelector = Objects.requireNonNull(engineSelector);
        this.timingCoordinator = Objects.requireNonNull(timingCoordinator);
        this.metricsPublisher = Objects.requireNonNull(metricsPublisher);
    }
    // CHECKSTYLE.ON: ParameterNumber

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
     * @see AudioCaptureService#startSession()
     */
    @EventListener
    @Async("eventExecutor")
    public void onHotkeyPressed(HotkeyPressedEvent evt) {
        if (captureStateMachine.isActive()) {
            // In toggle mode, pressing again stops the capture
            if (hotkeyProps.isToggleMode()) {
                LOG.info("Toggle mode: stopping capture on second press (session={})",
                        captureStateMachine.getActiveSession());
                processToggleStop();
            } else {
                LOG.debug("Capture already active (session={})", captureStateMachine.getActiveSession());
            }
            return;
        }

        UUID sessionId = captureService.startSession();
        if (captureStateMachine.startCapture(sessionId)) {
            LOG.info("Capture session started at {} (session={}, toggleMode={})",
                     evt.at(), sessionId, hotkeyProps.isToggleMode());
        } else {
            // Race condition: another session started between our check and start attempt
            LOG.warn("Failed to start capture session {}, another session already active", sessionId);
            captureService.cancelSession(sessionId);
        }
    }

    /**
     * Processes toggle mode stop: stops capture and initiates transcription.
     */
    private void processToggleStop() {
        UUID session = captureStateMachine.getActiveSession();
        if (session == null) {
            LOG.warn("processToggleStop called but no active session");
            return;
        }

        captureService.stopSession(session);
        captureStateMachine.stopCapture(session);

        // Process transcription asynchronously (same as onHotkeyReleased)
        byte[] pcm = finalizeCaptureSession(session);
        if (pcm == null) {
            return; // Capture failed
        }

        if (isReconciliationEnabled()) {
            transcribeWithReconciliation(pcm);
        } else {
            transcribeWithSingleEngine(pcm);
        }
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
     *   <li>Publishes {@link TranscriptionCompletedEvent} on success</li>
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
     * @see AudioCaptureService#stopSession(UUID)
     * @see AudioCaptureService#readAll(UUID)
     * @see TranscriptionCompletedEvent
     */
    @EventListener
    @Async("eventExecutor")
    public void onHotkeyReleased(HotkeyReleasedEvent evt) {
        // In toggle mode, release events are ignored
        if (hotkeyProps.isToggleMode()) {
            LOG.debug("Toggle mode: ignoring release event");
            return;
        }

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
        UUID session = captureStateMachine.getActiveSession();
        if (session == null) {
            LOG.debug("No active capture session on release; ignoring");
            return null;
        }

        captureService.stopSession(session);
        if (!captureStateMachine.stopCapture(session)) {
            LOG.warn("Failed to stop capture session {} - session ID mismatch or not active", session);
        }
        return session;
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
     * <p><b>Failure semantics:</b> If reconciliation fails (either with a
     * {@link TranscriptionException} from an engine or any other runtime exception), this method
     * will record a failure metric for the "reconciled" engine and publish a
     * {@link TranscriptionCompletedEvent} with an empty text result. It will not fall back to
     * any previously computed single-engine result. This conservative behavior avoids emitting
     * potentially inaccurate text when both engines disagree or fail.
     *
     * @param pcm PCM audio data to transcribe
     */
    private void transcribeWithReconciliation(byte[] pcm) {
        long startTime = System.nanoTime();
        try {
            var pair = parallel.transcribeBoth(pcm, USE_DEFAULT_TIMEOUT);
            TranscriptionResult result = reconciler.reconcile(pair.vosk(), pair.whisper());
            String strategy = String.valueOf(recProps.getStrategy());

            // Record metrics
            long duration = System.nanoTime() - startTime;
            metricsPublisher.recordSuccess(ENGINE_RECONCILED, duration, strategy);

            logTranscriptionSuccess(ENGINE_RECONCILED, startTime, result.text().length(), strategy);
            publishResult(result, ENGINE_RECONCILED);
        } catch (TranscriptionException te) {
            metricsPublisher.recordFailure(ENGINE_RECONCILED, "transcription_error");
            LOG.warn("Reconciled transcription failed: {}", te.getMessage());
            // Publish empty result to notify downstream consumers
            TranscriptionResult emptyResult = TranscriptionResult.of("", 0.0, ENGINE_RECONCILED);
            publishResult(emptyResult, ENGINE_RECONCILED);
        } catch (RuntimeException re) {
            metricsPublisher.recordFailure(ENGINE_RECONCILED, "unexpected_error");
            LOG.error("Unexpected error during reconciled transcription", re);
            // Publish empty result to notify downstream consumers
            TranscriptionResult emptyResult = TranscriptionResult.of("", 0.0, ENGINE_RECONCILED);
            publishResult(emptyResult, ENGINE_RECONCILED);
        }
    }

    /**
     * Transcribes audio using a single engine selected based on watchdog state.
     * Implements smart reconciliation: if Vosk confidence is below threshold,
     * automatically upgrades to dual-engine mode for better accuracy.
     *
     * <p><b>Upgrade and failure behavior:</b> When upgraded to reconciliation due to low
     * Vosk confidence, this method delegates to {@link #transcribeWithReconciliation(byte[])} and
     * returns immediately. If reconciliation then fails, an empty result is published and there
     * is no fallback to the original single-engine (low-confidence) text. This avoids emitting
     * potentially incorrect transcriptions.
     *
     * @param pcm PCM audio data to transcribe
     */
    private void transcribeWithSingleEngine(byte[] pcm) {
        SttEngine engine = selectSingleEngine();
        long startTime = System.nanoTime();

        try {
            TranscriptionResult result = engine.transcribe(pcm);
            String engineName = engine.getEngineName();

            // Smart reconciliation: Check if we should upgrade to dual-engine
            // based on Vosk confidence threshold
            if (isReconciliationEnabled() &&
                SttEngineNames.VOSK.equals(engineName) &&
                result.confidence() < recProps.getConfidenceThreshold()) {

                LOG.info("Vosk confidence {} < threshold {}, upgrading to dual-engine reconciliation",
                         String.format("%.3f", result.confidence()),
                         String.format("%.3f", recProps.getConfidenceThreshold()));

                // Upgrade to dual-engine mode for this transcription
                transcribeWithReconciliation(pcm);
                return; // Exit early - reconciliation handles metrics & publishing
            }

            // Record metrics for successful single-engine transcription
            long duration = System.nanoTime() - startTime;
            metricsPublisher.recordSuccess(engineName, duration, null);

            logTranscriptionSuccess(engineName, startTime, result.text().length(), null);
            publishResult(result, engineName);
        } catch (TranscriptionException te) {
            metricsPublisher.recordFailure(engine.getEngineName(), "transcription_error");
            LOG.warn("Transcription failed: {}", te.getMessage());
        } catch (RuntimeException re) {
            metricsPublisher.recordFailure(engine.getEngineName(), "unexpected_error");
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
        long durationMs = TimeUtils.elapsedMillis(startTimeNanos);
        if (strategy != null) {
            LOG.info("Reconciled transcription in {} ms (strategy={}, chars={})", durationMs, strategy, textLength);
        } else {
            LOG.info("Transcription completed by {} in {} ms (chars={})", engineName, durationMs, textLength);
        }
    }

    /**
     * Publishes transcription result as an event for downstream processing.
     * Prepends a newline if the gap since the last transcription exceeds the configured threshold.
     *
     * @param result the transcription result
     * @param engineName name of the engine that produced the result
     */
    private void publishResult(TranscriptionResult result, String engineName) {
        // Check if we should prepend a newline based on silence gap
        TranscriptionResult finalResult = result;

        if (timingCoordinator.shouldAddParagraphBreak()) {
            // Prepend newline for new paragraph (avoid double newlines if text already starts with one)
            String text = result.text();
            String textWithNewline = text.startsWith("\n") ? text : "\n" + text;
            finalResult = TranscriptionResult.of(textWithNewline, result.confidence(), result.engineName());
            LOG.debug("Prepended newline after silence gap (threshold={}ms)", props.getSilenceGapMs());
        }

        // Record this transcription for future paragraph break decisions
        timingCoordinator.recordTranscription();

        publisher.publishEvent(new TranscriptionCompletedEvent(finalResult, Instant.now(), engineName));
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
        UUID cancelledSession = captureStateMachine.cancelCapture();
        if (cancelledSession != null) {
            LOG.warn("Capture error during session {}: {}", cancelledSession, event.reason());
            captureService.cancelSession(cancelledSession);
        } else {
            LOG.debug("Capture error when no active session: {}", event.reason());
        }
        // Phase 4.2: Publish user notification event for UI display
    }

    /**
     * Selects the best available engine based on primary preference and health status.
     *
     * @return selected STT engine
     * @throws TranscriptionException if both engines are unavailable
     */
    private SttEngine selectSingleEngine() {
        // Delegate to strategy for engine selection
        SttEngine selected = engineSelector.selectEngine();

        // Verify both engines aren't unhealthy (strategy returns primary anyway, but we want to fail fast)
        if (!engineSelector.areBothEnginesHealthy()) {
            boolean voskReady = watchdog.isEngineEnabled(SttEngineNames.VOSK) && vosk.isHealthy();
            boolean whisperReady = watchdog.isEngineEnabled(SttEngineNames.WHISPER) && whisper.isHealthy();

            if (!voskReady && !whisperReady) {
                // Both engines unavailable - construct detailed error message
                String errorMsg = String.format(
                        "Both engines unavailable (vosk.enabled=%s, vosk.healthy=%s, "
                                + "whisper.enabled=%s, whisper.healthy=%s)",
                        watchdog.isEngineEnabled(SttEngineNames.VOSK),
                        vosk.isHealthy(),
                        watchdog.isEngineEnabled(SttEngineNames.WHISPER),
                        whisper.isHealthy()
                );
                throw new TranscriptionException(errorMsg);
            }
        }

        return selected;
    }
}
