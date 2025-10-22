package com.phillippitts.speaktomack.service.stt;

import com.phillippitts.speaktomack.exception.TranscriptionException;
import com.phillippitts.speaktomack.service.stt.util.EngineEventPublisher;
import jakarta.annotation.PreDestroy;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Map;

/**
 * Abstract base class for STT engine implementations providing common lifecycle and state management.
 *
 * <p>This class implements the Template Method pattern, providing a thread-safe framework for
 * engine initialization, health checking, and resource cleanup while allowing subclasses to
 * customize engine-specific behavior.
 *
 * <p><b>Thread Safety:</b> This class is thread-safe. All state-modifying operations are
 * synchronized on an internal lock. Subclasses must ensure their implementations of
 * {@link #doInitialize()} and {@link #doClose()} are thread-safe when called within
 * the synchronized context.
 *
 * <p><b>Lifecycle:</b>
 * <ol>
 *   <li><b>Uninitialized:</b> Engine created but not yet initialized</li>
 *   <li><b>Initialized:</b> {@link #initialize()} called successfully</li>
 *   <li><b>Closed:</b> {@link #close()} called, engine no longer usable</li>
 * </ol>
 *
 * <p><b>Idempotency:</b> Both {@link #initialize()} and {@link #close()} are idempotent -
 * multiple calls are safe and will not cause errors or duplicate operations.
 *
 * <p><b>Subclass Responsibilities:</b>
 * Subclasses must implement:
 * <ul>
 *   <li>{@link #doInitialize()} - Engine-specific initialization logic</li>
 *   <li>{@link #doClose()} - Engine-specific cleanup logic</li>
 *   <li>{@link #transcribe(byte[])} - Core transcription functionality</li>
 *   <li>{@link #getEngineName()} - Engine identifier</li>
 * </ul>
 *
 * @since 1.1
 * @see SttEngine
 * @see com.phillippitts.speaktomack.service.stt.vosk.VoskSttEngine
 * @see com.phillippitts.speaktomack.service.stt.whisper.WhisperSttEngine
 */
public abstract class AbstractSttEngine implements SttEngine {

    /**
     * Lock for synchronizing state transitions and ensuring thread-safety.
     * All access to {@link #initialized} and {@link #closed} must be synchronized on this lock.
     */
    protected final Object lock = new Object();

    /**
     * Tracks whether the engine has been successfully initialized.
     * Access must be synchronized on {@link #lock}.
     */
    protected boolean initialized = false;

    /**
     * Tracks whether the engine has been closed and is no longer usable.
     * Access must be synchronized on {@link #lock}.
     */
    protected boolean closed = false;

    /**
     * Initializes the STT engine using the Template Method pattern.
     *
     * <p>This method is idempotent - multiple calls will not re-initialize the engine.
     * Once closed, the engine cannot be reinitialized unless the subclass explicitly
     * supports reinitialization by resetting the {@code closed} flag in {@link #doInitialize()}.
     *
     * <p><b>Thread-safe:</b> Synchronized to prevent concurrent initialization.
     *
     * <p><b>Template Method:</b> Calls {@link #doInitialize()} for engine-specific logic.
     *
     * @throws TranscriptionException if initialization fails
     */
    @Override
    public final void initialize() {
        synchronized (lock) {
            if (initialized && !closed) {
                return; // Already initialized and not closed
            }
            doInitialize();
            initialized = true;
        }
    }

    /**
     * Engine-specific initialization logic.
     *
     * <p>Called by {@link #initialize()} within a synchronized block. Subclasses should
     * perform all engine-specific initialization here (e.g., loading models, validating
     * configuration, initializing native libraries).
     *
     * <p><b>Contract:</b>
     * <ul>
     *   <li>Must be idempotent if {@link #initialize()} can be called multiple times</li>
     *   <li>Must throw {@link TranscriptionException} on failure</li>
     *   <li>Should log appropriate messages for initialization progress/completion</li>
     *   <li>If supporting reinitialization after close, must reset {@code closed = false}</li>
     * </ul>
     *
     * <p><b>Thread Safety:</b> Called within synchronized context - implementations do not
     * need additional synchronization for state fields.
     *
     * @throws TranscriptionException if initialization fails
     */
    protected abstract void doInitialize();

    /**
     * Checks if the engine is healthy and ready to transcribe.
     *
     * <p>Returns {@code true} if the engine has been initialized, is not closed, and is
     * ready to process transcription requests.
     *
     * <p><b>Thread-safe:</b> Synchronized read of engine state.
     *
     * @return true if engine is initialized and not closed, false otherwise
     */
    @Override
    public final boolean isHealthy() {
        synchronized (lock) {
            return initialized && !closed;
        }
    }

    /**
     * Closes the STT engine and releases all resources using the Template Method pattern.
     *
     * <p>This method is idempotent - multiple calls are safe and will only close resources once.
     *
     * <p><b>Thread-safe:</b> Synchronized to prevent concurrent closure.
     *
     * <p><b>Template Method:</b> Calls {@link #doClose()} for engine-specific cleanup.
     *
     * <p><b>Lifecycle:</b> Automatically invoked by Spring container on shutdown via
     * {@link PreDestroy} annotation.
     */
    @Override
    @PreDestroy
    public final void close() {
        synchronized (lock) {
            if (closed) {
                return; // Already closed
            }
            doClose();
            closed = true;
            initialized = false;
        }
    }

    /**
     * Engine-specific cleanup logic.
     *
     * <p>Called by {@link #close()} within a synchronized block. Subclasses should
     * release all engine-specific resources here (e.g., close native libraries,
     * free models, terminate processes).
     *
     * <p><b>Contract:</b>
     * <ul>
     *   <li>Must be idempotent - safe to call multiple times</li>
     *   <li>Should never throw exceptions - log errors instead</li>
     *   <li>Should gracefully handle partially-initialized state</li>
     *   <li>Should log appropriate messages for cleanup progress/completion</li>
     * </ul>
     *
     * <p><b>Thread Safety:</b> Called within synchronized context - implementations do not
     * need additional synchronization for state fields.
     */
    protected abstract void doClose();

    /**
     * Validates that the engine is initialized and ready for transcription.
     *
     * <p>Utility method for subclasses to call at the start of {@link #transcribe(byte[])}
     * to ensure the engine is in a valid state.
     *
     * <p><b>Thread-safe:</b> Synchronized read of engine state.
     *
     * @throws TranscriptionException if engine is not initialized or is closed
     */
    protected final void ensureInitialized() {
        synchronized (lock) {
            if (!initialized || closed) {
                throw new TranscriptionException(
                    getEngineName() + " engine not initialized or closed",
                    getEngineName()
                );
            }
        }
    }

    /**
     * Handles transcription errors with consistent event publishing and exception wrapping.
     *
     * <p>This method provides common error handling for transcription failures:
     * <ul>
     *   <li>Publishes failure event with context for monitoring</li>
     *   <li>Preserves TranscriptionException instances without double-wrapping</li>
     *   <li>Wraps other exceptions with engine context</li>
     * </ul>
     *
     * <p><b>Usage Pattern:</b>
     * <pre>{@code
     * try {
     *     // Transcription logic
     *     return result;
     * } catch (Exception e) {
     *     throw handleTranscriptionError(e, publisher, context);
     * }
     * }</pre>
     *
     * @param exception the exception that occurred during transcription
     * @param publisher Spring event publisher for failure events (may be null)
     * @param context additional context to include in failure event (may be null)
     * @return TranscriptionException (always throws, never returns)
     * @throws TranscriptionException always thrown with appropriate context
     */
    protected final TranscriptionException handleTranscriptionError(
            Exception exception,
            ApplicationEventPublisher publisher,
            Map<String, String> context) {

        // Publish failure event if publisher available
        if (publisher != null) {
            EngineEventPublisher.publishFailure(
                publisher,
                getEngineName(),
                "transcription failure",
                exception,
                context
            );
        }

        // Preserve TranscriptionException without double-wrapping
        if (exception instanceof TranscriptionException te) {
            throw te;
        }

        // Wrap other exceptions with engine context
        throw new TranscriptionException(
            getEngineName() + " transcription failed: " + exception.getMessage(),
            getEngineName(),
            exception
        );
    }
}
