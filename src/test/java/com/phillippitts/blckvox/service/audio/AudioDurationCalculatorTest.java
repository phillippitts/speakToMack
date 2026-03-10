package com.phillippitts.blckvox.service.audio;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class AudioDurationCalculatorTest {

    @Test
    void shouldComputeOnSecondOfAudio() {
        // 16kHz * 2 bytes * 1 channel = 32000 bytes/sec → 1000ms
        double ms = AudioDurationCalculator.durationMs(32000, 16000, 2, 1);
        assertThat(ms).isCloseTo(1000.0, within(0.01));
    }

    @Test
    void shouldComputeHalfSecondOfAudio() {
        double ms = AudioDurationCalculator.durationMs(16000, 16000, 2, 1);
        assertThat(ms).isCloseTo(500.0, within(0.01));
    }

    @Test
    void shouldComputeWithDefaultFormat() {
        // Default: 16kHz, 16-bit (2 bytes), mono → 32000 bytes/sec
        double ms = AudioDurationCalculator.durationMs(64000);
        assertThat(ms).isCloseTo(2000.0, within(0.01));
    }

    @Test
    void shouldReturnZeroForZeroLength() {
        double ms = AudioDurationCalculator.durationMs(0);
        assertThat(ms).isEqualTo(0.0);
    }

    @Test
    void shouldThrowForInvalidSampleRate() {
        assertThatThrownBy(() -> AudioDurationCalculator.durationMs(1000, 0, 2, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowForInvalidBytesPerSample() {
        assertThatThrownBy(() -> AudioDurationCalculator.durationMs(1000, 16000, 0, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowForInvalidChannels() {
        assertThatThrownBy(() -> AudioDurationCalculator.durationMs(1000, 16000, 2, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldHandleStereoFormat() {
        // Stereo: 16kHz * 2 bytes * 2 channels = 64000 bytes/sec → 1000ms
        double ms = AudioDurationCalculator.durationMs(64000, 16000, 2, 2);
        assertThat(ms).isCloseTo(1000.0, within(0.01));
    }
}
