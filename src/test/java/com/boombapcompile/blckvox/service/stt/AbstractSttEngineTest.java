package com.boombapcompile.blckvox.service.stt;

import com.boombapcompile.blckvox.domain.TranscriptionResult;
import com.boombapcompile.blckvox.exception.TranscriptionException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AbstractSttEngineTest {

    // --- Testable concrete subclass ---

    static class TestableEngine extends AbstractSttEngine {
        boolean doInitCalled = false;
        boolean doCloseCalled = false;
        RuntimeException initException = null;

        @Override
        protected void doInitialize() {
            if (initException != null) {
                throw initException;
            }
            doInitCalled = true;
            closed = false;
        }

        @Override
        protected void doClose() {
            doCloseCalled = true;
        }

        @Override
        public TranscriptionResult transcribe(byte[] audioData) {
            ensureInitialized();
            return TranscriptionResult.of("test", 1.0, getEngineName());
        }

        @Override
        public String getEngineName() {
            return "testable";
        }

        TranscriptionException testHandleError(Exception e) {
            return handleTranscriptionError(e, null, null);
        }
    }

    @Test
    void initializeSetsHealthyTrue() {
        TestableEngine engine = new TestableEngine();

        engine.initialize();

        assertThat(engine.isHealthy()).isTrue();
    }

    @Test
    void doubleInitializeIsIdempotent() {
        TestableEngine engine = new TestableEngine();

        engine.initialize();
        assertThat(engine.doInitCalled).isTrue();

        // Reset the flag to detect a second doInitialize() call
        engine.doInitCalled = false;
        engine.initialize();

        assertThat(engine.doInitCalled).isFalse();
    }

    @Test
    void closeAfterInitSetsHealthyFalse() {
        TestableEngine engine = new TestableEngine();
        engine.initialize();

        engine.close();

        assertThat(engine.isHealthy()).isFalse();
        assertThat(engine.doCloseCalled).isTrue();
    }

    @Test
    void doubleCloseIsIdempotent() {
        TestableEngine engine = new TestableEngine();
        engine.initialize();

        engine.close();
        assertThat(engine.doCloseCalled).isTrue();

        // Reset the flag to detect a second doClose() call
        engine.doCloseCalled = false;
        engine.close();

        // doClose() should not have been called again
        assertThat(engine.doCloseCalled).isFalse();
    }

    @Test
    void ensureInitializedThrowsWhenNotInitialized() {
        TestableEngine engine = new TestableEngine();

        assertThatThrownBy(() -> engine.transcribe(new byte[10]))
                .isInstanceOf(TranscriptionException.class)
                .hasMessageContaining("not initialized");
    }

    @Test
    void ensureInitializedThrowsWhenClosed() {
        TestableEngine engine = new TestableEngine();
        engine.initialize();
        engine.close();

        assertThatThrownBy(() -> engine.transcribe(new byte[10]))
                .isInstanceOf(TranscriptionException.class)
                .hasMessageContaining("not initialized");
    }

    @Test
    void handleTranscriptionErrorPreservesTranscriptionException() {
        TestableEngine engine = new TestableEngine();
        engine.initialize();

        TranscriptionException original = new TranscriptionException("original error", "testable");

        assertThatThrownBy(() -> engine.testHandleError(original))
                .isSameAs(original);
    }

    @Test
    void handleTranscriptionErrorWrapsOtherExceptions() {
        TestableEngine engine = new TestableEngine();
        engine.initialize();

        RuntimeException cause = new RuntimeException("something went wrong");

        assertThatThrownBy(() -> engine.testHandleError(cause))
                .isInstanceOf(TranscriptionException.class)
                .hasCause(cause)
                .hasMessageContaining("testable")
                .hasMessageContaining("something went wrong");
    }
}
