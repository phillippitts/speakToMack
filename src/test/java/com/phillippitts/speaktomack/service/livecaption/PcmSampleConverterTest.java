package com.phillippitts.speaktomack.service.livecaption;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link PcmSampleConverter} — package-private PCM16LE to sample converter.
 */
class PcmSampleConverterTest {

    @Test
    void shouldConvertSingleSampleLittleEndian() {
        // 0x0100 little-endian = 256
        byte[] pcm = {0x00, 0x01};
        short[] samples = PcmSampleConverter.toSamples(pcm, pcm.length);
        assertThat(samples).containsExactly((short) 256);
    }

    @Test
    void shouldConvertMultipleSamples() {
        // Sample 1: 0x0100 = 256, Sample 2: 0x0200 = 512
        byte[] pcm = {0x00, 0x01, 0x00, 0x02};
        short[] samples = PcmSampleConverter.toSamples(pcm, pcm.length);
        assertThat(samples).containsExactly((short) 256, (short) 512);
    }

    @Test
    void shouldHandleNegativeSamples() {
        // -1 in 16-bit signed LE = 0xFF, 0xFF
        byte[] pcm = {(byte) 0xFF, (byte) 0xFF};
        short[] samples = PcmSampleConverter.toSamples(pcm, pcm.length);
        assertThat(samples).containsExactly((short) -1);
    }

    @Test
    void shouldHandleShortMinValue() {
        // Short.MIN_VALUE = -32768 = 0x8000 → LE bytes: 0x00, 0x80
        byte[] pcm = {0x00, (byte) 0x80};
        short[] samples = PcmSampleConverter.toSamples(pcm, pcm.length);
        assertThat(samples).containsExactly(Short.MIN_VALUE);
    }

    @Test
    void shouldReturnEmptyForZeroLengthInput() {
        byte[] pcm = {};
        short[] samples = PcmSampleConverter.toSamples(pcm, 0);
        assertThat(samples).isEmpty();
    }

    @Test
    void shouldProcessOnlyFirstNBytes() {
        // 4 bytes but length=2 → only first sample
        byte[] pcm = {0x00, 0x01, 0x00, 0x02};
        short[] samples = PcmSampleConverter.toSamples(pcm, 2);
        assertThat(samples).containsExactly((short) 256);
    }

    @Test
    void shouldDiscardOddTrailingByte() {
        // 3 bytes with length=3 → only 1 sample (trailing byte ignored)
        byte[] pcm = {0x00, 0x01, 0x42};
        short[] samples = PcmSampleConverter.toSamples(pcm, 3);
        assertThat(samples).containsExactly((short) 256);
    }

    @Test
    void shouldHandleAllZeroSilenceSamples() {
        byte[] pcm = {0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
        short[] samples = PcmSampleConverter.toSamples(pcm, pcm.length);
        assertThat(samples).containsExactly((short) 0, (short) 0, (short) 0);
    }
}
