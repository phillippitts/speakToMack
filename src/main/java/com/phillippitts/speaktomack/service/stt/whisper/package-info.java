/**
 * Whisper.cpp speech-to-text engine implementation.
 *
 * <p>This package provides a process-based STT engine adapter for whisper.cpp,
 * offering high-accuracy offline transcription with higher latency (~1-2s).
 *
 * <p>Key Components:
 * <ul>
 *   <li>{@link com.phillippitts.speaktomack.service.stt.whisper.WhisperSttEngine} - Spring
 *       component implementing {@link com.phillippitts.speaktomack.service.stt.SttEngine}
 *       for Whisper integration</li>
 *   <li>{@link com.phillippitts.speaktomack.service.stt.whisper.WhisperProcessManager} -
 *       Process lifecycle management (spawn, execute, timeout, cleanup)</li>
 *   <li>{@link com.phillippitts.speaktomack.service.stt.whisper.ProcessFactory} - Abstraction
 *       for creating OS processes (enables hermetic testing)</li>
 *   <li>{@link com.phillippitts.speaktomack.service.stt.whisper.DefaultProcessFactory} -
 *       Production implementation using {@code ProcessBuilder}</li>
 * </ul>
 *
 * <p>Whisper Characteristics:
 * <ul>
 *   <li><b>Speed:</b> Slower (~1-2s per 3-second clip)</li>
 *   <li><b>Accuracy:</b> High accuracy for longer speech and accents</li>
 *   <li><b>Threading:</b> Thread-safe via process isolation</li>
 *   <li><b>Resource Model:</b> Spawns new process per transcription</li>
 *   <li><b>Input Format:</b> Requires WAV files (PCM converted via {@code WavWriter})</li>
 *   <li><b>Output Format:</b> Plain text to stdout</li>
 *   <li><b>Timeout:</b> Configurable (default 10s) to prevent hanging</li>
 * </ul>
 *
 * <p>Configuration (application.properties):
 * <pre>
 * stt.whisper.binary-path=tools/whisper.cpp/main
 * stt.whisper.model-path=models/ggml-base.en.bin
 * stt.whisper.timeout-seconds=10
 * stt.whisper.language=en
 * stt.whisper.threads=4
 * </pre>
 *
 * <p>Transcription Flow:
 * <ol>
 *   <li>Convert PCM to WAV format using {@code WavWriter}</li>
 *   <li>Write WAV to temporary file</li>
 *   <li>Spawn whisper.cpp process via {@code WhisperProcessManager}</li>
 *   <li>Wait for process completion (with timeout)</li>
 *   <li>Parse stdout for transcription text</li>
 *   <li>Cleanup temporary file</li>
 * </ol>
 *
 * @see com.phillippitts.speaktomack.service.stt.SttEngine
 * @see com.phillippitts.speaktomack.config.stt.WhisperConfig
 * @see com.phillippitts.speaktomack.service.audio.WavWriter
 * @since 1.0
 */
package com.phillippitts.speaktomack.service.stt.whisper;
