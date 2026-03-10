package com.phillippitts.blckvox.service.audio.capture;

import java.time.Instant;

/**
 * Published when the PCM ring buffer drops audio bytes due to capacity overflow.
 *
 * @param droppedBytes approximate number of bytes dropped
 * @param bufferCapacity total buffer capacity in bytes
 * @param timestamp when the overflow was detected
 */
public record BufferOverflowEvent(
        int droppedBytes,
        int bufferCapacity,
        Instant timestamp
) {
}
