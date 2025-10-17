/**
 * Input validation services for audio data.
 *
 * <p>This package provides validation logic for audio inputs before they reach STT
 * engines, ensuring data quality and preventing resource exhaustion.
 *
 * <p>Key Components:
 * <ul>
 *   <li>{@link com.phillippitts.speaktomack.service.validation.AudioValidator} - Service
 *       that validates audio byte arrays against duration and size constraints</li>
 *   <li>{@link com.phillippitts.speaktomack.service.validation.AudioValidationProperties} -
 *       Configuration properties bound from {@code audio.validation.*} settings</li>
 * </ul>
 *
 * <p>Validation Rules:
 * <ul>
 *   <li><b>Minimum Duration:</b> 250ms (prevents processing of accidental clicks)</li>
 *   <li><b>Maximum Duration:</b> 300,000ms (5 minutes, prevents memory exhaustion)</li>
 *   <li><b>Format Check:</b> Validates PCM format assumptions (16kHz, 16-bit, mono)</li>
 *   <li><b>Null/Empty Check:</b> Rejects null or empty audio arrays</li>
 * </ul>
 *
 * <p>Configuration (application.properties):
 * <pre>
 * audio.validation.min-duration-ms=250
 * audio.validation.max-duration-ms=300000
 * </pre>
 *
 * <p>Usage Example:
 * <pre>
 * &#64;Component
 * public class TranscriptionService {
 *     private final AudioValidator validator;
 *
 *     public TranscriptionResult transcribe(byte[] audio) {
 *         validator.validate(audio); // Throws InvalidAudioException if invalid
 *         // ... proceed with transcription
 *     }
 * }
 * </pre>
 *
 * <p>Exception Handling:
 * <ul>
 *   <li>Throws {@link com.phillippitts.speaktomack.exception.InvalidAudioException}
 *       with detailed reason (too short, too long, invalid format)</li>
 *   <li>Maps to HTTP 400 Bad Request via {@code GlobalExceptionHandler}</li>
 * </ul>
 *
 * @see com.phillippitts.speaktomack.service.validation.AudioValidator
 * @see com.phillippitts.speaktomack.exception.InvalidAudioException
 * @see com.phillippitts.speaktomack.service.audio.AudioFormat
 * @since 1.0
 */
package com.phillippitts.speaktomack.service.validation;
