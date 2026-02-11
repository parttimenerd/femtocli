package me.bechberger.minicli.examples;

import me.bechberger.minicli.MiniCli;
import me.bechberger.minicli.TypeConverter;
import me.bechberger.minicli.annotations.Command;
import me.bechberger.minicli.annotations.Option;

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
        MiniCli.builder()
                .registerType(java.time.Duration.class, java.time.Duration::parse)
                .run(new CustomTypeConverters(), args);
    }
}