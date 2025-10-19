package com.phillippitts.speaktomack.service.audio.capture;

import java.util.Arrays;

/**
 * Simple ring buffer for PCM bytes. Thread-safe for one producer (capture thread)
 * and one consumer (reader after stop).
 */
final class PcmRingBuffer {

    private final byte[] buffer;
    private int writePos = 0;
    private int size = 0;

    PcmRingBuffer(int capacityBytes) {
        this.buffer = new byte[capacityBytes];
    }

    int capacity() {
        return buffer.length;
    }

    synchronized void write(byte[] src, int off, int len) {
        if (len <= 0) {
            return;
        }
        // If incoming exceeds capacity, drop oldest by keeping only the last capacity bytes
        if (len >= buffer.length) {
            // keep tail of src
            System.arraycopy(src, off + (len - buffer.length), buffer, 0, buffer.length);
            writePos = 0; // buffer full
            size = buffer.length;
            return;
        }
        // If size + len exceeds capacity, drop oldest
        int space = buffer.length - size;
        if (len > space) {
            int toDrop = len - space;
            size -= toDrop;
            // No need to adjust writePos - toByteArray() computes start position
            // from (writePos - size), so reducing size automatically drops oldest bytes
        }
        // Write with wrap
        int first = Math.min(len, buffer.length - writePos);
        System.arraycopy(src, off, buffer, writePos, first);
        int remaining = len - first;
        if (remaining > 0) {
            System.arraycopy(src, off + first, buffer, 0, remaining);
            writePos = remaining;
        } else {
            writePos = (writePos + first) % buffer.length;
        }
        size = Math.min(size + len, buffer.length);
    }

    synchronized byte[] toByteArray() {
        if (size == 0) {
            return new byte[0];
        }
        byte[] out = new byte[size];
        int start = (writePos - size + buffer.length) % buffer.length;
        int first = Math.min(size, buffer.length - start);
        System.arraycopy(buffer, start, out, 0, first);
        if (first < size) {
            System.arraycopy(buffer, 0, out, first, size - first);
        }
        return out;
    }

    synchronized void clear() {
        Arrays.fill(buffer, (byte)0);
        writePos = 0; size = 0;
    }
}
