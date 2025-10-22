package com.phillippitts.speaktomack.service.orchestration;

import com.phillippitts.speaktomack.config.properties.OrchestrationProperties;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe coordinator for tracking transcription timing and determining paragraph breaks.
 *
 * <p>This class manages the timing logic for automatic paragraph insertion between
 * transcriptions. It uses {@link AtomicLong} instead of {@code volatile long} for
 * better thread safety and clarity.
 *
 * <p><b>Paragraph Break Logic:</b>
 * <ul>
 *   <li>If no previous transcription exists, no paragraph break is inserted</li>
 *   <li>If time since last transcription exceeds configured threshold, insert paragraph break</li>
 *   <li>Otherwise, transcriptions are separated by spaces only</li>
 * </ul>
 *
 * <p><b>Thread Safety:</b> All methods are thread-safe. The {@link AtomicLong}
 * ensures visibility and atomicity of timestamp updates across threads.
 *
 * @since 1.0
 */
public final class TimingCoordinator {

    private final AtomicLong lastTranscriptionTimeMs = new AtomicLong(0);
    private final OrchestrationProperties orchProps;

    /**
     * Constructs a timing coordinator.
     *
     * @param orchProps orchestration configuration (silence gap threshold)
     * @throws NullPointerException if orchProps is null
     */
    public TimingCoordinator(OrchestrationProperties orchProps) {
        this.orchProps = Objects.requireNonNull(orchProps, "orchProps");
    }

    /**
     * Determines if a paragraph break should be inserted before the next transcription.
     *
     * <p>A paragraph break is inserted if the time since the last transcription exceeds
     * the configured threshold ({@code stt.orchestration.silence-gap-ms}).
     *
     * @return {@code true} if paragraph break should be inserted, {@code false} otherwise
     */
    public boolean shouldAddParagraphBreak() {
        long last = lastTranscriptionTimeMs.get();

        if (last == 0) {
            return false; // No previous transcription
        }

        long now = System.currentTimeMillis();
        long elapsed = now - last;

        return elapsed > orchProps.getSilenceGapMs();
    }

    /**
     * Records that a transcription occurred at the current time.
     *
     * <p>This should be called after each successful transcription to update
     * the timing state for future paragraph break calculations.
     */
    public void recordTranscription() {
        lastTranscriptionTimeMs.set(System.currentTimeMillis());
    }

    /**
     * Resets the timing state (clears last transcription timestamp).
     *
     * <p>This is primarily for testing purposes or resetting state after
     * long inactivity periods.
     */
    public void reset() {
        lastTranscriptionTimeMs.set(0);
    }

    /**
     * Returns the timestamp of the last recorded transcription.
     *
     * @return milliseconds since epoch, or 0 if no transcription has been recorded
     */
    long getLastTranscriptionTimeMs() {
        return lastTranscriptionTimeMs.get();
    }
}
