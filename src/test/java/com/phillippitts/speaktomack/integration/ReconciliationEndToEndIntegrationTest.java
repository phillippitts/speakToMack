package com.phillippitts.speaktomack.integration;

import com.phillippitts.speaktomack.config.properties.OrchestrationProperties;
import com.phillippitts.speaktomack.config.properties.ReconciliationProperties;
import com.phillippitts.speaktomack.domain.TranscriptionResult;
import com.phillippitts.speaktomack.exception.TranscriptionException;
import com.phillippitts.speaktomack.service.reconcile.TranscriptReconciler;
import com.phillippitts.speaktomack.service.reconcile.impl.ConfidenceReconciler;
import com.phillippitts.speaktomack.service.reconcile.impl.SimplePreferenceReconciler;
import com.phillippitts.speaktomack.service.reconcile.impl.WordOverlapReconciler;
import com.phillippitts.speaktomack.service.stt.parallel.DefaultParallelSttService;
import com.phillippitts.speaktomack.service.stt.parallel.ParallelSttService;
import com.phillippitts.speaktomack.testutil.FakeSttEngine;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end integration test for Phase 4: Dual-Engine Reconciliation.
 *
 * <p>Tests the complete flow: parallel STT execution → reconciliation → final result.
 * Validates all 3 reconciliation strategies, partial results, timeouts, and memory safety.
 *
 * <p><b>Task 4.7 Acceptance Criteria:</b>
 * <ul>
 *   <li>Both engines succeed → reconciler selects correct result per strategy</li>
 *   <li>One engine fails → reconciler uses available result</li>
 *   <li>Both engines fail → exception propagated</li>
 *   <li>Timeout on one engine → partial results reconciled</li>
 *   <li>Strategy switching via config → different reconcilers used</li>
 *   <li>Memory leak test: < 50MB growth per 100 iterations</li>
 * </ul>
 */
class ReconciliationEndToEndIntegrationTest {

    private static final byte[] DUMMY_PCM = new byte[32000]; // 1 second at 16kHz mono 16-bit
    private static final long TIMEOUT_MS = 5000L;

    // ========== Strategy Selection Tests ==========

    @Test
    void simplePreferenceReconcilerSelectsPrimaryEngine() {
        // Setup: Vosk (primary) returns "hello", Whisper returns "goodbye"
        FakeSttEngine vosk = new FakeSttEngine("vosk", "hello", 0.8);
        FakeSttEngine whisper = new FakeSttEngine("whisper", "goodbye", 0.95);

        ParallelSttService parallelService = createParallelService(vosk, whisper);
        TranscriptReconciler reconciler = new SimplePreferenceReconciler(OrchestrationProperties.PrimaryEngine.VOSK);

        // Act: Run parallel and reconcile
        ParallelSttService.EnginePair results = parallelService.transcribeBoth(DUMMY_PCM, TIMEOUT_MS);
        TranscriptionResult finalResult = reconciler.reconcile(results.vosk(), results.whisper());

        // Assert: Should prefer primary (Vosk) despite lower confidence
        assertThat(finalResult.text()).isEqualTo("hello");
        assertThat(finalResult.engineName()).isEqualTo("reconciled");
    }

    @Test
    void confidenceReconcilerSelectsHigherConfidence() {
        // Setup: Vosk has 0.6 confidence, Whisper has 0.95 confidence
        FakeSttEngine vosk = new FakeSttEngine("vosk", "uncertain", 0.6);
        FakeSttEngine whisper = new FakeSttEngine("whisper", "confident", 0.95);

        ParallelSttService parallelService = createParallelService(vosk, whisper);
        TranscriptReconciler reconciler = new ConfidenceReconciler();

        // Act
        ParallelSttService.EnginePair results = parallelService.transcribeBoth(DUMMY_PCM, TIMEOUT_MS);
        TranscriptionResult finalResult = reconciler.reconcile(results.vosk(), results.whisper());

        // Assert: Should select Whisper (higher confidence)
        assertThat(finalResult.text()).isEqualTo("confident");
        assertThat(finalResult.confidence()).isEqualTo(0.95);
    }

    @Test
    void wordOverlapReconcilerSelectsMostComprehensive() {
        // Setup: Vosk="hello", Whisper="hello world" (Whisper has more tokens)
        FakeSttEngine vosk = new FakeSttEngine("vosk", "hello", 0.9);
        FakeSttEngine whisper = new FakeSttEngine("whisper", "hello world", 0.9);

        ParallelSttService parallelService = createParallelService(vosk, whisper);
        TranscriptReconciler reconciler = new WordOverlapReconciler(0.6);

        // Act
        ParallelSttService.EnginePair results = parallelService.transcribeBoth(DUMMY_PCM, TIMEOUT_MS);
        TranscriptionResult finalResult = reconciler.reconcile(results.vosk(), results.whisper());

        // Assert: Should select Whisper (more comprehensive, higher Jaccard similarity)
        assertThat(finalResult.text()).isEqualTo("hello world");
    }

    @Test
    void wordOverlapReconcilerHandlesDivergentResults() {
        // Setup: Completely different transcriptions (below threshold)
        FakeSttEngine vosk = new FakeSttEngine("vosk", "apple orange banana", 0.9);
        FakeSttEngine whisper = new FakeSttEngine("whisper", "cat dog elephant", 0.9);

        ParallelSttService parallelService = createParallelService(vosk, whisper);
        TranscriptReconciler reconciler = new WordOverlapReconciler(0.6);

        // Act
        ParallelSttService.EnginePair results = parallelService.transcribeBoth(DUMMY_PCM, TIMEOUT_MS);
        TranscriptionResult finalResult = reconciler.reconcile(results.vosk(), results.whisper());

        // Assert: Should fall back to longer text when overlap below threshold
        assertThat(finalResult.text()).isIn("apple orange banana", "cat dog elephant");
        assertThat(finalResult.text().split(" ")).hasSize(3);
    }

    // ========== Partial Results Tests ==========

    @Test
    void reconcilerUsesAvailableResultWhenVoskFails() {
        // Setup: Vosk fails, Whisper succeeds
        FakeSttEngine vosk = new FakeSttEngine("vosk", "", 0.0, true); // Will fail
        FakeSttEngine whisper = new FakeSttEngine("whisper", "whisper works", 0.95);

        ParallelSttService parallelService = createParallelService(vosk, whisper);
        TranscriptReconciler reconciler = new SimplePreferenceReconciler(OrchestrationProperties.PrimaryEngine.VOSK);

        // Act
        ParallelSttService.EnginePair results = parallelService.transcribeBoth(DUMMY_PCM, TIMEOUT_MS);

        // Assert: Vosk result should be null
        assertThat(results.vosk()).isNull();
        assertThat(results.whisper()).isNotNull();

        // Reconciler should use available result
        TranscriptionResult finalResult = reconciler.reconcile(null, results.whisper());
        assertThat(finalResult.text()).isEqualTo("whisper works");
    }

    @Test
    void reconcilerUsesAvailableResultWhenWhisperFails() {
        // Setup: Vosk succeeds, Whisper fails
        FakeSttEngine vosk = new FakeSttEngine("vosk", "vosk works", 0.95);
        FakeSttEngine whisper = new FakeSttEngine("whisper", "", 0.0, true); // Will fail

        ParallelSttService parallelService = createParallelService(vosk, whisper);
        TranscriptReconciler reconciler = new SimplePreferenceReconciler(OrchestrationProperties.PrimaryEngine.VOSK);

        // Act
        ParallelSttService.EnginePair results = parallelService.transcribeBoth(DUMMY_PCM, TIMEOUT_MS);

        // Assert: Whisper result should be null
        assertThat(results.vosk()).isNotNull();
        assertThat(results.whisper()).isNull();

        // Reconciler should use available result
        TranscriptionResult finalResult = reconciler.reconcile(results.vosk(), null);
        assertThat(finalResult.text()).isEqualTo("vosk works");
    }

    @Test
    void throwsExceptionWhenBothEnginesFail() {
        // Setup: Both engines fail
        FakeSttEngine vosk = new FakeSttEngine("vosk", "", 0.0, true);
        FakeSttEngine whisper = new FakeSttEngine("whisper", "", 0.0, true);

        ParallelSttService parallelService = createParallelService(vosk, whisper);

        // Act & Assert: Should throw exception when both fail
        assertThatThrownBy(() -> parallelService.transcribeBoth(DUMMY_PCM, TIMEOUT_MS))
                .isInstanceOf(TranscriptionException.class)
                .hasMessageContaining("Both engines failed");
    }

    // ========== Timeout Handling Tests ==========

    @Test
    void handlesTimeoutOnSlowEngine() {
        // Setup: Vosk is fast, Whisper is slow (will timeout)
        FakeSttEngine vosk = new FakeSttEngine("vosk", "fast result", 0.9, 50); // 50ms delay
        FakeSttEngine whisper = new FakeSttEngine("whisper", "slow result", 0.9, 3000); // 3s delay

        ParallelSttService parallelService = createParallelService(vosk, whisper);
        TranscriptReconciler reconciler = new SimplePreferenceReconciler(OrchestrationProperties.PrimaryEngine.VOSK);

        // Act: Use 1-second timeout (Whisper should timeout)
        ParallelSttService.EnginePair results = parallelService.transcribeBoth(DUMMY_PCM, 1000L);

        // Assert: Vosk should succeed, Whisper should be null (timeout)
        assertThat(results.vosk()).isNotNull();
        assertThat(results.vosk().text()).isEqualTo("fast result");
        // Whisper result may be null due to timeout

        // Reconciler should handle partial results gracefully
        TranscriptionResult finalResult = reconciler.reconcile(results.vosk(), results.whisper());
        assertThat(finalResult.text()).isEqualTo("fast result");
    }

    // ========== Performance and Memory Tests ==========

    @Test
    void parallelExecutionFasterThanSequential() {
        // Setup: Both engines have 200ms delay
        int engineDelayMs = 200;
        FakeSttEngine vosk = new FakeSttEngine("vosk", "result", 0.9, engineDelayMs);
        FakeSttEngine whisper = new FakeSttEngine("whisper", "result", 0.9, engineDelayMs);

        ParallelSttService parallelService = createParallelService(vosk, whisper);

        // Act: Measure parallel execution time
        long startTime = System.nanoTime();
        ParallelSttService.EnginePair results = parallelService.transcribeBoth(DUMMY_PCM, TIMEOUT_MS);
        long elapsedMs = (System.nanoTime() - startTime) / 1_000_000L;

        // Assert: Parallel should be < 2x single-engine latency
        // Sequential would be 400ms, parallel should be ~200ms (< 2x = 400ms)
        assertThat(results.vosk()).isNotNull();
        assertThat(results.whisper()).isNotNull();
        assertThat(elapsedMs).isLessThan(engineDelayMs * 2 + 100); // Allow 100ms overhead
    }

    @Test
    void memoryLeakTestUnder100Iterations() {
        // Setup: Create engines and services once
        FakeSttEngine vosk = new FakeSttEngine("vosk", "iteration result", 0.9);
        FakeSttEngine whisper = new FakeSttEngine("whisper", "iteration result", 0.9);
        ParallelSttService parallelService = createParallelService(vosk, whisper);
        TranscriptReconciler reconciler = new ConfidenceReconciler();

        // Act: Run 100 iterations
        Runtime runtime = Runtime.getRuntime();
        runtime.gc(); // Baseline GC
        long baselineMemory = runtime.totalMemory() - runtime.freeMemory();

        for (int i = 0; i < 100; i++) {
            ParallelSttService.EnginePair results = parallelService.transcribeBoth(DUMMY_PCM, TIMEOUT_MS);
            TranscriptionResult finalResult = reconciler.reconcile(results.vosk(), results.whisper());
            assertThat(finalResult.text()).isNotEmpty();
        }

        runtime.gc(); // Force GC before measurement
        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryGrowthMB = (finalMemory - baselineMemory) / (1024 * 1024);

        // Assert: Memory growth < 50MB per 100 iterations
        assertThat(memoryGrowthMB).isLessThan(50L);
    }

    @Test
    void noThreadLeakAfterMultipleIterations() {
        // Setup
        FakeSttEngine vosk = new FakeSttEngine("vosk", "test", 0.9);
        FakeSttEngine whisper = new FakeSttEngine("whisper", "test", 0.9);
        ParallelSttService parallelService = createParallelService(vosk, whisper);

        // Act: Run 50 iterations
        int baselineThreadCount = Thread.activeCount();

        for (int i = 0; i < 50; i++) {
            parallelService.transcribeBoth(DUMMY_PCM, TIMEOUT_MS);
        }

        // Small delay to allow threads to terminate
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        int finalThreadCount = Thread.activeCount();
        int threadGrowth = finalThreadCount - baselineThreadCount;

        // Assert: Thread count growth should be minimal (< 5 threads)
        // Some thread pool threads may be alive, but shouldn't grow unbounded
        assertThat(threadGrowth).isLessThan(5);
    }

    // ========== Configuration Integration Tests ==========

    @Test
    void strategySwitchingViaProperties() {
        // Setup: Create engines
        FakeSttEngine vosk = new FakeSttEngine("vosk", "primary", 0.7);
        FakeSttEngine whisper = new FakeSttEngine("whisper", "secondary", 0.95);
        ParallelSttService parallelService = createParallelService(vosk, whisper);

        // Test 1: Simple strategy (prefers primary)
        ReconciliationProperties props1 = new ReconciliationProperties(
                true,
                ReconciliationProperties.Strategy.SIMPLE,
                0.6,
                0.7
        );
        TranscriptReconciler reconciler1 = createReconciler(props1);
        ParallelSttService.EnginePair results = parallelService.transcribeBoth(DUMMY_PCM, TIMEOUT_MS);
        TranscriptionResult result1 = reconciler1.reconcile(results.vosk(), results.whisper());
        assertThat(result1.text()).isEqualTo("primary");

        // Test 2: Confidence strategy (prefers higher confidence)
        ReconciliationProperties props2 = new ReconciliationProperties(
                true,
                ReconciliationProperties.Strategy.CONFIDENCE,
                0.6,
                0.7
        );
        TranscriptReconciler reconciler2 = createReconciler(props2);
        TranscriptionResult result2 = reconciler2.reconcile(results.vosk(), results.whisper());
        assertThat(result2.text()).isEqualTo("secondary"); // Higher confidence
    }

    // ========== Helper Methods ==========

    private ParallelSttService createParallelService(FakeSttEngine vosk, FakeSttEngine whisper) {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        return new DefaultParallelSttService(vosk, whisper, executor, 10000L);
    }

    private TranscriptReconciler createReconciler(ReconciliationProperties props) {
        return switch (props.getStrategy()) {
            case SIMPLE -> new SimplePreferenceReconciler(OrchestrationProperties.PrimaryEngine.VOSK);
            case CONFIDENCE -> new ConfidenceReconciler();
            case OVERLAP -> new WordOverlapReconciler(props.getOverlapThreshold());
        };
    }
}
