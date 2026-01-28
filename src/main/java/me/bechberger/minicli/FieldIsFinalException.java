package me.bechberger.minicli;

/**
 * Exception thrown when a field annotated with @Option or @Parameters is final.
 */
public class FieldIsFinalException extends RuntimeException {
    public FieldIsFinalException(String message) {
        super(message);
    }
}