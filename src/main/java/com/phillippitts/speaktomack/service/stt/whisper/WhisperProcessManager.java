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
 * <p>Temp-file WAV handling is performed by the caller (engine) in Task 2.5.
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
        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();

        long startTime = System.nanoTime();
        Process whisperProcess = null;
        try {
            whisperProcess = processFactory.start(command, wavPath.getParent());
            this.current = whisperProcess;

            // Start gobblers before waiting to avoid deadlock
            // Cap stdout to prevent pathological memory usage; stderr capped for diagnostics
            outGobbler = startGobbler(whisperProcess.getInputStream(), stdout, "whisper-out",
                    cfg.maxStdoutBytes());
            errGobbler = startGobbler(whisperProcess.getErrorStream(), stderr, "whisper-err",
                    WhisperConstants.STDERR_MAX_BYTES);

            boolean finished = whisperProcess.waitFor(cfg.timeoutSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                destroyProcess(whisperProcess);
                ErrorContext ctx = new ErrorContext(cfg, -1, stderr, startTime, null);
                throw whisperError("Timeout after " + cfg.timeoutSeconds() + "s", ctx);
            }

            // Ensure gobblers have a moment to flush
            joinQuietly(outGobbler, ProcessTimeouts.GOBBLER_FLUSH_TIMEOUT);
            joinQuietly(errGobbler, ProcessTimeouts.GOBBLER_FLUSH_TIMEOUT);

            int exitCode = whisperProcess.exitValue();
            if (exitCode != 0) {
                ErrorContext ctx = new ErrorContext(cfg, exitCode, stderr, startTime, null);
                throw whisperError("Non-zero exit: " + exitCode, ctx);
            }

            String output = stdout.toString();
            LOG.debug("Whisper stdout size={} bytes", output.length());
            return output;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            int exitCode = (whisperProcess != null && !whisperProcess.isAlive()) ? whisperProcess.exitValue() : -1;
            ErrorContext ctx = new ErrorContext(cfg, exitCode, null, startTime, e);
            throw whisperError("I/O failure: " + e.getMessage(), ctx);
        } finally {
            close();
        }
    }

    private List<String> buildCommand(WhisperConfig cfg, Path wavPath) {
        List<String> cmd = new ArrayList<>();
        cmd.add(cfg.binaryPath());
        cmd.add("-m");
        cmd.add(cfg.modelPath());
        cmd.add("-f");
        cmd.add(wavPath.toString());
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

    private Thread startGobbler(InputStream inputStream, StringBuilder sink, String name, int maxBytes) {
        Thread gobbler = new Thread(() -> {
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
                    if (sink.length() > 0) {
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
        }, name);
        gobbler.setDaemon(true);
        gobbler.start();
        return gobbler;
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
        String detail = String.format(
                "%s (engine=whisper, exit=%d, durationMs=%d, bin=%s, model=%s, stderr=%s)",
                msg, ctx.exitCode(), durationMs, ctx.cfg().binaryPath(), ctx.cfg().modelPath(), stderrSnippet
        );
        return ctx.cause() == null ? new TranscriptionException(detail, "whisper")
                                   : new TranscriptionException(detail, "whisper", ctx.cause());
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
