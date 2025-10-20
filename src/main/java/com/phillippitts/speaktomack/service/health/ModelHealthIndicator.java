package com.phillippitts.speaktomack.service.health;

import com.phillippitts.speaktomack.config.stt.VoskConfig;
import com.phillippitts.speaktomack.config.stt.WhisperConfig;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Health indicator for STT model files and binaries.
 *
 * <p>Verifies that required model files and binaries exist and are accessible:
 * <ul>
 *   <li>Vosk model directory exists</li>
 *   <li>Whisper model file exists</li>
 *   <li>Whisper binary exists and is executable</li>
 * </ul>
 *
 * <p>Exposed via /actuator/health endpoint.
 */
@Component
public class ModelHealthIndicator implements HealthIndicator {

    private final VoskConfig voskConfig;
    private final WhisperConfig whisperConfig;

    public ModelHealthIndicator(VoskConfig voskConfig, WhisperConfig whisperConfig) {
        this.voskConfig = voskConfig;
        this.whisperConfig = whisperConfig;
    }

    @Override
    public Health health() {
        Health.Builder builder = new Health.Builder();

        // Check Vosk model
        Path voskModelPath = Paths.get(voskConfig.modelPath());
        boolean voskExists = Files.isDirectory(voskModelPath);

        // Check Whisper model
        Path whisperModelPath = Paths.get(whisperConfig.modelPath());
        boolean whisperModelExists = Files.isRegularFile(whisperModelPath);

        // Check Whisper binary
        Path whisperBinaryPath = Paths.get(whisperConfig.binaryPath());
        boolean whisperBinaryExists = Files.isRegularFile(whisperBinaryPath);
        boolean whisperBinaryExecutable = whisperBinaryExists && Files.isExecutable(whisperBinaryPath);

        // Determine overall health
        boolean allHealthy = voskExists && whisperModelExists && whisperBinaryExecutable;

        if (allHealthy) {
            builder.up()
                    .withDetail("status", "All models and binaries accessible")
                    .withDetail("voskModel", formatStatus(voskExists, voskModelPath))
                    .withDetail("whisperModel", formatStatus(whisperModelExists, whisperModelPath))
                    .withDetail("whisperBinary", formatBinaryStatus(whisperBinaryExists,
                            whisperBinaryExecutable, whisperBinaryPath));
        } else {
            builder.down()
                    .withDetail("status", "Missing or inaccessible models/binaries")
                    .withDetail("voskModel", formatStatus(voskExists, voskModelPath))
                    .withDetail("whisperModel", formatStatus(whisperModelExists, whisperModelPath))
                    .withDetail("whisperBinary", formatBinaryStatus(whisperBinaryExists,
                            whisperBinaryExecutable, whisperBinaryPath));
        }

        return builder.build();
    }

    private String formatStatus(boolean exists, Path path) {
        if (exists) {
            return "accessible at " + path;
        }
        return "NOT FOUND at " + path;
    }

    private String formatBinaryStatus(boolean exists, boolean executable, Path path) {
        if (!exists) {
            return "NOT FOUND at " + path;
        }
        if (!executable) {
            return "not executable at " + path;
        }
        return "accessible and executable at " + path;
    }
}
