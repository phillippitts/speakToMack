package com.phillippitts.speaktomack.config.orchestration;

import com.phillippitts.speaktomack.config.hotkey.HotkeyProperties;
import com.phillippitts.speaktomack.config.reconcile.ReconciliationProperties;
import com.phillippitts.speaktomack.service.audio.capture.AudioCaptureService;
import com.phillippitts.speaktomack.service.metrics.TranscriptionMetrics;
import com.phillippitts.speaktomack.service.orchestration.CaptureStateMachine;
import com.phillippitts.speaktomack.service.orchestration.DualEngineOrchestrator;
import com.phillippitts.speaktomack.service.orchestration.DualEngineOrchestratorBuilder;
import com.phillippitts.speaktomack.service.orchestration.EngineSelectionStrategy;
import com.phillippitts.speaktomack.service.orchestration.TimingCoordinator;
import com.phillippitts.speaktomack.service.reconcile.TranscriptReconciler;
import com.phillippitts.speaktomack.service.stt.SttEngine;
import com.phillippitts.speaktomack.service.stt.parallel.ParallelSttService;
import com.phillippitts.speaktomack.service.stt.watchdog.SttEngineWatchdog;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the DualEngineOrchestrator explicitly to avoid bean ambiguity.
 */
@Configuration
public class OrchestrationConfig {

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
    public EngineSelectionStrategy engineSelectionStrategy(SttEngine voskSttEngine,
                                                            SttEngine whisperSttEngine,
                                                            SttEngineWatchdog watchdog,
                                                            OrchestrationProperties props) {
        return new EngineSelectionStrategy(voskSttEngine, whisperSttEngine, watchdog, props);
    }

    /**
     * Timing coordinator for paragraph break logic.
     */
    @Bean
    public TimingCoordinator timingCoordinator(OrchestrationProperties props) {
        return new TimingCoordinator(props);
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
    // CHECKSTYLE.OFF: ParameterNumber - Spring bean factory method requires many dependencies
    public DualEngineOrchestrator dualEngineOrchestrator(AudioCaptureService captureService,
                                                         SttEngine voskSttEngine,
                                                         SttEngine whisperSttEngine,
                                                         SttEngineWatchdog watchdog,
                                                         OrchestrationProperties props,
                                                         HotkeyProperties hotkeyProps,
                                                         ApplicationEventPublisher publisher,
                                                         TranscriptionMetrics metrics,
                                                         CaptureStateMachine captureStateMachine,
                                                         EngineSelectionStrategy engineSelector,
                                                         TimingCoordinator timingCoordinator) {
        return DualEngineOrchestratorBuilder.builder()
                .captureService(captureService)
                .voskEngine(voskSttEngine)
                .whisperEngine(whisperSttEngine)
                .watchdog(watchdog)
                .orchestrationProperties(props)
                .hotkeyProperties(hotkeyProps)
                .publisher(publisher)
                .metrics(metrics)
                .captureStateMachine(captureStateMachine)
                .engineSelector(engineSelector)
                .timingCoordinator(timingCoordinator)
                .build();
    }
    // CHECKSTYLE.ON: ParameterNumber

    /**
     * Reconciled orchestrator. Active when stt.reconciliation.enabled=true.
     */
    @Bean
    @ConditionalOnProperty(prefix = "stt.reconciliation", name = "enabled", havingValue = "true")
    // CHECKSTYLE.OFF: ParameterNumber - Bean factory method requires many dependencies
    public DualEngineOrchestrator reconciledDualEngineOrchestrator(AudioCaptureService captureService,
                                                                   SttEngine voskSttEngine,
                                                                   SttEngine whisperSttEngine,
                                                                   SttEngineWatchdog watchdog,
                                                                   OrchestrationProperties props,
                                                                   HotkeyProperties hotkeyProps,
                                                                   ApplicationEventPublisher publisher,
                                                                   ParallelSttService parallelSttService,
                                                                   TranscriptReconciler transcriptReconciler,
                                                                   ReconciliationProperties reconciliationProperties,
                                                                   TranscriptionMetrics metrics,
                                                                   CaptureStateMachine captureStateMachine,
                                                                   EngineSelectionStrategy engineSelector,
                                                                   TimingCoordinator timingCoordinator) {
        return DualEngineOrchestratorBuilder.builder()
                .captureService(captureService)
                .voskEngine(voskSttEngine)
                .whisperEngine(whisperSttEngine)
                .watchdog(watchdog)
                .orchestrationProperties(props)
                .hotkeyProperties(hotkeyProps)
                .publisher(publisher)
                .parallelSttService(parallelSttService)
                .transcriptReconciler(transcriptReconciler)
                .reconciliationProperties(reconciliationProperties)
                .metrics(metrics)
                .captureStateMachine(captureStateMachine)
                .engineSelector(engineSelector)
                .timingCoordinator(timingCoordinator)
                .build();
    }
    // CHECKSTYLE.ON: ParameterNumber
}
