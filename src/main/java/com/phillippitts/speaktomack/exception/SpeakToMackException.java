package com.phillippitts.speaktomack.exception;

/**
 * Base exception for all speakToMack application-specific errors.
 * All domain exceptions should extend this class to enable centralized error handling.
 */
public class SpeakToMackException extends RuntimeException {

    public SpeakToMackException(String message) {
        super(message);
    }

    public SpeakToMackException(String message, Throwable cause) {
        super(message, cause);
    }

    public SpeakToMackException(Throwable cause) {
        super(cause);
    }
}
