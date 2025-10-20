package com.phillippitts.speaktomack.service.stt.whisper;

/**
 * Constants for Whisper process management and stream handling.
 *
 * <p>Centralized constants for buffer sizes, output limits, and error snippet lengths
 * used by {@link WhisperProcessManager} when managing the whisper.cpp subprocess.
 *
 * <p><b>Design Rationale:</b>
 * <ul>
 *   <li><b>STDERR_MAX_BYTES (256KB):</b> Sufficient for typical whisper.cpp error messages
 *       while preventing unbounded memory growth from pathological stderr output</li>
 *   <li><b>ERROR_SNIPPET_MAX_CHARS (2KB):</b> Captures first ~30 lines of stderr for
 *       debugging while keeping log messages manageable</li>
 * </ul>
 *
 * @see WhisperProcessManager
 * @since 1.0
 */
final class WhisperConstants {

    /**
     * Maximum bytes to capture from stderr per transcription.
     *
     * <p>256KB is sufficient for hundreds of log lines. Typical whisper.cpp stderr
     * is &lt;10KB even for complex errors. Larger values would risk OOM if the
     * process generates unbounded stderr.
     */
    static final int STDERR_MAX_BYTES = 256 * 1024; // 256KB

    /**
     * Maximum characters to include in error message snippets.
     *
     * <p>2048 characters captures approximately the first 30 lines of stderr,
     * providing sufficient context for debugging without overwhelming logs.
     */
    static final int ERROR_SNIPPET_MAX_CHARS = 2048;

    private WhisperConstants() {
        // Utility class - prevent instantiation
    }
}
