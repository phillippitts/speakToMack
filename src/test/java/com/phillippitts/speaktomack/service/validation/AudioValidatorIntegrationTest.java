package com.phillippitts.speaktomack.service.validation;

import com.phillippitts.speaktomack.TestResourceLoader;
import com.phillippitts.speaktomack.config.IntegrationTestConfiguration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test using real test resources under src/test/resources/audio.
 * Ensures AudioValidator accepts a valid 1-second PCM clip and rejects too-short clips.
 */
@Tag("integration")
@Import(IntegrationTestConfiguration.class)
@SpringBootTest(properties = {
        "stt.validation.enabled=false" // avoid requiring real models/binaries in CI
})
@TestPropertySource(properties = {
        "audio.validation.min-duration-ms=200",
        "audio.validation.max-duration-ms=60000"
})
class AudioValidatorIntegrationTest {

    @Autowired
    private AudioValidator validator;

    @Test
    void shouldAcceptOneSecondPcmSilence() throws IOException {
        byte[] pcm = TestResourceLoader.loadPcm("/audio/silence-1s.pcm");
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
}
