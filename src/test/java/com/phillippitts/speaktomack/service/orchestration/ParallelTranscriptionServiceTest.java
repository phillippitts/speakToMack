package com.phillippitts.speaktomack.service.orchestration;

import com.phillippitts.speaktomack.domain.TranscriptionResult;
import com.phillippitts.speaktomack.exception.TranscriptionException;
import com.phillippitts.speaktomack.service.stt.SttEngine;
import com.phillippitts.speaktomack.service.validation.AudioValidationProperties;
import com.phillippitts.speaktomack.service.validation.AudioValidator;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.phillippitts.speaktomack.service.orchestration.OrchestrationTestDoubles.FakeEngine;
import static com.phillippitts.speaktomack.service.orchestration.OrchestrationTestDoubles.FailingEngine;
import static com.phillippitts.speaktomack.service.orchestration.OrchestrationTestDoubles.SlowEngine;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParallelTranscriptionServiceTest {

    private static final byte[] ONE_SECOND_PCM = new byte[32_000]; // 1s @ 16kHz mono 16-bit

    @Test
    void shouldReturnBothResultsWithinTimeout() {
        AudioValidator validator = new AudioValidator(new AudioValidationProperties());
        SttEngine vosk = new FakeEngine("vosk", "hello");
        SttEngine whisper = new FakeEngine("whisper", "world");
        ExecutorService exec = Executors.newFixedThreadPool(4);

        ParallelTranscriptionService.EngineConfig config =
                new ParallelTranscriptionService.EngineConfig(vosk, whisper, exec);
        ParallelTranscriptionService svc = new ParallelTranscriptionService(validator, config);
        List<TranscriptionResult> results = svc.transcribeBoth(ONE_SECOND_PCM, Duration.ofSeconds(5));

        assertThat(results).hasSize(2);
        assertThat(results.get(0).engineName()).isEqualTo("vosk");
        assertThat(results.get(1).engineName()).isEqualTo("whisper");
        assertThat(results.get(0).text()).isEqualTo("hello");
        assertThat(results.get(1).text()).isEqualTo("world");

        exec.shutdownNow();
    }

    @Test
    void shouldTimeoutWhenEnginesAreSlow() {
        AudioValidator validator = new AudioValidator(new AudioValidationProperties());
        SttEngine slowVosk = new SlowEngine("vosk", 10_000);
        SttEngine slowWhisper = new SlowEngine("whisper", 10_000);
        ExecutorService exec = Executors.newFixedThreadPool(2);

        ParallelTranscriptionService.EngineConfig config =
                new ParallelTranscriptionService.EngineConfig(slowVosk, slowWhisper, exec);
        ParallelTranscriptionService svc = new ParallelTranscriptionService(validator, config);

        assertThatThrownBy(() -> svc.transcribeBoth(ONE_SECOND_PCM, Duration.ofMillis(200)))
                .isInstanceOf(TranscriptionException.class)
                .hasMessageContaining("timed out");

        exec.shutdownNow();
    }

    @Test
    void shouldHandlePartialFailure() {
        AudioValidator validator = new AudioValidator(new AudioValidationProperties());
        SttEngine goodVosk = new FakeEngine("vosk", "success");
        SttEngine failingWhisper = new FailingEngine("whisper");
        ExecutorService exec = Executors.newFixedThreadPool(2);

        ParallelTranscriptionService.EngineConfig config =
                new ParallelTranscriptionService.EngineConfig(goodVosk, failingWhisper, exec);
        ParallelTranscriptionService svc = new ParallelTranscriptionService(validator, config);

        assertThatThrownBy(() -> svc.transcribeBoth(ONE_SECOND_PCM, Duration.ofSeconds(5)))
                .isInstanceOf(TranscriptionException.class)
                .hasMessageContaining("Parallel transcription failed");

        exec.shutdownNow();
    }

    @Test
    void shouldPropagateValidationErrors() {
        AudioValidator validator = new AudioValidator(new AudioValidationProperties());
        SttEngine vosk = new FakeEngine("vosk", "hello");
        SttEngine whisper = new FakeEngine("whisper", "world");
        ExecutorService exec = Executors.newFixedThreadPool(2);

        ParallelTranscriptionService.EngineConfig config =
                new ParallelTranscriptionService.EngineConfig(vosk, whisper, exec);
        ParallelTranscriptionService svc = new ParallelTranscriptionService(validator, config);

        byte[] tooShort = new byte[1000]; // Less than 250ms minimum

        assertThatThrownBy(() -> svc.transcribeBoth(tooShort, Duration.ofSeconds(5)))
                .hasMessageContaining("Audio too short");

        exec.shutdownNow();
    }

    @Test
    void shouldActuallyRunInParallel() {
        AudioValidator validator = new AudioValidator(new AudioValidationProperties());
        SttEngine slowVosk = new SlowEngine("vosk", 500);
        SttEngine slowWhisper = new SlowEngine("whisper", 500);
        ExecutorService exec = Executors.newFixedThreadPool(2);

        ParallelTranscriptionService.EngineConfig config =
                new ParallelTranscriptionService.EngineConfig(slowVosk, slowWhisper, exec);
        ParallelTranscriptionService svc = new ParallelTranscriptionService(validator, config);

        long start = System.currentTimeMillis();
        List<TranscriptionResult> results = svc.transcribeBoth(ONE_SECOND_PCM, Duration.ofSeconds(5));
        long duration = System.currentTimeMillis() - start;

        assertThat(results).hasSize(2);
        // If running in parallel, should complete in ~500ms, not 1000ms (sequential)
        assertThat(duration).isLessThan(800);
        assertThat(duration).isGreaterThanOrEqualTo(500);

        exec.shutdownNow();
    }
}
