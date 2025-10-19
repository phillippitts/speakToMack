package com.phillippitts.speaktomack.service.fallback;

import com.phillippitts.speaktomack.service.orchestration.event.TranscriptionCompletedEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Listens for completed transcriptions and delivers text using TypingService with
 * graceful fallbacks (Robot -> Clipboard -> Notify).
 */
@Service
public class FallbackManager {
    private static final Logger LOG = LogManager.getLogger(FallbackManager.class);

    private final TypingService typingService;

    public FallbackManager(TypingService typingService) {
        this.typingService = Objects.requireNonNull(typingService);
    }

    @EventListener
    public void onTranscription(TranscriptionCompletedEvent evt) {
        String text = evt.result().text();
        boolean ok = typingService.paste(text);
        if (!ok) {
            LOG.warn("All typing fallbacks failed (chars={})", text == null ? 0 : text.length());
        }
    }
}
