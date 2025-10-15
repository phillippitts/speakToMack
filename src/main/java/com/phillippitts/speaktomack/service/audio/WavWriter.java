package com.phillippitts.speaktomack.service.audio;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Minimal PCM16LE WAV writer for Whisper CLI integration.
 */
public final class WavWriter {
    private WavWriter() {}

    /**
     * Wrap a raw PCM16LE mono buffer as a simple PCM WAV (44-byte header).
     */
    public static byte[] wrapPcmAsWav(byte[] pcm16le) throws IOException {
        int dataSize = pcm16le.length;
        int chunkSize = 36 + dataSize;
        ByteArrayOutputStream out = new ByteArrayOutputStream(AudioFormat.WAV_HEADER_SIZE + dataSize);
        ByteBuffer h = ByteBuffer.allocate(AudioFormat.WAV_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN);
        h.put((byte) 'R').put((byte) 'I').put((byte) 'F').put((byte) 'F');
        h.putInt(chunkSize);
        h.put((byte) 'W').put((byte) 'A').put((byte) 'V').put((byte) 'E');
        h.put((byte) 'f').put((byte) 'm').put((byte) 't').put((byte) ' ');
        h.putInt(16); // PCM subchunk size
        h.putShort((short) 1); // PCM format
        h.putShort((short) AudioFormat.REQUIRED_CHANNELS);
        h.putInt(AudioFormat.REQUIRED_SAMPLE_RATE);
        h.putInt(AudioFormat.REQUIRED_BYTE_RATE);
        h.putShort((short) AudioFormat.REQUIRED_BLOCK_ALIGN);
        h.putShort((short) AudioFormat.REQUIRED_BITS_PER_SAMPLE);
        h.put((byte) 'd').put((byte) 'a').put((byte) 't').put((byte) 'a');
        h.putInt(dataSize);
        out.write(h.array());
        out.write(pcm16le);
        return out.toByteArray();
    }
}
