package com.boombapcompile.blckvox.service.orchestration;

/**
 * Encapsulates the start/stop/cancel recording workflow.
 *
 * <p>This service is the single entry point for triggering recording from any source:
 * hotkey events, REST API, or system tray menu.
 *
 * @since 1.2
 */
public interface RecordingService {

    /**
     * Starts a new recording session.
     *
     * @return true if recording started, false if already recording
     */
    boolean startRecording();

    /**
     * Stops the active recording session and initiates transcription.
     *
     * @return true if recording was stopped, false if not currently recording
     */
    boolean stopRecording();

    /**
     * Cancels the active recording session without transcribing.
     */
    void cancelRecording();

    /**
     * Returns the current application state.
     */
    ApplicationState getState();

    /**
     * Returns true if currently recording audio.
     */
    boolean isRecording();

    /**
     * Atomically toggles recording state: starts if idle, stops if recording.
     *
     * <p>This method prevents TOCTOU race conditions that can occur when
     * {@link #isRecording()} and {@link #startRecording()}/{@link #stopRecording()}
     * are called separately from different threads.
     *
     * @return true if the toggle succeeded (either started or stopped)
     */
    boolean toggleRecording();
}
