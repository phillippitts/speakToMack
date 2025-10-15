package com.phillippitts.speaktomack.service.audio;

/**
 * Single source of truth for STT audio format.
 * Required: 16 kHz, 16-bit signed PCM, mono, little-endian.
 */
public final class AudioFormat {

    /** Required sample rate in Hz. */
    public static final int REQUIRED_SAMPLE_RATE = 16_000;
    /** Required bits per sample. */
    public static final int REQUIRED_BITS_PER_SAMPLE = 16;
    /** Required number of channels (mono). */
    public static final int REQUIRED_CHANNELS = 1;

    /** Required signed PCM flag for Java Sound. */
    public static final boolean REQUIRED_SIGNED = true;
    /** Required endian flag for Java Sound (false = little-endian). */
    public static final boolean REQUIRED_BIG_ENDIAN = false;

    /** Bytes per PCM frame (sample for all channels). */
    public static final int REQUIRED_BLOCK_ALIGN = (REQUIRED_BITS_PER_SAMPLE / 8) * REQUIRED_CHANNELS; // 2 bytes
    /** Bytes per second at required format. */
    public static final int REQUIRED_BYTE_RATE = REQUIRED_SAMPLE_RATE * REQUIRED_BLOCK_ALIGN;           // 32,000

    // WAV header constants (PCM simple header)
    public static final int WAV_HEADER_SIZE = 44;
    public static final int WAV_CHANNELS_OFFSET = 22;            // 2 bytes (LE)
    public static final int WAV_SAMPLE_RATE_OFFSET = 24;         // 4 bytes (LE)
    public static final int WAV_BYTE_RATE_OFFSET = 28;           // 4 bytes (LE)
    public static final int WAV_BLOCK_ALIGN_OFFSET = 32;         // 2 bytes (LE)
    public static final int WAV_BITS_PER_SAMPLE_OFFSET = 34;     // 2 bytes (LE)

    private AudioFormat() {}
}
