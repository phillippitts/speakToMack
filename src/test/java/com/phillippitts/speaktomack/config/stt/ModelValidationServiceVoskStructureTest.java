package com.phillippitts.speaktomack.config.stt;

import com.phillippitts.speaktomack.exception.ModelNotFoundException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelValidationServiceVoskStructureTest {

    @Test
    void shouldPassWhenVoskDirectoryHasExpectedSubfolders() throws IOException {
        Path tmp = Files.createTempDirectory("vosk-model-");
        Files.createDirectories(tmp.resolve("am"));
        Files.createDirectories(tmp.resolve("conf"));
        // Minimal config objects; values not used by this structure-only test
        VoskConfig vosk = new VoskConfig(tmp.toString(), 16_000, 1);
        WhisperConfig whisper = new WhisperConfig();
        ModelValidationService svc = new ModelValidationService(vosk, whisper);

        assertThatCode(() -> svc.validateVoskDirectoryStructure(tmp))
            .doesNotThrowAnyException();
    }

    @Test
    void shouldFailWhenVoskDirectoryMissingExpectedSubfolders() throws IOException {
        Path tmp = Files.createTempDirectory("vosk-model-");
        // Create only one expected folder to trigger failure
        Files.createDirectories(tmp.resolve("am"));

        VoskConfig vosk = new VoskConfig(tmp.toString(), 16_000, 1);
        WhisperConfig whisper = new WhisperConfig();
        ModelValidationService svc = new ModelValidationService(vosk, whisper);

        assertThatThrownBy(() -> svc.validateVoskDirectoryStructure(tmp))
            .isInstanceOf(ModelNotFoundException.class)
            .hasMessageContaining("Missing expected Vosk model subdirectories");
    }
}
