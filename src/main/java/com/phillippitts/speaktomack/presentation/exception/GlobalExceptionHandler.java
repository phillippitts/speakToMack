package com.phillippitts.speaktomack.presentation.exception;

import com.phillippitts.speaktomack.exception.InvalidAudioException;
import com.phillippitts.speaktomack.exception.ModelNotFoundException;
import com.phillippitts.speaktomack.exception.TranscriptionException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.Instant;

/**
 * Global exception handler for REST API boundary.
 *
 * Converts domain exceptions to HTTP responses with appropriate status codes.
 * Logs errors for monitoring while protecting sensitive details from clients.
 */
@ControllerAdvice
class GlobalExceptionHandler {

    private static final Logger LOG = LogManager.getLogger(GlobalExceptionHandler.class);

    /**
     * Configuration/setup error - fail fast on startup, but if encountered at runtime return 503.
     */
    @ExceptionHandler(ModelNotFoundException.class)
    ResponseEntity<ApiError> handleModelNotFound(ModelNotFoundException ex) {
        LOG.error("Model not found at path: {}", ex.getModelPath());
        return ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(new ApiError(
                ex.getClass().getSimpleName(),
                "Speech-to-text service unavailable",
                "Model not loaded. Contact administrator.",
                Instant.now()
            ));
    }

    /**
     * Client error - invalid input (HTTP 400).
     */
    @ExceptionHandler(InvalidAudioException.class)
    ResponseEntity<ApiError> handleInvalidAudio(InvalidAudioException ex) {
        LOG.warn("Invalid audio: size={}, reason={}", ex.getAudioSize(), ex.getReason());
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(new ApiError(
                ex.getClass().getSimpleName(),
                "Invalid audio format",
                ex.getMessage(),
                Instant.now()
            ));
    }

    /**
     * Transient error - retry possible (HTTP 503).
     */
    @ExceptionHandler(TranscriptionException.class)
    ResponseEntity<ApiError> handleTranscriptionFailure(TranscriptionException ex) {
        LOG.error("Transcription failed: engine={}", ex.getEngineName(), ex);
        return ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(new ApiError(
                ex.getClass().getSimpleName(),
                "Transcription service temporarily unavailable",
                "Please retry in a few seconds",
                Instant.now()
            ));
    }

    /**
     * Catch-all for unexpected errors (HTTP 500).
     */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        LOG.error("Unexpected error", ex);
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ApiError(
                "InternalServerError",
                "An unexpected error occurred",
                "Please contact support with request ID",
                Instant.now()
            ));
    }

    /**
     * Standardized error response for API clients.
     */
    private record ApiError(
        String errorCode,
        String message,
        String details,
        Instant timestamp
    ) {}
}
