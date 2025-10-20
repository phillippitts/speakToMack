package com.phillippitts.speaktomack.util;

import java.time.Duration;

/**
 * Standard timeout values for process and thread management.
 *
 * <p>Centralized timeout constants ensure consistency across the codebase and make
 * tuning behavior easier. These values are based on empirical testing and balance
 * responsiveness with graceful cleanup.
 *
 * <p><b>Usage:</b> Used by {@link com.phillippitts.speaktomack.service.stt.whisper.WhisperProcessManager}
 * and {@link com.phillippitts.speaktomack.service.audio.capture.JavaSoundAudioCaptureService}
 * for subprocess and thread lifecycle management.
 *
 * @see com.phillippitts.speaktomack.service.stt.whisper.WhisperProcessManager
 * @see com.phillippitts.speaktomack.service.audio.capture.JavaSoundAudioCaptureService
 * @since 1.0
 */
public final class ProcessTimeouts {

    /**
     * Timeout for stream gobbler threads to flush buffered output after process completion.
     *
     * <p>500ms is sufficient for typical stdout/stderr volumes (&lt;100KB). Longer durations
     * would delay error reporting without improving reliability.
     */
    public static final Duration GOBBLER_FLUSH_TIMEOUT = Duration.ofMillis(500);

    /**
     * Timeout for stream gobbler threads during cleanup (best-effort).
     *
     * <p>100ms is acceptable since cleanup is non-critical. If threads don't terminate,
     * they're daemon threads and will be killed on JVM exit.
     */
    public static final Duration GOBBLER_CLEANUP_TIMEOUT = Duration.ofMillis(100);

    /**
     * Timeout for graceful process shutdown via {@link Process#destroy()}.
     *
     * <p>Most processes terminate within 100-200ms. 500ms provides headroom for
     * slower shutdowns while keeping total cleanup time reasonable.
     */
    public static final Duration GRACEFUL_SHUTDOWN_TIMEOUT = Duration.ofMillis(500);

    /**
     * Timeout for forceful process termination via {@link Process#destroyForcibly()}.
     *
     * <p>1000ms is the OS-level deadline for SIGKILL (Unix) or TerminateProcess (Windows).
     * Processes that survive this are typically unkillable due to OS bugs.
     */
    public static final Duration FORCEFUL_SHUTDOWN_TIMEOUT = Duration.ofMillis(1000);

    /**
     * Timeout for audio capture thread to terminate during normal stop operation.
     *
     * <p>Longer than gobbler timeout because audio capture may be blocked on I/O.
     * 1000ms allows for buffered data to drain gracefully.
     */
    public static final Duration CAPTURE_THREAD_STOP_TIMEOUT = Duration.ofMillis(1000);

    /**
     * Timeout for audio capture thread during forced shutdown (best-effort).
     *
     * <p>500ms is acceptable during application shutdown. Daemon thread will be
     * terminated by JVM if it doesn't respond.
     */
    public static final Duration CAPTURE_THREAD_SHUTDOWN_TIMEOUT = Duration.ofMillis(500);

    private ProcessTimeouts() {
        // Utility class - prevent instantiation
    }
}
