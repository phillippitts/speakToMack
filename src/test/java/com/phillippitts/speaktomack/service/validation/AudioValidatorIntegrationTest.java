package com.phillippitts.speaktomack.service.validation;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test using real test resources under src/test/resources/audio.
 * Ensures AudioValidator accepts a valid 1-second PCM clip and rejects too-short clips.
 */
@SpringBootTest(properties = {
        "stt.validation.enabled=false" // avoid requiring real models/binaries in CI
})
@TestPropertySource(properties = {
        "audio.validation.min-duration-ms=200",
        "audio.validation.max-duration-ms=60000"
})
class AudioValidatorIntegrationTest {

    private final AudioValidator validator = new AudioValidator(new AudioValidationProperties());

    @Test
    void shouldAcceptOneSecondPcmSilence() throws IOException {
        byte[] pcm = load("/audio/silence-1s.pcm");
        assertThatCode(() -> validator.validate(pcm))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectTooShortPcm() {
        byte[] tiny = new byte[100];
        assertThatThrownBy(() -> validator.validate(tiny))
                .isInstanceOf(com.phillippitts.speaktomack.exception.InvalidAudioException.class)
                .hasMessageContaining("Audio too short");
    }

    private static byte[] load(String path) throws IOException {
        try (InputStream in = AudioValidatorIntegrationTest.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("Test resource not found: " + path);
            }
            return in.readAllBytes();
        }
    }
}
