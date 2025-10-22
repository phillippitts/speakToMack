package com.phillippitts.speaktomack.config;

import com.phillippitts.speaktomack.service.audio.AudioFormat;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AudioFormatConfigTest {

    @Test
    void validatesCorrectAudioFormatConstants() {
        AudioFormatConfig config = new AudioFormatConfig();

        // Should not throw when constants are correct
        config.validateAudioFormatConstants();
    }

    @Test
    void verifiesSampleRateIs16kHz() {
        assertThat(AudioFormat.REQUIRED_SAMPLE_RATE).isEqualTo(16_000);
    }

    @Test
    void verifiesBitsPerSampleIs16() {
        assertThat(AudioFormat.REQUIRED_BITS_PER_SAMPLE).isEqualTo(16);
    }

    @Test
    void verifiesChannelsIsMono() {
        assertThat(AudioFormat.REQUIRED_CHANNELS).isEqualTo(1);
    }

    @Test
    void verifiesLittleEndian() {
        assertThat(AudioFormat.REQUIRED_BIG_ENDIAN).isFalse();
    }

    @Test
    void verifiesByteRateCalculation() {
        // byteRate = sampleRate * channels * (bitsPerSample / 8)
        // 16000 * 1 * (16 / 8) = 16000 * 1 * 2 = 32000
        assertThat(AudioFormat.REQUIRED_BYTE_RATE).isEqualTo(32_000);
    }

    @Test
    void verifiesBlockAlignCalculation() {
        // blockAlign = channels * (bitsPerSample / 8)
        // 1 * (16 / 8) = 1 * 2 = 2
        assertThat(AudioFormat.REQUIRED_BLOCK_ALIGN).isEqualTo(2);
    }

    @Test
    void validatesAllConstantsTogether() {
        // All values should match the expected STT engine requirements
        assertThat(AudioFormat.REQUIRED_SAMPLE_RATE).isEqualTo(16_000);
        assertThat(AudioFormat.REQUIRED_BITS_PER_SAMPLE).isEqualTo(16);
        assertThat(AudioFormat.REQUIRED_CHANNELS).isEqualTo(1);
        assertThat(AudioFormat.REQUIRED_BIG_ENDIAN).isFalse();
        assertThat(AudioFormat.REQUIRED_BYTE_RATE).isEqualTo(32_000);
        assertThat(AudioFormat.REQUIRED_BLOCK_ALIGN).isEqualTo(2);
    }
}
