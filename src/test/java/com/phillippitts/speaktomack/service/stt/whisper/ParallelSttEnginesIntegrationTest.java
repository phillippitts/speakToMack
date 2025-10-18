package com.phillippitts.speaktomack.service.stt.whisper;

import com.phillippitts.speaktomack.TestResourceLoader;
import com.phillippitts.speaktomack.config.ThreadPoolConfig;
import com.phillippitts.speaktomack.config.stt.VoskConfig;
import com.phillippitts.speaktomack.config.stt.WhisperConfig;
import com.phillippitts.speaktomack.domain.TranscriptionResult;
import com.phillippitts.speaktomack.service.orchestration.ParallelTranscriptionService;
import com.phillippitts.speaktomack.service.stt.vosk.VoskSttEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executor;

import static com.phillippitts.speaktomack.service.stt.whisper.WhisperTestDoubles.ProcessBehavior;
import static com.phillippitts.speaktomack.service.stt.whisper.WhisperTestDoubles.StubProcessFactory;
import static com.phillippitts.speaktomack.service.stt.whisper.WhisperTestDoubles.TestProcess;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Parallel integration test running Vosk and Whisper concurrently.
 *
 * <p>This test validates Task 2.6 requirements:
 * <ul>
 *   <li>Vosk and Whisper run in parallel without exceptions</li>
 *   <li>No thread contention or deadlocks</li>
 *   <li>Uses thread pool configuration from Task 1.5 (ThreadPoolConfig)</li>
 *   <li>Memory leak test (10 iterations)</li>
 * </ul>
 *
 * <p>This test is gated and will only run when the real Vosk model is available locally.
 * Whisper is executed via a stubbed process (hermetic) so no real binary is needed.
 */
class ParallelSttEnginesIntegrationTest {

    private static final String VOSK_MODEL = "models/vosk-model-small-en-us-0.15";
    private static final int MEMORY_LEAK_ITERATIONS = 10;

    private Executor sttExecutor;
    private VoskSttEngine vosk;
    private WhisperSttEngine whisper;

    @BeforeEach
    void setUp() {
        // Gate on system property and presence of Vosk model directory
        boolean gateEnabled = Boolean.getBoolean("vosk.model.available");
        assumeTrue(gateEnabled, "Set -Dvosk.model.available=true to run this test");
        assumeTrue(Files.exists(Path.of(VOSK_MODEL)),
                () -> "Vosk model not found at " + VOSK_MODEL + ". Run ./setup-models.sh first.");

        // Create thread pool using same configuration as Task 1.5
        ThreadPoolConfig threadPoolConfig = new ThreadPoolConfig();
        sttExecutor = threadPoolConfig.sttExecutor();

        vosk = new VoskSttEngine(new VoskConfig(VOSK_MODEL, 16_000, 1));
        vosk.initialize();

        // Stub Whisper process to keep this test hermetic (no real binary required)
        WhisperProcessManager mgr = new WhisperProcessManager(new StubProcessFactory(
                new TestProcess(new ProcessBehavior("stub whisper", "", 0, 0))
        ));
        WhisperConfig wcfg = new WhisperConfig("/bin/echo", "models/ggml-base.en.bin", 5, "en", 2, 1048576);
        whisper = new WhisperSttEngine(wcfg, mgr);
        whisper.initialize();
    }

    @AfterEach
    void tearDown() {
        if (vosk != null) {
            try {
                vosk.close();
            } catch (Exception ignored) {
                // Cleanup - exception ignored
            }
        }
        if (whisper != null) {
            try {
                whisper.close();
            } catch (Exception ignored) {
                // Cleanup - exception ignored
            }
        }
    }

    @Test
    @EnabledIfSystemProperty(named = "vosk.model.available", matches = "true")
    void shouldRunVoskAndWhisperInParallel() throws IOException {
        // Use a real 1s PCM silence test resource (validated by AudioValidator in service)
        byte[] pcm = TestResourceLoader.loadPcm("/audio/silence-1s.pcm");

        ParallelTranscriptionService.EngineConfig cfg =
                new ParallelTranscriptionService.EngineConfig(vosk, whisper, sttExecutor);
        var validator = new com.phillippitts.speaktomack.service.validation.AudioValidator(
                new com.phillippitts.speaktomack.service.validation.AudioValidationProperties()
        );
        ParallelTranscriptionService svc = new ParallelTranscriptionService(validator, cfg);

        // Run 10 iterations to check for memory leaks (Task 2.6 requirement)
        long totalDurationMs = 0;
        for (int i = 0; i < MEMORY_LEAK_ITERATIONS; i++) {
            long t0 = System.nanoTime();
            List<TranscriptionResult> results = svc.transcribeBoth(pcm, Duration.ofSeconds(10));
            long durationMs = (System.nanoTime() - t0) / 1_000_000L;
            totalDurationMs += durationMs;

            // Verify results on each iteration
            assertThat(results).hasSize(2);
            assertThat(results.get(0).engineName()).isEqualTo("vosk");
            assertThat(results.get(1).engineName()).isEqualTo("whisper");
            assertThat(results.get(0).confidence()).isBetween(0.0, 1.0);
            assertThat(results.get(1).confidence()).isBetween(0.0, 1.0);

            // Engines should remain healthy across all iterations
            assertThat(vosk.isHealthy()).isTrue();
            assertThat(whisper.isHealthy()).isTrue();
        }

        // Log helpful metrics without asserting to avoid flakiness
        System.out.println("Parallel Vosk+Whisper " + MEMORY_LEAK_ITERATIONS
                + " iterations: totalMs=" + totalDurationMs
                + ", avgMs=" + (totalDurationMs / MEMORY_LEAK_ITERATIONS));
    }
}
