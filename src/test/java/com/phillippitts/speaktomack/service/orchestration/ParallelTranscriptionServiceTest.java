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
                .isInstanceOf(Exception.class);

        exec.shutdownNow();
    }

    // --- Test doubles ---

    private static final class FakeEngine implements SttEngine {
        private final String name;
        private final String text;
        private boolean initialized;

        FakeEngine(String name, String text) {
            this.name = name;
            this.text = text;
            this.initialized = true;
        }

        @Override
        public void initialize() {
            this.initialized = true;
        }

        @Override
        public TranscriptionResult transcribe(byte[] audioData) {
            if (!initialized) {
                throw new TranscriptionException("not initialized", name);
            }
            return TranscriptionResult.of(text, 1.0, name);
        }

        @Override
        public String getEngineName() {
            return name;
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

    private static final class SlowEngine implements SttEngine {
        private final String name;
        private final long delayMs;
        private boolean initialized = true;

        SlowEngine(String name, long delayMs) {
            this.name = name;
            this.delayMs = delayMs;
        }

        @Override
        public void initialize() {
            this.initialized = true;
        }

        @Override
        public TranscriptionResult transcribe(byte[] audioData) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (!initialized) {
                throw new TranscriptionException("not initialized", name);
            }
            return TranscriptionResult.of("slow", 1.0, name);
        }

        @Override
        public String getEngineName() {
            return name;
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
}
