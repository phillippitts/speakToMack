package com.phillippitts.speaktomack.service.audio;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AudioSilenceDetectorTest {

    private static final int SAMPLE_RATE = 16000;
    private static final int SILENCE_GAP_MS = 500;

    // --- Guard clauses ---

    @Test
    void shouldReturnEmptyForNullPcm() {
        assertThat(AudioSilenceDetector.detectSilenceBoundaries(null, SILENCE_GAP_MS, SAMPLE_RATE))
                .isEmpty();
    }

    @Test
    void shouldReturnEmptyForEmptyPcm() {
        assertThat(AudioSilenceDetector.detectSilenceBoundaries(new byte[0], SILENCE_GAP_MS, SAMPLE_RATE))
                .isEmpty();
    }

    @Test
    void shouldReturnEmptyForSingleBytePcm() {
        assertThat(AudioSilenceDetector.detectSilenceBoundaries(new byte[1], SILENCE_GAP_MS, SAMPLE_RATE))
                .isEmpty();
    }

    @Test
    void shouldReturnEmptyForZeroSilenceGapMs() {
        byte[] pcm = generateSilence(1000);
        assertThat(AudioSilenceDetector.detectSilenceBoundaries(pcm, 0, SAMPLE_RATE))
                .isEmpty();
    }

    @Test
    void shouldReturnEmptyForNegativeSilenceGapMs() {
        byte[] pcm = generateSilence(1000);
        assertThat(AudioSilenceDetector.detectSilenceBoundaries(pcm, -100, SAMPLE_RATE))
                .isEmpty();
    }

    @Test
    void shouldReturnEmptyForZeroSampleRate() {
        byte[] pcm = generateSilence(1000);
        assertThat(AudioSilenceDetector.detectSilenceBoundaries(pcm, SILENCE_GAP_MS, 0))
                .isEmpty();
    }

    @Test
    void shouldReturnEmptyForNegativeSampleRate() {
        byte[] pcm = generateSilence(1000);
        assertThat(AudioSilenceDetector.detectSilenceBoundaries(pcm, SILENCE_GAP_MS, -16000))
                .isEmpty();
    }

    // --- Core detection ---

    @Test
    void shouldDetectTrailingSilenceBoundary() {
        // Pure silence longer than threshold → boundary at end
        byte[] pcm = generateSilence(durationToSamples(SILENCE_GAP_MS + 100));
        List<Integer> boundaries = AudioSilenceDetector.detectSilenceBoundaries(pcm, SILENCE_GAP_MS, SAMPLE_RATE);
        assertThat(boundaries).hasSize(1);
        assertThat(boundaries.get(0)).isEqualTo(pcm.length);
    }

    @Test
    void shouldReturnEmptyForContinuousLoudAudio() {
        byte[] pcm = generateLoud(durationToSamples(1000));
        List<Integer> boundaries = AudioSilenceDetector.detectSilenceBoundaries(pcm, SILENCE_GAP_MS, SAMPLE_RATE);
        assertThat(boundaries).isEmpty();
    }

    @Test
    void shouldDetectSilenceExceedingThreshold() {
        // loud + silence (> threshold) + loud
        byte[] loud1 = generateLoud(durationToSamples(300));
        byte[] silence = generateSilence(durationToSamples(SILENCE_GAP_MS + 200));
        byte[] loud2 = generateLoud(durationToSamples(300));
        byte[] pcm = concat(loud1, silence, loud2);

        List<Integer> boundaries = AudioSilenceDetector.detectSilenceBoundaries(pcm, SILENCE_GAP_MS, SAMPLE_RATE);
        assertThat(boundaries).hasSize(1);
        // Boundary should be after silence region, before loud2
        assertThat(boundaries.get(0)).isGreaterThan(loud1.length)
                .isLessThanOrEqualTo(loud1.length + silence.length);
    }

    @Test
    void shouldNotDetectSilenceBelowThreshold() {
        // loud + silence (< threshold) + loud
        byte[] loud1 = generateLoud(durationToSamples(500));
        byte[] silence = generateSilence(durationToSamples(SILENCE_GAP_MS - 100));
        byte[] loud2 = generateLoud(durationToSamples(500));
        byte[] pcm = concat(loud1, silence, loud2);

        List<Integer> boundaries = AudioSilenceDetector.detectSilenceBoundaries(pcm, SILENCE_GAP_MS, SAMPLE_RATE);
        assertThat(boundaries).isEmpty();
    }

    @Test
    void shouldDetectMultipleSilenceGaps() {
        byte[] loud = generateLoud(durationToSamples(200));
        byte[] silence = generateSilence(durationToSamples(SILENCE_GAP_MS + 100));

        // loud + silence + loud + silence + loud
        byte[] pcm = concat(loud, silence, loud, silence, loud);

        List<Integer> boundaries = AudioSilenceDetector.detectSilenceBoundaries(pcm, SILENCE_GAP_MS, SAMPLE_RATE);
        assertThat(boundaries).hasSize(2);
    }

    @Test
    void shouldRespectCustomSilenceThreshold() {
        // Generate moderate amplitude that is below default threshold (800) but above a low threshold
        byte[] moderate = generateModerate(durationToSamples(1000));

        // With default threshold (800), moderate audio is "silence" → boundary detected
        List<Integer> withDefault = AudioSilenceDetector.detectSilenceBoundaries(
                moderate, SILENCE_GAP_MS, SAMPLE_RATE, 800);
        assertThat(withDefault).hasSize(1);

        // With low threshold (100), moderate audio is "loud" → no boundary
        List<Integer> withLow = AudioSilenceDetector.detectSilenceBoundaries(
                moderate, SILENCE_GAP_MS, SAMPLE_RATE, 100);
        assertThat(withLow).isEmpty();
    }

    // --- Helpers ---

    /** Converts duration in ms to number of 16-bit samples at SAMPLE_RATE. */
    private int durationToSamples(int durationMs) {
        return (SAMPLE_RATE * durationMs) / 1000;
    }

    /** Generates PCM16LE silence (all zeros). */
    private byte[] generateSilence(int sampleCount) {
        return new byte[sampleCount * 2]; // 2 bytes per sample, all zeros
    }

    /** Generates PCM16LE loud audio (amplitude ~10000). */
    private byte[] generateLoud(int sampleCount) {
        byte[] pcm = new byte[sampleCount * 2];
        short amplitude = 10000;
        for (int i = 0; i < sampleCount; i++) {
            // Alternate positive/negative to create audio-like signal
            short val = (i % 2 == 0) ? amplitude : (short) -amplitude;
            pcm[i * 2] = (byte) (val & 0xFF);
            pcm[i * 2 + 1] = (byte) (val >> 8);
        }
        return pcm;
    }

    /** Generates PCM16LE moderate amplitude audio (~500). */
    private byte[] generateModerate(int sampleCount) {
        byte[] pcm = new byte[sampleCount * 2];
        short amplitude = 500;
        for (int i = 0; i < sampleCount; i++) {
            short val = (i % 2 == 0) ? amplitude : (short) -amplitude;
            pcm[i * 2] = (byte) (val & 0xFF);
            pcm[i * 2 + 1] = (byte) (val >> 8);
        }
        return pcm;
    }

    private byte[] concat(byte[]... arrays) {
        int total = 0;
        for (byte[] a : arrays) total += a.length;
        byte[] result = new byte[total];
        int pos = 0;
        for (byte[] a : arrays) {
            System.arraycopy(a, 0, result, pos, a.length);
            pos += a.length;
        }
        return result;
    }
}
