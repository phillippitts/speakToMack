package com.phillippitts.speaktomack.service.stt.whisper;

import com.phillippitts.speaktomack.config.stt.WhisperConfig;
import com.phillippitts.speaktomack.exception.TranscriptionException;
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

    public WhisperProcessManager() {
        this(new DefaultProcessFactory());
    }

    WhisperProcessManager(ProcessFactory processFactory) {
        this.processFactory = Objects.requireNonNull(processFactory, "processFactory");
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

        long t0 = System.nanoTime();
        Process p = null;
        try {
            p = processFactory.start(command, wavPath.getParent());
            this.current = p;

            // Start gobblers before waiting to avoid deadlock
            // Cap stdout to prevent pathological memory usage; stderr capped at 256KB for diagnostics
            outGobbler = startGobbler(p.getInputStream(), stdout, "whisper-out", cfg.maxStdoutBytes());
            errGobbler = startGobbler(p.getErrorStream(), stderr, "whisper-err", 262144);

            boolean finished = p.waitFor(cfg.timeoutSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                destroyProcess(p);
                ErrorContext ctx = new ErrorContext(cfg, -1, stderr, t0, null);
                throw whisperError("Timeout after " + cfg.timeoutSeconds() + "s", ctx);
            }

            // Ensure gobblers have a moment to flush
            joinQuietly(outGobbler, Duration.ofMillis(500));
            joinQuietly(errGobbler, Duration.ofMillis(500));

            int exit = p.exitValue();
            if (exit != 0) {
                ErrorContext ctx = new ErrorContext(cfg, exit, stderr, t0, null);
                throw whisperError("Non-zero exit: " + exit, ctx);
            }

            String out = stdout.toString();
            LOG.debug("Whisper stdout size={} bytes", out.length());
            return out;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            int exit = (p != null && !p.isAlive()) ? p.exitValue() : -1;
            ErrorContext ctx = new ErrorContext(cfg, exit, null, t0, e);
            throw whisperError("I/O failure: " + e.getMessage(), ctx);
        } finally {
            close();
        }
    }

    private static List<String> buildCommand(WhisperConfig cfg, Path wavPath) {
        List<String> cmd = new ArrayList<>();
        cmd.add(cfg.binaryPath());
        cmd.add("-m");
        cmd.add(cfg.modelPath());
        cmd.add("-f");
        cmd.add(wavPath.toString());
        cmd.add("-l");
        cmd.add(cfg.language());
        // Prefer text to stdout for first implementation; switch to -oj if supported later
        cmd.add("-otxt");
        cmd.add("-of");
        cmd.add("stdout");
        cmd.add("-t");
        cmd.add(String.valueOf(cfg.threads()));
        return cmd;
    }

    private Thread startGobbler(InputStream is, StringBuilder sink, String name, int maxBytes) {
        Thread t = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
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
        t.setDaemon(true);
        t.start();
        return t;
    }

    private void joinQuietly(Thread t, Duration timeout) {
        if (t == null) {
            return;
        }
        try {
            t.join(timeout.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void destroyProcess(Process p) {
        try {
            p.destroy();
            // Wait briefly for graceful shutdown
            boolean exited = p.waitFor(500, TimeUnit.MILLISECONDS);
            if (!exited && p.isAlive()) {
                p.destroyForcibly();
                // Wait for forcible termination
                p.waitFor(1000, TimeUnit.MILLISECONDS);
                if (p.isAlive()) {
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
        long durationMs = (System.nanoTime() - ctx.startNano()) / 1_000_000L;
        String stderrSnippet = ctx.stderr() == null ? "" : snippet(ctx.stderr(), 2048);
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
        Process p = this.current;
        this.current = null;
        if (p != null && p.isAlive()) {
            destroyProcess(p);
        }
        // Join gobblers quietly
        joinQuietly(outGobbler, Duration.ofMillis(100));
        joinQuietly(errGobbler, Duration.ofMillis(100));
        outGobbler = null;
        errGobbler = null;
    }
}
