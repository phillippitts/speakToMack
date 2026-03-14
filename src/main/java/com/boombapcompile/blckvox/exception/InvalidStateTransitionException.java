package com.boombapcompile.blckvox.exception;

/**
 * Thrown when an invalid state transition is attempted.
 *
 * @since 1.2
 */
public class InvalidStateTransitionException extends BlckvoxException {

    private final String from;
    private final String to;

    public InvalidStateTransitionException(String from, String to) {
        super("Invalid state transition: " + from + " → " + to);
        this.from = from;
        this.to = to;
    }

    public String getFrom() {
        return from;
    }

    public String getTo() {
        return to;
    }
}
