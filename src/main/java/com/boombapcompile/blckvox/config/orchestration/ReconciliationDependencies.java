package com.boombapcompile.blckvox.config.orchestration;

import com.boombapcompile.blckvox.config.properties.ReconciliationProperties;
import com.boombapcompile.blckvox.service.reconcile.TranscriptReconciler;
import com.boombapcompile.blckvox.service.stt.parallel.ParallelSttService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Groups optional reconciliation dependencies for cleaner constructor injection.
 * Keeps parameter count in OrchestrationConfig at 9 instead of 11 parameters.
 *
 * <p>Only created when reconciliation is enabled, matching the conditional
 * {@link TranscriptReconciler} bean it depends on.
 */
@Component
@ConditionalOnProperty(prefix = "stt.reconciliation", name = "enabled", havingValue = "true")
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
