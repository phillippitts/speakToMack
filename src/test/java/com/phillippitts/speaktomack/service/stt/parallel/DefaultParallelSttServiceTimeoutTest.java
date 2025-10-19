package com.phillippitts.speaktomack.service.stt.parallel;

import com.phillippitts.speaktomack.domain.TranscriptionResult;
import com.phillippitts.speaktomack.exception.TranscriptionException;
import com.phillippitts.speaktomack.service.stt.SttEngine;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies that DefaultParallelSttService times out and throws when both engines exceed the timeout.
 */
class DefaultParallelSttServiceTimeoutTest {

    @Test
    void throwsOnTimeoutWhenBothEnginesSlow() {
        // Engines that sleep longer than the timeout and then succeed
        SttEngine slowVosk = new SleepyEngine("vosk", 300);
        SttEngine slowWhisper = new SleepyEngine("whisper", 300);
        Executor exec = Executors.newFixedThreadPool(2);
        DefaultParallelSttService svc = new DefaultParallelSttService(slowVosk, slowWhisper, exec, 100);
        // Use a very small timeout so both futures are canceled before completion
        assertThatThrownBy(() -> svc.transcribeBoth(new byte[3200], 50))
                .isInstanceOf(TranscriptionException.class)
                .hasMessageContaining("Both engines failed or timed out");
    }

    static class SleepyEngine implements SttEngine {
        final String name; final long delayMs;
        SleepyEngine(String name, long delayMs) { this.name = name; this.delayMs = delayMs; }
        @Override public void initialize() {}
        @Override public TranscriptionResult transcribe(byte[] audioData) {
            try { Thread.sleep(delayMs); } catch (InterruptedException ignored) {}
            return TranscriptionResult.of(name+"-text", 1.0, name);
        }
        @Override public String getEngineName() { return name; }
        @Override public boolean isHealthy() { return true; }
        @Override public void close() {}
    }
}
