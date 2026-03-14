package com.boombapcompile.blckvox.service.orchestration;

import com.boombapcompile.blckvox.config.properties.ReconciliationProperties;
import com.boombapcompile.blckvox.config.properties.ReconciliationProperties.Strategy;
import com.boombapcompile.blckvox.domain.TranscriptionResult;
import com.boombapcompile.blckvox.service.reconcile.TranscriptReconciler;
import com.boombapcompile.blckvox.service.stt.EngineResult;
import com.boombapcompile.blckvox.service.stt.parallel.ParallelSttService;
import com.boombapcompile.blckvox.service.stt.parallel.ParallelSttService.EnginePair;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link DefaultReconciliationService}.
 *
 * <p>Uses stub implementations for all collaborators (no Spring context required).
 */
class DefaultReconciliationServiceTest {

    // --- Constructor null-rejection tests ---

    @Test
    void shouldRejectNullParallelService() {
        assertThatThrownBy(() -> new DefaultReconciliationService(
                null, stubReconciler(), enabledProps()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("parallel");
    }

    @Test
    void shouldRejectNullReconciler() {
        assertThatThrownBy(() -> new DefaultReconciliationService(
                stubParallel(), null, enabledProps()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("reconciler");
    }

    @Test
    void shouldRejectNullProps() {
        assertThatThrownBy(() -> new DefaultReconciliationService(
                stubParallel(), stubReconciler(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("props");
    }

    // --- Disabled service ---

    @Test
    void disabledServiceShouldReportNotEnabled() {
        ReconciliationService disabled = DefaultReconciliationService.disabled();
        assertThat(disabled.isEnabled()).isFalse();
    }

    @Test
    void disabledServiceShouldThrowOnReconcile() {
        ReconciliationService disabled = DefaultReconciliationService.disabled();
        assertThatThrownBy(() -> disabled.reconcile(new byte[100]))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not enabled");
    }

    @Test
    void disabledServiceShouldThrowOnGetStrategy() {
        ReconciliationService disabled = DefaultReconciliationService.disabled();
        assertThatThrownBy(disabled::getStrategy)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not enabled");
    }

    // --- Enabled service: isEnabled ---

    @Test
    void enabledServiceShouldReportEnabled() {
        DefaultReconciliationService service = new DefaultReconciliationService(
                stubParallel(), stubReconciler(), enabledProps());
        assertThat(service.isEnabled()).isTrue();
    }

    @Test
    void disabledPropsShouldReportNotEnabled() {
        DefaultReconciliationService service = new DefaultReconciliationService(
                stubParallel(), stubReconciler(), disabledProps());
        assertThat(service.isEnabled()).isFalse();
    }

    // --- reconcile() ---

    @Test
    void shouldRejectNullPcm() {
        DefaultReconciliationService service = new DefaultReconciliationService(
                stubParallel(), stubReconciler(), enabledProps());
        assertThatThrownBy(() -> service.reconcile(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("pcm");
    }

    @Test
    void shouldThrowWhenReconciliationDisabledButReconcileCalled() {
        DefaultReconciliationService service = new DefaultReconciliationService(
                stubParallel(), stubReconciler(), disabledProps());
        assertThatThrownBy(() -> service.reconcile(new byte[100]))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not enabled");
    }

    @Test
    void shouldReconcileSuccessfully() {
        EngineResult voskResult = new EngineResult("hello", 0.9, List.of("hello"), 100, "vosk", "{}");
        EngineResult whisperResult = new EngineResult("hello world", 0.95, List.of("hello", "world"), 200, "whisper", "{}");
        EnginePair pair = new EnginePair(voskResult, whisperResult);

        ParallelSttService parallel = (pcm, timeoutMs) -> pair;

        TranscriptionResult expectedResult = TranscriptionResult.of("hello world", 0.95, "reconciled");
        TranscriptReconciler reconciler = (vosk, whisper) -> expectedResult;

        DefaultReconciliationService service = new DefaultReconciliationService(
                parallel, reconciler, enabledProps());

        TranscriptionResult result = service.reconcile(new byte[100]);

        assertThat(result.text()).isEqualTo("hello world");
        assertThat(result.confidence()).isEqualTo(0.95);
    }

    // --- getStrategy() ---

    @Test
    void shouldReturnStrategyName() {
        DefaultReconciliationService service = new DefaultReconciliationService(
                stubParallel(), stubReconciler(), enabledProps());
        assertThat(service.getStrategy()).isEqualTo("SIMPLE");
    }

    @Test
    void shouldReturnConfidenceStrategy() {
        ReconciliationProperties props = new ReconciliationProperties(true, Strategy.CONFIDENCE, 0.6, 0.7);
        DefaultReconciliationService service = new DefaultReconciliationService(
                stubParallel(), stubReconciler(), props);
        assertThat(service.getStrategy()).isEqualTo("CONFIDENCE");
    }

    @Test
    void shouldThrowOnGetStrategyWhenDisabled() {
        DefaultReconciliationService service = new DefaultReconciliationService(
                stubParallel(), stubReconciler(), disabledProps());
        assertThatThrownBy(service::getStrategy)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not enabled");
    }

    // --- Helpers ---

    private static ReconciliationProperties enabledProps() {
        return new ReconciliationProperties(true, Strategy.SIMPLE, 0.6, 0.7);
    }

    private static ReconciliationProperties disabledProps() {
        return new ReconciliationProperties(false, Strategy.SIMPLE, 0.6, 0.7);
    }

    private static ParallelSttService stubParallel() {
        return (pcm, timeoutMs) -> new EnginePair(
                new EngineResult("stub", 1.0, List.of("stub"), 10, "vosk", "{}"),
                new EngineResult("stub", 1.0, List.of("stub"), 10, "whisper", "{}")
        );
    }

    private static TranscriptReconciler stubReconciler() {
        return (vosk, whisper) -> TranscriptionResult.of("stub", 1.0, "reconciled");
    }
}
