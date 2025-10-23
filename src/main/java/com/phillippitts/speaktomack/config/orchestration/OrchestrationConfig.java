package com.phillippitts.speaktomack.config.orchestration;

import com.phillippitts.speaktomack.config.properties.HotkeyProperties;
import com.phillippitts.speaktomack.service.audio.capture.AudioCaptureService;
import com.phillippitts.speaktomack.service.orchestration.CaptureStateMachine;
import com.phillippitts.speaktomack.service.orchestration.DualEngineOrchestrator;
import com.phillippitts.speaktomack.config.properties.OrchestrationProperties;
import com.phillippitts.speaktomack.service.orchestration.DualEngineOrchestratorBuilder;
import com.phillippitts.speaktomack.service.orchestration.EngineSelectionStrategy;
import com.phillippitts.speaktomack.service.orchestration.TranscriptionMetricsPublisher;
import com.phillippitts.speaktomack.service.stt.SttEngine;
import com.phillippitts.speaktomack.service.stt.watchdog.SttEngineWatchdog;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the DualEngineOrchestrator explicitly to avoid bean ambiguity.
 * Uses constructor injection to manage common dependencies across bean methods.
 */
@Configuration
public class OrchestrationConfig {

    // Core dependencies shared across all orchestrators
    private final AudioCaptureService captureService;
    private final SttEngine voskSttEngine;
    private final SttEngine whisperSttEngine;
    private final SttEngineWatchdog watchdog;
    private final OrchestrationProperties orchestrationProperties;
    private final HotkeyProperties hotkeyProperties;
    private final ApplicationEventPublisher publisher;
    private final TranscriptionMetricsPublisher metricsPublisher;
    private final ReconciliationDependencies reconciliationDeps;

    /**
     * Constructor injection for all orchestration dependencies.
     * Reconciliation dependencies are grouped in ReconciliationDependencies.
     */
    public OrchestrationConfig(AudioCaptureService captureService,
                               SttEngine voskSttEngine,
                               SttEngine whisperSttEngine,
                               SttEngineWatchdog watchdog,
                               OrchestrationProperties orchestrationProperties,
                               HotkeyProperties hotkeyProperties,
                               ApplicationEventPublisher publisher,
                               TranscriptionMetricsPublisher metricsPublisher,
                               ReconciliationDependencies reconciliationDeps) {
        this.captureService = captureService;
        this.voskSttEngine = voskSttEngine;
        this.whisperSttEngine = whisperSttEngine;
        this.watchdog = watchdog;
        this.orchestrationProperties = orchestrationProperties;
        this.hotkeyProperties = hotkeyProperties;
        this.publisher = publisher;
        this.metricsPublisher = metricsPublisher;
        this.reconciliationDeps = reconciliationDeps;
    }

    /**
     * Capture state machine for managing audio capture session lifecycle.
     */
    @Bean
    public CaptureStateMachine captureStateMachine() {
        return new CaptureStateMachine();
    }

    /**
     * Engine selection strategy for choosing healthy STT engines.
     */
    @Bean
    public EngineSelectionStrategy engineSelectionStrategy() {
        return new EngineSelectionStrategy(voskSttEngine, whisperSttEngine,
                watchdog, orchestrationProperties);
    }

    /**
     * Default orchestrator (no reconciliation).
     * Active when stt.reconciliation.enabled is false or missing.
     */
    @Bean
    @ConditionalOnProperty(
            prefix = "stt.reconciliation",
            name = "enabled",
            havingValue = "false",
            matchIfMissing = true
    )
    public DualEngineOrchestrator dualEngineOrchestrator(CaptureStateMachine captureStateMachine,
                                                         EngineSelectionStrategy engineSelector) {
        return DualEngineOrchestratorBuilder.builder()
                .captureService(this.captureService)
                .voskEngine(this.voskSttEngine)
                .whisperEngine(this.whisperSttEngine)
                .watchdog(this.watchdog)
                .orchestrationProperties(this.orchestrationProperties)
                .hotkeyProperties(this.hotkeyProperties)
                .publisher(this.publisher)
                .metricsPublisher(this.metricsPublisher)
                .captureStateMachine(captureStateMachine)
                .engineSelector(engineSelector)
                .build();
    }

    /**
     * Reconciled orchestrator. Active when stt.reconciliation.enabled=true.
     */
    @Bean
    @ConditionalOnProperty(prefix = "stt.reconciliation", name = "enabled", havingValue = "true")
    public DualEngineOrchestrator reconciledDualEngineOrchestrator(CaptureStateMachine captureStateMachine,
                                                                   EngineSelectionStrategy engineSelector) {
        return DualEngineOrchestratorBuilder.builder()
                .captureService(this.captureService)
                .voskEngine(this.voskSttEngine)
                .whisperEngine(this.whisperSttEngine)
                .watchdog(this.watchdog)
                .orchestrationProperties(this.orchestrationProperties)
                .hotkeyProperties(this.hotkeyProperties)
                .publisher(this.publisher)
                .parallelSttService(this.reconciliationDeps.getParallelSttService())
                .transcriptReconciler(this.reconciliationDeps.getTranscriptReconciler())
                .reconciliationProperties(this.reconciliationDeps.getReconciliationProperties())
                .metricsPublisher(this.metricsPublisher)
                .captureStateMachine(captureStateMachine)
                .engineSelector(engineSelector)
                .build();
    }
}
