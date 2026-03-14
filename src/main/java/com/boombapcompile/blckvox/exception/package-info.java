/**
 * Application-specific exception hierarchy.
 *
 * <p>This package defines a structured exception hierarchy that follows Spring's exception
 * translation patterns. All exceptions extend from a common base to enable consistent
 * error handling and HTTP response mapping.
 *
 * <p>Exception Hierarchy:
 * <ul>
 *   <li>{@link com.boombapcompile.blckvox.exception.BlckvoxException} - Base exception
 *       for all application-specific errors</li>
 *   <li>{@link com.boombapcompile.blckvox.exception.InvalidAudioException} - Thrown when
 *       audio data fails validation (wrong format, duration, or size)</li>
 *   <li>{@link com.boombapcompile.blckvox.exception.ModelNotFoundException} - Thrown when
 *       required STT model files are missing at startup</li>
 *   <li>{@link com.boombapcompile.blckvox.exception.TranscriptionException} - Thrown when
 *       STT engine fails to transcribe audio (crash, timeout, or invalid output)</li>
 * </ul>
 *
 * <p>All exceptions:
 * <ul>
 *   <li>Extend {@code BlckvoxException} for unified handling</li>
 *   <li>Support exception chaining via {@code cause} parameter</li>
 *   <li>Include context fields (e.g., {@code engineName} in {@code TranscriptionException})</li>
 *   <li>Map to appropriate HTTP status codes via a global exception handler</li>
 * </ul>
 *
 * @see com.boombapcompile.blckvox.exception.BlckvoxException
 * @since 1.0
 */
package com.boombapcompile.blckvox.exception;
