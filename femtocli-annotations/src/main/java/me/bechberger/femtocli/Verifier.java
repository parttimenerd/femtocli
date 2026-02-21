package me.bechberger.femtocli;

/**
 * Validates a converted option/parameter value.
 */
@FunctionalInterface
public interface Verifier<T> {

    /**
     * A verifier that performs no verification (sentinel for annotation defaults).
     */
    class NullVerifier implements Verifier<Object> {
        @Override
        public void verify(Object value) throws VerifierException {
            // no verification
        }
    }

    void verify(T value) throws VerifierException;
}