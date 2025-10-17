package com.phillippitts.speaktomack.service.stt.whisper;

import com.phillippitts.speaktomack.config.stt.WhisperConfig;
import com.phillippitts.speaktomack.exception.TranscriptionException;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WhisperProcessManagerHermeticTest {

    @Test
    void successReturnsStdout() throws Exception {
        // Arrange
        ProcessBehavior behavior = new ProcessBehavior("hello world", "", 0, 0);
        TestProcess tp = new TestProcess(behavior);
        ProcessFactory factory = new StubProcessFactory(tp);
        WhisperProcessManager mgr = new WhisperProcessManager(factory);
        WhisperConfig cfg = new WhisperConfig("/bin/echo", "/tmp/model.bin", 2, "en", 2);
        Path wav = Files.createTempFile("whisper-", ".wav");

        // Act
        String out = mgr.transcribe(wav, cfg);

        // Assert
        assertThat(out).contains("hello world");
    }

    @Test
    void nonZeroExitThrowsWithStderrSnippet() throws Exception {
        ProcessBehavior behavior = new ProcessBehavior("", "something went wrong", 1, 0);
        TestProcess tp = new TestProcess(behavior);
        ProcessFactory factory = new StubProcessFactory(tp);
        WhisperProcessManager mgr = new WhisperProcessManager(factory);
        WhisperConfig cfg = new WhisperConfig("/bin/echo", "/tmp/model.bin", 2, "en", 2);
        Path wav = Files.createTempFile("whisper-", ".wav");

        assertThatThrownBy(() -> mgr.transcribe(wav, cfg))
            .isInstanceOf(TranscriptionException.class)
            .hasMessageContaining("Non-zero exit")
            .hasMessageContaining("stderr=")
            .hasMessageContaining("engine=whisper");
    }

    @Test
    void timeoutKillsProcessAndThrows() throws Exception {
        // Process that never terminates by itself
        ProcessBehavior behavior = new ProcessBehavior("", "", 0, -1 /*never finish*/);
        TestProcess tp = new TestProcess(behavior);
        ProcessFactory factory = new StubProcessFactory(tp);
        WhisperProcessManager mgr = new WhisperProcessManager(factory);
        WhisperConfig cfg = new WhisperConfig("/bin/echo", "/tmp/model.bin", 1, "en", 2);
        Path wav = Files.createTempFile("whisper-", ".wav");

        long start = System.nanoTime();
        assertThatThrownBy(() -> mgr.transcribe(wav, cfg))
            .isInstanceOf(TranscriptionException.class)
            .hasMessageContaining("Timeout after 1s");
        long durationMs = (System.nanoTime() - start) / 1_000_000L;
        // Should not take excessively longer than timeout (allow some overhead)
        assertThat(durationMs).isLessThan(5000);

        // Manager close should terminate gobbler threads
        Awaitility.await().atMost(2, TimeUnit.SECONDS).until(() -> tp.destroyCalled);
    }

    // ---- Test doubles ----

    /**
     * Encapsulates test process behavior configuration.
     *
     * @param stdout stdout content to return
     * @param stderr stderr content to return
     * @param exitCode process exit code
     * @param finishAfterMillis delay before process finishes (-1 means never finish)
     */
    private record ProcessBehavior(String stdout, String stderr, int exitCode, long finishAfterMillis) {}

    private static final class StubProcessFactory implements ProcessFactory {
        private final Process p;
        private StubProcessFactory(Process p) {
            this.p = p;
        }
        @Override
        public Process start(List<String> command, Path workingDir) {
            return p;
        }
    }

    /**
     * Minimal fake Process that allows controlling stdout/stderr, exit code, and termination timing.
     */
    private static final class TestProcess extends Process {
        private final byte[] out;
        private final byte[] err;
        private final int exitCode;
        private final long finishAfterMillis; // -1 means never finish until destroyed
        private volatile boolean alive = true;
        private volatile boolean destroyCalled = false;

        TestProcess(ProcessBehavior behavior) {
            this.out = behavior.stdout().getBytes();
            this.err = behavior.stderr().getBytes();
            this.exitCode = behavior.exitCode();
            this.finishAfterMillis = behavior.finishAfterMillis();
            if (finishAfterMillis == 0) {
                this.alive = false;
            } else if (finishAfterMillis > 0) {
                Thread finisher = new Thread(() -> {
                    try {
                        Thread.sleep(finishAfterMillis);
                        alive = false;
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                }, "test-proc-finisher");
                finisher.setDaemon(true);
                finisher.start();
            }
        }

        @Override
        public OutputStream getOutputStream() {
            return new java.io.ByteArrayOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(out);
        }

        @Override
        public InputStream getErrorStream() {
            return new ByteArrayInputStream(err);
        }

        @Override
        public int waitFor() {
            this.alive = false;
            return exitCode;
        }
        @Override public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException {
            long ms = unit.toMillis(timeout);
            if (finishAfterMillis < 0) {
                Thread.sleep(ms);
                return false; // still alive
            }
            if (finishAfterMillis <= ms) {
                Thread.sleep(Math.max(0, finishAfterMillis));
                this.alive = false;
                return true;
            } else {
                Thread.sleep(ms);
                return false;
            }
        }
        @Override
        public int exitValue() {
            return exitCode;
        }

        @Override
        public void destroy() {
            destroyCalled = true;
            alive = false;
        }

        @Override
        public Process destroyForcibly() {
            destroyCalled = true;
            alive = false;
            return this;
        }

        @Override
        public boolean isAlive() {
            return alive;
        }
    }
}
