package com.phillippitts.speaktomack.service.fallback;

import com.phillippitts.speaktomack.util.LogSanitizer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

/** Tier 3: log-only notification (no OS interactions). */
@Component
class NotifyOnlyAdapter implements TypingAdapter {
    private static final Logger LOG = LogManager.getLogger(NotifyOnlyAdapter.class);

    @Override
    public boolean canType() {
        return true;
    }

    @Override
    public boolean type(String text) {
        String preview = LogSanitizer.truncate(text, 120);
        LOG.info("Transcription ready (chars={})", text == null ? 0 : text.length());
        LOG.debug("Preview: '{}'", preview);
        return true; // Consider notify as success to avoid retry loops
    }

    @Override
    public String name() {
        return "notify";
    }
}
