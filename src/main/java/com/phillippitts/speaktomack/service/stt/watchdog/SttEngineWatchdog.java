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

    /** Visible for tests */
    EngineState getState(String engine) { return state.get(engine); }
    /** Visible for tests */
    boolean isEngineEnabled(String engine) {
        EngineState s = state.get(engine);
        if (s == EngineState.DISABLED) return false;
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
        if (!enginesByName.containsKey(engine)) return;
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

    private void attemptRestart(String engine) {
        ReentrantLock lock = restartLocks.get(engine);
        if (!lock.tryLock()) {
            LOG.debug("Restart already in progress for {}", engine);
            return;
        }
        try {
            // Enforce sliding window budget
            Deque<Instant> window = restartWindow.get(engine);
            Instant now = Instant.now();
            pruneOld(window, props.getWindowMinutes());
            if (window.size() >= props.getMaxRestartsPerWindow()) {
                // Disable with cooldown
                state.put(engine, EngineState.DISABLED);
                Instant until = now.plus(Duration.ofMinutes(props.getCooldownMinutes()));
                disabledUntil.put(engine, until);
                LOG.error("Engine {} disabled after {} failures within {}m; cooldown until {}",
                        engine, window.size(), props.getWindowMinutes(), until);
                return;
            }

            // Perform restart
            window.addLast(now);
            SttEngine e = enginesByName.get(engine);
            try {
                LOG.warn("Restarting engine {}", engine);
                e.close();
            } catch (Exception ex) {
                LOG.debug("Error during engine.close(): {}", ex.toString());
            }
            try {
                e.initialize();
                publisher.publishEvent(new EngineRecoveredEvent(engine, Instant.now()));
            } catch (Exception ex) {
                // keep degraded; let future failures continue counting toward budget
                LOG.error("Engine {} failed to initialize after restart: {}", engine, ex.toString());
            }
        } finally {
            lock.unlock();
        }
    }

    private void pruneOld(Deque<Instant> window, int windowMinutes) {
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(windowMinutes));
        while (!window.isEmpty() && window.peekFirst().isBefore(cutoff)) {
            window.removeFirst();
        }
    }
}
