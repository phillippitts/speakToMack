package com.phillippitts.speaktomack.service.stt.whisper;

import com.phillippitts.speaktomack.config.stt.WhisperConfig;
import com.phillippitts.speaktomack.exception.TranscriptionException;
import com.phillippitts.speaktomack.util.ProcessTimeouts;
import com.phillippitts.speaktomack.util.TimeUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Manages execution of the external whisper.cpp process for transcription.
 *
 * <p>Responsibilities:
 * - Build a deterministic CLI based on {@link WhisperConfig}
 * - Start the process via {@link ProcessFactory}
 * - Capture stdout (transcription) and stderr (diagnostics) concurrently
 * - Enforce a timeout and terminate runaway processes
 * - Provide structured error context in {@link TranscriptionException}
 * - Idempotent {@link #close()} for cleanup
 *
 * <p>Temp-file WAV handling is performed by the caller (engine).
 */
import org.springframework.stereotype.Component;

@Component
public final class WhisperProcessManager implements AutoCloseable {

    private static final Logger LOG = LogManager.getLogger(WhisperProcessManager.class);

    private final ProcessFactory processFactory;
    private final String outputMode; // "text" | "json"

    private volatile Process current;
    private volatile Thread outGobbler;
    private volatile Thread errGobbler;

    /**
     * Context for creating detailed error messages.
     */
    private record ErrorContext(
            WhisperConfig cfg,
            int exitCode,
            StringBuilder stderr,
            long startNano,
            Throwable cause
    ) {}

    /**
     * Holds process execution state including process reference and stream gobblers.
     */
    private record ProcessExecution(
            Process process,
            Thread outGobbler,
            Thread errGobbler,
            StringBuilder stdout,
            StringBuilder stderr
    ) {}

    @org.springframework.beans.factory.annotation.Autowired
    public WhisperProcessManager(
            @org.springframework.beans.factory.annotation.Value("${stt.whisper.output:text}")
            String outputMode) {
        this(new DefaultProcessFactory(), outputMode);
    }

    WhisperProcessManager(ProcessFactory processFactory) {
        this(processFactory, "text");
    }

    WhisperProcessManager(ProcessFactory processFactory, String outputMode) {
        this.processFactory = Objects.requireNonNull(processFactory, "processFactory");
        this.outputMode = normalizeOutputMode(outputMode);
    }

    /**
     * Normalizes output mode string to lowercase, defaulting to "text" if null or blank.
     *
     * @param mode raw output mode string (may be null)
     * @return normalized mode ("text" or lowercase input)
     */
    private static String normalizeOutputMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return "text";
        }
        return mode.toLowerCase();
    }

    /**
     * Starts the whisper.cpp process and initializes stream gobblers.
     *
     * @param command CLI command to execute
     * @param wavPath path to working directory
     * @param cfg whisper configuration
     * @return process execution state with process and gobblers
     * @throws IOException if process creation fails
     */
    private ProcessExecution startProcessWithGobblers(List<String> command, Path wavPath, WhisperConfig cfg)
            throws IOException {
        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();

        Process whisperProcess = processFactory.start(command, wavPath.getParent());
        this.current = whisperProcess;

        // Start gobblers before waiting to avoid deadlock
        // Cap stdout to prevent pathological memory usage; stderr capped for diagnostics
        Thread outGobbler = startGobbler(whisperProcess.getInputStream(), stdout, "whisper-out",
                cfg.maxStdoutBytes());
        Thread errGobbler = startGobbler(whisperProcess.getErrorStream(), stderr, "whisper-err",
                WhisperConstants.STDERR_MAX_BYTES);

        return new ProcessExecution(whisperProcess, outGobbler, errGobbler, stdout, stderr);
    }

    /**
     * Waits for process completion with timeout and ensures gobblers finish.
     *
     * @param exec process execution state
     * @param cfg whisper configuration
     * @param startTime start time in nanoseconds
     * @throws InterruptedException if wait is interrupted
     * @throws TranscriptionException if process times out
     */
    private void waitForProcessCompletion(ProcessExecution exec, WhisperConfig cfg, long startTime)
            throws InterruptedException {
        boolean finished = exec.process().waitFor(cfg.timeoutSeconds(), TimeUnit.SECONDS);
        if (!finished) {
            destroyProcess(exec.process());
            ErrorContext ctx = new ErrorContext(cfg, -1, exec.stderr(), startTime, null);
            throw whisperError("Timeout after " + cfg.timeoutSeconds() + "s", ctx);
        }

        // Ensure gobblers have a moment to flush
        joinQuietly(exec.outGobbler(), ProcessTimeouts.GOBBLER_FLUSH_TIMEOUT);
        joinQuietly(exec.errGobbler(), ProcessTimeouts.GOBBLER_FLUSH_TIMEOUT);
    }

    /**
     * Validates process exit code and returns stdout output.
     *
     * @param exec process execution state
     * @param cfg whisper configuration
     * @param startTime start time in nanoseconds
     * @return stdout output from whisper.cpp
     * @throws TranscriptionException if exit code is non-zero
     */
    private String handleProcessResult(ProcessExecution exec, WhisperConfig cfg, long startTime) {
        int exitCode = exec.process().exitValue();
        if (exitCode != 0) {
            ErrorContext ctx = new ErrorContext(cfg, exitCode, exec.stderr(), startTime, null);
            throw whisperError("Non-zero exit: " + exitCode, ctx);
        }

        String output = exec.stdout().toString();
        LOG.debug("Whisper stdout size={} bytes", output.length());
        return output;
    }

    /**
     * Executes whisper.cpp for the given WAV file and returns its stdout as the transcription output.
     *
     * <p>CLI contract (text mode by default):
     *   <pre>
     *   ${binary} -m ${model} -f ${wav} -l ${language} -otxt -of stdout -t ${threads}
     *   </pre>
     *
     * @param wavPath path to WAV file (created by caller)
     * @param cfg whisper configuration
     * @return stdout content produced by whisper (may be empty)
     * @throws TranscriptionException on timeout, non-zero exit, or I/O error
     */
    public String transcribe(Path wavPath, WhisperConfig cfg) {
        Objects.requireNonNull(wavPath, "wavPath");
        Objects.requireNonNull(cfg, "cfg");

        List<String> command = buildCommand(cfg, wavPath);
        long startTime = System.nanoTime();

        try {
            ProcessExecution exec = startProcessWithGobblers(command, wavPath, cfg);
            this.outGobbler = exec.outGobbler();
            this.errGobbler = exec.errGobbler();

            waitForProcessCompletion(exec, cfg, startTime);
            return handleProcessResult(exec, cfg, startTime);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            ErrorContext ctx = new ErrorContext(cfg, -1, null, startTime, e);
            throw whisperError("I/O failure: " + e.getMessage(), ctx);
        } finally {
            close();
        }
    }

    private List<String> buildCommand(WhisperConfig cfg, Path wavPath) {
        List<String> cmd = new ArrayList<>();

        // Resolve binary path to absolute to avoid working directory ambiguity
        Path binaryPath = resolvePath(cfg.binaryPath());
        cmd.add(binaryPath.toString());

        cmd.add("-m");
        // Resolve model path to absolute
        Path modelPath = resolvePath(cfg.modelPath());
        cmd.add(modelPath.toString());

        cmd.add("-f");
        // WAV path is already absolute (created by Files.createTempFile)
        cmd.add(wavPath.toAbsolutePath().toString());

        cmd.add("-l");
        cmd.add(cfg.language());

        // Output selection: JSON (-oj) when enabled, otherwise text (-otxt)
        if ("json".equalsIgnoreCase(this.outputMode)) {
            cmd.add("-oj");
        } else {
            cmd.add("-otxt");
        }

        cmd.add("-of");
        cmd.add("stdout");
        cmd.add("-t");
        cmd.add(String.valueOf(cfg.threads()));

        return cmd;
    }

    /**
     * Resolves a path to absolute, handling both relative and absolute inputs.
     *
     * <p>This ensures commands work correctly regardless of the process working directory.
     *
     * @param pathString path from configuration (may be relative or absolute)
     * @return absolute Path
     */
    private static Path resolvePath(String pathString) {
        Path path = Path.of(pathString);
        if (path.isAbsolute()) {
            return path;
        }
        // Resolve relative paths against current working directory
        return Path.of(".").toAbsolutePath().normalize().resolve(path).normalize();
    }

    private Thread startGobbler(InputStream inputStream, StringBuilder sink, String name, int maxBytes) {
        StreamGobbler gobbler = new StreamGobbler(inputStream, sink, name, maxBytes);
        Thread thread = new Thread(gobbler, name);
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    /**
     * Encapsulates stream gobbling logic with capacity limits and drain behavior.
     *
     * <p>Reads lines from an input stream into a StringBuilder until capacity is reached.
     * Once the cap is hit, continues draining the stream without accumulating to prevent
     * deadlock, but logs a warning to indicate data loss.
     */
    private static final class StreamGobbler implements Runnable {
        private final InputStream inputStream;
        private final StringBuilder sink;
        private final String name;
        private final int maxBytes;

        StreamGobbler(InputStream inputStream, StringBuilder sink, String name, int maxBytes) {
            this.inputStream = inputStream;
            this.sink = sink;
            this.name = name;
            this.maxBytes = maxBytes;
        }

        @Override
        public void run() {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String line;
                boolean capReached = false;
                while ((line = br.readLine()) != null) {
                    // Stop accumulating once we hit the cap (but keep reading to drain the stream)
                    if (sink.length() >= maxBytes) {
                        if (!capReached) {
                            LOG.warn("Stream '{}' reached {}B cap; discarding further output", name, maxBytes);
                            capReached = true;
                        }
                        continue; // Drain stream without accumulating
                    }
                    if (!sink.isEmpty()) {
                        sink.append('\n');
                    }
                    // Truncate line if it would exceed the cap
                    int available = maxBytes - sink.length();
                    if (line.length() > available) {
                        sink.append(line, 0, available);
                        LOG.warn("Stream '{}' reached {}B cap (truncated line)", name, maxBytes);
                        capReached = true;
                    } else {
                        sink.append(line);
                    }
                }
            } catch (IOException e) {
                LOG.debug("Stream gobbler '{}' stopped: {}", name, e.toString());
            }
        }
    }

    private void joinQuietly(Thread thread, Duration timeout) {
        if (thread == null) {
            return;
        }
        try {
            thread.join(timeout.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void destroyProcess(Process process) {
        try {
            process.destroy();
            // Wait briefly for graceful shutdown
            boolean exited = process.waitFor(ProcessTimeouts.GRACEFUL_SHUTDOWN_TIMEOUT.toMillis(),
                    TimeUnit.MILLISECONDS);
            if (!exited && process.isAlive()) {
                process.destroyForcibly();
                // Wait for forcible termination
                process.waitFor(ProcessTimeouts.FORCEFUL_SHUTDOWN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
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

    private TranscriptionException whisperError(String msg, ErrorContext ctx) {
        long durationMs = TimeUtils.nanosToMillis(System.nanoTime() - ctx.startNano());
        String stderrSnippet = ctx.stderr() == null ? ""
                : snippet(ctx.stderr(), WhisperConstants.ERROR_SNIPPET_MAX_CHARS);

        com.phillippitts.speaktomack.exception.TranscriptionExceptionBuilder builder =
                com.phillippitts.speaktomack.exception.TranscriptionExceptionBuilder.create(msg)
                        .engine("whisper")
                        .exitCode(ctx.exitCode())
                        .durationMs(durationMs)
                        .metadata("binaryPath", ctx.cfg().binaryPath())
                        .metadata("modelPath", ctx.cfg().modelPath())
                        .metadata("stderr", stderrSnippet);

        if (ctx.cause() != null) {
            builder.cause(ctx.cause());
        }

        return builder.build();
    }

    private static String snippet(StringBuilder sb, int maxChars) {
        int len = Math.min(maxChars, sb.length());
        return sb.substring(0, len);
    }

    /**
     * Idempotent cleanup of any running process and gobbler threads.
     */
    @Override
    public void close() {
        Process process = this.current;
        this.current = null;
        if (process != null && process.isAlive()) {
            destroyProcess(process);
        }
        // Join gobblers quietly
        joinQuietly(outGobbler, ProcessTimeouts.GOBBLER_CLEANUP_TIMEOUT);
        joinQuietly(errGobbler, ProcessTimeouts.GOBBLER_CLEANUP_TIMEOUT);
        outGobbler = null;
        errGobbler = null;
    }
}
