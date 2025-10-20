package com.phillippitts.speaktomack.config.orchestration;

import com.phillippitts.speaktomack.config.reconcile.ReconciliationProperties;
import com.phillippitts.speaktomack.service.audio.capture.AudioCaptureService;
import com.phillippitts.speaktomack.service.orchestration.DualEngineOrchestrator;
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
    public DualEngineOrchestrator dualEngineOrchestrator(AudioCaptureService captureService,
                                                         SttEngine voskSttEngine,
                                                         SttEngine whisperSttEngine,
                                                         SttEngineWatchdog watchdog,
                                                         OrchestrationProperties props,
                                                         ApplicationEventPublisher publisher) {
        return new DualEngineOrchestrator(captureService, voskSttEngine, whisperSttEngine, watchdog, props, publisher);
    }

    /**
     * Reconciled orchestrator. Active when stt.reconciliation.enabled=true.
     */
    @Bean
    @ConditionalOnProperty(prefix = "stt.reconciliation", name = "enabled", havingValue = "true")
    public DualEngineOrchestrator reconciledDualEngineOrchestrator(AudioCaptureService captureService,
                                                                   SttEngine voskSttEngine,
                                                                   SttEngine whisperSttEngine,
                                                                   SttEngineWatchdog watchdog,
                                                                   OrchestrationProperties props,
                                                                   ApplicationEventPublisher publisher,
                                                                   ParallelSttService parallelSttService,
                                                                   TranscriptReconciler transcriptReconciler,
                                                                   ReconciliationProperties reconciliationProperties) {
        return new DualEngineOrchestrator(
                captureService,
                voskSttEngine,
                whisperSttEngine,
                watchdog,
                props,
                publisher,
                parallelSttService,
                transcriptReconciler,
                reconciliationProperties
        );
    }
}
