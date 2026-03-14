/**
 * Application-wide configuration beans and properties.
 *
 * <p>This package contains Spring configuration classes that define beans and load
 * externalized configuration from {@code application.properties}.
 *
 * <p>Configuration Classes:
 * <ul>
 *   <li>{@link com.boombapcompile.blckvox.config.AudioFormatConfig} - Audio format constants
 *       and validation for STT engines (16kHz, 16-bit, mono PCM)</li>
 *   <li>{@link com.boombapcompile.blckvox.config.ThreadPoolConfig} - Thread pool executor
 *       configuration for asynchronous transcription tasks</li>
 * </ul>
 *
 * <p>Sub-packages:
 * <ul>
 *   <li>{@code config.stt} - STT engine-specific configuration properties</li>
 *   <li>{@code config.logging} - Logging infrastructure configuration (MDC filters)</li>
 * </ul>
 *
 * @see com.boombapcompile.blckvox.config.AudioFormatConfig
 * @see com.boombapcompile.blckvox.config.ThreadPoolConfig
 * @see com.boombapcompile.blckvox.config.stt
 * @since 1.0
 */
package com.boombapcompile.blckvox.config;
