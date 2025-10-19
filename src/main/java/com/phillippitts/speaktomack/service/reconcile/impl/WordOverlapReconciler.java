package com.phillippitts.speaktomack.service.reconcile.impl;

import com.phillippitts.speaktomack.domain.TranscriptionResult;
import com.phillippitts.speaktomack.service.reconcile.TranscriptReconciler;
import com.phillippitts.speaktomack.service.stt.EngineResult;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Chooses the text whose tokens have higher Jaccard similarity to the union (simple heuristic).
 * If both empty, returns empty. If both have low overlap (< threshold), fall back to longer text.
 */
public final class WordOverlapReconciler implements TranscriptReconciler {
    private final double threshold;

    public WordOverlapReconciler(double threshold) {
        if (threshold < 0.0 || threshold > 1.0) {
            throw new IllegalArgumentException("threshold in [0,1]");
        }
        this.threshold = threshold;
    }

    @Override
    public TranscriptionResult reconcile(EngineResult vosk, EngineResult whisper) {
        EngineResult a = vosk, b = whisper;
        if (a == null && b == null) {
            return TranscriptionResult.of("", 0.0, "reconciled");
        }
        if (a == null) {
            return TranscriptionResult.of(b.text(), b.confidence(), "reconciled");
        }
        if (b == null) {
            return TranscriptionResult.of(a.text(), a.confidence(), "reconciled");
        }

        double simA = jaccard(a.tokens(), union(a.tokens(), b.tokens()));
        double simB = jaccard(b.tokens(), union(a.tokens(), b.tokens()));

        EngineResult pick;
        if (Math.max(simA, simB) < threshold) {
            // pick the longer non-empty text as a fallback
            pick = (len(a.text()) >= len(b.text())) ? a : b;
        } else {
            pick = simA >= simB ? a : b;
        }
        double conf = pick.confidence();
        return TranscriptionResult.of(pick.text(), conf, "reconciled");
    }

    private static int len(String s) {
        return s == null ? 0 : s.length();
    }

    private static Set<String> union(List<String> t1, List<String> t2) {
        Set<String> u = new HashSet<>();
        if (t1 != null) {
            u.addAll(t1);
        }
        if (t2 != null) {
            u.addAll(t2);
        }
        return u;
    }

    private static double jaccard(List<String> a, Set<String> union) {
        if (union.isEmpty()) {
            return 0.0;
        }
        int inter = 0;
        if (a != null) {
            for (String s : a) {
                if (union.contains(s)) {
                    inter++;
                }
            }
        }
        return inter / (double) union.size();
    }
}