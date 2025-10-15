package com.phillippitts.speaktomack.service.stt;

import com.phillippitts.speaktomack.domain.TranscriptionResult;
import com.phillippitts.speaktomack.exception.ModelNotFoundException;
import com.phillippitts.speaktomack.exception.TranscriptionException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SttEngineTest {

    @Test
    void interfaceShouldCompileAndBeImplementable() {
        SttEngine engine = new TestSttEngine();
        assertThat(engine).isNotNull();
        assertThat(engine.getEngineName()).isEqualTo("test");
    }

    @Test
    void shouldSupportAutoCloseable() {
        SttEngine engine = new TestSttEngine();
        assertThat(engine).isInstanceOf(AutoCloseable.class);
    }

    @Test
    void implementationShouldThrowModelNotFoundException() {
        SttEngine engine = new FailingTestEngine();
        assertThatThrownBy(engine::initialize)
                .isInstanceOf(ModelNotFoundException.class);
    }

    @Test
    void implementationShouldThrowTranscriptionException() {
        SttEngine engine = new FailingTestEngine();
        assertThatThrownBy(() -> engine.transcribe(new byte[100]))
                .isInstanceOf(TranscriptionException.class);
    }

    // --- Test Implementations ---

    private static class TestSttEngine implements SttEngine {
        private boolean initialized = false;

        @Override
        public void initialize() {
            this.initialized = true;
        }

        @Override
        public TranscriptionResult transcribe(byte[] audioData) {
            if (!initialized) {
                throw new TranscriptionException("Engine not initialized", "test");
            }
            return TranscriptionResult.of("test transcription", 1.0, "test");
        }

        @Override
        public String getEngineName() {
            return "test";
        }

        @Override
        public boolean isHealthy() {
            return initialized;
        }

        @Override
        public void close() {
            this.initialized = false;
        }
    }

    private static class FailingTestEngine implements SttEngine {
        @Override
        public void initialize() {
            throw new ModelNotFoundException("/fake/model/path");
        }

        @Override
        public TranscriptionResult transcribe(byte[] audioData) {
            throw new TranscriptionException("Transcription failed", "failing-test");
        }

        @Override
        public String getEngineName() {
            return "failing-test";
        }

        @Override
        public boolean isHealthy() {
            return false;
        }

        @Override
        public void close() {
            // No-op
        }
    }
}
