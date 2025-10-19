package com.phillippitts.speaktomack.service.fallback;

import com.phillippitts.speaktomack.service.fallback.event.AllTypingFallbacksFailedEvent;
import com.phillippitts.speaktomack.service.fallback.event.TypingFallbackEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Logs typing fallback events succinctly (no PII). */
@Component
class TypingEventsListener {
    private static final Logger LOG = LogManager.getLogger(TypingEventsListener.class);

    @EventListener
    void onFallback(TypingFallbackEvent e) {
        LOG.warn("Typing fallback: tier={}, reason={}", e.tier(), e.reason());
    }

    @EventListener
    void onAllFailed(AllTypingFallbacksFailedEvent e) {
        LOG.warn("All typing fallbacks failed: reason={}", e.reason());
    }
}
