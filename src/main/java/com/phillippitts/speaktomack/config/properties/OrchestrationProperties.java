package com.phillippitts.speaktomack.config.properties;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Typed properties for STT orchestration.
 */
@Validated
@ConfigurationProperties(prefix = "stt.orchestration")
public record OrchestrationProperties(

        @DefaultValue("VOSK")
        @NotNull
        PrimaryEngine primaryEngine,

        /**
         * Silence gap threshold in milliseconds. If time between transcriptions exceeds this value,
         * a newline is prepended to the next transcription for better paragraph formatting.
         * Set to 0 to disable automatic paragraph breaks.
         */
        @DefaultValue("1000")
        @Min(0)
        int silenceGapMs
) {

    public enum PrimaryEngine { VOSK, WHISPER }

    public PrimaryEngine getPrimaryEngine() {
        return primaryEngine;
    }

    public int getSilenceGapMs() {
        return silenceGapMs;
    }
}
