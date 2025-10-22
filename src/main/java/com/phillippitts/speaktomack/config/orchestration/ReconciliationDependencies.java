package com.phillippitts.speaktomack.config.orchestration;

import com.phillippitts.speaktomack.config.properties.ReconciliationProperties;
import com.phillippitts.speaktomack.service.reconcile.TranscriptReconciler;
import com.phillippitts.speaktomack.service.stt.parallel.ParallelSttService;
import org.springframework.stereotype.Component;

/**
 * Groups optional reconciliation dependencies for cleaner constructor injection.
 * Reduces parameter count in OrchestrationConfig from 11 to 9 parameters.
 */
@Component
public final class ReconciliationDependencies {
    private final ParallelSttService parallelSttService;
    private final TranscriptReconciler transcriptReconciler;
    private final ReconciliationProperties reconciliationProperties;

    public ReconciliationDependencies(ParallelSttService parallelSttService,
                                      TranscriptReconciler transcriptReconciler,
                                      ReconciliationProperties reconciliationProperties) {
        this.parallelSttService = parallelSttService;
        this.transcriptReconciler = transcriptReconciler;
        this.reconciliationProperties = reconciliationProperties;
    }

    public ParallelSttService getParallelSttService() {
        return parallelSttService;
    }

    public TranscriptReconciler getTranscriptReconciler() {
        return transcriptReconciler;
    }

    public ReconciliationProperties getReconciliationProperties() {
        return reconciliationProperties;
    }
}
