/**
 * Service layer containing business logic and domain services.
 *
 * <p>This package serves as the root for all service-layer components, following a
 * 3-tier architecture where services implement business logic and orchestrate
 * interactions between domain models, persistence, and external systems.
 *
 * <p>Service Sub-packages:
 * <ul>
 *   <li>{@code service.stt} - Speech-to-Text engine abstractions and implementations
 *       (Vosk, Whisper, orchestration)</li>
 *   <li>{@code service.audio} - Audio format utilities (PCM→WAV conversion, format constants)</li>
 *   <li>{@code service.validation} - Input validation services (audio format, duration, size)</li>
 * </ul>
 *
 * <p>Design Principles:
 * <ul>
 *   <li>Services are stateless Spring beans ({@code @Component}, {@code @Service})</li>
 *   <li>Services depend on domain models, not presentation layer</li>
 *   <li>Services throw domain exceptions (not HTTP exceptions)</li>
 *   <li>Services are thread-safe for concurrent requests</li>
 *   <li>Services use constructor injection (not field injection)</li>
 * </ul>
 *
 * @see com.phillippitts.speaktomack.service.stt
 * @see com.phillippitts.speaktomack.service.audio
 * @see com.phillippitts.speaktomack.service.validation
 * @since 1.0
 */
package com.phillippitts.speaktomack.service;
