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
import com.phillippitts.speaktomack.service.metrics.TranscriptionMetrics;
import com.phillippitts.speaktomack.service.stt.parallel.ParallelSttService;
import com.phillippitts.speaktomack.service.stt.watchdog.SttEngineWatchdog;
import com.phillippitts.speaktomack.util.TimeUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Orchestrates push-to-talk workflow: audio capture on hotkey press, transcription on release.
 *
 * <p>This orchestrator coordinates the complete speech-to-text pipeline:
 * <ol>
 *   <li><b>Hotkey Press:</b> Starts audio capture session via {@link AudioCaptureService}</li>
 *   <li><b>Hotkey Release:</b> Stops capture, retrieves audio, selects engine, transcribes</li>
 *   <li><b>Engine Selection:</b> Uses {@link SttEngineWatchdog} to choose healthy engine
 *       based on primary preference</li>
 *   <li><b>Transcription:</b> Supports both single-engine and dual-engine reconciliation modes</li>
 *   <li><b>Event Publishing:</b> Emits {@link TranscriptionCompletedEvent} for downstream processing</li>
 * </ol>
 *
 * <p><b>Operating Modes:</b>
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

    // Optional metrics (nullable for tests)
    private final TranscriptionMetrics metrics;

    private final Object lock = new Object();
    private UUID activeSession;

    // Track last transcription timestamp for automatic paragraph breaks
    private volatile long lastTranscriptionTimeMs = 0;

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
     * @param publisher Spring event publisher for transcription results
     * @throws NullPointerException if any parameter is null
     * @see #DualEngineOrchestrator(AudioCaptureService, SttEngine, SttEngine, SttEngineWatchdog,
     *      OrchestrationProperties, ApplicationEventPublisher, ParallelSttService,
     *      TranscriptReconciler, ReconciliationProperties)
     */
    public DualEngineOrchestrator(AudioCaptureService captureService,
                                  SttEngine vosk,
                                  SttEngine whisper,
                                  SttEngineWatchdog watchdog,
                                  OrchestrationProperties props,
                                  ApplicationEventPublisher publisher) {
        this(captureService, vosk, whisper, watchdog, props, publisher, null, null, null, null);
    }

    /**
     * Constructs a DualEngineOrchestrator with optional reconciliation and metrics support.
     *
     * <p>When all Phase 4 parameters ({@code parallel}, {@code reconciler}, {@code recProps})
     * are provided and {@code recProps.isEnabled() == true}, the orchestrator runs both engines
     * in parallel and reconciles their results using the configured strategy.
     *
     * <p>If any Phase 4 parameter is {@code null} or reconciliation is disabled, the orchestrator
     * falls back to single-engine mode (same behavior as the 6-parameter constructor).
     *
     * @param captureService service for audio capture lifecycle management
     * @param vosk Vosk STT engine instance
     * @param whisper Whisper STT engine instance
     * @param watchdog engine health monitor and enablement controller
     * @param props orchestration configuration (primary engine preference)
     * @param publisher Spring event publisher for transcription results
     * @param parallel service for running both engines in parallel (nullable)
     * @param reconciler strategy for merging dual-engine results (nullable)
     * @param recProps reconciliation configuration and enablement flag (nullable)
     * @param metrics metrics tracking service (nullable)
     * @throws NullPointerException if any required parameter is null (Phase 4 params are optional)
     * @see ParallelSttService
     * @see TranscriptReconciler
     * @see ReconciliationProperties
     */
    public DualEngineOrchestrator(AudioCaptureService captureService,
                                  SttEngine vosk,
                                  SttEngine whisper,
                                  SttEngineWatchdog watchdog,
                                  OrchestrationProperties props,
                                  ApplicationEventPublisher publisher,
                                  ParallelSttService parallel,
                                  TranscriptReconciler reconciler,
                                  ReconciliationProperties recProps,
                                  TranscriptionMetrics metrics) {
        this.captureService = Objects.requireNonNull(captureService);
        this.vosk = Objects.requireNonNull(vosk);
        this.whisper = Objects.requireNonNull(whisper);
        this.watchdog = Objects.requireNonNull(watchdog);
        this.props = Objects.requireNonNull(props);
        this.publisher = Objects.requireNonNull(publisher);
        this.parallel = parallel;
        this.reconciler = reconciler;
        this.recProps = recProps;
        this.metrics = metrics;
    }

    /**
     * Handles hotkey press events by starting a new audio capture session.
     *
     * <p>This method is invoked asynchronously by Spring's event system when the user presses
     * the configured hotkey (e.g., Cmd+Shift+M on macOS). It starts a new capture session
     * via {@link AudioCaptureService} and records the session ID for later retrieval.
     *
     * <p><b>Thread Safety:</b> Synchronized on {@code lock} to ensure only one session can
     * be active at a time. Duplicate presses during an active session are ignored to prevent
     * audio stream corruption.
     *
     * <p><b>Performance:</b> This method completes in &lt;5ms under normal conditions. The
     * audio capture runs on a background thread managed by {@link AudioCaptureService}.
     *
     * @param evt the hotkey pressed event with timestamp
     * @see HotkeyPressedEvent
     * @see AudioCaptureService#startSession()
     */
    @EventListener
    public void onHotkeyPressed(HotkeyPressedEvent evt) {
        synchronized (lock) {
            if (activeSession != null) {
                LOG.debug("Capture already active (session={})", activeSession);
                return; // Ignore duplicate presses
            }
            activeSession = captureService.startSession();
            LOG.info("Capture session started at {} (session={})", evt.at(), activeSession);
        }
    }

    /**
     * Handles hotkey release events by stopping capture and initiating transcription.
     *
     * <p>This method is invoked asynchronously by Spring's event system when the user releases
     * the hotkey. It performs the following workflow:
     * <ol>
     *   <li>Stops the active capture session (synchronized)</li>
     *   <li>Retrieves captured PCM audio data</li>
     *   <li>Selects transcription mode (single-engine vs. reconciliation)</li>
     *   <li>Transcribes audio using selected mode</li>
     *   <li>Publishes {@link TranscriptionCompletedEvent} on success</li>
     * </ol>
     *
     * <p><b>Error Handling:</b> If no active session exists or audio retrieval fails, the
     * method returns early without attempting transcription. Transcription failures are logged
     * but do not crash the application.
     *
     * <p><b>Performance:</b> Transcription is CPU-intensive and may take 1-5 seconds depending
     * on audio length and engine choice. This method blocks on Spring's event thread pool during
     * transcription. Future optimization may move transcription to a dedicated thread pool.
     *
     * @param evt the hotkey released event with timestamp
     * @see HotkeyReleasedEvent
     * @see AudioCaptureService#stopSession(UUID)
     * @see AudioCaptureService#readAll(UUID)
     * @see TranscriptionCompletedEvent
     */
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
        long startTime = System.nanoTime();
        try {
            var pair = parallel.transcribeBoth(pcm, USE_DEFAULT_TIMEOUT);
            TranscriptionResult result = reconciler.reconcile(pair.vosk(), pair.whisper());
            String strategy = String.valueOf(recProps.getStrategy());

            // Record metrics
            if (metrics != null) {
                long duration = System.nanoTime() - startTime;
                metrics.recordLatency(ENGINE_RECONCILED, duration);
                metrics.incrementSuccess(ENGINE_RECONCILED);
                metrics.recordReconciliation(strategy, result.engineName());
            }

            logTranscriptionSuccess(ENGINE_RECONCILED, startTime, result.text().length(), strategy);
            publishResult(result, ENGINE_RECONCILED);
        } catch (TranscriptionException te) {
            if (metrics != null) {
                metrics.incrementFailure(ENGINE_RECONCILED, "transcription_error");
            }
            LOG.warn("Reconciled transcription failed: {}", te.getMessage());
        } catch (RuntimeException re) {
            if (metrics != null) {
                metrics.incrementFailure(ENGINE_RECONCILED, "unexpected_error");
            }
            LOG.error("Unexpected error during reconciled transcription", re);
        }
    }

    /**
     * Transcribes audio using a single engine selected based on watchdog state.
     * Implements smart reconciliation: if Vosk confidence is below threshold,
     * automatically upgrades to dual-engine mode for better accuracy.
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
                ENGINE_VOSK.equals(engineName) &&
                result.confidence() < recProps.getConfidenceThreshold()) {

                LOG.info("Vosk confidence {:.3f} < threshold {:.3f}, upgrading to dual-engine reconciliation",
                         result.confidence(), recProps.getConfidenceThreshold());

                // Upgrade to dual-engine mode for this transcription
                transcribeWithReconciliation(pcm);
                return; // Exit early - reconciliation handles metrics & publishing
            }

            // Record metrics for successful single-engine transcription
            if (metrics != null) {
                long duration = System.nanoTime() - startTime;
                metrics.recordLatency(engineName, duration);
                metrics.incrementSuccess(engineName);
            }

            logTranscriptionSuccess(engineName, startTime, result.text().length(), null);
            publishResult(result, engineName);
        } catch (TranscriptionException te) {
            if (metrics != null) {
                metrics.incrementFailure(engine.getEngineName(), "transcription_error");
            }
            LOG.warn("Transcription failed: {}", te.getMessage());
        } catch (RuntimeException re) {
            if (metrics != null) {
                metrics.incrementFailure(engine.getEngineName(), "unexpected_error");
            }
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
        int silenceGapMs = props.getSilenceGapMs();
        TranscriptionResult finalResult = result;

        if (silenceGapMs > 0) {
            long currentTimeMs = System.currentTimeMillis();
            long lastTime = lastTranscriptionTimeMs;

            if (lastTime > 0) {
                long gapMs = currentTimeMs - lastTime;
                if (gapMs >= silenceGapMs) {
                    // Prepend newline for new paragraph
                    String textWithNewline = "\n" + result.text();
                    finalResult = TranscriptionResult.of(textWithNewline, result.confidence(), result.engineName());
                    LOG.debug("Prepended newline after {}ms silence gap (threshold={}ms)", gapMs, silenceGapMs);
                }
            }

            // Update last transcription time
            lastTranscriptionTimeMs = currentTimeMs;
        }

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
    private SttEngine selectSingleEngine() {
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
