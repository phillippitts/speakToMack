package com.phillippitts.speaktomack.service.stt.whisper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Shared test doubles for Whisper process management tests.
 * Provides fake Process implementations for hermetic testing without real whisper.cpp binary.
 */
final class WhisperTestDoubles {

    private WhisperTestDoubles() {}

    /**
     * Encapsulates test process behavior configuration.
     *
     * @param stdout stdout content to return
     * @param stderr stderr content to return
     * @param exitCode process exit code
     * @param finishAfterMillis delay before process finishes (-1 means never finish)
     */
    record ProcessBehavior(String stdout, String stderr, int exitCode, long finishAfterMillis) {}

    /**
     * Stub ProcessFactory that returns a pre-configured Process.
     */
    static final class StubProcessFactory implements ProcessFactory {
        private final Process p;

        StubProcessFactory(Process p) {
            this.p = p;
        }

        @Override
        public Process start(List<String> command, Path workingDir) {
            return p;
        }
    }

    /**
     * Minimal fake Process that allows controlling stdout/stderr, exit code, and termination timing.
     * Enables hermetic testing without spawning real subprocesses.
     */
    static final class TestProcess extends Process {
        private final byte[] out;
        private final byte[] err;
        private final int exitCode;
        private final long finishAfterMillis;
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
                }, "whisper-test-proc-finisher");
                finisher.setDaemon(true);
                finisher.start();
            }
        }

        boolean wasDestroyCalled() {
            return destroyCalled;
        }

        @Override
        public OutputStream getOutputStream() {
            return new ByteArrayOutputStream();
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

        @Override
        public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException {
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
