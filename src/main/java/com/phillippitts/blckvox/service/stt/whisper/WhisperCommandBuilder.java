package com.phillippitts.blckvox.service.stt.whisper;

import com.phillippitts.blckvox.config.stt.WhisperConfig;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Builds CLI command arrays for invoking whisper.cpp binary.
 *
 * <p>This class is responsible for constructing the complete command-line invocation
 * for the whisper.cpp process, including path resolution, output mode selection, and
 * parameter ordering.
 *
 * <p><b>Responsibilities:</b>
 * <ul>
 *   <li>Resolving relative/absolute paths for binary and model files</li>
 *   <li>Building deterministic CLI command structure</li>
 *   <li>Handling output mode selection (text vs JSON)</li>
 * </ul>
 *
 * <p><b>Thread Safety:</b> This class is immutable and thread-safe. All methods are pure
 * functions that do not modify state.
 *
 * @since 1.2
 */
final class WhisperCommandBuilder {

    private final String outputMode;

    /**
     * Constructs a command builder with the specified output mode.
     *
     * @param outputMode output format ("text" or "json"), defaults to "text" if null/blank
     */
    WhisperCommandBuilder(String outputMode) {
        this.outputMode = normalizeOutputMode(outputMode);
    }

    /**
     * Builds the complete whisper.cpp command array.
     *
     * <p>Command structure:
     * <pre>
     * ${binary} -m ${model} -f ${wav} -l ${language} -otxt|-oj -of stdout -t ${threads}
     * </pre>
     *
     * @param cfg whisper configuration containing binary/model paths and parameters
     * @param wavPath path to input WAV file (will be resolved to absolute)
     * @return command array ready for ProcessBuilder
     * @throws NullPointerException if cfg or wavPath is null
     */
    List<String> buildCommand(WhisperConfig cfg, Path wavPath) {
        Objects.requireNonNull(cfg, "cfg");
        Objects.requireNonNull(wavPath, "wavPath");

        List<String> cmd = new ArrayList<>();

        // Resolve binary path to absolute to avoid working directory ambiguity
        Path binaryPath = resolvePath(cfg.binaryPath());
        cmd.add(binaryPath.toString());

        cmd.add("-m");
        // Resolve model path to absolute
        Path modelPath = resolvePath(cfg.modelPath());
        cmd.add(modelPath.toString());

        cmd.add("-f");
        // WAV path is already absolute (created by Files.createTempFile)
        cmd.add(wavPath.toAbsolutePath().toString());

        cmd.add("-l");
        cmd.add(cfg.language());

        // Output selection: JSON (-oj) when enabled, otherwise text (-otxt)
        if ("json".equalsIgnoreCase(this.outputMode)) {
            cmd.add("-oj");
        } else {
            cmd.add("-otxt");
        }

        cmd.add("-of");
        cmd.add("stdout");
        cmd.add("-t");
        cmd.add(String.valueOf(cfg.threads()));

        return cmd;
    }

    /**
     * Normalizes output mode string to lowercase, defaulting to "text" if null or blank.
     *
     * @param mode raw output mode string (may be null)
     * @return normalized mode ("text" or lowercase input)
     */
    private static String normalizeOutputMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return "text";
        }
        return mode.toLowerCase();
    }

    /**
     * Resolves a path to absolute, handling both relative and absolute inputs.
     *
     * <p>This ensures commands work correctly regardless of the process working directory.
     *
     * @param pathString path from configuration (may be relative or absolute)
     * @return absolute Path
     */
    private static Path resolvePath(String pathString) {
        Path path = Path.of(pathString);
        if (path.isAbsolute()) {
            return path;
        }
        // Resolve relative paths against current working directory
        return Path.of(".").toAbsolutePath().normalize().resolve(path).normalize();
    }
}
