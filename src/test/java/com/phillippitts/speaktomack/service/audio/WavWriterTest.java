package com.phillippitts.speaktomack.service.audio;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.phillippitts.speaktomack.service.audio.AudioFormat.REQUIRED_BITS_PER_SAMPLE;
import static com.phillippitts.speaktomack.service.audio.AudioFormat.REQUIRED_BLOCK_ALIGN;
import static com.phillippitts.speaktomack.service.audio.AudioFormat.REQUIRED_BYTE_RATE;
import static com.phillippitts.speaktomack.service.audio.AudioFormat.REQUIRED_CHANNELS;
import static com.phillippitts.speaktomack.service.audio.AudioFormat.REQUIRED_SAMPLE_RATE;
import static com.phillippitts.speaktomack.service.audio.AudioFormat.WAV_BITS_PER_SAMPLE_OFFSET;
import static com.phillippitts.speaktomack.service.audio.AudioFormat.WAV_BLOCK_ALIGN_OFFSET;
import static com.phillippitts.speaktomack.service.audio.AudioFormat.WAV_BYTE_RATE_OFFSET;
import static com.phillippitts.speaktomack.service.audio.AudioFormat.WAV_CHANNELS_OFFSET;
import static com.phillippitts.speaktomack.service.audio.AudioFormat.WAV_HEADER_SIZE;
import static com.phillippitts.speaktomack.service.audio.AudioFormat.WAV_SAMPLE_RATE_OFFSET;
import static org.assertj.core.api.Assertions.assertThat;

class WavWriterTest {

    @Test
    void shouldWriteValidWavHeaderAndPayload() throws IOException {
        // 1 second of silence at 16kHz mono 16-bit = 32,000 bytes
        byte[] pcm = new byte[REQUIRED_BYTE_RATE];
        Path wav = Files.createTempFile("wav-writer-", ".wav");
        try {
            WavWriter.writePcm16LeMono16kHz(pcm, wav);
            byte[] all = Files.readAllBytes(wav);

            // Overall size
            assertThat(all.length).isEqualTo(WAV_HEADER_SIZE + pcm.length);

            // RIFF/WAVE markers
            assertThat(new String(all, 0, 4)).isEqualTo("RIFF");
            assertThat(new String(all, 8, 4)).isEqualTo("WAVE");

            // fmt chunk size (little-endian 16) at bytes 16-19
            int fmtSize = ((all[16] & 0xFF)) | ((all[17] & 0xFF) << 8)
                    | ((all[18] & 0xFF) << 16) | ((all[19] & 0xFF) << 24);
            assertThat(fmtSize).isEqualTo(16);

            // Channels, sample rate, byte rate, block align, bits/sample
            int channels = (all[WAV_CHANNELS_OFFSET] & 0xFF) | ((all[WAV_CHANNELS_OFFSET + 1] & 0xFF) << 8);
            int sampleRate = (all[WAV_SAMPLE_RATE_OFFSET] & 0xFF)
                    | ((all[WAV_SAMPLE_RATE_OFFSET + 1] & 0xFF) << 8)
                    | ((all[WAV_SAMPLE_RATE_OFFSET + 2] & 0xFF) << 16)
                    | ((all[WAV_SAMPLE_RATE_OFFSET + 3] & 0xFF) << 24);
            int byteRate = (all[WAV_BYTE_RATE_OFFSET] & 0xFF)
                    | ((all[WAV_BYTE_RATE_OFFSET + 1] & 0xFF) << 8)
                    | ((all[WAV_BYTE_RATE_OFFSET + 2] & 0xFF) << 16)
                    | ((all[WAV_BYTE_RATE_OFFSET + 3] & 0xFF) << 24);
            int blockAlign = (all[WAV_BLOCK_ALIGN_OFFSET] & 0xFF)
                    | ((all[WAV_BLOCK_ALIGN_OFFSET + 1] & 0xFF) << 8);
            int bitsPerSample = (all[WAV_BITS_PER_SAMPLE_OFFSET] & 0xFF)
                    | ((all[WAV_BITS_PER_SAMPLE_OFFSET + 1] & 0xFF) << 8);

            assertThat(channels).isEqualTo(REQUIRED_CHANNELS);
            assertThat(sampleRate).isEqualTo(REQUIRED_SAMPLE_RATE);
            assertThat(byteRate).isEqualTo(REQUIRED_BYTE_RATE);
            assertThat(blockAlign).isEqualTo(REQUIRED_BLOCK_ALIGN);
            assertThat(bitsPerSample).isEqualTo(REQUIRED_BITS_PER_SAMPLE);
        } finally {
            Files.deleteIfExists(wav);
        }
    }
}
