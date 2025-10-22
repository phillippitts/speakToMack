package com.phillippitts.speaktomack.service.orchestration;

import java.util.UUID;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe state machine for managing audio capture session lifecycle.
 *
 * <p>This class encapsulates the state transitions for capture sessions,
 * ensuring only one session can be active at a time. It uses explicit locking
 * instead of synchronized blocks for better clarity and control.
 *
 * <p><b>State Transitions:</b>
 * <pre>
 * IDLE → CAPTURING (via startCapture)
 * CAPTURING → IDLE (via stopCapture or cancelCapture)
 * </pre>
 *
 * <p><b>Thread Safety:</b> All public methods are thread-safe and use a
 * {@link ReentrantLock} to protect state transitions.
 *
 * @since 1.0
 */
public final class CaptureStateMachine {

    private final Lock lock = new ReentrantLock();
    private UUID activeSession;

    /**
     * Attempts to start a new capture session.
     *
     * @param sessionId unique identifier for the new session
     * @return {@code true} if session started successfully,
     *         {@code false} if another session is already active
     * @throws NullPointerException if sessionId is null
     */
    public boolean startCapture(UUID sessionId) {
        if (sessionId == null) {
            throw new NullPointerException("sessionId cannot be null");
        }

        lock.lock();
        try {
            if (activeSession != null) {
                return false; // Already capturing
            }
            activeSession = sessionId;
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Stops the current capture session if it matches the expected session ID.
     *
     * @param expectedSessionId the session ID to stop
     * @return {@code true} if session was stopped,
     *         {@code false} if no session active or ID mismatch
     */
    public boolean stopCapture(UUID expectedSessionId) {
        lock.lock();
        try {
            if (activeSession == null || !activeSession.equals(expectedSessionId)) {
                return false;
            }
            activeSession = null;
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Cancels any active capture session regardless of session ID.
     *
     * <p>This is used for error handling when capture fails.
     *
     * @return the cancelled session ID, or {@code null} if no session was active
     */
    public UUID cancelCapture() {
        lock.lock();
        try {
            UUID cancelled = activeSession;
            activeSession = null;
            return cancelled;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the currently active session ID.
     *
     * @return active session ID, or {@code null} if no session is active
     */
    public UUID getActiveSession() {
        lock.lock();
        try {
            return activeSession;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Checks if a capture session is currently active.
     *
     * @return {@code true} if capturing, {@code false} otherwise
     */
    public boolean isActive() {
        lock.lock();
        try {
            return activeSession != null;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Checks if a specific session is currently active.
     *
     * @param sessionId session ID to check
     * @return {@code true} if the given session is active, {@code false} otherwise
     */
    public boolean isSessionActive(UUID sessionId) {
        if (sessionId == null) {
            return false;
        }
        lock.lock();
        try {
            return sessionId.equals(activeSession);
        } finally {
            lock.unlock();
        }
    }
}
