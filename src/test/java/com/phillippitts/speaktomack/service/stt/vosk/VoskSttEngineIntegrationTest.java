package com.phillippitts.speaktomack.service.stt.vosk;

import com.phillippitts.speaktomack.config.stt.VoskConfig;
import com.phillippitts.speaktomack.domain.TranscriptionResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration test for VoskSttEngine with real Vosk model.
 *
 * <p>This test runs only if the Vosk model is available in the expected location.
 * It uses JUnit assumptions to skip gracefully if the model is not present.
 *
 * <p>Expected model location: models/vosk-model-small-en-us-0.15
 */
class VoskSttEngineIntegrationTest {

    private static final String MODEL_PATH = "models/vosk-model-small-en-us-0.15";
    private VoskSttEngine engine;

    @BeforeEach
    void setUp() {
        // Skip test if model not available
        assumeTrue(Files.exists(Path.of(MODEL_PATH)),
            "Vosk model not found at " + MODEL_PATH + ". Run ./setup-models.sh to download.");

        VoskConfig config = new VoskConfig(MODEL_PATH, 16_000, 1);
        engine = new VoskSttEngine(config);

        try {
            engine.initialize();
        } catch (Exception e) {
            // Skip test if JNI libraries not available (e.g., in CI without native libs)
            assumeTrue(false,
                "Vosk JNI libraries not available: " + e.getMessage()
                + ". This test requires Vosk native libraries to be installed.");
        }
    }

    @AfterEach
    void tearDown() {
        if (engine != null) {
            engine.close();
        }
    }

    @Test
    void shouldTranscribeSilenceWithoutError() {
        // 1 second of silence: 16kHz * 2 bytes/sample * 1 second = 32,000 bytes
        byte[] silence1s = new byte[32_000];

        TranscriptionResult result = engine.transcribe(silence1s);

        assertThat(result).isNotNull();
        assertThat(result.engineName()).isEqualTo("vosk");
        // Silence should produce empty or minimal text (not throw exception)
        assertThat(result.text()).isNotNull();
        assertThat(result.confidence()).isBetween(0.0, 1.0);
    }

    @Test
    void shouldTranscribe3SecondsOfSilence() {
        // 3 seconds of silence: 16kHz * 2 bytes/sample * 3 seconds = 96,000 bytes
        byte[] silence3s = new byte[96_000];

        TranscriptionResult result = engine.transcribe(silence3s);

        assertThat(result).isNotNull();
        assertThat(result.engineName()).isEqualTo("vosk");
        assertThat(result.text()).isNotNull();
        assertThat(result.confidence()).isBetween(0.0, 1.0);
    }

    @Test
    void shouldBeHealthyAfterInitialization() {
        assertThat(engine.isHealthy()).isTrue();
        assertThat(engine.getEngineName()).isEqualTo("vosk");
    }

    @Test
    void shouldBeUnhealthyAfterClose() {
        engine.close();
        assertThat(engine.isHealthy()).isFalse();
    }

    @Test
    void shouldHandleMultipleTranscriptions() {
        byte[] silence1s = new byte[32_000];

        // Transcribe 5 times in sequence
        for (int i = 0; i < 5; i++) {
            TranscriptionResult result = engine.transcribe(silence1s);
            assertThat(result).isNotNull();
            assertThat(result.engineName()).isEqualTo("vosk");
        }

        // Engine should still be healthy
        assertThat(engine.isHealthy()).isTrue();
    }

    @Test
    void shouldSupportIdempotentInitialize() {
        // Initialize again (should be idempotent)
        engine.initialize();
        engine.initialize();

        assertThat(engine.isHealthy()).isTrue();

        // Should still transcribe
        byte[] silence1s = new byte[32_000];
        TranscriptionResult result = engine.transcribe(silence1s);
        assertThat(result).isNotNull();
    }
}
