package com.phillippitts.speaktomack.config.stt;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Positive;

/**
 * Configurable per-engine concurrency caps to prevent process/thread storms during parallel runs.
 *
 * Defaults are conservative and can be tuned per environment via properties.
 *
 * Properties:
 * - stt.concurrency.vosk-max
 * - stt.concurrency.whisper-max
 */
@ConfigurationProperties(prefix = "stt.concurrency")
@Validated
public class SttConcurrencyProperties {

    /** Maximum parallel Vosk transcriptions allowed. */
    @Positive(message = "Vosk max concurrency must be positive")
    private int voskMax = 4;

    /** Maximum parallel Whisper transcriptions allowed. */
    @Positive(message = "Whisper max concurrency must be positive")
    private int whisperMax = 2;

    public int getVoskMax() {
        return voskMax;
    }

    public void setVoskMax(int voskMax) {
        this.voskMax = voskMax;
    }

    public int getWhisperMax() {
        return whisperMax;
    }

    public void setWhisperMax(int whisperMax) {
        this.whisperMax = whisperMax;
    }
}
