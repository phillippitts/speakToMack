package com.boombapcompile.blckvox.service.stt.whisper;

import com.boombapcompile.blckvox.config.stt.WhisperConfig;
import com.boombapcompile.blckvox.exception.TranscriptionException;

import java.nio.file.Path;

/**
 * Abstraction for managing external whisper.cpp process execution.
 *
 * <p>This interface provides a contract for executing whisper.cpp transcription processes,
 * allowing different implementations for testing, mocking, or alternative process management
 * strategies.
 *
 * <p><b>Responsibilities:</b>
 * <ul>
 *   <li>Executing whisper.cpp binary with provided configuration</li>
 *   <li>Returning transcription output from the process</li>
 *   <li>Managing process lifecycle and cleanup</li>
 * </ul>
 *
 * <p><b>Thread Safety:</b> Implementations should support concurrent transcription calls.
 *
 * <p><b>Design Principle:</b> This interface follows the Dependency Inversion Principle (DIP)
 * by allowing high-level modules (like {@link WhisperSttEngine}) to depend on this abstraction
 * rather than concrete implementations.
 *
 * @see WhisperProcessManager
 * @since 1.3
 */
public interface ProcessManager extends AutoCloseable {

    /**
     * Executes whisper.cpp for the given WAV file and returns its stdout as the transcription output.
     *
     * <p>Implementations should:
     * <ul>
     *   <li>Build the appropriate CLI command from configuration</li>
     *   <li>Start the whisper.cpp process</li>
     *   <li>Enforce timeout constraints from configuration</li>
     *   <li>Capture and return stdout content</li>
     *   <li>Handle process cleanup on completion or failure</li>
     * </ul>
     *
     * @param wavPath path to WAV file (created by caller)
     * @param cfg whisper configuration containing binary path, model path, and parameters
     * @return stdout content produced by whisper (may be empty)
     * @throws TranscriptionException on timeout, non-zero exit, or I/O error
     * @throws NullPointerException if wavPath or cfg is null
     */
    String transcribe(Path wavPath, WhisperConfig cfg);

    /**
     * Idempotent cleanup of any running process and associated resources.
     *
     * <p>Implementations should ensure this method can be called multiple times safely.
     */
    @Override
    void close();
}
