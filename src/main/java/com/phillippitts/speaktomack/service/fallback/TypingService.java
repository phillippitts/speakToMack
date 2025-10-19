package com.phillippitts.speaktomack.service.fallback;

/**
 * Service that pastes/transfers transcribed text into the active application using
 * a strategy chain with graceful fallbacks (Robot -> Clipboard -> Notify-only).
 */
public interface TypingService {
    /**
     * Attempts to paste the given text using the best available strategy.
     * Implementations must be privacy-safe in logs and avoid leaking full text at INFO.
     *
     * @param text transcription text (can be empty)
     * @return true if any tier succeeded, false otherwise
     */
    boolean paste(String text);
}
