package com.phillippitts.speaktomack.exception;

/**
 * Thrown when audio data is invalid or does not match the required format
 * (16kHz, 16-bit signed PCM, mono, little-endian) or violates duration bounds.
 */
public class InvalidAudioException extends SpeakToMackException {

    private final int audioSize;
    private final String reason;

    public InvalidAudioException(String reason) {
        super("Invalid audio data: " + reason);
        this.audioSize = 0;
        this.reason = reason;
    }

    public InvalidAudioException(int audioSize, String reason) {
        super("Invalid audio data (" + audioSize + " bytes): " + reason);
        this.audioSize = audioSize;
        this.reason = reason;
    }

    public int getAudioSize() {
        return audioSize;
    }

    public String getReason() {
        return reason;
    }
}
