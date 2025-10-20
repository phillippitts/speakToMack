package com.phillippitts.speaktomack.service.audio;

/**
 * Constants for WAV (RIFF/WAVE) file format parsing and validation.
 *
 * <p>Defines structural constants for the WAV container format as specified in the
 * RIFF/WAVE specification. Used by {@link com.phillippitts.speaktomack.service.validation.AudioValidator}
 * to parse and validate WAV headers without magic numbers.
 *
 * <p><b>WAV File Structure:</b>
 * <pre>
 * ┌─────────────────────────────────────┐
 * │ RIFF Header (12 bytes)              │  RIFF_HEADER_SIZE
 * ├─────────────────────────────────────┤
 * │ fmt chunk:                          │
 * │   - Chunk ID + Size (8 bytes)       │  CHUNK_HEADER_SIZE
 * │   - Chunk Data (≥16 bytes)          │  FMT_CHUNK_MIN_SIZE
 * ├─────────────────────────────────────┤
 * │ data chunk:                         │
 * │   - Chunk ID + Size (8 bytes)       │  CHUNK_HEADER_SIZE
 * │   - PCM Audio Data (variable)       │
 * └─────────────────────────────────────┘
 * </pre>
 *
 * @see com.phillippitts.speaktomack.service.validation.AudioValidator
 * @since 1.0
 */
public final class WavFormat {

    /**
     * Size of the RIFF header in bytes.
     *
     * <p>RIFF header structure:
     * <ul>
     *   <li>Bytes 0-3: "RIFF" chunk ID</li>
     *   <li>Bytes 4-7: File size - 8 (little-endian uint32)</li>
     *   <li>Bytes 8-11: "WAVE" format ID</li>
     * </ul>
     */
    public static final int RIFF_HEADER_SIZE = 12;

    /**
     * Size of a chunk header in bytes (chunk ID + chunk size).
     *
     * <p>All RIFF chunks follow this structure:
     * <ul>
     *   <li>Bytes 0-3: 4-character chunk ID (e.g., "fmt ", "data")</li>
     *   <li>Bytes 4-7: Chunk data size (little-endian uint32)</li>
     * </ul>
     */
    public static final int CHUNK_HEADER_SIZE = 8;

    /**
     * Minimum size of the fmt chunk data in bytes.
     *
     * <p>Standard PCM fmt chunk contains 16 bytes:
     * <ul>
     *   <li>Bytes 0-1: Audio format (1 = PCM)</li>
     *   <li>Bytes 2-3: Number of channels</li>
     *   <li>Bytes 4-7: Sample rate (Hz)</li>
     *   <li>Bytes 8-11: Byte rate (bytes/sec)</li>
     *   <li>Bytes 12-13: Block align (bytes per sample frame)</li>
     *   <li>Bytes 14-15: Bits per sample</li>
     * </ul>
     *
     * <p>Extended formats may have additional bytes, but 16 is the minimum for PCM.
     */
    public static final int FMT_CHUNK_MIN_SIZE = 16;

    /**
     * Audio format code for uncompressed PCM.
     *
     * <p>In the fmt chunk, audio format = 1 indicates PCM (pulse-code modulation),
     * which is the only format supported by this application.
     */
    public static final int AUDIO_FORMAT_PCM = 1;

    private WavFormat() {
        // Utility class - prevent instantiation
    }
}
