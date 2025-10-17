/**
 * Global exception handling for REST API responses.
 *
 * <p>This package contains Spring MVC {@code @ControllerAdvice} classes that intercept
 * exceptions thrown from controllers and convert them to appropriate HTTP responses.
 *
 * <p>Key Components:
 * <ul>
 *   <li>{@link com.phillippitts.speaktomack.presentation.exception.GlobalExceptionHandler}
 *       - Centralized exception-to-HTTP mapping with structured {@code ApiError} responses</li>
 * </ul>
 *
 * <p>Exception Mapping:
 * <ul>
 *   <li>{@link com.phillippitts.speaktomack.exception.InvalidAudioException} → 400 Bad Request</li>
 *   <li>{@link com.phillippitts.speaktomack.exception.ModelNotFoundException} → 503 Service Unavailable</li>
 *   <li>{@link com.phillippitts.speaktomack.exception.TranscriptionException} → 503 Service Unavailable (retry)</li>
 *   <li>{@code Exception} (catch-all) → 500 Internal Server Error</li>
 * </ul>
 *
 * <p>Response Format:
 * <pre>
 * {
 *   "errorCode": "InvalidAudioException",
 *   "message": "Invalid audio format",
 *   "details": "Audio duration 50ms is below minimum 250ms",
 *   "timestamp": "2025-10-17T15:42:32.529Z"
 * }
 * </pre>
 *
 * <p>Design Principles:
 * <ul>
 *   <li>Never expose stack traces or internal details to clients</li>
 *   <li>Log detailed errors server-side for debugging</li>
 *   <li>Return actionable messages for client-side errors (4xx)</li>
 *   <li>Return retry guidance for transient errors (503)</li>
 * </ul>
 *
 * @see com.phillippitts.speaktomack.exception
 * @since 1.0
 */
package com.phillippitts.speaktomack.presentation.exception;
