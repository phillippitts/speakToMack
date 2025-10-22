package com.phillippitts.speaktomack.config.properties;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.validation.annotation.Validated;

/**
 * Typed properties for STT orchestration.
 */
@Validated
@ConfigurationProperties(prefix = "stt.orchestration")
public class OrchestrationProperties {

    public enum PrimaryEngine { VOSK, WHISPER }

    @NotNull
    private final PrimaryEngine primaryEngine;

    /**
     * Silence gap threshold in milliseconds. If time between transcriptions exceeds this value,
     * a newline is prepended to the next transcription for better paragraph formatting.
     * Set to 0 to disable automatic paragraph breaks.
     */
    @Min(0)
    private final int silenceGapMs;

    @ConstructorBinding
    public OrchestrationProperties(PrimaryEngine primaryEngine, Integer silenceGapMs) {
        this.primaryEngine = primaryEngine == null ? PrimaryEngine.VOSK : primaryEngine;
        this.silenceGapMs = silenceGapMs == null ? 1000 : silenceGapMs; // Default 1 second
    }

    /**
     * Backward-compatible constructor for tests (defaults silence gap to 1 second).
     */
    public OrchestrationProperties(PrimaryEngine primaryEngine) {
        this(primaryEngine, 1000);
    }

    public PrimaryEngine getPrimaryEngine() {
        return primaryEngine;
    }

    public int getSilenceGapMs() {
        return silenceGapMs;
    }
}
