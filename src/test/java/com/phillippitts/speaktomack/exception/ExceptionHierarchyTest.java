package com.phillippitts.speaktomack.exception;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class ExceptionHierarchyTest {

    @Test
    void speakToMackExceptionShouldIncludeMessage() {
        SpeakToMackException ex = new SpeakToMackException("test error");
        assertThat(ex.getMessage()).isEqualTo("test error");
    }

    @Test
    void speakToMackExceptionShouldIncludeCause() {
        IOException cause = new IOException("IO failure");
        SpeakToMackException ex = new SpeakToMackException("wrapper error", cause);

        assertThat(ex.getMessage()).isEqualTo("wrapper error");
        assertThat(ex.getCause()).isEqualTo(cause);
    }

    @Test
    void modelNotFoundExceptionShouldIncludePath() {
        ModelNotFoundException ex = new ModelNotFoundException("/path/to/model.zip");

        assertThat(ex.getMessage()).contains("/path/to/model.zip");
        assertThat(ex.getModelPath()).isEqualTo("/path/to/model.zip");
    }

    @Test
    void modelNotFoundExceptionShouldIncludeCause() {
        IOException cause = new IOException("File not found");
        ModelNotFoundException ex = new ModelNotFoundException("/path/to/model.zip", cause);

        assertThat(ex.getMessage()).contains("/path/to/model.zip");
        assertThat(ex.getCause()).isEqualTo(cause);
        assertThat(ex.getModelPath()).isEqualTo("/path/to/model.zip");
    }

    @Test
    void transcriptionExceptionShouldIncludeMessage() {
        TranscriptionException ex = new TranscriptionException("transcription failed");

        assertThat(ex.getMessage()).isEqualTo("transcription failed");
        assertThat(ex.getEngineName()).isEqualTo("unknown");
    }

    @Test
    void transcriptionExceptionShouldIncludeEngineName() {
        TranscriptionException ex = new TranscriptionException("timeout occurred", "vosk");

        assertThat(ex.getMessage()).contains("timeout occurred");
        assertThat(ex.getMessage()).contains("vosk");
        assertThat(ex.getEngineName()).isEqualTo("vosk");
    }

    @Test
    void transcriptionExceptionShouldIncludeCause() {
        RuntimeException cause = new RuntimeException("JNI crash");
        TranscriptionException ex = new TranscriptionException("engine crashed", cause);

        assertThat(ex.getMessage()).isEqualTo("engine crashed");
        assertThat(ex.getCause()).isEqualTo(cause);
    }

    @Test
    void transcriptionExceptionShouldIncludeEngineAndCause() {
        RuntimeException cause = new RuntimeException("Process died");
        TranscriptionException ex = new TranscriptionException(
                "whisper process failed",
                "whisper",
                cause
        );

        assertThat(ex.getMessage()).contains("whisper process failed");
        assertThat(ex.getMessage()).contains("whisper");
        assertThat(ex.getEngineName()).isEqualTo("whisper");
        assertThat(ex.getCause()).isEqualTo(cause);
    }

    @Test
    void invalidAudioExceptionShouldExtendBase() {
        InvalidAudioException ex = new InvalidAudioException("wrong format");
        assertThat(ex).isInstanceOf(SpeakToMackException.class);
    }

    @Test
    void allExceptionsShouldBeRuntimeExceptions() {
        assertThat(new SpeakToMackException("test")).isInstanceOf(RuntimeException.class);
        assertThat(new ModelNotFoundException("/test")).isInstanceOf(RuntimeException.class);
        assertThat(new TranscriptionException("test")).isInstanceOf(RuntimeException.class);
        assertThat(new InvalidAudioException("test")).isInstanceOf(RuntimeException.class);
    }
}
