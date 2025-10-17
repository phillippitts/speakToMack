/**
 * Application-specific exception hierarchy.
 *
 * <p>This package defines a structured exception hierarchy that follows Spring's exception
 * translation patterns. All exceptions extend from a common base to enable consistent
 * error handling and HTTP response mapping.
 *
 * <p>Exception Hierarchy:
 * <ul>
 *   <li>{@link com.phillippitts.speaktomack.exception.SpeakToMackException} - Base exception
 *       for all application-specific errors</li>
 *   <li>{@link com.phillippitts.speaktomack.exception.InvalidAudioException} - Thrown when
 *       audio data fails validation (wrong format, duration, or size)</li>
 *   <li>{@link com.phillippitts.speaktomack.exception.ModelNotFoundException} - Thrown when
 *       required STT model files are missing at startup</li>
 *   <li>{@link com.phillippitts.speaktomack.exception.TranscriptionException} - Thrown when
 *       STT engine fails to transcribe audio (crash, timeout, or invalid output)</li>
 * </ul>
 *
 * <p>All exceptions:
 * <ul>
 *   <li>Extend {@code SpeakToMackException} for unified handling</li>
 *   <li>Support exception chaining via {@code cause} parameter</li>
 *   <li>Include context fields (e.g., {@code engineName} in {@code TranscriptionException})</li>
 *   <li>Map to appropriate HTTP status codes via {@code GlobalExceptionHandler}</li>
 * </ul>
 *
 * @see com.phillippitts.speaktomack.exception.SpeakToMackException
 * @see com.phillippitts.speaktomack.presentation.exception.GlobalExceptionHandler
 * @since 1.0
 */
package com.phillippitts.speaktomack.exception;
