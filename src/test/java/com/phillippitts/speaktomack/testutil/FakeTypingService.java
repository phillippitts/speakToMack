package com.phillippitts.speaktomack.testutil;

import com.phillippitts.speaktomack.service.fallback.TypingService;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Test double for TypingService that records typed text for verification.
 *
 * <p>Thread-safe implementation using CopyOnWriteArrayList.
 */
public class FakeTypingService implements TypingService {
    public final List<String> typedTexts = new CopyOnWriteArrayList<>();

    @Override
    public boolean paste(String text) {
        typedTexts.add(text);
        return true;
    }

    /**
     * Clears all recorded typed texts (useful for multi-test scenarios).
     */
    public void clear() {
        typedTexts.clear();
    }
}
