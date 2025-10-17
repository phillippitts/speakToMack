package com.phillippitts.speaktomack.service.stt.vosk;

import com.phillippitts.speaktomack.config.stt.VoskConfig;
import com.phillippitts.speaktomack.exception.TranscriptionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VoskSttEngineLifecycleTest {

    @Test
    void shouldFailWhenInitializingWithInvalidModelPath() {
        VoskConfig bad = new VoskConfig("/tmp/does-not-exist-vosk", 16_000, 1);
        VoskSttEngine engine = new VoskSttEngine(bad);

        assertThatThrownBy(engine::initialize)
            .isInstanceOf(TranscriptionException.class)
            .hasMessageContaining("Failed to initialize Vosk");
    }

    @Test
    void shouldAllowIdempotentCloseEvenAfterFailedInitialize() {
        VoskConfig bad = new VoskConfig("/tmp/does-not-exist-vosk", 16_000, 1);
        VoskSttEngine engine = new VoskSttEngine(bad);

        try {
            engine.initialize();
        } catch (Exception ignored) {
            // Expected failure
        }

        // close should not throw even after failed initialize
        assertThatCode(engine::close).doesNotThrowAnyException();
        // idempotent close
        assertThatCode(engine::close).doesNotThrowAnyException();
    }

    @Test
    void shouldFailTranscribeWhenNotInitialized() {
        VoskConfig cfg = new VoskConfig("/tmp/does-not-exist-vosk", 16_000, 1);
        VoskSttEngine engine = new VoskSttEngine(cfg);

        assertThatThrownBy(() -> engine.transcribe(new byte[100]))
            .isInstanceOf(TranscriptionException.class)
            .hasMessageContaining("not initialized");
    }

    @Test
    void shouldReportUnhealthyWhenNotInitialized() {
        VoskConfig cfg = new VoskConfig("/tmp/does-not-exist-vosk", 16_000, 1);
        VoskSttEngine engine = new VoskSttEngine(cfg);

        assertThat(engine.isHealthy()).isFalse();
        assertThat(engine.getEngineName()).isEqualTo("vosk");
    }

    @Test
    void shouldReportUnhealthyAfterClose() {
        VoskConfig cfg = new VoskConfig("/tmp/does-not-exist-vosk", 16_000, 1);
        VoskSttEngine engine = new VoskSttEngine(cfg);

        engine.close();
        assertThat(engine.isHealthy()).isFalse();
    }

    /**
     * Integration test with real Vosk model.
     * Only runs if models are available (typically in CI or local dev with models downloaded).
     */
    @Test
    @EnabledIfSystemProperty(named = "vosk.model.available", matches = "true")
    void shouldInitializeAndCloseSuccessfullyWithRealModel() {
        VoskConfig config = new VoskConfig("models/vosk-model-small-en-us-0.15", 16_000, 1);
        VoskSttEngine engine = new VoskSttEngine(config);

        assertThat(engine.isHealthy()).isFalse();

        assertThatCode(engine::initialize).doesNotThrowAnyException();

        assertThat(engine.isHealthy()).isTrue();
        assertThat(engine.getEngineName()).isEqualTo("vosk");

        engine.close();
        assertThat(engine.isHealthy()).isFalse();
    }

    /**
     * Integration test verifying idempotent initialization.
     * Only runs if models are available.
     */
    @Test
    @EnabledIfSystemProperty(named = "vosk.model.available", matches = "true")
    void shouldBeIdempotentForMultipleInitializations() {
        VoskConfig config = new VoskConfig("models/vosk-model-small-en-us-0.15", 16_000, 1);
        VoskSttEngine engine = new VoskSttEngine(config);

        engine.initialize();
        engine.initialize(); // should not throw or leak resources

        assertThat(engine.isHealthy()).isTrue();
        engine.close();
    }
}
