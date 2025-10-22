package com.phillippitts.speaktomack.service.orchestration;

import com.phillippitts.speaktomack.config.properties.OrchestrationProperties;
import com.phillippitts.speaktomack.config.properties.ReconciliationProperties;
import com.phillippitts.speaktomack.domain.TranscriptionResult;
import com.phillippitts.speaktomack.exception.TranscriptionException;
import com.phillippitts.speaktomack.service.orchestration.event.TranscriptionCompletedEvent;
import com.phillippitts.speaktomack.service.reconcile.TranscriptReconciler;
import com.phillippitts.speaktomack.service.stt.SttEngine;
import com.phillippitts.speaktomack.service.stt.SttEngineNames;
import com.phillippitts.speaktomack.service.stt.parallel.ParallelSttService;
import com.phillippitts.speaktomack.service.stt.watchdog.SttEngineWatchdog;
import com.phillippitts.speaktomack.util.TimeUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.Objects;

/**
 * Default implementation of {@link TranscriptionOrchestrator} supporting single-engine
 * and dual-engine reconciliation modes.
 *
 * <p>This implementation coordinates transcription execution with engine selection, confidence
 * evaluation, result publishing, and metrics recording. All operations are thread-safe.
 *
 * <p><b>Single-Engine Mode:</b> Selects one engine based on primary preference and health status.
 * Automatically upgrades to reconciliation if Vosk confidence is below threshold.
 *
 * <p><b>Reconciliation Mode:</b> Runs both engines in parallel and reconciles results using
 * configurable strategy (SIMPLE, CONFIDENCE, or OVERLAP).
 *
 * <p><b>Error Handling:</b> Transcription failures result in empty text being published to
 * notify downstream consumers while preventing incorrect text emission.
 *
 * @since 1.1
 */
public class DefaultTranscriptionOrchestrator implements TranscriptionOrchestrator {

    private static final Logger LOG = LogManager.getLogger(DefaultTranscriptionOrchestrator.class);

    // Engine name constants
    private static final String ENGINE_RECONCILED = "reconciled";

    // Timeout value indicating "use service default"
    private static final long USE_DEFAULT_TIMEOUT = 0L;

    private final SttEngine vosk;
    private final SttEngine whisper;
    private final SttEngineWatchdog watchdog;
    private final OrchestrationProperties props;
    private final ApplicationEventPublisher publisher;

    // Optional reconciliation components
    private final ParallelSttService parallel;
    private final TranscriptReconciler reconciler;
    private final ReconciliationProperties recProps;

    // State machines and services
    private final EngineSelectionStrategy engineSelector;
    private final TimingCoordinator timingCoordinator;
    private final TranscriptionMetricsPublisher metricsPublisher;

    /**
     * Constructs a DefaultTranscriptionOrchestrator.
     *
     * @param vosk Vosk STT engine instance
     * @param whisper Whisper STT engine instance
     * @param watchdog engine health monitor and enablement controller
     * @param props orchestration configuration (silence gap threshold)
     * @param publisher Spring event publisher for transcription results
     * @param parallel service for running both engines in parallel (nullable for single-engine mode)
     * @param reconciler strategy for merging dual-engine results (nullable for single-engine mode)
     * @param recProps reconciliation configuration and enablement flag (nullable for single-engine mode)
     * @param engineSelector strategy for selecting STT engine
     * @param timingCoordinator coordinator for timing and paragraph breaks
     * @param metricsPublisher metrics publishing service
     * @throws NullPointerException if any required parameter is null
     */
    // CHECKSTYLE.OFF: ParameterNumber - Complex orchestrator requires many dependencies
    public DefaultTranscriptionOrchestrator(SttEngine vosk,
                                            SttEngine whisper,
                                            SttEngineWatchdog watchdog,
                                            OrchestrationProperties props,
                                            ApplicationEventPublisher publisher,
                                            ParallelSttService parallel,
                                            TranscriptReconciler reconciler,
                                            ReconciliationProperties recProps,
                                            EngineSelectionStrategy engineSelector,
                                            TimingCoordinator timingCoordinator,
                                            TranscriptionMetricsPublisher metricsPublisher) {
        this.vosk = Objects.requireNonNull(vosk, "vosk must not be null");
        this.whisper = Objects.requireNonNull(whisper, "whisper must not be null");
        this.watchdog = Objects.requireNonNull(watchdog, "watchdog must not be null");
        this.props = Objects.requireNonNull(props, "props must not be null");
        this.publisher = Objects.requireNonNull(publisher, "publisher must not be null");
        this.parallel = parallel;
        this.reconciler = reconciler;
        this.recProps = recProps;
        this.engineSelector = Objects.requireNonNull(engineSelector, "engineSelector must not be null");
        this.timingCoordinator = Objects.requireNonNull(timingCoordinator, "timingCoordinator must not be null");
        this.metricsPublisher = Objects.requireNonNull(metricsPublisher, "metricsPublisher must not be null");
    }
    // CHECKSTYLE.ON: ParameterNumber

    @Override
    public void transcribe(byte[] pcm) {
        Objects.requireNonNull(pcm, "pcm must not be null");

        // If reconciliation is enabled and services are present, run both engines and reconcile
        if (isReconciliationEnabled()) {
            transcribeWithReconciliation(pcm);
        } else {
            transcribeWithSingleEngine(pcm);
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
            if (isReconciliationEnabled()
                    && SttEngineNames.VOSK.equals(engineName)
                    && result.confidence() < recProps.getConfidenceThreshold()) {

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
}
