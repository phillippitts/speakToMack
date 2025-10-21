package com.phillippitts.speaktomack.presentation.exception;

import com.phillippitts.speaktomack.exception.InvalidAudioException;
import com.phillippitts.speaktomack.exception.ModelNotFoundException;
import com.phillippitts.speaktomack.exception.TranscriptionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void verifiesModelNotFoundExceptionReturns503() {
        ModelNotFoundException ex = new ModelNotFoundException("/path/to/model");

        ResponseEntity<?> response = handler.handleModelNotFound(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void verifiesModelNotFoundContainsErrorCode() {
        ModelNotFoundException ex = new ModelNotFoundException("/path/to/model");

        ResponseEntity<?> response = handler.handleModelNotFound(ex);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().toString()).contains("ModelNotFoundException");
    }

    @Test
    void verifiesModelNotFoundContainsUserFriendlyMessage() {
        ModelNotFoundException ex = new ModelNotFoundException("/path/to/model");

        ResponseEntity<?> response = handler.handleModelNotFound(ex);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().toString()).contains("Speech-to-text service unavailable");
        assertThat(response.getBody().toString()).contains("Model not loaded");
    }

    @Test
    void verifiesModelNotFoundContainsTimestamp() {
        ModelNotFoundException ex = new ModelNotFoundException("/path/to/model");
        Instant beforeCall = Instant.now().minusSeconds(1);

        ResponseEntity<?> response = handler.handleModelNotFound(ex);

        Instant afterCall = Instant.now().plusSeconds(1);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().toString()).contains("timestamp");
        // Timestamp should be recent
        String bodyStr = response.getBody().toString();
        assertThat(bodyStr).matches(".*timestamp=\\d{4}-\\d{2}-\\d{2}T.*");
    }

    @Test
    void verifiesModelNotFoundDoesNotExposeFilePath() {
        ModelNotFoundException ex = new ModelNotFoundException("/secret/internal/path/model");

        ResponseEntity<?> response = handler.handleModelNotFound(ex);

        assertThat(response.getBody()).isNotNull();
        // Should NOT leak internal file path in response body
        assertThat(response.getBody().toString()).doesNotContain("/secret/internal/path");
    }

    @Test
    void verifiesInvalidAudioReturns400() {
        InvalidAudioException ex = new InvalidAudioException("Too short");

        ResponseEntity<?> response = handler.handleInvalidAudio(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void verifiesInvalidAudioContainsErrorCode() {
        InvalidAudioException ex = new InvalidAudioException(1024, "Too short");

        ResponseEntity<?> response = handler.handleInvalidAudio(ex);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().toString()).contains("InvalidAudioException");
    }

    @Test
    void verifiesInvalidAudioContainsErrorMessage() {
        InvalidAudioException ex = new InvalidAudioException(1024, "Audio too short");

        ResponseEntity<?> response = handler.handleInvalidAudio(ex);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().toString()).contains("Invalid audio format");
        assertThat(response.getBody().toString()).contains("Audio too short");
    }

    @Test
    void verifiesInvalidAudioContainsTimestamp() {
        InvalidAudioException ex = new InvalidAudioException("Invalid");

        ResponseEntity<?> response = handler.handleInvalidAudio(ex);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().toString()).contains("timestamp");
    }

    @Test
    void verifiesTranscriptionFailureReturns503() {
        TranscriptionException ex = new TranscriptionException("Timeout", "vosk");

        ResponseEntity<?> response = handler.handleTranscriptionFailure(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void verifiesTranscriptionFailureContainsErrorCode() {
        TranscriptionException ex = new TranscriptionException("Engine failed", "whisper");

        ResponseEntity<?> response = handler.handleTranscriptionFailure(ex);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().toString()).contains("TranscriptionException");
    }

    @Test
    void verifiesTranscriptionFailureContainsRetryMessage() {
        TranscriptionException ex = new TranscriptionException("Engine busy", "vosk");

        ResponseEntity<?> response = handler.handleTranscriptionFailure(ex);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().toString()).contains("temporarily unavailable");
        assertThat(response.getBody().toString()).contains("retry");
    }

    @Test
    void verifiesTranscriptionFailureContainsTimestamp() {
        TranscriptionException ex = new TranscriptionException("Failed");

        ResponseEntity<?> response = handler.handleTranscriptionFailure(ex);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().toString()).contains("timestamp");
    }

    @Test
    void verifiesTranscriptionFailureDoesNotExposeInternalDetails() {
        TranscriptionException ex = new TranscriptionException(
                "Internal error: database password: secret123",
                "vosk"
        );

        ResponseEntity<?> response = handler.handleTranscriptionFailure(ex);

        assertThat(response.getBody()).isNotNull();
        // Should NOT leak exception message with sensitive data
        assertThat(response.getBody().toString()).doesNotContain("secret123");
        assertThat(response.getBody().toString()).doesNotContain("database password");
    }

    @Test
    void verifiesUnexpectedReturns500() {
        Exception ex = new RuntimeException("Unexpected failure");

        ResponseEntity<?> response = handler.handleUnexpected(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void verifiesUnexpectedContainsGenericErrorCode() {
        Exception ex = new NullPointerException("NPE");

        ResponseEntity<?> response = handler.handleUnexpected(ex);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().toString()).contains("InternalServerError");
    }

    @Test
    void verifiesUnexpectedContainsGenericMessage() {
        Exception ex = new IllegalStateException("Bad state");

        ResponseEntity<?> response = handler.handleUnexpected(ex);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().toString()).contains("unexpected error");
        assertThat(response.getBody().toString()).contains("contact support");
    }

    @Test
    void verifiesUnexpectedContainsTimestamp() {
        Exception ex = new RuntimeException("Error");

        ResponseEntity<?> response = handler.handleUnexpected(ex);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().toString()).contains("timestamp");
    }

    @Test
    void verifiesUnexpectedDoesNotExposeStackTrace() {
        Exception ex = new RuntimeException("Internal error with stack trace");

        ResponseEntity<?> response = handler.handleUnexpected(ex);

        assertThat(response.getBody()).isNotNull();
        // Should NOT expose exception message or stack trace
        assertThat(response.getBody().toString()).doesNotContain("RuntimeException");
        assertThat(response.getBody().toString()).doesNotContain("stack trace");
    }

    @Test
    void verifiesErrorResponseHasValidStructure() {
        InvalidAudioException ex = new InvalidAudioException("Test");

        ResponseEntity<?> response = handler.handleInvalidAudio(ex);

        assertThat(response.getBody()).isNotNull();
        String bodyStr = response.getBody().toString();
        // Verify ApiError record structure
        assertThat(bodyStr).contains("errorCode=");
        assertThat(bodyStr).contains("message=");
        assertThat(bodyStr).contains("details=");
        assertThat(bodyStr).contains("timestamp=");
    }

    @Test
    void verifiesAllHandlersReturnNonNullBody() {
        // Model not found
        ResponseEntity<?> response1 = handler.handleModelNotFound(
                new ModelNotFoundException("/path"));
        assertThat(response1.getBody()).isNotNull();

        // Invalid audio
        ResponseEntity<?> response2 = handler.handleInvalidAudio(
                new InvalidAudioException("test"));
        assertThat(response2.getBody()).isNotNull();

        // Transcription failure
        ResponseEntity<?> response3 = handler.handleTranscriptionFailure(
                new TranscriptionException("test"));
        assertThat(response3.getBody()).isNotNull();

        // Unexpected
        ResponseEntity<?> response4 = handler.handleUnexpected(
                new RuntimeException("test"));
        assertThat(response4.getBody()).isNotNull();
    }

    @Test
    void verifiesHttpStatusCodesAreCorrect() {
        // 503 for service unavailable (model not found)
        assertThat(handler.handleModelNotFound(
                new ModelNotFoundException("/path")).getStatusCode()
        ).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

        // 400 for bad request (invalid audio)
        assertThat(handler.handleInvalidAudio(
                new InvalidAudioException("test")).getStatusCode()
        ).isEqualTo(HttpStatus.BAD_REQUEST);

        // 503 for service unavailable (transcription failed)
        assertThat(handler.handleTranscriptionFailure(
                new TranscriptionException("test")).getStatusCode()
        ).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

        // 500 for internal server error (unexpected)
        assertThat(handler.handleUnexpected(
                new RuntimeException("test")).getStatusCode()
        ).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
