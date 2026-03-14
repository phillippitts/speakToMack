package com.boombapcompile.blckvox.service.stt.vosk;

import com.boombapcompile.blckvox.config.stt.VoskConfig;
import com.boombapcompile.blckvox.service.audio.capture.PcmChunkCapturedEvent;
import com.boombapcompile.blckvox.service.orchestration.ApplicationState;
import com.boombapcompile.blckvox.service.orchestration.event.ApplicationStateChangedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

class VoskStreamingServiceTest {

    private final VoskConfig config = new VoskConfig("/tmp/nonexistent", 16000, 1);
    private final VoskModelProvider modelProvider = mock(VoskModelProvider.class);
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);

    @Test
    void onPcmChunkReturnsEarlyWhenRecognizerNull() {
        // Recognizer is null by default (no recording session started)
        VoskStreamingService service = new VoskStreamingService(config, modelProvider, publisher);

        assertThatCode(() -> service.onPcmChunk(
                new PcmChunkCapturedEvent(new byte[100], 100, UUID.randomUUID())))
                .doesNotThrowAnyException();
    }

    @Test
    void onStateChangedWithNonRecordingClosesRecognizer() {
        VoskStreamingService service = new VoskStreamingService(config, modelProvider, publisher);

        // Transition to IDLE should close recognizer (null recognizer is fine)
        assertThatCode(() -> service.onStateChanged(
                new ApplicationStateChangedEvent(
                        ApplicationState.RECORDING, ApplicationState.IDLE, Instant.now())))
                .doesNotThrowAnyException();
    }

    @Test
    void onStateChangedWithTranscribingClosesRecognizer() {
        VoskStreamingService service = new VoskStreamingService(config, modelProvider, publisher);

        assertThatCode(() -> service.onStateChanged(
                new ApplicationStateChangedEvent(
                        ApplicationState.RECORDING, ApplicationState.TRANSCRIBING, Instant.now())))
                .doesNotThrowAnyException();
    }

    @Test
    void shutdownDoesNotThrowWhenRecognizerNull() {
        VoskStreamingService service = new VoskStreamingService(config, modelProvider, publisher);

        assertThatCode(service::shutdown).doesNotThrowAnyException();
    }
}
