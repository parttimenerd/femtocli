package me.bechberger.femtocli;

/**
 * Functional interface for custom type conversion.
 * @param <T> target conversion type
 */
@FunctionalInterface
public interface TypeConverter<T> {

    /**
     * A type converter that is ignored (sentinel for annotation defaults).
     */
    class NullTypeConverter implements TypeConverter<Object> {
        @Override
        public Object convert(String value) throws IllegalArgumentException {
            return null;
        }
    }

    T convert(String value) throws IllegalArgumentException;
}