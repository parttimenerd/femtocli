package me.bechberger.minicli;

/**
 * Functional interface for custom type conversion.
 */
@FunctionalInterface
public interface TypeConverter<T> {
    T convert(String value) throws IllegalArgumentException;
}