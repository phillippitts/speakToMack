package com.phillippitts.speaktomack.service.validation;

import com.phillippitts.speaktomack.exception.InvalidAudioException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AudioValidatorTest {

    private AudioValidator validator;
    private AudioValidationProperties props;

    @BeforeEach
    void setup() {
        props = new AudioValidationProperties();
        props.setMinDurationMs(250);
        props.setMaxDurationMs(300_000);
        validator = new AudioValidator(props);
    }

    @Test
    void wavShouldRejectStereo() {
        byte[] wav = makeMinimalWav(16_000, 2, 16); // Correct rate, wrong channels
        assertThatThrownBy(() -> validator.validate(wav))
                .isInstanceOf(InvalidAudioException.class)
                .hasMessageContaining("channel count");
    }

    @Test
    void wavShouldRejectWrongSampleRate() {
        byte[] wav = makeMinimalWav(44_100, 1, 16); // Wrong rate, correct channels
        assertThatThrownBy(() -> validator.validate(wav))
                .isInstanceOf(InvalidAudioException.class)
                .hasMessageContaining("sample rate");
    }

    @Test
    void wavShouldAccept16000HzMono16bit() {
        byte[] wav = makeMinimalWav(16_000, 1, 16);
        assertThatCode(() -> validator.validate(wav)).doesNotThrowAnyException();
    }

    @Test
    void wavShouldReject24bit() {
        byte[] wav = makeMinimalWav(16_000, 1, 24);
        assertThatThrownBy(() -> validator.validate(wav))
                .isInstanceOf(InvalidAudioException.class)
                .hasMessageContaining("bit depth");
    }

    @Test
    void pcmShouldAccept16000HzMono16bitByLength() {
        // ~1 second at 16kHz mono 16-bit = 32,000 bytes
        byte[] pcm = new byte[32_000];
        assertThatCode(() -> validator.validate(pcm)).doesNotThrowAnyException();
    }

    @Test
    void pcmShouldRejectTooShortUnderMinDuration() {
        // ~200ms at 32kB/s => 6,400 bytes; use smaller to trigger min threshold (250ms)
        byte[] pcm = new byte[5_000];
        assertThatThrownBy(() -> validator.validate(pcm))
                .isInstanceOf(InvalidAudioException.class)
                .hasMessageContaining("too short");
    }

    @Test
    void wavShouldHandleNonStandardHeaderWithListChunk() {
        // WAV with LIST chunk before data chunk (common in files with metadata)
        byte[] wav = makeWavWithListChunk(16_000, 1, 16);
        assertThatCode(() -> validator.validate(wav)).doesNotThrowAnyException();
    }

    @Test
    void wavShouldHandleExtendedFmtChunk() {
        // WAV with extended fmt chunk (18 bytes instead of 16)
        byte[] wav = makeWavWithExtendedFmt(16_000, 1, 16);
        assertThatCode(() -> validator.validate(wav)).doesNotThrowAnyException();
    }

    @Test
    void wavShouldRejectMissingFmtChunk() {
        // WAV with data chunk but no fmt chunk
        byte[] wav = makeWavWithoutFmtChunk();
        assertThatThrownBy(() -> validator.validate(wav))
                .isInstanceOf(InvalidAudioException.class)
                .hasMessageContaining("Missing fmt chunk");
    }

    @Test
    void wavShouldRejectMissingDataChunk() {
        // WAV with fmt chunk but no data chunk
        byte[] wav = makeWavWithoutDataChunk();
        assertThatThrownBy(() -> validator.validate(wav))
                .isInstanceOf(InvalidAudioException.class)
                .hasMessageContaining("Missing data chunk");
    }

    @Test
    void wavShouldRejectMisalignedDataChunk() {
        // WAV with odd data size (not aligned to block size of 2 bytes)
        byte[] wav = makeWavWithMisalignedData(16_000, 1, 16);
        assertThatThrownBy(() -> validator.validate(wav))
                .isInstanceOf(InvalidAudioException.class)
                .hasMessageContaining("not aligned to block size");
    }

    // --- Helpers ---

    private static byte[] makeMinimalWav(int sampleRate, int channels, int bitsPerSample) {
        int payloadBytes = 40_000; // Default payload size for test WAV files
        int header = 44;
        byte[] out = new byte[header + payloadBytes];
        // RIFF/WAVE
        out[0] = 'R'; out[1] = 'I'; out[2] = 'F'; out[3] = 'F';
        putLEInt(out, 4, 36 + payloadBytes);
        out[8] = 'W'; out[9] = 'A'; out[10] = 'V'; out[11] = 'E';
        // fmt chunk
        out[12] = 'f'; out[13] = 'm'; out[14] = 't'; out[15] = ' ';
        putLEInt(out, 16, 16);
        putLEShort(out, 20, 1);
        putLEShort(out, 22, channels);
        putLEInt(out, 24, sampleRate);
        int blockAlign = (bitsPerSample / 8) * channels;
        int byteRate = sampleRate * blockAlign;
        putLEInt(out, 28, byteRate);
        putLEShort(out, 32, blockAlign);
        putLEShort(out, 34, bitsPerSample);
        // data
        out[36] = 'd'; out[37] = 'a'; out[38] = 't'; out[39] = 'a';
        putLEInt(out, 40, payloadBytes);
        return out;
    }

    private static byte[] makeWavWithListChunk(int sampleRate, int channels, int bitsPerSample) {
        int payloadBytes = 40_000;
        int listChunkSize = 24; // LIST chunk with some metadata
        int totalSize = 12 + 8 + 16 + 8 + listChunkSize + 8 + payloadBytes;
        byte[] out = new byte[totalSize];
        int offset = 0;

        // RIFF header
        out[offset++] = 'R'; out[offset++] = 'I'; out[offset++] = 'F'; out[offset++] = 'F';
        putLEInt(out, offset, totalSize - 8); offset += 4;
        out[offset++] = 'W'; out[offset++] = 'A'; out[offset++] = 'V'; out[offset++] = 'E';

        // fmt chunk
        out[offset++] = 'f'; out[offset++] = 'm'; out[offset++] = 't'; out[offset++] = ' ';
        putLEInt(out, offset, 16); offset += 4;
        putLEShort(out, offset, 1); offset += 2; // PCM
        putLEShort(out, offset, channels); offset += 2;
        putLEInt(out, offset, sampleRate); offset += 4;
        int blockAlign = (bitsPerSample / 8) * channels;
        int byteRate = sampleRate * blockAlign;
        putLEInt(out, offset, byteRate); offset += 4;
        putLEShort(out, offset, blockAlign); offset += 2;
        putLEShort(out, offset, bitsPerSample); offset += 2;

        // LIST chunk (metadata)
        out[offset++] = 'L'; out[offset++] = 'I'; out[offset++] = 'S'; out[offset++] = 'T';
        putLEInt(out, offset, listChunkSize); offset += 4;
        offset += listChunkSize; // Skip LIST content

        // data chunk
        out[offset++] = 'd'; out[offset++] = 'a'; out[offset++] = 't'; out[offset++] = 'a';
        putLEInt(out, offset, payloadBytes);
        return out;
    }

    private static byte[] makeWavWithExtendedFmt(int sampleRate, int channels, int bitsPerSample) {
        int payloadBytes = 40_000;
        int fmtSize = 18; // Extended format with cbSize field
        int totalSize = 12 + 8 + fmtSize + 8 + payloadBytes;
        byte[] out = new byte[totalSize];
        int offset = 0;

        // RIFF header
        out[offset++] = 'R'; out[offset++] = 'I'; out[offset++] = 'F'; out[offset++] = 'F';
        putLEInt(out, offset, totalSize - 8); offset += 4;
        out[offset++] = 'W'; out[offset++] = 'A'; out[offset++] = 'V'; out[offset++] = 'E';

        // fmt chunk (extended)
        out[offset++] = 'f'; out[offset++] = 'm'; out[offset++] = 't'; out[offset++] = ' ';
        putLEInt(out, offset, fmtSize); offset += 4;
        putLEShort(out, offset, 1); offset += 2; // PCM
        putLEShort(out, offset, channels); offset += 2;
        putLEInt(out, offset, sampleRate); offset += 4;
        int blockAlign = (bitsPerSample / 8) * channels;
        int byteRate = sampleRate * blockAlign;
        putLEInt(out, offset, byteRate); offset += 4;
        putLEShort(out, offset, blockAlign); offset += 2;
        putLEShort(out, offset, bitsPerSample); offset += 2;
        putLEShort(out, offset, 0); offset += 2; // cbSize = 0 (no extension)

        // data chunk
        out[offset++] = 'd'; out[offset++] = 'a'; out[offset++] = 't'; out[offset++] = 'a';
        putLEInt(out, offset, payloadBytes);
        return out;
    }

    private static byte[] makeWavWithoutFmtChunk() {
        int payloadBytes = 40_000;
        int totalSize = 12 + 8 + payloadBytes;
        byte[] out = new byte[totalSize];
        int offset = 0;

        // RIFF header
        out[offset++] = 'R'; out[offset++] = 'I'; out[offset++] = 'F'; out[offset++] = 'F';
        putLEInt(out, offset, totalSize - 8); offset += 4;
        out[offset++] = 'W'; out[offset++] = 'A'; out[offset++] = 'V'; out[offset++] = 'E';

        // data chunk only (no fmt)
        out[offset++] = 'd'; out[offset++] = 'a'; out[offset++] = 't'; out[offset++] = 'a';
        putLEInt(out, offset, payloadBytes);
        return out;
    }

    private static byte[] makeWavWithoutDataChunk() {
        int totalSize = 12 + 8 + 16;
        byte[] out = new byte[totalSize];
        int offset = 0;

        // RIFF header
        out[offset++] = 'R'; out[offset++] = 'I'; out[offset++] = 'F'; out[offset++] = 'F';
        putLEInt(out, offset, totalSize - 8); offset += 4;
        out[offset++] = 'W'; out[offset++] = 'A'; out[offset++] = 'V'; out[offset++] = 'E';

        // fmt chunk only (no data)
        out[offset++] = 'f'; out[offset++] = 'm'; out[offset++] = 't'; out[offset++] = ' ';
        putLEInt(out, offset, 16); offset += 4;
        putLEShort(out, offset, 1); offset += 2; // PCM
        putLEShort(out, offset, 1); offset += 2; // mono
        putLEInt(out, offset, 16000); offset += 4; // sample rate
        putLEInt(out, offset, 32000); offset += 4; // byte rate
        putLEShort(out, offset, 2); offset += 2; // block align
        putLEShort(out, offset, 16); // bits per sample
        return out;
    }

    private static byte[] makeWavWithMisalignedData(int sampleRate, int channels, int bitsPerSample) {
        int payloadBytes = 40_001; // ODD size - not aligned to 2-byte block
        int header = 44;
        byte[] out = new byte[header + payloadBytes];
        // RIFF/WAVE
        out[0] = 'R'; out[1] = 'I'; out[2] = 'F'; out[3] = 'F';
        putLEInt(out, 4, 36 + payloadBytes);
        out[8] = 'W'; out[9] = 'A'; out[10] = 'V'; out[11] = 'E';
        // fmt chunk
        out[12] = 'f'; out[13] = 'm'; out[14] = 't'; out[15] = ' ';
        putLEInt(out, 16, 16);
        putLEShort(out, 20, 1);
        putLEShort(out, 22, channels);
        putLEInt(out, 24, sampleRate);
        int blockAlign = (bitsPerSample / 8) * channels;
        int byteRate = sampleRate * blockAlign;
        putLEInt(out, 28, byteRate);
        putLEShort(out, 32, blockAlign);
        putLEShort(out, 34, bitsPerSample);
        // data with misaligned size
        out[36] = 'd'; out[37] = 'a'; out[38] = 't'; out[39] = 'a';
        putLEInt(out, 40, payloadBytes); // ODD size - misaligned!
        return out;
    }

    private static void putLEShort(byte[] a, int off, int v) {
        a[off] = (byte) (v & 0xFF);
        a[off + 1] = (byte) ((v >>> 8) & 0xFF);
    }
    private static void putLEInt(byte[] a, int off, int v) {
        a[off] = (byte) (v & 0xFF);
        a[off + 1] = (byte) ((v >>> 8) & 0xFF);
        a[off + 2] = (byte) ((v >>> 16) & 0xFF);
        a[off + 3] = (byte) ((v >>> 24) & 0xFF);
    }
}