package com.phillippitts.speaktomack.config;

import jakarta.annotation.PostConstruct;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.annotation.Configuration;

import static com.phillippitts.speaktomack.service.audio.AudioFormat.REQUIRED_BIG_ENDIAN;
import static com.phillippitts.speaktomack.service.audio.AudioFormat.REQUIRED_BITS_PER_SAMPLE;
import static com.phillippitts.speaktomack.service.audio.AudioFormat.REQUIRED_BLOCK_ALIGN;
import static com.phillippitts.speaktomack.service.audio.AudioFormat.REQUIRED_BYTE_RATE;
import static com.phillippitts.speaktomack.service.audio.AudioFormat.REQUIRED_CHANNELS;
import static com.phillippitts.speaktomack.service.audio.AudioFormat.REQUIRED_SAMPLE_RATE;

/**
 * Startup sanity check for audio format constants required by STT engines.
 * Logs the effective format and fails fast if misconfigured.
 */
@Configuration
class AudioFormatConfig {
    private static final Logger LOG = LogManager.getLogger(AudioFormatConfig.class);

    @PostConstruct
    void validateAudioFormatConstants() {
        if (REQUIRED_SAMPLE_RATE != 16_000 || REQUIRED_BITS_PER_SAMPLE != 16
                || REQUIRED_CHANNELS != 1 || REQUIRED_BIG_ENDIAN) {
            throw new IllegalStateException(
                    "Audio format constants misconfigured. Expected 16kHz, 16-bit, mono, little-endian.");
        }
        LOG.info("Audio format configured: sampleRate={} Hz, bitsPerSample={}, channels={}, "
                        + "byteRate={}, blockAlign={} (littleEndian={})",
                REQUIRED_SAMPLE_RATE, REQUIRED_BITS_PER_SAMPLE, REQUIRED_CHANNELS,
                REQUIRED_BYTE_RATE, REQUIRED_BLOCK_ALIGN, !REQUIRED_BIG_ENDIAN);
    }
}
