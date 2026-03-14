package com.boombapcompile.blckvox.service.stt.vosk;

import com.boombapcompile.blckvox.config.stt.VoskConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VoskModelProviderTest {

    @Test
    void getModelAfterCloseThrowsIllegalState() {
        VoskConfig config = new VoskConfig("/tmp/nonexistent-model", 16000, 1);
        VoskModelProvider provider = new VoskModelProvider(config);

        provider.close();

        assertThatThrownBy(provider::getModel)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("closed");
    }

    @Test
    void closeWhenModelNullDoesNotThrow() {
        VoskConfig config = new VoskConfig("/tmp/nonexistent-model", 16000, 1);
        VoskModelProvider provider = new VoskModelProvider(config);

        // Model was never loaded, close should be a no-op
        assertThatCode(provider::close).doesNotThrowAnyException();
    }

    @Test
    void doubleCloseDoesNotThrow() {
        VoskConfig config = new VoskConfig("/tmp/nonexistent-model", 16000, 1);
        VoskModelProvider provider = new VoskModelProvider(config);

        provider.close();
        assertThatCode(provider::close).doesNotThrowAnyException();
    }

    @Test
    void getModelWithInvalidPathThrowsRuntimeException() {
        VoskConfig config = new VoskConfig("/tmp/nonexistent-vosk-model-xyz", 16000, 1);
        VoskModelProvider provider = new VoskModelProvider(config);

        assertThatThrownBy(provider::getModel)
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to load Vosk model");
    }

    @Test
    void constructorRejectsNullConfig() {
        assertThatThrownBy(() -> new VoskModelProvider(null))
                .isInstanceOf(NullPointerException.class);
    }
}
