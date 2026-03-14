package com.boombapcompile.blckvox.integration;

import com.boombapcompile.blckvox.config.IntegrationTestConfiguration;
import com.boombapcompile.blckvox.domain.TranscriptionResult;
import com.boombapcompile.blckvox.service.fallback.TypingService;
import com.boombapcompile.blckvox.service.orchestration.ApplicationState;
import com.boombapcompile.blckvox.service.orchestration.RecordingService;
import com.boombapcompile.blckvox.service.stt.vosk.VoskSttEngine;
import com.boombapcompile.blckvox.service.stt.whisper.WhisperSttEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * Spring-context integration test for the orchestration pipeline.
 *
 * <p>Verifies that {@link RecordingService#startRecording()} followed by
 * {@link RecordingService#stopRecording()} produces a transcription event
 * that reaches the {@link TypingService}.
 *
 * <p>Uses {@link IntegrationTestConfiguration} to provide fake audio capture
 * and mock STT engines, avoiding any hardware or binary dependencies.
 */
@SpringBootTest(properties = {
        "stt.reconciliation.enabled=false"
})
@Import(IntegrationTestConfiguration.class)
@Tag("integration")
class OrchestrationPipelineIntegrationTest {

    @Autowired
    private RecordingService recordingService;

    @Autowired
    @Qualifier("voskSttEngine")
    private VoskSttEngine voskEngine;

    @Autowired
    @Qualifier("whisperSttEngine")
    private WhisperSttEngine whisperEngine;

    @MockitoBean
    private TypingService typingService;

    @BeforeEach
    void configureEngines() {
        Mockito.clearInvocations(voskEngine, whisperEngine, typingService);

        Mockito.when(voskEngine.transcribe(any(byte[].class)))
                .thenReturn(TranscriptionResult.of("hello world", 0.95, "vosk"));
        Mockito.when(voskEngine.isHealthy()).thenReturn(true);

        Mockito.when(whisperEngine.transcribe(any(byte[].class)))
                .thenReturn(TranscriptionResult.of("hello world", 0.90, "whisper"));
        Mockito.when(whisperEngine.isHealthy()).thenReturn(true);

        Mockito.when(typingService.paste(anyString())).thenReturn(true);
    }

    @AfterEach
    void cleanup() {
        if (recordingService.isRecording()) {
            recordingService.cancelRecording();
        }
    }

    @Test
    void recordingLifecycleProducesTranscriptionAndTypesText() {
        assertTrue(recordingService.startRecording());
        assertThat(recordingService.getState()).isEqualTo(ApplicationState.RECORDING);

        assertTrue(recordingService.stopRecording());

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                Mockito.verify(typingService).paste("hello world")
        );
    }

    @Test
    void cancelRecordingDoesNotTriggerTranscription() {
        assertTrue(recordingService.startRecording());
        recordingService.cancelRecording();

        await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                assertThat(recordingService.getState()).isEqualTo(ApplicationState.IDLE)
        );

        Mockito.verify(typingService, Mockito.never()).paste(anyString());
    }

    @Test
    void doubleStartRecordingIsRejected() {
        assertTrue(recordingService.startRecording());
        assertFalse(recordingService.startRecording());
    }
}
