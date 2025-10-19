package com.phillippitts.speaktomack.config.orchestration;

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

    @ConstructorBinding
    public OrchestrationProperties(PrimaryEngine primaryEngine) {
        this.primaryEngine = primaryEngine == null ? PrimaryEngine.VOSK : primaryEngine;
    }

    public PrimaryEngine getPrimaryEngine() {
        return primaryEngine;
    }
}
