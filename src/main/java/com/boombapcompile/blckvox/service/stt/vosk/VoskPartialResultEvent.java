package com.boombapcompile.blckvox.service.stt.vosk;

/**
 * Published when Vosk streaming recognition produces a result.
 *
 * @param text the recognized text (partial or final)
 * @param isFinal true if this is a completed utterance, false if still in progress
 */
public record VoskPartialResultEvent(String text, boolean isFinal) { }
