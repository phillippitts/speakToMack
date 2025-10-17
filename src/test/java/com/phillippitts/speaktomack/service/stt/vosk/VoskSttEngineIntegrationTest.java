package com.phillippitts.speaktomack.service.stt.vosk;

import com.phillippitts.speaktomack.TestResourceLoader;
import com.phillippitts.speaktomack.config.stt.VoskConfig;
import com.phillippitts.speaktomack.domain.TranscriptionResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
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

    // Audio constants (16kHz, 16-bit, mono = 16000 Hz * 2 bytes/sample)
    private static final int BYTES_PER_SECOND = 32_000;
    private static final int SILENCE_1S_BYTES = BYTES_PER_SECOND;
    private static final int SILENCE_3S_BYTES = BYTES_PER_SECOND * 3;
    private static final int SILENCE_250MS_BYTES = BYTES_PER_SECOND / 4;

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
        byte[] silence1s = new byte[SILENCE_1S_BYTES];

        TranscriptionResult result = engine.transcribe(silence1s);

        assertThat(result).isNotNull();
        assertThat(result.engineName()).isEqualTo("vosk");
        // Silence should produce empty or minimal text (not throw exception)
        assertThat(result.text()).isNotNull();
        assertThat(result.confidence()).isBetween(0.0, 1.0);
    }

    @Test
    void shouldTranscribe3SecondsOfSilence() {
        byte[] silence3s = new byte[SILENCE_3S_BYTES];

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
        byte[] silence1s = new byte[SILENCE_1S_BYTES];

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
        byte[] silence1s = new byte[SILENCE_1S_BYTES];
        TranscriptionResult result = engine.transcribe(silence1s);
        assertThat(result).isNotNull();
    }

    @Test
    void shouldTranscribeFromTestResource() throws IOException {
        byte[] pcm = TestResourceLoader.loadPcm("/audio/silence-1s.pcm");

        TranscriptionResult result = engine.transcribe(pcm);

        assertThat(result).isNotNull();
        assertThat(result.engineName()).isEqualTo("vosk");
        assertThat(result.text()).isNotNull();
        assertThat(result.confidence()).isBetween(0.0, 1.0);
    }

    @Test
    void shouldTranscribeNonSilenceAudio() throws IOException {
        byte[] pcm = TestResourceLoader.loadPcm("/audio/tone-pattern.pcm");

        TranscriptionResult result = engine.transcribe(pcm);

        assertThat(result).isNotNull();
        assertThat(result.engineName()).isEqualTo("vosk");
        // Tone pattern is not speech, so transcription may be empty or gibberish
        assertThat(result.text()).isNotNull();
        assertThat(result.confidence()).isBetween(0.0, 1.0);
    }

    @Test
    void shouldProduceLowConfidenceForNonSpeechAudio() throws IOException {
        byte[] tonePattern = TestResourceLoader.loadPcm("/audio/tone-pattern.pcm");

        TranscriptionResult result = engine.transcribe(tonePattern);

        // Non-speech audio typically produces low confidence or empty result
        assertThat(result).isNotNull();
        // Either empty text or low confidence (or both)
        boolean isLowQuality = result.text().isEmpty() || result.confidence() < 0.5;
        assertThat(isLowQuality).isTrue();
    }

    @Test
    void shouldHandleDifferentDurations() throws IOException {
        byte[] silence1s = TestResourceLoader.loadPcm("/audio/silence-1s.pcm");
        byte[] silence3s = TestResourceLoader.loadPcm("/audio/silence-3s.pcm");

        TranscriptionResult result1s = engine.transcribe(silence1s);
        TranscriptionResult result3s = engine.transcribe(silence3s);

        // Both should succeed without errors
        assertThat(result1s).isNotNull();
        assertThat(result3s).isNotNull();
        assertThat(result1s.engineName()).isEqualTo("vosk");
        assertThat(result3s.engineName()).isEqualTo("vosk");
    }

    @Test
    void shouldTranscribeMinimalValidDurationPcm() {
        byte[] pcm = new byte[SILENCE_250MS_BYTES];
        TranscriptionResult result = engine.transcribe(pcm);
        assertThat(result).isNotNull();
        assertThat(result.confidence()).isBetween(0.0, 1.0);
    }

    // Note: Additional tests for accents and background noise require recording
    // real audio samples. These tests are intentionally omitted until we have
    // actual test resources. See src/test/resources/audio/README.md for instructions
    // on creating these files:
    // - phrase_british_en_1s.pcm (British accent speech)
    // - phrase_indian_en_1s.pcm (Indian accent speech)
    // - speech_with_cafe_noise_3s.pcm (Speech with background noise)
    // - cafe_noise_only_3s.pcm (Background noise only)
}
