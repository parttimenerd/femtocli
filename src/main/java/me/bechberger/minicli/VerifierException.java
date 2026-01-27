package me.bechberger.minicli;

/**
 * Thrown by {@link Verifier} implementations when a value is invalid.
 */
public class VerifierException extends RuntimeException {
    public VerifierException(String message) {
        super(message);
    }

    public VerifierException(String message, Throwable cause) {
        super(message, cause);
    }
}