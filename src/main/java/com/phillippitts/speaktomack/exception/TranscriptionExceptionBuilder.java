package com.phillippitts.speaktomack.exception;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fluent builder for constructing TranscriptionException with rich contextual information.
 *
 * <p>This builder eliminates code duplication across STT engines by providing a consistent
 * pattern for building detailed exception messages with metadata.
 *
 * <p><b>Usage Examples:</b>
 * <pre>
 * // Simple exception
 * throw TranscriptionExceptionBuilder.create("Transcription failed")
 *         .engine("vosk")
 *         .build();
 *
 * // With cause and metadata
 * throw TranscriptionExceptionBuilder.create("Initialization failed")
 *         .engine("whisper")
 *         .cause(exception)
 *         .metadata("modelPath", modelPath)
 *         .metadata("sampleRate", sampleRate)
 *         .build();
 *
 * // With duration and exit code
 * throw TranscriptionExceptionBuilder.create("Process failed")
 *         .engine("whisper")
 *         .exitCode(1)
 *         .durationMs(1500)
 *         .metadata("binaryPath", binPath)
 *         .metadata("stderr", stderrSnippet)
 *         .build();
 * </pre>
 */
public final class TranscriptionExceptionBuilder {

    private final String message;
    private String engineName;
    private Throwable cause;
    private Integer exitCode;
    private Long durationMs;
    private final Map<String, String> metadata = new LinkedHashMap<>();

    private TranscriptionExceptionBuilder(String message) {
        this.message = message;
    }

    /**
     * Creates a new builder with the base error message.
     *
     * @param message base error message (must not be null)
     * @return new builder instance
     */
    public static TranscriptionExceptionBuilder create(String message) {
        if (message == null || message.isEmpty()) {
            throw new IllegalArgumentException("message must not be null or empty");
        }
        return new TranscriptionExceptionBuilder(message);
    }

    /**
     * Sets the engine name for the exception.
     *
     * @param engineName STT engine name (e.g., "vosk", "whisper")
     * @return this builder for chaining
     */
    public TranscriptionExceptionBuilder engine(String engineName) {
        this.engineName = engineName;
        return this;
    }

    /**
     * Sets the root cause of the exception.
     *
     * @param cause underlying exception
     * @return this builder for chaining
     */
    public TranscriptionExceptionBuilder cause(Throwable cause) {
        this.cause = cause;
        return this;
    }

    /**
     * Sets the process exit code (for external process failures).
     *
     * @param exitCode process exit code
     * @return this builder for chaining
     */
    public TranscriptionExceptionBuilder exitCode(int exitCode) {
        this.exitCode = exitCode;
        return this;
    }

    /**
     * Sets the operation duration in milliseconds.
     *
     * @param durationMs duration in milliseconds
     * @return this builder for chaining
     */
    public TranscriptionExceptionBuilder durationMs(long durationMs) {
        this.durationMs = durationMs;
        return this;
    }

    /**
     * Adds a metadata key-value pair to the exception message.
     *
     * <p>Common metadata keys: modelPath, sampleRate, binaryPath, stderr, etc.
     *
     * @param key metadata key
     * @param value metadata value
     * @return this builder for chaining
     */
    public TranscriptionExceptionBuilder metadata(String key, Object value) {
        if (key != null && value != null) {
            this.metadata.put(key, String.valueOf(value));
        }
        return this;
    }

    /**
     * Builds the TranscriptionException with the configured properties.
     *
     * <p>The final message format is:
     * <pre>
     * {message} (engine={engine}, exitCode={code}, durationMs={ms}, {key1}={val1}, ...)
     * </pre>
     *
     * @return constructed TranscriptionException
     */
    public TranscriptionException build() {
        String detailedMessage = buildDetailedMessage();
        String engine = engineName != null ? engineName : "unknown";

        if (cause != null) {
            return new TranscriptionException(detailedMessage, engine, cause);
        } else {
            return new TranscriptionException(detailedMessage, engine);
        }
    }

    private String buildDetailedMessage() {
        StringBuilder sb = new StringBuilder(message);

        boolean hasDetails = exitCode != null || durationMs != null || !metadata.isEmpty();
        if (!hasDetails) {
            return message;
        }

        sb.append(" (");
        boolean first = true;

        if (exitCode != null) {
            sb.append("exitCode=").append(exitCode);
            first = false;
        }

        if (durationMs != null) {
            if (!first) {
                sb.append(", ");
            }
            sb.append("durationMs=").append(durationMs);
            first = false;
        }

        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            if (!first) {
                sb.append(", ");
            }
            sb.append(entry.getKey()).append("=").append(entry.getValue());
            first = false;
        }

        sb.append(")");
        return sb.toString();
    }
}
