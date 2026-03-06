package com.phillippitts.speaktomack.service.stt.watchdog;

import com.phillippitts.speaktomack.config.properties.SttWatchdogProperties;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Tracks restart attempts per engine in a sliding time window and enforces a budget.
 *
 * <p>Thread-safe via per-engine {@link ReentrantLock}s.
 */
public class RestartBudgetTracker {

    private final int windowMinutes;
    private final int maxRestartsPerWindow;
    private final int cooldownMinutes;

    private final ConcurrentMap<String, Deque<Instant>> restartWindow = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Instant> disabledUntil = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public RestartBudgetTracker(SttWatchdogProperties props) {
        Objects.requireNonNull(props, "props");
        this.windowMinutes = props.getWindowMinutes();
        this.maxRestartsPerWindow = props.getMaxRestartsPerWindow();
        this.cooldownMinutes = props.getCooldownMinutes();
    }

    /** Registers an engine for tracking. Must be called before any other method for this engine. */
    public void register(String engine) {
        restartWindow.put(engine, new ArrayDeque<>());
        locks.put(engine, new ReentrantLock());
    }

    /**
     * Returns true if the budget allows another restart attempt for this engine.
     * Prunes expired entries from the sliding window.
     */
    public boolean allowsRestart(String engine) {
        ReentrantLock lock = locks.get(engine);
        lock.lock();
        try {
            Deque<Instant> window = restartWindow.get(engine);
            pruneOld(window);
            return window.size() < maxRestartsPerWindow;
        } finally {
            lock.unlock();
        }
    }

    /** Records a restart attempt for the given engine. */
    public void recordRestart(String engine) {
        ReentrantLock lock = locks.get(engine);
        lock.lock();
        try {
            restartWindow.get(engine).addLast(Instant.now());
        } finally {
            lock.unlock();
        }
    }

    /** Marks the engine as disabled with a cooldown period. Returns the cooldown expiry. */
    public Instant disable(String engine) {
        Instant until = Instant.now().plus(Duration.ofMinutes(cooldownMinutes));
        disabledUntil.put(engine, until);
        return until;
    }

    /** Clears cooldown and restart window for the engine. */
    public void clearOnRecovery(String engine) {
        ReentrantLock lock = locks.get(engine);
        lock.lock();
        try {
            restartWindow.get(engine).clear();
        } finally {
            lock.unlock();
        }
        disabledUntil.remove(engine);
    }

    /** Returns true if the engine is currently in its cooldown period. */
    public boolean isInCooldown(String engine) {
        Instant until = disabledUntil.get(engine);
        return until != null && Instant.now().isBefore(until);
    }

    /** Returns the cooldown expiry timestamp, or null if not in cooldown. */
    public Instant getCooldownUntil(String engine) {
        return disabledUntil.get(engine);
    }

    /**
     * Attempts to acquire the per-engine restart lock (non-blocking).
     * Returns true if the lock was acquired. Caller MUST call {@link #unlockRestart} when done.
     */
    public boolean tryLockRestart(String engine) {
        return locks.get(engine).tryLock();
    }

    /** Releases the per-engine restart lock. */
    public void unlockRestart(String engine) {
        locks.get(engine).unlock();
    }

    /** Returns the current restart count within the window (for logging). */
    public int getRestartCount(String engine) {
        ReentrantLock lock = locks.get(engine);
        lock.lock();
        try {
            Deque<Instant> window = restartWindow.get(engine);
            pruneOld(window);
            return window.size();
        } finally {
            lock.unlock();
        }
    }

    private void pruneOld(Deque<Instant> window) {
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(windowMinutes));
        while (!window.isEmpty() && window.peekFirst().isBefore(cutoff)) {
            window.removeFirst();
        }
    }
}
