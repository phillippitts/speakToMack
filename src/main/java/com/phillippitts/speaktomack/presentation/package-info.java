/**
 * Presentation layer (REST API controllers and exception handling).
 *
 * <p>This package contains the HTTP/REST boundary of the application, following a
 * 3-tier architecture where presentation depends on service but not vice versa.
 *
 * <p>Architecture:
 * <ul>
 *   <li><b>Controllers</b> - REST endpoints that accept HTTP requests and delegate to services</li>
 *   <li><b>Exception Handlers</b> - Translate domain exceptions to HTTP responses</li>
 *   <li><b>DTOs</b> (future) - Request/response objects for API contracts</li>
 * </ul>
 *
 * <p>Sub-packages:
 * <ul>
 *   <li>{@code presentation.controller} - REST controllers for API endpoints</li>
 *   <li>{@code presentation.exception} - Global exception handling for HTTP responses</li>
 * </ul>
 *
 * <p>Design Principles:
 * <ul>
 *   <li>Controllers are thin adapters - business logic lives in services</li>
 *   <li>Exception handlers map domain exceptions to HTTP status codes</li>
 *   <li>Controllers never throw HTTP-specific exceptions (use domain exceptions)</li>
 *   <li>All controllers are {@code @RestController} (no view rendering)</li>
 * </ul>
 *
 * @see com.phillippitts.speaktomack.presentation.controller
 * @see com.phillippitts.speaktomack.presentation.exception.GlobalExceptionHandler
 * @since 1.0
 */
package com.phillippitts.speaktomack.presentation;
