package com.phillippitts.speaktomack.service.health;

import com.phillippitts.speaktomack.config.stt.VoskConfig;
import com.phillippitts.speaktomack.config.stt.WhisperConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ModelHealthIndicatorTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldReportUpWhenAllResourcesAccessible() throws IOException {
        // Create test resources
        Path voskModelDir = Files.createDirectory(tempDir.resolve("vosk-model"));
        Path whisperModel = Files.createFile(tempDir.resolve("whisper.bin"));
        Path whisperBinary = Files.createFile(tempDir.resolve("whisper"));
        whisperBinary.toFile().setExecutable(true);

        VoskConfig voskConfig = new VoskConfig(voskModelDir.toString(), 16000, 1);
        WhisperConfig whisperConfig = new WhisperConfig(
                whisperBinary.toString(),
                whisperModel.toString(),
                10,
                "en",
                4,
                1048576
        );

        ModelHealthIndicator indicator = new ModelHealthIndicator(voskConfig, whisperConfig);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("status", "All models and binaries accessible");
        assertThat(health.getDetails().get("voskModel")).asString().contains("accessible at");
        assertThat(health.getDetails().get("whisperModel")).asString().contains("accessible at");
        assertThat(health.getDetails().get("whisperBinary")).asString().contains("accessible and executable at");
    }

    @Test
    void shouldReportDownWhenVoskModelMissing() throws IOException {
        Path whisperModel = Files.createFile(tempDir.resolve("whisper.bin"));
        Path whisperBinary = Files.createFile(tempDir.resolve("whisper"));
        whisperBinary.toFile().setExecutable(true);

        VoskConfig voskConfig = new VoskConfig(tempDir.resolve("nonexistent").toString(), 16000, 1);
        WhisperConfig whisperConfig = new WhisperConfig(
                whisperBinary.toString(),
                whisperModel.toString(),
                10,
                "en",
                4,
                1048576
        );

        ModelHealthIndicator indicator = new ModelHealthIndicator(voskConfig, whisperConfig);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("status", "Missing or inaccessible models/binaries");
        assertThat(health.getDetails().get("voskModel")).asString().contains("NOT FOUND at");
    }

    @Test
    void shouldReportDownWhenWhisperModelMissing() throws IOException {
        Path voskModelDir = Files.createDirectory(tempDir.resolve("vosk-model"));
        Path whisperBinary = Files.createFile(tempDir.resolve("whisper"));
        whisperBinary.toFile().setExecutable(true);

        VoskConfig voskConfig = new VoskConfig(voskModelDir.toString(), 16000, 1);
        WhisperConfig whisperConfig = new WhisperConfig(
                whisperBinary.toString(),
                tempDir.resolve("nonexistent.bin").toString(),
                10,
                "en",
                4,
                1048576
        );

        ModelHealthIndicator indicator = new ModelHealthIndicator(voskConfig, whisperConfig);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("status", "Missing or inaccessible models/binaries");
        assertThat(health.getDetails().get("whisperModel")).asString().contains("NOT FOUND at");
    }

    @Test
    void shouldReportDownWhenWhisperBinaryMissing() throws IOException {
        Path voskModelDir = Files.createDirectory(tempDir.resolve("vosk-model"));
        Path whisperModel = Files.createFile(tempDir.resolve("whisper.bin"));

        VoskConfig voskConfig = new VoskConfig(voskModelDir.toString(), 16000, 1);
        WhisperConfig whisperConfig = new WhisperConfig(
                tempDir.resolve("nonexistent").toString(),
                whisperModel.toString(),
                10,
                "en",
                4,
                1048576
        );

        ModelHealthIndicator indicator = new ModelHealthIndicator(voskConfig, whisperConfig);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("status", "Missing or inaccessible models/binaries");
        assertThat(health.getDetails().get("whisperBinary")).asString().contains("NOT FOUND at");
    }

    @Test
    void shouldReportDownWhenWhisperBinaryNotExecutable() throws IOException {
        Path voskModelDir = Files.createDirectory(tempDir.resolve("vosk-model"));
        Path whisperModel = Files.createFile(tempDir.resolve("whisper.bin"));
        Path whisperBinary = Files.createFile(tempDir.resolve("whisper"));
        // Don't set executable bit

        VoskConfig voskConfig = new VoskConfig(voskModelDir.toString(), 16000, 1);
        WhisperConfig whisperConfig = new WhisperConfig(
                whisperBinary.toString(),
                whisperModel.toString(),
                10,
                "en",
                4,
                1048576
        );

        ModelHealthIndicator indicator = new ModelHealthIndicator(voskConfig, whisperConfig);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("status", "Missing or inaccessible models/binaries");
        assertThat(health.getDetails().get("whisperBinary")).asString().contains("not executable at");
    }

    @Test
    void shouldReportDownWhenMultipleResourcesMissing() {
        VoskConfig voskConfig = new VoskConfig(tempDir.resolve("nonexistent-vosk").toString(), 16000, 1);
        WhisperConfig whisperConfig = new WhisperConfig(
                tempDir.resolve("nonexistent-binary").toString(),
                tempDir.resolve("nonexistent-model.bin").toString(),
                10,
                "en",
                4,
                1048576
        );

        ModelHealthIndicator indicator = new ModelHealthIndicator(voskConfig, whisperConfig);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("status", "Missing or inaccessible models/binaries");
        assertThat(health.getDetails().get("voskModel")).asString().contains("NOT FOUND");
        assertThat(health.getDetails().get("whisperModel")).asString().contains("NOT FOUND");
        assertThat(health.getDetails().get("whisperBinary")).asString().contains("NOT FOUND");
    }
}
