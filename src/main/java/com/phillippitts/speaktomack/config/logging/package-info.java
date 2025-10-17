/**
 * Logging infrastructure and MDC (Mapped Diagnostic Context) configuration.
 *
 * <p>This package provides structured logging capabilities using Log4j2 with MDC for
 * request correlation and tracing across asynchronous boundaries.
 *
 * <p>Key Components:
 * <ul>
 *   <li>{@link com.phillippitts.speaktomack.config.logging.MdcFilter} - Servlet filter
 *       that injects {@code requestId} into MDC for every HTTP request, enabling
 *       correlated log tracing</li>
 * </ul>
 *
 * <p>MDC Keys:
 * <ul>
 *   <li>{@code requestId} - Unique identifier for each HTTP request (UUID format)</li>
 *   <li>{@code engineName} - STT engine name (vosk/whisper) added during transcription</li>
 * </ul>
 *
 * <p>Log Format:
 * <pre>
 * 2025-10-17 15:42:32.529 [thread-name] [requestId] [engineName] LEVEL logger.name - message
 * </pre>
 *
 * @see com.phillippitts.speaktomack.config.logging.MdcFilter
 * @see org.slf4j.MDC
 * @since 1.0
 */
package com.phillippitts.speaktomack.config.logging;
