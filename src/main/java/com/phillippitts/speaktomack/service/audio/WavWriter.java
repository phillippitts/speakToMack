package com.phillippitts.speaktomack.service.audio;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static com.phillippitts.speaktomack.service.audio.AudioFormat.REQUIRED_BITS_PER_SAMPLE;
import static com.phillippitts.speaktomack.service.audio.AudioFormat.REQUIRED_BLOCK_ALIGN;
import static com.phillippitts.speaktomack.service.audio.AudioFormat.REQUIRED_BYTE_RATE;
import static com.phillippitts.speaktomack.service.audio.AudioFormat.REQUIRED_CHANNELS;
import static com.phillippitts.speaktomack.service.audio.AudioFormat.REQUIRED_SAMPLE_RATE;

/**
 * Writes minimal PCM WAV files using the project-required audio format.
 *
 * <p>Format: 16 kHz, 16-bit signed PCM, mono, little-endian.
 * This utility only supports this fixed format to avoid bugs and ambiguity.
 */
public final class WavWriter {

    private WavWriter() {}

    /**
     * Writes a WAV file containing the given raw PCM16LE mono 16 kHz payload.
     *
     * @param pcm     raw PCM16LE mono audio at 16 kHz
     * @param wavPath output file path (will be created or overwritten)
     */
    public static void writePcm16LeMono16kHz(byte[] pcm, Path wavPath) {
        Objects.requireNonNull(pcm, "pcm must not be null");
        Objects.requireNonNull(wavPath, "wavPath must not be null");
        try (OutputStream os = Files.newOutputStream(wavPath)) {
            // RIFF header (44 bytes total)
            // ChunkID: "RIFF"
            os.write(new byte[] { 'R', 'I', 'F', 'F' });

            // ChunkSize: 36 + Subchunk2Size (little-endian)
            int subchunk2Size = pcm.length; // data size in bytes
            int chunkSize = 36 + subchunk2Size;
            writeLEInt(os, chunkSize);

            // Format: "WAVE"
            os.write(new byte[] { 'W', 'A', 'V', 'E' });

            // Subchunk1ID: "fmt "
            os.write(new byte[] { 'f', 'm', 't', ' ' });
            // Subchunk1Size: 16 for PCM
            writeLEInt(os, 16);
            // AudioFormat: 1 for PCM
            writeLEShort(os, (short) 1);
            // NumChannels: 1 (mono)
            writeLEShort(os, (short) REQUIRED_CHANNELS);
            // SampleRate: 16000
            writeLEInt(os, REQUIRED_SAMPLE_RATE);
            // ByteRate: SampleRate * NumChannels * BitsPerSample/8
            writeLEInt(os, REQUIRED_BYTE_RATE);
            // BlockAlign: NumChannels * BitsPerSample/8
            writeLEShort(os, (short) REQUIRED_BLOCK_ALIGN);
            // BitsPerSample: 16
            writeLEShort(os, (short) REQUIRED_BITS_PER_SAMPLE);

            // Subchunk2ID: "data"
            os.write(new byte[] { 'd', 'a', 't', 'a' });
            // Subchunk2Size: pcm data size
            writeLEInt(os, subchunk2Size);

            // Data
            os.write(pcm);
            os.flush();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write WAV file to " + wavPath + ": " + e.getMessage(), e);
        }
    }

    private static void writeLEShort(OutputStream os, short v) throws IOException {
        os.write(v & 0xFF);
        os.write((v >>> 8) & 0xFF);
    }

    private static void writeLEInt(OutputStream os, int v) throws IOException {
        os.write(v & 0xFF);
        os.write((v >>> 8) & 0xFF);
        os.write((v >>> 16) & 0xFF);
        os.write((v >>> 24) & 0xFF);
    }
}
