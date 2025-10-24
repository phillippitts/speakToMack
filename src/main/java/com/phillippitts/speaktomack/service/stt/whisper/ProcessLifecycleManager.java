package com.phillippitts.speaktomack.service.stt.whisper;

import com.phillippitts.speaktomack.util.ProcessTimeouts;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Manages the lifecycle of external processes, including startup, timeout enforcement, and cleanup.
 *
 * <p>This class is responsible for:
 * <ul>
 *   <li>Starting processes via {@link ProcessFactory}</li>
 *   <li>Starting stream gobblers to prevent deadlock</li>
 *   <li>Enforcing timeout constraints on process execution</li>
 *   <li>Graceful and forceful process termination</li>
 *   <li>Cleanup of stream gobbler threads</li>
 * </ul>
 *
 * <p><b>Thread Safety:</b> Individual operations are thread-safe, but callers should not
 * share ProcessExecution instances across threads without external synchronization.
 *
 * @since 1.2
 */
final class ProcessLifecycleManager {

    private static final Logger LOG = LogManager.getLogger(ProcessLifecycleManager.class);

    private final ProcessFactory processFactory;

    /**
     * Constructs a lifecycle manager with the specified process factory.
     *
     * @param processFactory factory for creating OS processes
     */
    ProcessLifecycleManager(ProcessFactory processFactory) {
        this.processFactory = processFactory;
    }

    /**
     * Holds process execution state including process reference and stream gobblers.
     */
    static final class ProcessExecution {
        private final Process process;
        private final ProcessStreamHandler.StreamGobbler stdoutGobbler;
        private final ProcessStreamHandler.StreamGobbler stderrGobbler;

        ProcessExecution(Process process,
                        ProcessStreamHandler.StreamGobbler stdoutGobbler,
                        ProcessStreamHandler.StreamGobbler stderrGobbler) {
            this.process = process;
            this.stdoutGobbler = stdoutGobbler;
            this.stderrGobbler = stderrGobbler;
        }

        Process getProcess() {
            return process;
        }

        /**
         * Returns captured stdout content.
         */
        String getStdout() {
            return stdoutGobbler.getOutput();
        }

        /**
         * Returns captured stderr content.
         */
        String getStderr() {
            return stderrGobbler.getOutput();
        }

        /**
         * Waits for gobbler threads to flush buffered output.
         */
        void flushGobblers() {
            stdoutGobbler.join(ProcessTimeouts.GOBBLER_FLUSH_TIMEOUT);
            stderrGobbler.join(ProcessTimeouts.GOBBLER_FLUSH_TIMEOUT);
        }

        /**
         * Performs best-effort cleanup of gobbler threads.
         */
        void cleanupGobblers() {
            stdoutGobbler.join(ProcessTimeouts.GOBBLER_CLEANUP_TIMEOUT);
            stderrGobbler.join(ProcessTimeouts.GOBBLER_CLEANUP_TIMEOUT);
        }
    }

    /**
     * Starts a process and initializes stream gobblers.
     *
     * @param command CLI command to execute
     * @param workingDir path to working directory
     * @param stdoutMaxBytes maximum bytes to capture from stdout
     * @param stderrMaxBytes maximum bytes to capture from stderr
     * @return process execution state with process and gobblers
     * @throws IOException if process creation fails
     */
    ProcessExecution startProcess(List<String> command, Path workingDir,
                                  int stdoutMaxBytes, int stderrMaxBytes) throws IOException {
        Process process = processFactory.start(command, workingDir);

        // Start gobblers before waiting to avoid deadlock
        // Cap stdout to prevent pathological memory usage; stderr capped for diagnostics
        ProcessStreamHandler.StreamGobbler stdoutGobbler = ProcessStreamHandler.startGobbler(
                process.getInputStream(), "whisper-stdout", stdoutMaxBytes);
        ProcessStreamHandler.StreamGobbler stderrGobbler = ProcessStreamHandler.startGobbler(
                process.getErrorStream(), "whisper-stderr", stderrMaxBytes);

        return new ProcessExecution(process, stdoutGobbler, stderrGobbler);
    }

    /**
     * Waits for process completion with timeout and ensures gobblers finish.
     *
     * @param exec process execution state
     * @param timeoutSeconds maximum seconds to wait for process completion
     * @return true if process completed within timeout, false if timeout occurred
     * @throws InterruptedException if wait is interrupted
     */
    boolean waitForCompletion(ProcessExecution exec, long timeoutSeconds) throws InterruptedException {
        boolean finished = exec.process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            return false;
        }

        // Ensure gobblers have a moment to flush
        exec.flushGobblers();
        return true;
    }

    /**
     * Destroys a process gracefully, escalating to forceful termination if necessary.
     *
     * <p>Attempts graceful shutdown via {@link Process#destroy()}, then escalates to
     * {@link Process#destroyForcibly()} if the process doesn't terminate within the timeout.
     *
     * @param process the process to terminate
     */
    void destroyProcess(Process process) {
        if (process == null || !process.isAlive()) {
            return;
        }

        try {
            process.destroy();
            // Wait briefly for graceful shutdown
            boolean exited = process.waitFor(
                    ProcessTimeouts.GRACEFUL_SHUTDOWN_TIMEOUT.toMillis(),
                    TimeUnit.MILLISECONDS);

            if (!exited && process.isAlive()) {
                process.destroyForcibly();
                // Wait for forcible termination
                process.waitFor(
                        ProcessTimeouts.FORCEFUL_SHUTDOWN_TIMEOUT.toMillis(),
                        TimeUnit.MILLISECONDS);

                if (process.isAlive()) {
                    LOG.warn("Process still alive after destroyForcibly");
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("Interrupted while destroying process");
        } catch (Throwable t) {
            LOG.warn("Error destroying process: {}", t.toString());
        }
    }

    /**
     * Cleans up a process execution, destroying the process and joining gobblers.
     *
     * @param exec process execution to clean up (may be null)
     */
    void cleanup(ProcessExecution exec) {
        if (exec == null) {
            return;
        }

        if (exec.process != null && exec.process.isAlive()) {
            destroyProcess(exec.process);
        }

        exec.cleanupGobblers();
    }
}
