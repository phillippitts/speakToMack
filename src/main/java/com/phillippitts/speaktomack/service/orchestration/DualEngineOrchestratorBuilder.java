package com.phillippitts.speaktomack.service.orchestration;

import com.phillippitts.speaktomack.config.hotkey.HotkeyProperties;
import com.phillippitts.speaktomack.config.orchestration.OrchestrationProperties;
import com.phillippitts.speaktomack.config.reconcile.ReconciliationProperties;
import com.phillippitts.speaktomack.service.audio.capture.AudioCaptureService;
import com.phillippitts.speaktomack.service.reconcile.TranscriptReconciler;
import com.phillippitts.speaktomack.service.stt.SttEngine;
import com.phillippitts.speaktomack.service.stt.parallel.ParallelSttService;
import com.phillippitts.speaktomack.service.stt.watchdog.SttEngineWatchdog;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Objects;

/**
 * Builder for {@link DualEngineOrchestrator} to simplify construction with many dependencies.
 *
 * <p>This builder provides a fluent API for constructing orchestrators in both single-engine
 * and reconciliation modes. It enforces that all required dependencies are provided before
 * allowing construction.
 *
 * <p><b>Usage Examples:</b>
 * <pre>{@code
 * // Single-engine mode
 * DualEngineOrchestrator orchestrator = DualEngineOrchestratorBuilder.builder()
 *     .captureService(captureService)
 *     .voskEngine(voskEngine)
 *     .whisperEngine(whisperEngine)
 *     .watchdog(watchdog)
 *     .orchestrationProperties(props)
 *     .hotkeyProperties(hotkeyProps)
 *     .publisher(publisher)
 *     .captureStateMachine(stateMachine)
 *     .engineSelector(selector)
 *     .timingCoordinator(coordinator)
 *     .build();
 *
 * // Reconciliation mode
 * DualEngineOrchestrator orchestrator = DualEngineOrchestratorBuilder.builder()
 *     .captureService(captureService)
 *     .voskEngine(voskEngine)
 *     .whisperEngine(whisperEngine)
 *     .watchdog(watchdog)
 *     .orchestrationProperties(props)
 *     .hotkeyProperties(hotkeyProps)
 *     .publisher(publisher)
 *     .captureStateMachine(stateMachine)
 *     .engineSelector(selector)
 *     .timingCoordinator(coordinator)
 *     .parallelSttService(parallelService)
 *     .transcriptReconciler(reconciler)
 *     .reconciliationProperties(recProps)
 *     .metrics(metrics)
 *     .build();
 * }</pre>
 *
 * @since 1.0
 */
public final class DualEngineOrchestratorBuilder {

    // Required dependencies
    private AudioCaptureService captureService;
    private SttEngine voskEngine;
    private SttEngine whisperEngine;
    private SttEngineWatchdog watchdog;
    private OrchestrationProperties orchestrationProperties;
    private HotkeyProperties hotkeyProperties;
    private ApplicationEventPublisher publisher;
    private CaptureStateMachine captureStateMachine;
    private EngineSelectionStrategy engineSelector;
    private TimingCoordinator timingCoordinator;

    // Optional dependencies (for reconciliation mode)
    private ParallelSttService parallelSttService;
    private TranscriptReconciler transcriptReconciler;
    private ReconciliationProperties reconciliationProperties;
    private TranscriptionMetricsPublisher metricsPublisher;

    private DualEngineOrchestratorBuilder() {
        // Private constructor - use builder() factory method
    }

    /**
     * Creates a new builder instance.
     *
     * @return new builder for DualEngineOrchestrator
     */
    public static DualEngineOrchestratorBuilder builder() {
        return new DualEngineOrchestratorBuilder();
    }

    /**
     * Sets the audio capture service.
     *
     * @param captureService audio capture service (required)
     * @return this builder
     */
    public DualEngineOrchestratorBuilder captureService(AudioCaptureService captureService) {
        this.captureService = captureService;
        return this;
    }

    /**
     * Sets the Vosk STT engine.
     *
     * @param voskEngine Vosk engine (required)
     * @return this builder
     */
    public DualEngineOrchestratorBuilder voskEngine(SttEngine voskEngine) {
        this.voskEngine = voskEngine;
        return this;
    }

    /**
     * Sets the Whisper STT engine.
     *
     * @param whisperEngine Whisper engine (required)
     * @return this builder
     */
    public DualEngineOrchestratorBuilder whisperEngine(SttEngine whisperEngine) {
        this.whisperEngine = whisperEngine;
        return this;
    }

    /**
     * Sets the STT engine watchdog.
     *
     * @param watchdog engine health monitor (required)
     * @return this builder
     */
    public DualEngineOrchestratorBuilder watchdog(SttEngineWatchdog watchdog) {
        this.watchdog = watchdog;
        return this;
    }

    /**
     * Sets the orchestration properties.
     *
     * @param orchestrationProperties orchestration configuration (required)
     * @return this builder
     */
    public DualEngineOrchestratorBuilder orchestrationProperties(
            OrchestrationProperties orchestrationProperties) {
        this.orchestrationProperties = orchestrationProperties;
        return this;
    }

    /**
     * Sets the hotkey properties.
     *
     * @param hotkeyProperties hotkey configuration (required)
     * @return this builder
     */
    public DualEngineOrchestratorBuilder hotkeyProperties(HotkeyProperties hotkeyProperties) {
        this.hotkeyProperties = hotkeyProperties;
        return this;
    }

    /**
     * Sets the application event publisher.
     *
     * @param publisher Spring event publisher (required)
     * @return this builder
     */
    public DualEngineOrchestratorBuilder publisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
        return this;
    }

    /**
     * Sets the capture state machine.
     *
     * @param captureStateMachine state machine for capture lifecycle (required)
     * @return this builder
     */
    public DualEngineOrchestratorBuilder captureStateMachine(CaptureStateMachine captureStateMachine) {
        this.captureStateMachine = captureStateMachine;
        return this;
    }

    /**
     * Sets the engine selection strategy.
     *
     * @param engineSelector strategy for selecting healthy engines (required)
     * @return this builder
     */
    public DualEngineOrchestratorBuilder engineSelector(EngineSelectionStrategy engineSelector) {
        this.engineSelector = engineSelector;
        return this;
    }

    /**
     * Sets the timing coordinator.
     *
     * @param timingCoordinator coordinator for paragraph break timing (required)
     * @return this builder
     */
    public DualEngineOrchestratorBuilder timingCoordinator(TimingCoordinator timingCoordinator) {
        this.timingCoordinator = timingCoordinator;
        return this;
    }

    /**
     * Sets the parallel STT service (optional, for reconciliation mode).
     *
     * @param parallelSttService service for running both engines in parallel
     * @return this builder
     */
    public DualEngineOrchestratorBuilder parallelSttService(ParallelSttService parallelSttService) {
        this.parallelSttService = parallelSttService;
        return this;
    }

    /**
     * Sets the transcript reconciler (optional, for reconciliation mode).
     *
     * @param transcriptReconciler strategy for merging dual-engine results
     * @return this builder
     */
    public DualEngineOrchestratorBuilder transcriptReconciler(TranscriptReconciler transcriptReconciler) {
        this.transcriptReconciler = transcriptReconciler;
        return this;
    }

    /**
     * Sets the reconciliation properties (optional, for reconciliation mode).
     *
     * @param reconciliationProperties reconciliation configuration
     * @return this builder
     */
    public DualEngineOrchestratorBuilder reconciliationProperties(
            ReconciliationProperties reconciliationProperties) {
        this.reconciliationProperties = reconciliationProperties;
        return this;
    }

    /**
     * Sets the transcription metrics publisher.
     *
     * @param metricsPublisher metrics publishing service
     * @return this builder
     */
    public DualEngineOrchestratorBuilder metricsPublisher(TranscriptionMetricsPublisher metricsPublisher) {
        this.metricsPublisher = metricsPublisher;
        return this;
    }

    /**
     * Builds the DualEngineOrchestrator instance.
     *
     * <p>If all reconciliation dependencies are provided, creates an orchestrator in
     * reconciliation mode. Otherwise, creates a single-engine orchestrator.
     *
     * @return configured DualEngineOrchestrator
     * @throws NullPointerException if any required dependency is null
     * @throws IllegalStateException if required dependencies are missing
     */
    public DualEngineOrchestrator build() {
        // Validate required dependencies
        Objects.requireNonNull(captureService, "captureService is required");
        Objects.requireNonNull(voskEngine, "voskEngine is required");
        Objects.requireNonNull(whisperEngine, "whisperEngine is required");
        Objects.requireNonNull(watchdog, "watchdog is required");
        Objects.requireNonNull(orchestrationProperties, "orchestrationProperties is required");
        Objects.requireNonNull(hotkeyProperties, "hotkeyProperties is required");
        Objects.requireNonNull(publisher, "publisher is required");
        Objects.requireNonNull(captureStateMachine, "captureStateMachine is required");
        Objects.requireNonNull(engineSelector, "engineSelector is required");
        Objects.requireNonNull(timingCoordinator, "timingCoordinator is required");

        // Provide default metricsPublisher if not set (for tests)
        TranscriptionMetricsPublisher effectiveMetricsPublisher = metricsPublisher != null
                ? metricsPublisher
                : new TranscriptionMetricsPublisher(null);

        // Create orchestrator with all dependencies (nullable optional ones will be handled by constructor)
        return new DualEngineOrchestrator(
                captureService,
                voskEngine,
                whisperEngine,
                watchdog,
                orchestrationProperties,
                hotkeyProperties,
                publisher,
                parallelSttService,
                transcriptReconciler,
                reconciliationProperties,
                captureStateMachine,
                engineSelector,
                timingCoordinator,
                effectiveMetricsPublisher
        );
    }
}
