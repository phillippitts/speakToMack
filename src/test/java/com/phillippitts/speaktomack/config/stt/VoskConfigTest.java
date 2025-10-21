package com.phillippitts.speaktomack.config.stt;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class VoskConfigTest {

    private Validator validator;

    @BeforeEach
    void setup() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void shouldCreateValidConfig() {
        VoskConfig config = new VoskConfig("models/vosk", 16_000, 1);

        assertThat(config.modelPath()).isEqualTo("models/vosk");
        assertThat(config.sampleRate()).isEqualTo(16_000);
        assertThat(config.maxAlternatives()).isEqualTo(1);
    }

    @Test
    void shouldAcceptExplicitDefaults() {
        VoskConfig config = new VoskConfig("models/vosk-model-small-en-us-0.15", 16_000, 1);

        assertThat(config.modelPath()).isEqualTo("models/vosk-model-small-en-us-0.15");
        assertThat(config.sampleRate()).isEqualTo(16_000);
        assertThat(config.maxAlternatives()).isEqualTo(1);
    }

    @Test
    void shouldRejectBlankModelPath() {
        VoskConfig config = new VoskConfig("", 16_000, 1);
        Set<ConstraintViolation<VoskConfig>> violations = validator.validate(config);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
                .contains("model path must not be blank");
    }

    @Test
    void shouldRejectNegativeSampleRate() {
        VoskConfig config = new VoskConfig("models/vosk", -1, 1);
        Set<ConstraintViolation<VoskConfig>> violations = validator.validate(config);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
                .contains("Sample rate must be positive");
    }

    @Test
    void shouldRejectZeroMaxAlternatives() {
        VoskConfig config = new VoskConfig("models/vosk", 16_000, 0);
        Set<ConstraintViolation<VoskConfig>> violations = validator.validate(config);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
                .contains("Max alternatives must be positive");
    }

    @Test
    void shouldAcceptValidNonDefaultValues() {
        VoskConfig config = new VoskConfig("custom/path", 44_100, 5);
        Set<ConstraintViolation<VoskConfig>> violations = validator.validate(config);

        assertThat(violations).isEmpty();
    }
}
