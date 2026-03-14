package com.boombapcompile.blckvox.service.livecaption;

import com.boombapcompile.blckvox.config.properties.LiveCaptionProperties;
import com.boombapcompile.blckvox.service.audio.capture.PcmChunkCapturedEvent;
import com.boombapcompile.blckvox.service.orchestration.ApplicationState;
import com.boombapcompile.blckvox.service.orchestration.event.ApplicationStateChangedEvent;
import com.boombapcompile.blckvox.service.stt.vosk.VoskPartialResultEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class LiveCaptionManagerTest {

    private final LiveCaptionProperties props = new LiveCaptionProperties(true, 600, 250, 0.85);

    private LiveCaptionManager createDisabledManager() throws Exception {
        LiveCaptionManager manager = new LiveCaptionManager(props);
        // Use reflection to set enabled to false without calling Platform.runLater
        Field enabledField = LiveCaptionManager.class.getDeclaredField("enabled");
        enabledField.setAccessible(true);
        ((AtomicBoolean) enabledField.get(manager)).set(false);
        return manager;
    }

    @Test
    void isEnabledReturnsTrueByDefault() {
        LiveCaptionManager manager = new LiveCaptionManager(props);
        assertThat(manager.isEnabled()).isTrue();
    }

    @Test
    void onStateChangedReturnsEarlyWhenDisabled() throws Exception {
        LiveCaptionManager manager = createDisabledManager();
        // Should not throw or call Platform.runLater
        assertThatCode(() -> manager.onStateChanged(
                new ApplicationStateChangedEvent(
                        ApplicationState.IDLE, ApplicationState.RECORDING, Instant.now())))
                .doesNotThrowAnyException();
    }

    @Test
    void onPcmChunkReturnsEarlyWhenDisabled() throws Exception {
        LiveCaptionManager manager = createDisabledManager();
        assertThatCode(() -> manager.onPcmChunk(
                new PcmChunkCapturedEvent(new byte[100], 100, UUID.randomUUID())))
                .doesNotThrowAnyException();
    }

    @Test
    void onPcmChunkReturnsEarlyWhenWindowNull() {
        // Window is null by default (never shown)
        LiveCaptionManager manager = new LiveCaptionManager(props);
        assertThatCode(() -> manager.onPcmChunk(
                new PcmChunkCapturedEvent(new byte[100], 100, UUID.randomUUID())))
                .doesNotThrowAnyException();
    }

    @Test
    void onVoskPartialResultReturnsEarlyWhenDisabled() throws Exception {
        LiveCaptionManager manager = createDisabledManager();
        assertThatCode(() -> manager.onVoskPartialResult(
                new VoskPartialResultEvent("hello", false)))
                .doesNotThrowAnyException();
    }

    @Test
    void onVoskPartialResultReturnsEarlyWhenWindowNull() {
        LiveCaptionManager manager = new LiveCaptionManager(props);
        assertThatCode(() -> manager.onVoskPartialResult(
                new VoskPartialResultEvent("hello", false)))
                .doesNotThrowAnyException();
    }
}
