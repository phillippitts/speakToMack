/**
 * Domain models representing core business entities.
 *
 * <p>This package contains immutable domain objects that follow Domain-Driven Design principles.
 * All domain models are:
 * <ul>
 *   <li>Immutable (using Java records where applicable)</li>
 *   <li>Self-validating (validation in constructors)</li>
 *   <li>Rich in behavior (not anemic data structures)</li>
 *   <li>Independent of persistence concerns (no JPA annotations)</li>
 * </ul>
 *
 * <p>Key domain concepts:
 * <ul>
 *   <li>{@link com.phillippitts.speaktomack.domain.TranscriptionResult} - Result of STT transcription
 *       with text, confidence score, timestamp, and engine metadata</li>
 * </ul>
 *
 * @see com.phillippitts.speaktomack.domain.TranscriptionResult
 * @since 1.0
 */
package com.phillippitts.speaktomack.domain;
