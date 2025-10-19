package com.phillippitts.speaktomack.service.reconcile;

import com.phillippitts.speaktomack.config.orchestration.OrchestrationProperties;
import com.phillippitts.speaktomack.domain.TranscriptionResult;
import com.phillippitts.speaktomack.service.reconcile.impl.ConfidenceReconciler;
import com.phillippitts.speaktomack.service.reconcile.impl.SimplePreferenceReconciler;
import com.phillippitts.speaktomack.service.reconcile.impl.WordOverlapReconciler;
import com.phillippitts.speaktomack.service.stt.EngineResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReconcilerStrategiesTest {

    private static EngineResult er(String text, double conf, String engine) {
        return new EngineResult(text, conf, tokenize(text), 100, engine, null);
    }

    private static List<String> tokenize(String text) {
        if (text == null || text.isBlank()) return List.of();
        return List.of(text.toLowerCase().split("\\s+"));
    }

    @Test
    void simplePreferencePrefersPrimaryUnlessEmpty() {
        var prefVosk = new SimplePreferenceReconciler(OrchestrationProperties.PrimaryEngine.VOSK);
        TranscriptionResult r1 = prefVosk.reconcile(er("hello", 0.5, "vosk"), er("world", 0.9, "whisper"));
        assertThat(r1.text()).isEqualTo("hello");

        TranscriptionResult r2 = prefVosk.reconcile(er("", 0.5, "vosk"), er("world", 0.9, "whisper"));
        assertThat(r2.text()).isEqualTo("world");

        // null handling: if primary is null, pick other
        TranscriptionResult r3 = prefVosk.reconcile(null, er("alt", 0.8, "whisper"));
        assertThat(r3.text()).isEqualTo("alt");
    }

    @Test
    void confidenceReconcilerPicksHigher() {
        var confRec = new ConfidenceReconciler();
        TranscriptionResult r1 = confRec.reconcile(er("a", 0.6, "vosk"), er("b", 0.9, "whisper"));
        assertThat(r1.text()).isEqualTo("b");

        // tie-breaker prefers non-empty
        TranscriptionResult r2 = confRec.reconcile(er("a", 0.8, "vosk"), er("", 0.8, "whisper"));
        assertThat(r2.text()).isEqualTo("a");

        // null handling
        TranscriptionResult r3 = confRec.reconcile(null, er("x", 0.1, "whisper"));
        assertThat(r3.text()).isEqualTo("x");
    }

    @Test
    void wordOverlapUsesJaccardWithThreshold() {
        var rec = new WordOverlapReconciler(0.6);
        EngineResult vosk = er("hello world", 0.5, "vosk");
        EngineResult whisper = er("hello there", 0.9, "whisper");
        TranscriptionResult r = rec.reconcile(vosk, whisper);
        // Union tokens: {hello, world, there} -> overlap sizes likely pick one; ensure non-empty result
        assertThat(r.text()).isNotNull();

        // Low-overlap fallback to longer text
        EngineResult a = er("one two", 0.4, "vosk");
        EngineResult b = er("alpha beta gamma delta", 0.3, "whisper");
        TranscriptionResult r2 = rec.reconcile(a, b);
        assertThat(r2.text()).isEqualTo("alpha beta gamma delta");

        // Both null -> empty
        TranscriptionResult r3 = rec.reconcile(null, null);
        assertThat(r3.text()).isEmpty();
    }
}
