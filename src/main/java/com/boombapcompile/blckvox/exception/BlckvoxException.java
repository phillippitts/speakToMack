package com.boombapcompile.blckvox.exception;

/**
 * Base exception for all blckvox application-specific errors.
 * All domain exceptions should extend this class to enable centralized error handling.
 */
public class BlckvoxException extends RuntimeException {

    public BlckvoxException(String message) {
        super(message);
    }

    public BlckvoxException(String message, Throwable cause) {
        super(message, cause);
    }
}
