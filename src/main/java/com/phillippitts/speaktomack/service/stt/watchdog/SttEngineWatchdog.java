package com.phillippitts.speaktomack.service.stt.watchdog;

import com.phillippitts.speaktomack.config.stt.SttWatchdogProperties;
import com.phillippitts.speaktomack.service.stt.SttEngine;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Event-driven watchdog that observes engine failures and performs bounded auto-restarts with cooldown.
 *
 * Detection model:
 * - Engines publish {@link EngineFailureEvent} when a transcription/initialize fails.
 * - Watchdog tracks failures per engine in a sliding time window and attempts restart within budget.
 * - On exceeding budget, engine is marked DISABLED and re-enable is allowed after cooldown.
 *
 * This keeps runtime overhead minimal (no heavy polling) and reacts only to real traffic failures.
 */
@Component
@ConditionalOnProperty(prefix = "stt.watchdog", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SttEngineWatchdog {

    private static final Logger LOG = LogManager.getLogger(SttEngineWatchdog.class);

    public enum EngineState { HEALTHY, DEGRADED, DISABLED }

    private final SttWatchdogProperties props;
    private final ApplicationEventPublisher publisher;

    private final Map<String, SttEngine> enginesByName = new ConcurrentHashMap<>();

    // Sliding window of restart attempts per engine
    private final ConcurrentMap<String, Deque<Instant>> restartWindow = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, EngineState> state = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Instant> disabledUntil = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ReentrantLock> restartLocks = new ConcurrentHashMap<>();

    public SttEngineWatchdog(List<SttEngine> engines,
                             SttWatchdogProperties props,
                             ApplicationEventPublisher publisher) {
        this.props = Objects.requireNonNull(props, "props");
        this.publisher = Objects.requireNonNull(publisher, "publisher");
        for (SttEngine e : engines) {
            enginesByName.put(e.getEngineName(), e);
            state.put(e.getEngineName(), EngineState.HEALTHY);
            restartLocks.put(e.getEngineName(), new ReentrantLock());
            restartWindow.put(e.getEngineName(), new ArrayDeque<>());
        }
        LOG.info("Watchdog initialized for engines={}", enginesByName.keySet());
    }

    @PostConstruct
    public void initializeEngines() {
        LOG.info("Initializing STT engines at startup...");
        for (Map.Entry<String, SttEngine> entry : enginesByName.entrySet()) {
            String name = entry.getKey();
            SttEngine engine = entry.getValue();
            try {
                engine.initialize();
                LOG.info("Engine {} initialized successfully", name);
            } catch (Exception ex) {
                LOG.error("Failed to initialize engine {} at startup: {}", name, ex.toString());
                state.put(name, EngineState.DISABLED);
            }
        }
    }

    /** Visible for tests */
    EngineState getState(String engine) {
        return state.get(engine);
    }

    /**
     * Checks if an engine is currently enabled (not disabled or in cooldown).
     *
     * <p>Used by orchestrators to query engine availability before routing.
     *
     * @param engine engine name to check
     * @return true if engine is enabled and not in cooldown, false otherwise
     */
    public boolean isEngineEnabled(String engine) {
        EngineState s = state.get(engine);
        if (s == EngineState.DISABLED) {
            return false;
        }
        Instant until = disabledUntil.get(engine);
        return until == null || Instant.now().isAfter(until);
    }

    @EventListener
    public void onFailure(EngineFailureEvent event) {
        String engine = event.engine();
        if (!enginesByName.containsKey(engine)) {
            LOG.warn("EngineFailureEvent for unknown engine: {}", engine);
            return;
        }

        LOG.warn("Engine failure: engine={}, msg={} ", engine, event.message());
        // If currently disabled and cooldown not passed, ignore
        if (!isEngineEnabled(engine)) {
            LOG.warn("Engine {} currently disabled until {}", engine, disabledUntil.get(engine));
            return;
        }

        // Mark degraded on first failure
        state.put(engine, EngineState.DEGRADED);

        // Attempt restart under lock within budget
        attemptRestart(engine);
    }

    @EventListener
    public void onRecovered(EngineRecoveredEvent event) {
        String engine = event.engine();
        if (!enginesByName.containsKey(engine)) {
            return;
        }
        state.put(engine, EngineState.HEALTHY);
        // Clear restart window after successful recovery
        restartWindow.get(engine).clear();
        disabledUntil.remove(engine);
        LOG.info("Engine recovered: {}", engine);
    }

    @Scheduled(fixedRate = 60_000)
    void logHealthSummary() {
        // Lightweight: no JNI probes; just log states and counts
        StringBuilder sb = new StringBuilder("Watchdog states: ");
        state.forEach((name, st) -> sb.append(name).append('=').append(st).append(' '));
        LOG.info(sb.toString().trim());
    }

    /**
     * Attempts to restart a failed engine within budget constraints.
     *
     * <p>State transitions:
     * <ul>
     *   <li>Budget available → restart → HEALTHY (on success) or remain DEGRADED (on failure)</li>
     *   <li>Budget exceeded → DISABLED with cooldown period</li>
     * </ul>
     *
     * @param engine engine name to restart
     */
    private void attemptRestart(String engine) {
        withRestartLock(engine, () -> {
            if (isDisabledByCooldown(engine)) {
                LOG.warn("Engine {} is in cooldown until {}", engine, disabledUntil.get(engine));
                return;
            }
            if (!budgetAllowsRestart(engine)) {
                disableEngine(engine);
                return;
            }
            recordRestartAttempt(engine);
            if (tryRestart(engine)) {
                // Don't transition to HEALTHY or clear window here - let onRecovered() handle it
                // This ensures budget tracking remains accurate even if event delivery is async
                publisher.publishEvent(new EngineRecoveredEvent(engine, Instant.now()));
                LOG.info("Engine {} restarted successfully", engine);
            } else {
                // remain degraded; future failures will trigger further attempts until budget exceeded
                state.put(engine, EngineState.DEGRADED);
                LOG.warn("Engine {} restart failed; remaining in DEGRADED state", engine);
            }
        });
    }

    /** Guard restart with a per-engine lock to avoid concurrent restarts. */
    private void withRestartLock(String engine, Runnable action) {
        ReentrantLock lock = restartLocks.get(engine);
        if (!lock.tryLock()) {
            LOG.debug("Restart already in progress for {}", engine);
            return;
        }
        try {
            action.run();
        } finally {
            lock.unlock();
        }
    }

    /** Returns true when engine is currently disabled and cooldown has not elapsed. */
    private boolean isDisabledByCooldown(String engine) {
        Instant until = disabledUntil.get(engine);
        return until != null && Instant.now().isBefore(until);
    }

    /** Returns true if restart budget allows another attempt (after pruning old attempts). */
    private boolean budgetAllowsRestart(String engine) {
        Deque<Instant> window = restartWindow.get(engine);
        pruneOld(window, props.getWindowMinutes());
        return window.size() < props.getMaxRestartsPerWindow();
    }

    /** Records a restart attempt timestamp in the sliding window. */
    private void recordRestartAttempt(String engine) {
        restartWindow.get(engine).addLast(Instant.now());
    }

    /** Disables engine and sets cooldown timestamp. */
    private void disableEngine(String engine) {
        state.put(engine, EngineState.DISABLED);
        Instant until = Instant.now().plus(Duration.ofMinutes(props.getCooldownMinutes()));
        disabledUntil.put(engine, until);
        LOG.error("Engine {} disabled after {} failures within {}m; cooldown until {}",
                engine, restartWindow.get(engine).size(), props.getWindowMinutes(), until);
    }

    /** Attempts to restart engine: close then initialize. Returns true on success. */
    private boolean tryRestart(String engine) {
        SttEngine e = enginesByName.get(engine);
        try {
            LOG.warn("Restarting engine {}", engine);
            e.close();
        } catch (Exception ex) {
            LOG.debug("Error during engine.close(): {}", ex.toString());
        }
        try {
            e.initialize();
            return true;
        } catch (Exception ex) {
            LOG.error("Engine {} failed to initialize after restart: {}", engine, ex.toString());
            return false;
        }
    }

    private void pruneOld(Deque<Instant> window, int windowMinutes) {
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(windowMinutes));
        while (!window.isEmpty() && window.peekFirst().isBefore(cutoff)) {
            window.removeFirst();
        }
    }
}
