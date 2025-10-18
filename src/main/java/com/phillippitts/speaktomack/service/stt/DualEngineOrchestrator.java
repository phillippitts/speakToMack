package com.phillippitts.speaktomack.service.stt;

import com.phillippitts.speaktomack.domain.TranscriptionResult;
import com.phillippitts.speaktomack.exception.TranscriptionException;
import com.phillippitts.speaktomack.service.stt.watchdog.SttEngineWatchdog;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Objects;

/**
 * Lightweight orchestrator that coordinates Vosk (fast) and Whisper (accurate) engines
 * with automatic fallback based on watchdog health state.
 *
 * <p>Strategy:
 * <ul>
 *   <li>Primary: Vosk (fast JNI-based engine for low-latency transcription)</li>
 *   <li>Fallback: Whisper (accurate process-based engine when Vosk is degraded/disabled)</li>
 *   <li>Health-aware: Consults {@link SttEngineWatchdog} before routing requests</li>
 * </ul>
 *
 * <p>This is a minimal Phase 3 implementation. Future enhancements:
 * <ul>
 *   <li>Parallel dual-engine mode with consensus/voting</li>
 *   <li>Adaptive routing based on audio characteristics</li>
 *   <li>Confidence-based retry with alternate engine</li>
 * </ul>
 *
 * <p>Not annotated with @Component - intended for manual instantiation when dual-engine
 * coordination is needed. This prevents bean ambiguity issues when multiple SttEngine
 * implementations are present in the Spring context.
 *
 * <p>Thread-safe: All operations delegate to thread-safe engine implementations.
 */
public class DualEngineOrchestrator implements SttEngine {

    private static final Logger LOG = LogManager.getLogger(DualEngineOrchestrator.class);
    private static final String ENGINE_NAME = "dual-orchestrator";

    private final SttEngine voskEngine;
    private final SttEngine whisperEngine;
    private final SttEngineWatchdog watchdog;

    /**
     * Creates orchestrator with Vosk and Whisper engines.
     *
     * @param voskEngine primary fast engine
     * @param whisperEngine fallback accurate engine
     * @param watchdog health monitor for engine state
     */
    public DualEngineOrchestrator(SttEngine voskEngine,
                                   SttEngine whisperEngine,
                                   SttEngineWatchdog watchdog) {
        this.voskEngine = Objects.requireNonNull(voskEngine, "voskEngine");
        this.whisperEngine = Objects.requireNonNull(whisperEngine, "whisperEngine");
        this.watchdog = Objects.requireNonNull(watchdog, "watchdog");
    }

    @Override
    public void initialize() {
        // Engines are already initialized by Spring
        LOG.info("DualEngineOrchestrator initialized (vosk={}, whisper={})",
                voskEngine.isHealthy(), whisperEngine.isHealthy());
    }

    /**
     * Transcribes audio using Vosk (primary) with Whisper fallback based on watchdog state.
     *
     * <p>Routing logic:
     * <ol>
     *   <li>If Vosk is healthy → use Vosk (fast path)</li>
     *   <li>If Vosk is degraded/disabled and Whisper is healthy → use Whisper (fallback)</li>
     *   <li>If both disabled → throw TranscriptionException</li>
     * </ol>
     *
     * @param audioData PCM audio data (16kHz, 16-bit, mono)
     * @return transcription result from selected engine
     * @throws TranscriptionException if all engines are disabled or transcription fails
     */
    @Override
    public TranscriptionResult transcribe(byte[] audioData) {
        if (audioData == null || audioData.length == 0) {
            throw new IllegalArgumentException("audioData must not be null or empty");
        }

        // Query watchdog for current engine health
        boolean voskEnabled = watchdog.isEngineEnabled("vosk");
        boolean whisperEnabled = watchdog.isEngineEnabled("whisper");

        // Route to primary (Vosk) if healthy
        if (voskEnabled && voskEngine.isHealthy()) {
            LOG.debug("Routing to Vosk (primary)");
            return voskEngine.transcribe(audioData);
        }

        // Fallback to Whisper if Vosk unavailable
        if (whisperEnabled && whisperEngine.isHealthy()) {
            LOG.info("Vosk unavailable (enabled={}, healthy={}), falling back to Whisper",
                    voskEnabled, voskEngine.isHealthy());
            return whisperEngine.transcribe(audioData);
        }

        // Both engines disabled - fail
        throw new TranscriptionException(
                "All STT engines disabled (vosk.enabled=" + voskEnabled
                + ", vosk.healthy=" + voskEngine.isHealthy()
                + ", whisper.enabled=" + whisperEnabled
                + ", whisper.healthy=" + whisperEngine.isHealthy() + ")",
                ENGINE_NAME);
    }

    @Override
    public String getEngineName() {
        return ENGINE_NAME;
    }

    /**
     * Orchestrator is healthy if at least one underlying engine is available.
     *
     * @return true if Vosk OR Whisper is healthy and enabled
     */
    @Override
    public boolean isHealthy() {
        boolean voskReady = watchdog.isEngineEnabled("vosk") && voskEngine.isHealthy();
        boolean whisperReady = watchdog.isEngineEnabled("whisper") && whisperEngine.isHealthy();
        return voskReady || whisperReady;
    }

    /**
     * Delegates close to underlying engines (managed by Spring lifecycle).
     */
    @Override
    public void close() {
        // Engines are Spring-managed with @PreDestroy, so no explicit cleanup needed here
        LOG.info("DualEngineOrchestrator closed");
    }
}
