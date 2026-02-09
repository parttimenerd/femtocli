package me.bechberger.minicli.examples;

import me.bechberger.minicli.MiniCli;
import me.bechberger.minicli.TypeConverter;
import me.bechberger.minicli.annotations.Option;

public class CustomTypeConverters implements Runnable {

    public static class Upper implements TypeConverter<String> {
        public String convert(String value) {
            return value.toUpperCase();
        }
    }

    @Option(names = "--name", converter = Upper.class)
    String name;

    @Option(names = "--timeout")
    java.time.Duration timeout;

    @Override
    public void run() {
        System.out.println("Name: " + name);
        System.out.println("Timeout: " + timeout);
    }

    public static void main(String[] args) {
        MiniCli.builder()
                .registerType(java.time.Duration.class, java.time.Duration::parse)
                .run(new QuickStart(), System.out, System.err, args);
    }
}