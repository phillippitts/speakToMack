package com.phillippitts.speaktomack.exception;

/**
 * Thrown when an STT model file cannot be found at the configured path.
 * This is a fatal error that prevents the STT engine from starting.
 */
public class ModelNotFoundException extends SpeakToMackException {

    private final String modelPath;

    public ModelNotFoundException(String modelPath) {
        super("STT model not found at path: " + modelPath);
        this.modelPath = modelPath;
    }

    public ModelNotFoundException(String modelPath, Throwable cause) {
        super("STT model not found at path: " + modelPath, cause);
        this.modelPath = modelPath;
    }

    public String getModelPath() {
        return modelPath;
    }
}
