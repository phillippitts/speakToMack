package com.phillippitts.speaktomack.service.audio.capture;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PcmRingBufferTest {

    @Test
    void writesAndReadsInOrderWithinCapacity() {
        PcmRingBuffer buf = new PcmRingBuffer(8);
        buf.write(new byte[]{1,2,3,4}, 0, 4);
        assertThat(buf.toByteArray()).containsExactly(1,2,3,4);
        buf.write(new byte[]{5,6,7,8}, 0, 4);
        assertThat(buf.toByteArray()).containsExactly(1,2,3,4,5,6,7,8);
    }

    @Test
    void dropsOldestWhenOverflow() {
        PcmRingBuffer buf = new PcmRingBuffer(8);
        buf.write(new byte[]{1,2,3,4,5,6,7,8}, 0, 8);
        buf.write(new byte[]{9,10,11}, 0, 3);
        // Expect last 8 numbers [4..11]
        assertThat(buf.toByteArray()).containsExactly(4,5,6,7,8,9,10,11);
    }

    @Test
    void clearResetsState() {
        PcmRingBuffer buf = new PcmRingBuffer(4);
        buf.write(new byte[]{1,2,3,4}, 0, 4);
        buf.clear();
        assertThat(buf.toByteArray()).isEmpty();
    }

    @Test
    void wrapAndOverflowDropsOldestCorrectly() {
        // Capacity 10: test wrap+overflow in single write
        PcmRingBuffer buf = new PcmRingBuffer(10);

        // Write 7 bytes, positioning writePos near end
        buf.write(new byte[]{1,2,3,4,5,6,7}, 0, 7);
        assertThat(buf.toByteArray()).containsExactly(1,2,3,4,5,6,7);

        // Write 8 bytes: wraps (3 remaining + 5 wrapped) AND overflows (8 > 3, drops 5 oldest)
        // After this: oldest 5 bytes [1,2,3,4,5] are dropped, buffer contains [6,7,11,12,13,14,15,16,17,18]
        buf.write(new byte[]{11,12,13,14,15,16,17,18}, 0, 8);

        // Expect last 10 bytes: [6,7] from first write + [11..18] from second write
        assertThat(buf.toByteArray()).containsExactly(6,7,11,12,13,14,15,16,17,18);
    }
}
