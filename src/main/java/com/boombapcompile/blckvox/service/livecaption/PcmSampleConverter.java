package com.boombapcompile.blckvox.service.livecaption;

/**
 * Converts raw PCM16LE byte arrays to signed 16-bit samples.
 *
 * @since 1.3
 */
final class PcmSampleConverter {

    private PcmSampleConverter() {
        // Utility class
    }

    /**
     * Converts little-endian 16-bit PCM bytes to signed short samples.
     *
     * @param pcm raw PCM byte data
     * @param length number of valid bytes in the array
     * @return array of signed 16-bit samples
     */
    static short[] toSamples(byte[] pcm, int length) {
        int sampleCount = length / 2;
        short[] samples = new short[sampleCount];
        for (int i = 0; i < sampleCount; i++) {
            int lo = pcm[2 * i] & 0xFF;
            int hi = pcm[2 * i + 1];
            samples[i] = (short) (lo | (hi << 8));
        }
        return samples;
    }
}
