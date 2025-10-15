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