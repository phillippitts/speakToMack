package com.phillippitts.speaktomack.service.reconcile.impl;

import com.phillippitts.speaktomack.service.stt.EngineResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConfidenceReconcilerTest {

    private final ConfidenceReconciler reconciler = new ConfidenceReconciler();

    @Test
    void prefersHigherConfidence() {
        var vosk = new EngineResult("vosk text", 0.9, List.of("vosk"), 100L, "vosk", null);
        var whisper = new EngineResult("whisper text", 0.95, List.of("whisper"), 200L, "whisper", null);

        var result = reconciler.reconcile(vosk, whisper);

        assertThat(result.text()).isEqualTo("whisper text");
        assertThat(result.confidence()).isEqualTo(0.95);
        assertThat(result.engineName()).isEqualTo("reconciled");
    }

    @Test
    void prefersVoskWhenVoskHasHigherConfidence() {
        var vosk = new EngineResult("vosk text", 0.98, List.of("vosk"), 100L, "vosk", null);
        var whisper = new EngineResult("whisper text", 0.85, List.of("whisper"), 200L, "whisper", null);

        var result = reconciler.reconcile(vosk, whisper);

        assertThat(result.text()).isEqualTo("vosk text");
        assertThat(result.confidence()).isEqualTo(0.98);
    }

    @Test
    void tieBreaksPrefersNonEmptyText() {
        var vosk = new EngineResult("", 0.9, List.of(), 100L, "vosk", null);
        var whisper = new EngineResult("whisper text", 0.9, List.of("whisper"), 200L, "whisper", null);

        var result = reconciler.reconcile(vosk, whisper);

        assertThat(result.text()).isEqualTo("whisper text");
        assertThat(result.confidence()).isEqualTo(0.9);
    }

    @Test
    void tieBreaksDefaultsToVoskWhenBothNonEmpty() {
        var vosk = new EngineResult("vosk text", 0.9, List.of("vosk"), 100L, "vosk", null);
        var whisper = new EngineResult("whisper text", 0.9, List.of("whisper"), 200L, "whisper", null);

        var result = reconciler.reconcile(vosk, whisper);

        assertThat(result.text()).isEqualTo("vosk text");
        assertThat(result.confidence()).isEqualTo(0.9);
    }

    @Test
    void tieBreaksDefaultsToVoskWhenBothEmpty() {
        var vosk = new EngineResult("", 0.5, List.of(), 100L, "vosk", null);
        var whisper = new EngineResult("", 0.5, List.of(), 200L, "whisper", null);

        var result = reconciler.reconcile(vosk, whisper);

        assertThat(result.text()).isEmpty();
        assertThat(result.confidence()).isEqualTo(0.5);
    }

    @Test
    void handlesNullVosk() {
        var whisper = new EngineResult("whisper text", 0.95, List.of("whisper"), 200L, "whisper", null);

        var result = reconciler.reconcile(null, whisper);

        assertThat(result.text()).isEqualTo("whisper text");
        assertThat(result.confidence()).isEqualTo(0.95);
    }

    @Test
    void handlesNullWhisper() {
        var vosk = new EngineResult("vosk text", 0.9, List.of("vosk"), 100L, "vosk", null);

        var result = reconciler.reconcile(vosk, null);

        assertThat(result.text()).isEqualTo("vosk text");
        assertThat(result.confidence()).isEqualTo(0.9);
    }

    @Test
    void handlesBothNull() {
        var result = reconciler.reconcile(null, null);

        assertThat(result.text()).isEmpty();
        assertThat(result.confidence()).isEqualTo(0.0);
    }

    @Test
    void prefersNonBlankTextOnTie() {
        var vosk = new EngineResult("  ", 0.9, List.of(), 100L, "vosk", null);
        var whisper = new EngineResult("whisper text", 0.9, List.of("whisper"), 200L, "whisper", null);

        var result = reconciler.reconcile(vosk, whisper);

        assertThat(result.text()).isEqualTo("whisper text");
    }

    @Test
    void handlesVeryLowConfidence() {
        var vosk = new EngineResult("vosk text", 0.1, List.of("vosk"), 100L, "vosk", null);
        var whisper = new EngineResult("whisper text", 0.05, List.of("whisper"), 200L, "whisper", null);

        var result = reconciler.reconcile(vosk, whisper);

        assertThat(result.text()).isEqualTo("vosk text");
        assertThat(result.confidence()).isEqualTo(0.1);
    }

    @Test
    void handlesPerfectConfidence() {
        var vosk = new EngineResult("vosk text", 0.95, List.of("vosk"), 100L, "vosk", null);
        var whisper = new EngineResult("whisper text", 1.0, List.of("whisper"), 200L, "whisper", null);

        var result = reconciler.reconcile(vosk, whisper);

        assertThat(result.text()).isEqualTo("whisper text");
        assertThat(result.confidence()).isEqualTo(1.0);
    }
}
