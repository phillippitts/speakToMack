package com.phillippitts.speaktomack.exception;

/**
 * Thrown when a transcription operation fails.
 * This may occur due to engine errors, timeout, or invalid audio processing.
 */
public class TranscriptionException extends SpeakToMackException {

    private final String engineName;

    public TranscriptionException(String message) {
        super(message);
        this.engineName = "unknown";
    }

    public TranscriptionException(String message, String engineName) {
        super(message + " (engine: " + engineName + ")");
        this.engineName = engineName;
    }

    public TranscriptionException(String message, Throwable cause) {
        super(message, cause);
        this.engineName = "unknown";
    }

    public TranscriptionException(String message, String engineName, Throwable cause) {
        super(message + " (engine: " + engineName + ")", cause);
        this.engineName = engineName;
    }

    public String getEngineName() {
        return engineName;
    }
}
