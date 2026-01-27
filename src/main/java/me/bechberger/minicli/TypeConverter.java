package me.bechberger.minicli;

/**
 * Functional interface for custom type conversion.
 * @param <T> target conversion type
 */
@FunctionalInterface
public interface TypeConverter<T> {

    /**
     * A type converter that is ignored
     */
    class NullTypeConverter implements TypeConverter<Object> {
        @Override
        public Object convert(String value) throws IllegalArgumentException {
            return null;
        }
    }

    T convert(String value) throws IllegalArgumentException;
}