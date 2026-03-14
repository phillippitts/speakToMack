package com.boombapcompile.blckvox.service.stt.parallel;

import com.boombapcompile.blckvox.domain.TranscriptionResult;
import com.boombapcompile.blckvox.exception.TranscriptionException;
import com.boombapcompile.blckvox.service.stt.SttEngine;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultParallelSttServiceTest {

    @Test
    void returnsBothWhenBothSucceed() {
        SttEngine vosk = new StubEngine("vosk", 10, false);
        SttEngine whisper = new StubEngine("whisper", 10, false);
        Executor exec = Executors.newFixedThreadPool(2);
        DefaultParallelSttService svc = new DefaultParallelSttService(vosk, whisper, exec, 1000);
        var pair = svc.transcribeBoth(new byte[3200], 500);
        assertThat(pair.vosk()).isNotNull();
        assertThat(pair.whisper()).isNotNull();
    }

    @Test
    void succeedsWhenOneFails() {
        SttEngine vosk = new StubEngine("vosk", 10, true);
        SttEngine whisper = new StubEngine("whisper", 10, false);
        Executor exec = Executors.newFixedThreadPool(2);
        DefaultParallelSttService svc = new DefaultParallelSttService(vosk, whisper, exec, 1000);
        var pair = svc.transcribeBoth(new byte[3200], 500);
        assertThat(pair.vosk()).isNull();
        assertThat(pair.whisper()).isNotNull();
    }

    @Test
    void throwsWhenBothFail() {
        SttEngine vosk = new StubEngine("vosk", 10, true);
        SttEngine whisper = new StubEngine("whisper", 10, true);
        Executor exec = Executors.newFixedThreadPool(2);
        DefaultParallelSttService svc = new DefaultParallelSttService(vosk, whisper, exec, 100);
        assertThatThrownBy(() -> svc.transcribeBoth(new byte[3200], 50))
                .isInstanceOf(TranscriptionException.class);
    }

    @Test
    void usesDefaultTimeoutWhenZero() {
        SttEngine vosk = new StubEngine("vosk", 10, false);
        SttEngine whisper = new StubEngine("whisper", 10, false);
        Executor exec = Executors.newFixedThreadPool(2);
        DefaultParallelSttService svc = new DefaultParallelSttService(vosk, whisper, exec, 5000);
        // timeoutMs=0 should use default
        var pair = svc.transcribeBoth(new byte[3200], 0);
        assertThat(pair.vosk()).isNotNull();
    }

    @Test
    void usesDefaultTimeoutWhenNegative() {
        SttEngine vosk = new StubEngine("vosk", 10, false);
        SttEngine whisper = new StubEngine("whisper", 10, false);
        Executor exec = Executors.newFixedThreadPool(2);
        DefaultParallelSttService svc = new DefaultParallelSttService(vosk, whisper, exec, 5000);
        var pair = svc.transcribeBoth(new byte[3200], -1);
        assertThat(pair.vosk()).isNotNull();
    }

    @Test
    void handlesRuntimeExceptionFromEngine() {
        SttEngine vosk = new RuntimeExceptionEngine("vosk");
        SttEngine whisper = new StubEngine("whisper", 10, false);
        Executor exec = Executors.newFixedThreadPool(2);
        DefaultParallelSttService svc = new DefaultParallelSttService(vosk, whisper, exec, 5000);
        var pair = svc.transcribeBoth(new byte[3200], 1000);
        assertThat(pair.vosk()).isNull();
        assertThat(pair.whisper()).isNotNull();
    }

    @Test
    void constructorFallsBackToDefaultTimeoutWhenNegative() {
        SttEngine vosk = new StubEngine("vosk", 10, false);
        SttEngine whisper = new StubEngine("whisper", 10, false);
        Executor exec = Executors.newFixedThreadPool(2);
        // Negative timeout should fallback to 10000
        DefaultParallelSttService svc = new DefaultParallelSttService(vosk, whisper, exec, -1);
        var pair = svc.transcribeBoth(new byte[3200], 5000);
        assertThat(pair.vosk()).isNotNull();
    }

    @Test
    void rejectsNullPcm() {
        SttEngine vosk = new StubEngine("vosk", 10, false);
        SttEngine whisper = new StubEngine("whisper", 10, false);
        Executor exec = Executors.newFixedThreadPool(2);
        DefaultParallelSttService svc = new DefaultParallelSttService(vosk, whisper, exec, 5000);
        assertThatThrownBy(() -> svc.transcribeBoth(null, 1000))
                .isInstanceOf(NullPointerException.class);
    }

    static class RuntimeExceptionEngine implements SttEngine {
        private final String name;
        RuntimeExceptionEngine(String name) { this.name = name; }
        @Override public void initialize() {}
        @Override public TranscriptionResult transcribe(byte[] audioData) {
            throw new RuntimeException("unexpected boom");
        }
        @Override public String getEngineName() { return name; }
        @Override public boolean isHealthy() { return true; }
        @Override public void close() {}
    }

    static class StubEngine implements SttEngine {
        final String name;
        final long delayMs;
        final boolean fail;
        StubEngine(String name, long delayMs, boolean fail) {
            this.name = name;
            this.delayMs = delayMs;
            this.fail = fail;
        }
        @Override
        public void initialize() {
        }
        @Override
        public TranscriptionResult transcribe(byte[] audioData) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException ignored) {
            }
            if (fail) {
                throw new TranscriptionException("fail", name);
            }
            return TranscriptionResult.of(name + "-text", 1.0, name);
        }
        @Override
        public String getEngineName() {
            return name;
        }
        @Override
        public boolean isHealthy() {
            return true;
        }
        @Override
        public void close() {
        }
    }
}
