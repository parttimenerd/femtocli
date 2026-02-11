package me.bechberger.femtocli.examples;

import me.bechberger.femtocli.FemtoCli;
import me.bechberger.femtocli.TypeConverter;
import me.bechberger.femtocli.annotations.Command;
import me.bechberger.femtocli.annotations.Option;

import java.time.Duration;

/**
 * Example showcasing custom type converters.
 * <p>
 * Example invocation:
 * <pre>{@code
 * java CustomTypeConverters --name=hello --timeout=PT30S
 * }</pre>
 */
@Command(name = "convert")
public class CustomTypeConverters implements Runnable {

    /** Custom type converter that converts a string to uppercase. */
    public static class Upper implements TypeConverter<String> {
        public String convert(String value) {
            return value.toUpperCase();
        }
    }

    static boolean parseOnOff(String value) {
        if (value.equalsIgnoreCase("on")) return true;
        if (value.equalsIgnoreCase("off")) return false;
        throw new IllegalArgumentException("Expected 'on' or 'off'");
    }

    @Option(names = "--name", converter = Upper.class)
    String name;

    @Option(names = "--turn", converterMethod = "parseOnOff")
    boolean turn;

    @Option(names = "--timeout")
    Duration timeout;

    @Override
    public void run() {
        System.out.println("Name: " + name);
        System.out.println("Turn: " + turn);
        System.out.println("Timeout: " + timeout);
    }

    public static void main(String[] args) {
        FemtoCli.builder()
                .registerType(java.time.Duration.class, java.time.Duration::parse)
                .run(new CustomTypeConverters(), args);
    }
}