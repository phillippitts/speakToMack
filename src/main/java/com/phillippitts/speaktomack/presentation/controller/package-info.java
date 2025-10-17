/**
 * REST API controllers for HTTP endpoints.
 *
 * <p>This package contains Spring MVC {@code @RestController} classes that define the
 * HTTP API surface of the application.
 *
 * <p>Current Endpoints:
 * <ul>
 *   <li>{@link com.phillippitts.speaktomack.presentation.controller.PingController}
 *       - Health check endpoint ({@code GET /ping}) for verifying server status
 *       and structured logging (MDC validation)</li>
 * </ul>
 *
 * <p>Planned Endpoints (Phase 3):
 * <ul>
 *   <li>{@code TranscriptionController} - Main transcription API ({@code POST /api/v1/transcribe})</li>
 *   <li>{@code HealthController} - Detailed health checks for STT engines</li>
 * </ul>
 *
 * <p>Controller Responsibilities:
 * <ul>
 *   <li>Accept HTTP requests and extract parameters</li>
 *   <li>Validate request format (basic checks - detailed validation in services)</li>
 *   <li>Delegate to service layer for business logic</li>
 *   <li>Convert service results to HTTP responses</li>
 *   <li>Let {@code GlobalExceptionHandler} handle exceptions</li>
 * </ul>
 *
 * @see com.phillippitts.speaktomack.presentation.exception.GlobalExceptionHandler
 * @since 1.0
 */
package com.phillippitts.speaktomack.presentation.controller;
