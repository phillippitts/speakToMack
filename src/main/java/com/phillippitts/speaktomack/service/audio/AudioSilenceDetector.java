package com.phillippitts.speaktomack.service.audio;

import java.util.ArrayList;
import java.util.List;

/**
 * Detects silence regions in PCM16LE audio using Voice Activity Detection (VAD).
 *
 * <p>This utility analyzes raw PCM audio buffers to identify continuous silence regions
 * that exceed a configurable threshold. It uses RMS (Root Mean Square) amplitude analysis
 * in sliding time windows to distinguish speech from silence.
 *
 * <p><b>Algorithm:</b>
 * <ol>
 *   <li>Divide audio into small time windows (e.g., 20ms chunks)</li>
 *   <li>Calculate RMS amplitude for each window</li>
 *   <li>Compare RMS to silence threshold (configurable, typically 500-1000 for 16-bit PCM)</li>
 *   <li>Track continuous silence regions</li>
 *   <li>Return boundaries of silence gaps exceeding the minimum duration</li>
 * </ol>
 *
 * <p><b>Use Case:</b> Enables pause-based paragraph detection for Vosk transcription,
 * which doesn't provide timestamp data like Whisper does.
 *
 * <p><b>Audio Format:</b> Expects PCM16LE mono audio (16-bit signed little-endian, mono channel).
 *
 * @since 1.0
 */
public final class AudioSilenceDetector {

    private AudioSilenceDetector() {
        // Utility class
    }

    /**
     * Default RMS amplitude threshold for silence detection.
     * Values below this are considered silence in 16-bit PCM audio.
     */
    private static final int DEFAULT_SILENCE_THRESHOLD = 800;

    /**
     * Default window size for RMS analysis (milliseconds).
     * Smaller = more granular detection but more CPU; larger = smoother but less precise.
     */
    private static final int DEFAULT_WINDOW_MS = 20;

    /**
     * Detects silence regions in PCM audio and returns their byte positions.
     *
     * <p>Analyzes the audio buffer using RMS amplitude in sliding windows to identify
     * continuous silence regions exceeding the minimum gap duration.
     *
     * @param pcmData PCM16LE mono audio buffer (16-bit signed little-endian)
     * @param silenceGapMs minimum silence duration to detect (milliseconds)
     * @param sampleRate audio sample rate (typically 16000 Hz)
     * @return list of byte positions marking the END of each detected silence region
     *         (empty list if no silence detected or invalid input)
     */
    public static List<Integer> detectSilenceBoundaries(byte[] pcmData, int silenceGapMs, int sampleRate) {
        return detectSilenceBoundaries(pcmData, silenceGapMs, sampleRate, DEFAULT_SILENCE_THRESHOLD);
    }

    /**
     * Detects silence regions in PCM audio with custom threshold.
     *
     * @param pcmData PCM16LE mono audio buffer
     * @param silenceGapMs minimum silence duration to detect (milliseconds)
     * @param sampleRate audio sample rate (typically 16000 Hz)
     * @param silenceThreshold RMS amplitude threshold for silence (0-32767 for 16-bit PCM)
     * @return list of byte positions marking the END of each detected silence region
     */
    public static List<Integer> detectSilenceBoundaries(
            byte[] pcmData,
            int silenceGapMs,
            int sampleRate,
            int silenceThreshold) {

        List<Integer> boundaries = new ArrayList<>();

        if (pcmData == null || pcmData.length < 2 || silenceGapMs <= 0 || sampleRate <= 0) {
            return boundaries;
        }

        // Calculate window size in bytes (16-bit = 2 bytes per sample)
        int windowBytes = calculateWindowBytes(DEFAULT_WINDOW_MS, sampleRate);
        int minSilenceBytes = calculateWindowBytes(silenceGapMs, sampleRate);

        int silenceStart = -1;
        int pos = 0;

        while (pos + windowBytes <= pcmData.length) {
            double rms = calculateRMS(pcmData, pos, windowBytes);

            if (rms < silenceThreshold) {
                // Silence detected
                if (silenceStart == -1) {
                    silenceStart = pos; // Start tracking silence
                }
            } else {
                // Speech detected
                if (silenceStart != -1) {
                    // Check if silence duration exceeds threshold
                    int silenceBytes = pos - silenceStart;
                    if (silenceBytes >= minSilenceBytes) {
                        boundaries.add(pos); // Mark end of silence region
                    }
                    silenceStart = -1; // Reset
                }
            }

            pos += windowBytes;
        }

        // Handle trailing silence
        if (silenceStart != -1 && (pcmData.length - silenceStart) >= minSilenceBytes) {
            boundaries.add(pcmData.length);
        }

        return boundaries;
    }

    /**
     * Calculates the number of bytes corresponding to a time duration.
     *
     * @param durationMs time duration in milliseconds
     * @param sampleRate audio sample rate in Hz
     * @return number of bytes (aligned to 2-byte samples)
     */
    private static int calculateWindowBytes(int durationMs, int sampleRate) {
        int samples = (sampleRate * durationMs) / 1000;
        return samples * 2; // 2 bytes per sample (16-bit PCM)
    }

    /**
     * Calculates RMS (Root Mean Square) amplitude for a PCM audio window.
     *
     * <p>RMS provides a measure of the audio signal's energy. Higher RMS indicates
     * louder audio (speech), lower RMS indicates quiet audio (silence).
     *
     * @param pcmData PCM16LE audio buffer
     * @param offset starting byte position
     * @param length number of bytes to analyze (must be even)
     * @return RMS amplitude (0-32767 range for 16-bit PCM)
     */
    private static double calculateRMS(byte[] pcmData, int offset, int length) {
        long sumSquares = 0;
        int sampleCount = 0;

        // Process 16-bit samples (2 bytes each, little-endian)
        for (int i = offset; i + 1 < offset + length && i + 1 < pcmData.length; i += 2) {
            // Convert 2 bytes to 16-bit signed sample (little-endian)
            int sample = (pcmData[i] & 0xFF) | (pcmData[i + 1] << 8);
            sumSquares += (long) sample * sample;
            sampleCount++;
        }

        if (sampleCount == 0) {
            return 0;
        }

        return Math.sqrt((double) sumSquares / sampleCount);
    }
}
