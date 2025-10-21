package com.phillippitts.speaktomack.config.reconcile;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "stt.reconciliation")
public class ReconciliationProperties {

    public enum Strategy { SIMPLE, CONFIDENCE, OVERLAP }

    /** Enable reconciled path in orchestrator. */
    private final boolean enabled;

    /** Strategy to use when enabled. */
    @NotNull
    private final Strategy strategy;

    /** Threshold for word-overlap (Jaccard) strategy (0..1). */
    @Min(0)
    @Max(1)
    private final double overlapThreshold;

    /**
     * Confidence threshold for smart reconciliation (0..1).
     * If Vosk confidence < this threshold, run Whisper too and reconcile.
     * Set to 0.0 to always run dual-engine, 1.0 to never upgrade to dual-engine.
     */
    @Min(0)
    @Max(1)
    private final double confidenceThreshold;

    @ConstructorBinding
    public ReconciliationProperties(Boolean enabled, Strategy strategy, Double overlapThreshold,
                                    Double confidenceThreshold) {
        this.enabled = enabled == null ? false : enabled;
        this.strategy = strategy == null ? Strategy.SIMPLE : strategy;
        double t = overlapThreshold == null ? 0.6 : overlapThreshold;
        if (t < 0.0 || t > 1.0) {
            throw new IllegalArgumentException("stt.reconciliation.overlap-threshold must be in [0,1]");
        }
        this.overlapThreshold = t;

        double ct = confidenceThreshold == null ? 0.7 : confidenceThreshold;
        if (ct < 0.0 || ct > 1.0) {
            throw new IllegalArgumentException("stt.reconciliation.confidence-threshold must be in [0,1]");
        }
        this.confidenceThreshold = ct;
    }

    // Backward-compatible constructor for tests and manual instantiation
    public ReconciliationProperties(Boolean enabled, Strategy strategy, Double overlapThreshold) {
        this(enabled, strategy, overlapThreshold, null);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Strategy getStrategy() {
        return strategy;
    }

    public double getOverlapThreshold() {
        return overlapThreshold;
    }

    public double getConfidenceThreshold() {
        return confidenceThreshold;
    }
}
