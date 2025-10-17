package com.phillippitts.speaktomack.service.stt.whisper;

import java.nio.file.Path;
import java.util.List;

/**
 * Abstraction over {@link ProcessBuilder} to enable hermetic testing of process-based engines.
 *
 * <p>Production code uses {@link DefaultProcessFactory}. Tests may provide a stub
 * implementation that returns a fake {@link Process} with controlled stdout/stderr/exit behavior.
 */
interface ProcessFactory {
    /**
     * Starts a new process with the given command and working directory.
     *
     * @param command full command line, with the executable as the first element
     * @param workingDir working directory for the process (may be null)
     * @return started {@link Process}
     * @throws java.io.IOException if the process cannot be started
     */
    Process start(List<String> command, Path workingDir) throws java.io.IOException;
}
