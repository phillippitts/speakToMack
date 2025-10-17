package com.phillippitts.speaktomack.service.stt.whisper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Default production implementation of {@link ProcessFactory} using {@link ProcessBuilder}.
 */
final class DefaultProcessFactory implements ProcessFactory {

    @Override
    public Process start(List<String> command, Path workingDir) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(command);
        if (workingDir != null) {
            pb.directory(workingDir.toFile());
        }
        // Keep stderr separate from stdout (we capture both)
        pb.redirectErrorStream(false);
        return pb.start();
    }
}
