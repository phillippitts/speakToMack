/**
 * Application-wide configuration beans and properties.
 *
 * <p>This package contains Spring configuration classes that define beans and load
 * externalized configuration from {@code application.properties}.
 *
 * <p>Configuration Classes:
 * <ul>
 *   <li>{@link com.phillippitts.speaktomack.config.AudioFormatConfig} - Audio format constants
 *       and validation for STT engines (16kHz, 16-bit, mono PCM)</li>
 *   <li>{@link com.phillippitts.speaktomack.config.ThreadPoolConfig} - Thread pool executor
 *       configuration for asynchronous transcription tasks</li>
 * </ul>
 *
 * <p>Sub-packages:
 * <ul>
 *   <li>{@code config.stt} - STT engine-specific configuration properties</li>
 *   <li>{@code config.logging} - Logging infrastructure configuration (MDC filters)</li>
 * </ul>
 *
 * @see com.phillippitts.speaktomack.config.AudioFormatConfig
 * @see com.phillippitts.speaktomack.config.ThreadPoolConfig
 * @see com.phillippitts.speaktomack.config.stt
 * @since 1.0
 */
package com.phillippitts.speaktomack.config;
