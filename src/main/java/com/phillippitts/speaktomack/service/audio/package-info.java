/**
 * Audio format utilities and conversions.
 *
 * <p>This package provides utilities for working with audio data in the application's
 * required format: 16kHz, 16-bit signed PCM, mono, little-endian.
 *
 * <p>Key Components:
 * <ul>
 *   <li>{@link com.phillippitts.speaktomack.service.audio.AudioFormat} - Single source
 *       of truth for audio format constants (sample rate, bits per sample, channels,
 *       byte rates, WAV header offsets)</li>
 *   <li>{@link com.phillippitts.speaktomack.service.audio.WavWriter} - Converts raw PCM
 *       bytes to WAV format with proper RIFF headers for whisper.cpp compatibility</li>
 * </ul>
 *
 * <p>Audio Format Requirements:
 * <ul>
 *   <li><b>Sample Rate:</b> 16,000 Hz (16 kHz)</li>
 *   <li><b>Bits Per Sample:</b> 16-bit signed integers</li>
 *   <li><b>Channels:</b> 1 (mono)</li>
 *   <li><b>Endianness:</b> Little-endian</li>
 *   <li><b>Byte Rate:</b> 32,000 bytes/second (16,000 samples × 2 bytes)</li>
 *   <li><b>Block Align:</b> 2 bytes per frame</li>
 * </ul>
 *
 * <p>Usage Example:
 * <pre>
 * byte[] pcmData = ...; // Raw PCM from microphone
 * byte[] wavData = WavWriter.toWav(pcmData);
 * // wavData now has 44-byte WAV header + PCM payload
 * </pre>
 *
 * @see com.phillippitts.speaktomack.service.audio.AudioFormat
 * @see com.phillippitts.speaktomack.service.audio.WavWriter
 * @since 1.0
 */
package com.phillippitts.speaktomack.service.audio;
