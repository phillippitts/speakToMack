/**
 * Event-driven watchdog that monitors STT engine health and performs bounded auto-restarts.
 *
 * <h2>Overview</h2>
 * <p>This package implements a resilient monitoring system for speech-to-text engines that
 * automatically detects failures and attempts recovery within configurable budget constraints.
 * The watchdog uses Spring's event-driven architecture to react to engine failures without
 * introducing polling overhead.</p>
 *
 * <h2>Components</h2>
 * <ul>
 *   <li>{@link com.phillippitts.speaktomack.service.stt.watchdog.SttEngineWatchdog SttEngineWatchdog} -
 *       Main watchdog component that tracks engine health and performs auto-restart with
 *       sliding window budget tracking</li>
 *   <li>{@link com.phillippitts.speaktomack.service.stt.watchdog.EngineFailureEvent EngineFailureEvent} -
 *       Event published when an STT engine fails during initialization or transcription</li>
 *   <li>{@link com.phillippitts.speaktomack.service.stt.watchdog.EngineRecoveredEvent EngineRecoveredEvent} -
 *       Event published when an engine successfully recovers after restart</li>
 * </ul>
 *
 * <h2>Design Approach</h2>
 * <p><strong>Event-Driven Detection:</strong> Engines publish
 * {@link com.phillippitts.speaktomack.service.stt.watchdog.EngineFailureEvent EngineFailureEvent}
 * when transcription or initialization fails. The watchdog listens for these events and reacts
 * only when failures occur, avoiding the overhead of periodic health probes.</p>
 *
 * <p><strong>Sliding Window Budget:</strong> The watchdog tracks restart attempts per engine in a
 * sliding time window (default: 60 minutes). Each engine is allowed a maximum number of restarts
 * within this window (default: 3 restarts). This prevents infinite restart loops while allowing
 * recovery from transient failures.</p>
 *
 * <p><strong>State Machine:</strong> Engines transition through three states:
 * <ul>
 *   <li><strong>HEALTHY</strong> - Engine is operational and within budget</li>
 *   <li><strong>DEGRADED</strong> - Engine has failed but restart attempts are within budget</li>
 *   <li><strong>DISABLED</strong> - Engine has exceeded restart budget and is in cooldown period</li>
 * </ul>
 *
 * <h2>Configuration</h2>
 * <p>The watchdog is configured via
 * {@link com.phillippitts.speaktomack.config.properties.SttWatchdogProperties SttWatchdogProperties}
 * with the following properties (prefix: {@code stt.watchdog}):</p>
 * <ul>
 *   <li>{@code enabled} - Enable/disable watchdog globally (default: true)</li>
 *   <li>{@code window-minutes} - Sliding window size for restart budget (default: 60)</li>
 *   <li>{@code max-restarts-per-window} - Maximum restarts allowed within window (default: 3)</li>
 *   <li>{@code cooldown-minutes} - Cooldown period after disabling an engine (default: 10)</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Engine publishes failure event
 * publisher.publishEvent(new EngineFailureEvent(
 *     "vosk",
 *     Instant.now(),
 *     "JNI library crashed",
 *     exception,
 *     contextMap
 * ));
 *
 * // Watchdog automatically:
 * // 1. Checks if engine is within restart budget
 * // 2. Attempts to close() and initialize() the engine
 * // 3. Publishes EngineRecoveredEvent on success
 * // 4. Disables engine if budget exceeded
 * }</pre>
 *
 * <h2>Restart Budget Example</h2>
 * <p>With defaults (3 restarts per 60 minutes):</p>
 * <ul>
 *   <li><strong>T+0min:</strong> First failure → restart (1/3)</li>
 *   <li><strong>T+5min:</strong> Second failure → restart (2/3)</li>
 *   <li><strong>T+10min:</strong> Third failure → restart (3/3)</li>
 *   <li><strong>T+15min:</strong> Fourth failure → DISABLED for 10 minutes</li>
 *   <li><strong>T+65min:</strong> First failure falls out of window → budget resets to 2/3</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>All watchdog operations are thread-safe:
 * <ul>
 *   <li>Per-engine {@link java.util.concurrent.locks.ReentrantLock} prevents concurrent restarts</li>
 *   <li>{@link java.util.concurrent.ConcurrentHashMap} for state and timestamp tracking</li>
 *   <li>Sliding window pruning is synchronized per engine</li>
 * </ul>
 *
 * <h2>Observability</h2>
 * <p>The watchdog provides comprehensive logging:
 * <ul>
 *   <li><strong>INFO</strong> - Engine initialization, successful restarts, recovery events</li>
 *   <li><strong>WARN</strong> - Failures, restart attempts, cooldown notifications</li>
 *   <li><strong>ERROR</strong> - Engine disabled after budget exceeded</li>
 *   <li><strong>Scheduled</strong> - Health summary logged every 60 seconds</li>
 * </ul>
 *
 * <h2>Integration with STT Engines</h2>
 * <p>STT engines should publish
 * {@link com.phillippitts.speaktomack.service.stt.watchdog.EngineFailureEvent EngineFailureEvent}
 * during both initialization and transcription failures:</p>
 *
 * <pre>{@code
 * @Component
 * public class VoskSttEngine implements SttEngine {
 *     private final ApplicationEventPublisher publisher;
 *
 *     @Override
 *     public void initialize() {
 *         try {
 *             // Initialize Vosk recognizer
 *         } catch (Exception e) {
 *             if (publisher != null) {
 *                 publisher.publishEvent(new EngineFailureEvent(
 *                     ENGINE_NAME,
 *                     Instant.now(),
 *                     "Initialization failed",
 *                     e,
 *                     Map.of("modelPath", modelPath)
 *                 ));
 *             }
 *             throw new TranscriptionException("Init failed", ENGINE_NAME, e);
 *         }
 *     }
 * }
 * }</pre>
 *
 * <h2>Testing</h2>
 * <p>See {@code SttEngineWatchdogTest} for comprehensive test coverage including:
 * <ul>
 *   <li>Restart within budget scenarios</li>
 *   <li>Budget exhaustion and engine disabling</li>
 *   <li>Cooldown period enforcement</li>
 *   <li>Sliding window expiration</li>
 * </ul>
 *
 * @since 1.0
 * @see com.phillippitts.speaktomack.service.stt.watchdog.SttEngineWatchdog
 * @see com.phillippitts.speaktomack.service.stt.watchdog.EngineFailureEvent
 * @see com.phillippitts.speaktomack.service.stt.watchdog.EngineRecoveredEvent
 * @see com.phillippitts.speaktomack.config.properties.SttWatchdogProperties
 */
package com.phillippitts.speaktomack.service.stt.watchdog;
