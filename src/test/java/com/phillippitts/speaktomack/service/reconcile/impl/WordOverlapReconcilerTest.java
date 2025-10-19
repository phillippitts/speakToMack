package com.phillippitts.speaktomack.service.reconcile.impl;

import com.phillippitts.speaktomack.domain.TranscriptionResult;
import com.phillippitts.speaktomack.service.stt.EngineResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WordOverlapReconcilerTest {

    @Test
    void throwsWhenThresholdOutOfRange() {
        assertThatThrownBy(() -> new WordOverlapReconciler(-0.1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("threshold in [0,1]");

        assertThatThrownBy(() -> new WordOverlapReconciler(1.1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("threshold in [0,1]");
    }

    @Test
    void acceptsValidThreshold() {
        var reconciler = new WordOverlapReconciler(0.6);
        assertThat(reconciler).isNotNull();
    }

    @Test
    void prefersHigherJaccardSimilarity() {
        var reconciler = new WordOverlapReconciler(0.5);
        // "hello world" vs "world hello" - whisper has better overlap with union
        var vosk = new EngineResult("hello", 0.9, List.of("hello"), 100L, "vosk", null);
        var whisper = new EngineResult("hello world", 0.9, List.of("hello", "world"), 200L, "whisper", null);

        var result = reconciler.reconcile(vosk, whisper);

        // Whisper has 2/2 = 1.0 similarity, Vosk has 1/2 = 0.5 similarity
        assertThat(result.text()).isEqualTo("hello world");
        assertThat(result.confidence()).isEqualTo(0.9);
        assertThat(result.engineName()).isEqualTo("reconciled");
    }

    @Test
    void fallsBackToLongerTextWhenBothBelowThreshold() {
        var reconciler = new WordOverlapReconciler(0.9); // High threshold
        // Completely different words
        var vosk = new EngineResult("cat", 0.9, List.of("cat"), 100L, "vosk", null);
        var whisper = new EngineResult("dog bird", 0.9, List.of("dog", "bird"), 200L, "whisper", null);

        var result = reconciler.reconcile(vosk, whisper);

        // Both have low overlap (<0.9), so pick longer text
        assertThat(result.text()).isEqualTo("dog bird");
    }

    @Test
    void handlesIdenticalTokens() {
        var reconciler = new WordOverlapReconciler(0.6);
        var vosk = new EngineResult("hello world", 0.9, List.of("hello", "world"), 100L, "vosk", null);
        var whisper = new EngineResult("hello world", 0.95, List.of("hello", "world"), 200L, "whisper", null);

        var result = reconciler.reconcile(vosk, whisper);

        // Both have 100% overlap, should pick first (vosk)
        assertThat(result.text()).isEqualTo("hello world");
        assertThat(result.confidence()).isEqualTo(0.9);
    }

    @Test
    void handlesDifferentTokenization() {
        var reconciler = new WordOverlapReconciler(0.5);
        var vosk = new EngineResult("testing one two three", 0.9,
                List.of("testing", "one", "two", "three"), 100L, "vosk", null);
        var whisper = new EngineResult("testing three", 0.95,
                List.of("testing", "three"), 200L, "whisper", null);

        var result = reconciler.reconcile(vosk, whisper);

        // Vosk: 4/4 = 1.0, Whisper: 2/4 = 0.5
        assertThat(result.text()).isEqualTo("testing one two three");
    }

    @Test
    void handlesEmptyTokens() {
        var reconciler = new WordOverlapReconciler(0.6);
        var vosk = new EngineResult("", 0.0, List.of(), 100L, "vosk", null);
        var whisper = new EngineResult("hello", 0.9, List.of("hello"), 200L, "whisper", null);

        var result = reconciler.reconcile(vosk, whisper);

        assertThat(result.text()).isEqualTo("hello");
    }

    @Test
    void handlesBothEmpty() {
        var reconciler = new WordOverlapReconciler(0.6);
        var vosk = new EngineResult("", 0.0, List.of(), 100L, "vosk", null);
        var whisper = new EngineResult("", 0.0, List.of(), 200L, "whisper", null);

        var result = reconciler.reconcile(vosk, whisper);

        assertThat(result.text()).isEmpty();
        assertThat(result.confidence()).isEqualTo(0.0);
    }

    @Test
    void handlesNullVosk() {
        var reconciler = new WordOverlapReconciler(0.6);
        var whisper = new EngineResult("whisper text", 0.95, List.of("whisper", "text"), 200L, "whisper", null);

        var result = reconciler.reconcile(null, whisper);

        assertThat(result.text()).isEqualTo("whisper text");
        assertThat(result.confidence()).isEqualTo(0.95);
    }

    @Test
    void handlesNullWhisper() {
        var reconciler = new WordOverlapReconciler(0.6);
        var vosk = new EngineResult("vosk text", 0.9, List.of("vosk", "text"), 100L, "vosk", null);

        var result = reconciler.reconcile(vosk, null);

        assertThat(result.text()).isEqualTo("vosk text");
        assertThat(result.confidence()).isEqualTo(0.9);
    }

    @Test
    void handlesBothNull() {
        var reconciler = new WordOverlapReconciler(0.6);

        var result = reconciler.reconcile(null, null);

        assertThat(result.text()).isEmpty();
        assertThat(result.confidence()).isEqualTo(0.0);
    }

    @Test
    void edgeCaseZeroThreshold() {
        var reconciler = new WordOverlapReconciler(0.0);
        var vosk = new EngineResult("cat", 0.9, List.of("cat"), 100L, "vosk", null);
        var whisper = new EngineResult("dog", 0.95, List.of("dog"), 200L, "whisper", null);

        var result = reconciler.reconcile(vosk, whisper);

        // With threshold 0, even low overlap should work
        assertThat(result).isNotNull();
    }

    @Test
    void edgeCasePerfectThreshold() {
        var reconciler = new WordOverlapReconciler(1.0);
        var vosk = new EngineResult("hello world", 0.9, List.of("hello", "world"), 100L, "vosk", null);
        var whisper = new EngineResult("hello world", 0.95, List.of("hello", "world"), 200L, "whisper", null);

        var result = reconciler.reconcile(vosk, whisper);

        // Perfect overlap = 1.0, meets threshold of 1.0
        assertThat(result.text()).isEqualTo("hello world");
    }

    @Test
    void partialOverlapScenario() {
        var reconciler = new WordOverlapReconciler(0.5);
        // Vosk: "the quick brown fox"
        // Whisper: "the brown dog"
        // Union: {the, quick, brown, fox, dog} = 5 words
        // Vosk similarity: 4/5 = 0.8
        // Whisper similarity: 3/5 = 0.6
        var vosk = new EngineResult("the quick brown fox", 0.9,
                List.of("the", "quick", "brown", "fox"), 100L, "vosk", null);
        var whisper = new EngineResult("the brown dog", 0.95,
                List.of("the", "brown", "dog"), 200L, "whisper", null);

        var result = reconciler.reconcile(vosk, whisper);

        assertThat(result.text()).isEqualTo("the quick brown fox");
        assertThat(result.confidence()).isEqualTo(0.9);
    }

    @Test
    void prefersLongerWhenEqualLowSimilarity() {
        var reconciler = new WordOverlapReconciler(0.9);
        // Both have low similarity, pick longer
        var vosk = new EngineResult("short", 0.9, List.of("short"), 100L, "vosk", null);
        var whisper = new EngineResult("much longer text here", 0.95,
                List.of("much", "longer", "text", "here"), 200L, "whisper", null);

        var result = reconciler.reconcile(vosk, whisper);

        assertThat(result.text()).isEqualTo("much longer text here");
    }
}
