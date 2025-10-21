package com.phillippitts.speaktomack.config.stt;

import com.phillippitts.speaktomack.exception.ModelNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelValidationServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldFailWhenWhisperModelMissing() {
        WhisperConfig whisper = new WhisperConfig("/bin/echo", "/tmp/does-not-exist.bin", 10, "en", 2, 1048576);
        VoskConfig vosk = new VoskConfig("models/vosk-model-en-us-0.22", 16000, 1);
        ModelValidationService svc = new ModelValidationService(vosk, whisper);

        assertThatThrownBy(svc::validateWhisper)
            .isInstanceOf(ModelNotFoundException.class)
            .hasMessageContaining("Whisper model not found");
    }

    @Test
    void shouldFailWhenWhisperModelTooSmall() throws Exception {
        Path tmpModel = tempDir.resolve("ggml-tiny.bin");
        Files.write(tmpModel, new byte[1024]); // Only 1KB - below 100MB threshold

        Path tmpBin = tempDir.resolve("whisper");
        Files.createFile(tmpBin);
        tmpBin.toFile().setExecutable(true);

        WhisperConfig whisper = new WhisperConfig(tmpBin.toString(), tmpModel.toString(), 10, "en", 2, 1048576);
        VoskConfig vosk = new VoskConfig("models/vosk-model-en-us-0.22", 16000, 1);
        ModelValidationService svc = new ModelValidationService(vosk, whisper);

        assertThatThrownBy(svc::validateWhisper)
            .isInstanceOf(ModelNotFoundException.class)
            .hasMessageContaining("too small");
    }

    @Test
    void shouldFailWhenWhisperBinaryNotExecutable() throws Exception {
        Path tmpModel = tempDir.resolve("ggml-base.en.bin");
        Files.write(tmpModel, new byte[101 * 1024 * 1024]); // 101MB

        Path tmpBin = tempDir.resolve("whisper");
        Files.createFile(tmpBin);
        tmpBin.toFile().setExecutable(false);

        WhisperConfig whisper = new WhisperConfig(tmpBin.toString(), tmpModel.toString(), 10, "en", 2, 1048576);
        VoskConfig vosk = new VoskConfig("models/vosk-model-en-us-0.22", 16000, 1);
        ModelValidationService svc = new ModelValidationService(vosk, whisper);

        assertThatThrownBy(svc::validateWhisper)
            .isInstanceOf(ModelNotFoundException.class)
            .hasMessageContaining("not executable");
    }

    @Test
    void shouldFailWhenWhisperBinaryIsDirectory() throws Exception {
        Path tmpModel = tempDir.resolve("ggml-base.en.bin");
        Files.write(tmpModel, new byte[101 * 1024 * 1024]); // 101MB

        Path tmpDir = tempDir.resolve("whisper-dir");
        Files.createDirectory(tmpDir);

        WhisperConfig whisper = new WhisperConfig(tmpDir.toString(), tmpModel.toString(), 10, "en", 2, 1048576);
        VoskConfig vosk = new VoskConfig("models/vosk-model-en-us-0.22", 16000, 1);
        ModelValidationService svc = new ModelValidationService(vosk, whisper);

        assertThatThrownBy(svc::validateWhisper)
            .isInstanceOf(ModelNotFoundException.class)
            .hasMessageContaining("not a regular file");
    }

    @Test
    void shouldFailWhenVoskModelDirectoryMissing() {
        Path nonExistentPath = Path.of("/tmp/does-not-exist-vosk");
        VoskConfig vosk = new VoskConfig(nonExistentPath.toString(), 16000, 1);
        WhisperConfig whisper = new WhisperConfig();
        ModelValidationService svc = new ModelValidationService(vosk, whisper);

        assertThatThrownBy(() -> svc.validateVoskDirectoryStructure(nonExistentPath))
            .isInstanceOf(ModelNotFoundException.class);
    }

    @Test
    void shouldFailWhenVoskModelMissingSubdirectories() throws Exception {
        Path voskDir = tempDir.resolve("vosk-model");
        Files.createDirectory(voskDir);
        // Missing 'am' and 'conf' subdirectories

        VoskConfig vosk = new VoskConfig(voskDir.toString(), 16000, 1);
        WhisperConfig whisper = new WhisperConfig();
        ModelValidationService svc = new ModelValidationService(vosk, whisper);

        assertThatThrownBy(() -> svc.validateVoskDirectoryStructure(voskDir))
            .isInstanceOf(ModelNotFoundException.class)
            .hasMessageContaining("Missing expected Vosk model subdirectories");
    }
}
