package com.phillippitts.speaktomack.config.stt;

import com.phillippitts.speaktomack.exception.ModelNotFoundException;
import jakarta.annotation.PostConstruct;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Validates presence and basic loadability of STT models/binaries at startup.
 *
 * Fail-fast philosophy: abort application startup with clear, actionable errors
 * if models or binaries are missing or invalid.
 *
 * Validation performed:
 * - Vosk: model dir exists with expected structure; JNI smoke test (create/close recognizer)
 * - Whisper: model file exists and is reasonably sized; binary exists and is executable
 */
@Component
@ConditionalOnProperty(name = "stt.validation.enabled", havingValue = "true", matchIfMissing = true)
class ModelValidationService {

    private static final Logger LOG = LogManager.getLogger(ModelValidationService.class);

    // Audio format constants for validation
    private static final double SILENCE_DURATION_SECONDS = 0.02; // 20ms
    private static final int VOSK_SAMPLE_RATE = 16000; // Hz
    private static final int BYTES_PER_SAMPLE = 2; // 16-bit = 2 bytes
    private static final long BYTES_PER_MB = 1024 * 1024;

    private final VoskConfig vosk;
    private final WhisperConfig whisper;

    ModelValidationService(VoskConfig vosk, WhisperConfig whisper) {
        this.vosk = vosk;
        this.whisper = whisper;
    }

    @PostConstruct
    void validateAllOnStartup() {
        LOG.info("Validating STT models and binaries... os={}, arch={}",
            System.getProperty("os.name"), System.getProperty("os.arch"));

        validateVosk();
        validateWhisper();

        LOG.info("STT validation complete: vosk.model='{}', whisper.model='{}', whisper.binary='{}'",
            vosk.modelPath(), whisper.modelPath(), whisper.binaryPath());
    }

    // Visible for tests
    void validateVosk() {
        Path modelDir = Paths.get(vosk.modelPath());
        validateVoskDirectoryStructure(modelDir);

        // JNI smoke test: create/close recognizer on a tiny buffer; do not keep native resources
        try {
            org.vosk.Model model = new org.vosk.Model(modelDir.toString());
            try {
                org.vosk.Recognizer rec = new org.vosk.Recognizer(model, vosk.sampleRate());
                try {
                    // Generate ~20ms of silence at 16kHz, 16-bit mono for smoke test
                    int sampleCount = (int) (SILENCE_DURATION_SECONDS * VOSK_SAMPLE_RATE);
                    int bufferSize = sampleCount * BYTES_PER_SAMPLE;
                    byte[] silence20ms = new byte[bufferSize];
                    // Allow any result; just ensure JNI path works
                    rec.acceptWaveForm(silence20ms, silence20ms.length);
                } finally {
                    rec.close();
                }
            } finally {
                model.close();
            }
        } catch (Throwable t) { // include UnsatisfiedLinkError, etc.
            throw new ModelNotFoundException("Vosk JNI/model failed to load at: " + modelDir, t);
        }
    }

    // Package-private to enable hermetic tests without JNI or real model
    void validateVoskDirectoryStructure(Path modelDir) {
        if (!Files.isDirectory(modelDir)) {
            throw new ModelNotFoundException(modelDir.toString());
        }
        // Basic expected structure (these vary across models; keep lenient but helpful)
        if (!Files.isDirectory(modelDir.resolve("am")) || !Files.isDirectory(modelDir.resolve("conf"))) {
            throw new ModelNotFoundException("Missing expected Vosk model subdirectories under: " + modelDir);
        }
    }

    // Visible for tests
    void validateWhisper() {
        Path model = Paths.get(whisper.modelPath());
        LOG.info("Validating Whisper: modelPath='{}', binaryPath='{}'", model, whisper.binaryPath());
        if (!Files.exists(model)) {
            throw new ModelNotFoundException("Whisper model not found: " + model);
        }
        if (!Files.isRegularFile(model)) {
            throw new ModelNotFoundException("Whisper model is not a regular file: " + model);
        }
        try {
            long sizeBytes = Files.size(model);
            LOG.debug("Whisper model stats: size={} bytes, lastModified={}", sizeBytes, Files.getLastModifiedTime(model));
            if (sizeBytes < SttModelConstants.MIN_WHISPER_MODEL_SIZE_BYTES) {
                throw new ModelNotFoundException("Whisper model too small (" + sizeBytes + " bytes) at: " + model);
            }
            long sizeInMB = sizeBytes / BYTES_PER_MB;
            long thresholdInMB = SttModelConstants.MIN_WHISPER_MODEL_SIZE_BYTES / BYTES_PER_MB;
            LOG.info("Whisper model size: {} MB (threshold: {} MB)", sizeInMB, thresholdInMB);
        } catch (IOException e) {
            throw new ModelNotFoundException("Failed to read Whisper model metadata at: " + model, e);
        }

        Path binary = Paths.get(whisper.binaryPath());
        if (!Files.exists(binary)) {
            throw new ModelNotFoundException("Whisper binary not found: " + binary);
        }
        if (!Files.isRegularFile(binary)) {
            throw new ModelNotFoundException("Whisper binary is not a regular file: " + binary);
        }
        try {
            LOG.debug("Whisper binary stats: perms(readable={}, writable={}, executable={}), lastModified={}",
                Files.isReadable(binary), Files.isWritable(binary),
                Files.isExecutable(binary), Files.getLastModifiedTime(binary));
        } catch (IOException ignore) {
            // best-effort logging
        }
        if (!Files.isExecutable(binary)) {
            String os = System.getProperty("os.name").toLowerCase();
            String hint = os.contains("mac")
                ? " (try: chmod +x '" + binary + "' && xattr -dr com.apple.quarantine '" + binary + "')"
                : " (try: chmod +x '" + binary + "')";
            throw new ModelNotFoundException("Whisper binary not executable: " + binary + hint);
        }
        LOG.info("Whisper validation OK: model='{}', binary='{}'", model, binary);
    }
}
