package com.phillippitts.speaktomack.service.validation;

import com.phillippitts.speaktomack.exception.InvalidAudioException;
import org.springframework.stereotype.Component;

import static com.phillippitts.speaktomack.service.audio.AudioFormat.REQUIRED_BITS_PER_SAMPLE;
import static com.phillippitts.speaktomack.service.audio.AudioFormat.REQUIRED_BLOCK_ALIGN;
import static com.phillippitts.speaktomack.service.audio.AudioFormat.REQUIRED_BYTE_RATE;
import static com.phillippitts.speaktomack.service.audio.AudioFormat.REQUIRED_CHANNELS;
import static com.phillippitts.speaktomack.service.audio.AudioFormat.REQUIRED_SAMPLE_RATE;
import static com.phillippitts.speaktomack.service.audio.AudioFormat.WAV_BITS_PER_SAMPLE_OFFSET;
import static com.phillippitts.speaktomack.service.audio.AudioFormat.WAV_BLOCK_ALIGN_OFFSET;
import static com.phillippitts.speaktomack.service.audio.AudioFormat.WAV_BYTE_RATE_OFFSET;
import static com.phillippitts.speaktomack.service.audio.AudioFormat.WAV_CHANNELS_OFFSET;
import static com.phillippitts.speaktomack.service.audio.AudioFormat.WAV_HEADER_SIZE;
import static com.phillippitts.speaktomack.service.audio.AudioFormat.WAV_SAMPLE_RATE_OFFSET;

/**
 * Validates input audio as either WAV (RIFF/WAVE) or raw PCM for STT ingestion.
 *
 * WAV: Checks RIFF/WAVE presence and exact header fields match the required format.
 * PCM: Checks length-derived duration and block alignment under known capture settings.
 */
@Component
public class AudioValidator {
    private final AudioValidationProperties props;

    public AudioValidator(AudioValidationProperties props) {
        this.props = props;
    }

    /**
     * Validate a complete audio clip (final buffer) before STT.
     * @param data audio bytes, either WAV (RIFF/WAVE) or raw PCM16LE mono at 16kHz
     * @throws InvalidAudioException when format or duration constraints are violated
     */
    public void validate(byte[] data) {
        if (data == null) {
            throw new InvalidAudioException("Audio data is null");
        }
        if (isWav(data)) {
            validateWav(data);
        } else {
            validatePcm(data);
        }
    }

    private boolean isWav(byte[] a) {
        return a.length >= WAV_HEADER_SIZE
            && a[0] == 'R' && a[1] == 'I' && a[2] == 'F' && a[3] == 'F'
            && a[8] == 'W' && a[9] == 'A' && a[10] == 'V' && a[11] == 'E';
    }

    private void validateWav(byte[] wav) {
        if (wav.length < WAV_HEADER_SIZE) {
            throw new InvalidAudioException("Audio too small for WAV header (44 bytes)");
        }
        int channels = readLEShort(wav, WAV_CHANNELS_OFFSET);
        int sampleRate = readLEInt(wav, WAV_SAMPLE_RATE_OFFSET);
        int byteRate = readLEInt(wav, WAV_BYTE_RATE_OFFSET);
        int blockAlign = readLEShort(wav, WAV_BLOCK_ALIGN_OFFSET);
        int bitsPerSample = readLEShort(wav, WAV_BITS_PER_SAMPLE_OFFSET);

        if (channels != REQUIRED_CHANNELS) {
            throw new InvalidAudioException("Invalid channel count: " + channels + ". Expected: " + REQUIRED_CHANNELS);
        }
        if (sampleRate != REQUIRED_SAMPLE_RATE) {
            throw new InvalidAudioException("Invalid sample rate: " + sampleRate + " Hz. Expected: "
                    + REQUIRED_SAMPLE_RATE + " Hz");
        }
        if (bitsPerSample != REQUIRED_BITS_PER_SAMPLE) {
            throw new InvalidAudioException("Invalid bit depth: " + bitsPerSample + "-bit. Expected: "
                    + REQUIRED_BITS_PER_SAMPLE + "-bit");
        }
        if (blockAlign != REQUIRED_BLOCK_ALIGN) {
            throw new InvalidAudioException("Invalid block align: " + blockAlign + ". Expected: "
                    + REQUIRED_BLOCK_ALIGN);
        }
        if (byteRate != REQUIRED_BYTE_RATE) {
            throw new InvalidAudioException("Invalid byte rate: " + byteRate + ". Expected: " + REQUIRED_BYTE_RATE);
        }

        int payloadBytes = Math.max(0, wav.length - WAV_HEADER_SIZE);
        validateDurationByBytes(payloadBytes);
    }

    private void validatePcm(byte[] pcm) {
        if (pcm.length % REQUIRED_BLOCK_ALIGN != 0) {
            throw new InvalidAudioException("PCM not aligned to block size (" + REQUIRED_BLOCK_ALIGN + " bytes)");
        }
        validateDurationByBytes(pcm.length);
    }

    private void validateDurationByBytes(int bytes) {
        long durationMs = (bytes * 1000L) / REQUIRED_BYTE_RATE; // ms at 16kHz mono 16-bit
        if (durationMs < props.getMinDurationMs()) {
            throw new InvalidAudioException(bytes, "Audio too short: ~" + durationMs
                    + " ms. Min: " + props.getMinDurationMs() + " ms");
        }
        if (durationMs > props.getMaxDurationMs()) {
            throw new InvalidAudioException(bytes, "Audio too long: ~" + durationMs
                    + " ms. Max: " + props.getMaxDurationMs() + " ms");
        }
    }

    private static int readLEShort(byte[] a, int off) {
        return (a[off] & 0xFF) | ((a[off + 1] & 0xFF) << 8);
    }

    private static int readLEInt(byte[] a, int off) {
        return (a[off] & 0xFF)
             | ((a[off + 1] & 0xFF) << 8)
             | ((a[off + 2] & 0xFF) << 16)
             | ((a[off + 3] & 0xFF) << 24);
    }
}