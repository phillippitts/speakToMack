package com.phillippitts.speaktomack.service.orchestration;

import com.phillippitts.speaktomack.config.properties.ReconciliationProperties;
import com.phillippitts.speaktomack.domain.TranscriptionResult;
import com.phillippitts.speaktomack.service.reconcile.TranscriptReconciler;
import com.phillippitts.speaktomack.service.stt.parallel.ParallelSttService;

import java.util.Objects;

/**
 * Default implementation of {@link ReconciliationService} supporting dual-engine reconciliation.
 *
 * <p>This implementation coordinates parallel transcription execution with result reconciliation
 * using the configured strategy. All operations are thread-safe.
 *
 * <p><b>Reconciliation Process:</b>
 * <ol>
 *   <li>Run both engines in parallel via {@link ParallelSttService}</li>
 *   <li>Wait for both engines to complete</li>
 *   <li>Reconcile results using {@link TranscriptReconciler}</li>
 *   <li>Return final reconciled result</li>
 * </ol>
 *
 * @since 1.1
 */
public final class DefaultReconciliationService implements ReconciliationService {

    // Timeout value indicating "use service default"
    private static final long USE_DEFAULT_TIMEOUT = 0L;

    private final ParallelSttService parallel;
    private final TranscriptReconciler reconciler;
    private final ReconciliationProperties props;

    /**
     * Constructs a DefaultReconciliationService.
     *
     * @param parallel service for running both engines in parallel
     * @param reconciler strategy for merging dual-engine results
     * @param props reconciliation configuration and enablement flag
     * @throws NullPointerException if any parameter is null
     */
    public DefaultReconciliationService(ParallelSttService parallel,
                                        TranscriptReconciler reconciler,
                                        ReconciliationProperties props) {
        this.parallel = Objects.requireNonNull(parallel, "parallel must not be null");
        this.reconciler = Objects.requireNonNull(reconciler, "reconciler must not be null");
        this.props = Objects.requireNonNull(props, "props must not be null");
    }

    @Override
    public boolean isEnabled() {
        return props.isEnabled();
    }

    @Override
    public TranscriptionResult reconcile(byte[] pcm) {
        Objects.requireNonNull(pcm, "pcm must not be null");

        if (!isEnabled()) {
            throw new IllegalStateException("Reconciliation is not enabled");
        }

        var pair = parallel.transcribeBoth(pcm, USE_DEFAULT_TIMEOUT);
        return reconciler.reconcile(pair.vosk(), pair.whisper());
    }

    @Override
    public double getConfidenceThreshold() {
        if (!isEnabled()) {
            throw new IllegalStateException("Reconciliation is not enabled");
        }
        return props.getConfidenceThreshold();
    }

    @Override
    public String getStrategy() {
        if (!isEnabled()) {
            throw new IllegalStateException("Reconciliation is not enabled");
        }
        return String.valueOf(props.getStrategy());
    }

    /**
     * Creates a no-op reconciliation service for when reconciliation is disabled.
     *
     * <p>This factory method is used when any of the reconciliation dependencies
     * are null (parallel, reconciler, or props). The returned service will always
     * report {@link #isEnabled()} as false and throw IllegalStateException if
     * reconciliation is attempted.
     *
     * @return no-op reconciliation service
     */
    public static ReconciliationService disabled() {
        return DisabledReconciliationService.INSTANCE;
    }

    /**
     * Singleton no-op implementation used when reconciliation is disabled.
     */
    private static final class DisabledReconciliationService implements ReconciliationService {
        private static final DisabledReconciliationService INSTANCE = new DisabledReconciliationService();

        private DisabledReconciliationService() {
            // Private constructor for singleton
        }

        @Override
        public boolean isEnabled() {
            return false;
        }

        @Override
        public TranscriptionResult reconcile(byte[] pcm) {
            throw new IllegalStateException("Reconciliation is not enabled");
        }

        @Override
        public double getConfidenceThreshold() {
            throw new IllegalStateException("Reconciliation is not enabled");
        }

        @Override
        public String getStrategy() {
            throw new IllegalStateException("Reconciliation is not enabled");
        }
    }
}
