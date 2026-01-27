# minicli

[![CI](https://github.com/parttimenerd/minicli/actions/workflows/ci.yml/badge.svg)](https://github.com/parttimenerd/minicli/actions/workflows/ci.yml) [![Maven Central Version](https://img.shields.io/maven-central/v/me.bechberger.jfr/minicli)](https://central.sonatype.com/artifact/me.bechberger.jfr/minicli)

A minimal (< 40KB) Java command line interface (CLI) framework for building small command line applications,
using annotations to define commands, options, and positional parameters. 
It is designed for tools where minimizing dependencies and binary size is important.

While it's small, it should still cover most of the common use cases.

_It's currently in early development, so expect some rough edges._

Features
--------

- Define commands with `@Command` (classes and subcommand methods)
- Options via `@Option` (short/long names, required, default values, param labels, split, per-option converter)
- Positional parameters via `@Parameters` (index, arity, paramLabel, defaultValue)
- Mixins (reusable option groups) via `@Mixin`
- Nested subcommands (classes and methods)
- Multi-value options: arrays and `List` (repeat option or use `split` delimiter)
- Built-in type conversion for primitive types, `Path`, enums, and support for custom converters
- Automatic `-h/--help` and `-V/--version` flags
- End-of-options marker (`--`)
- Description placeholders (`${DEFAULT-VALUE}`, `${COMPLETION-CANDIDATES}`)
- Custom `header` and `customSynopsis` in help output

Non-Goals
---------
- Replace any existing full-featured CLI library.
- Include more advanced features like command completion, interactive prompts, or complex validation.
- Support for localization or internationalization.
- Extensive error handling or logging mechanisms.

Quick Start
-----------

Use the library to wire up command objects and run them from your application's `main` method. Example usage is shown below.

```java
import me.bechberger.minicli.*;
import me.bechberger.minicli.annotations.Command;
import me.bechberger.minicli.annotations.Option;

import java.util.concurrent.Callable;

@Command(name = "greet", description = "Greet a person")
class GreetCommand implements Callable<Integer> {
    @Option(names = {"-n", "--name"}, description = "Name to greet", required = true)
    String name;

    @Option(names = {"-c", "--count"}, description = "Count (default: ${DEFAULT-VALUE})", defaultValue = "1")
    int count;

    @Override
    public Integer call() {
        for (int i = 0; i < count; i++) System.out.println("Hello, " + name + "!");
        return 0;
    }
}

@Command(name = "myapp", description = "My CLI application", version = "1.0.0",
        subcommands = {GreetCommand.class})
class MyApp implements Runnable {
    public void run() {
        System.out.println("Use 'myapp greet --help'");
    }

    public static void main(String[] args) {
        System.exit(MiniCli.run(new MyApp(), System.out, System.err, args));
    }
}
```

Maven dependency
----------------

Add the library as a dependency in your project:

```xml
<dependency>
  <groupId>me.bechberger.jfr</groupId>
  <artifactId>minicli</artifactId>
  <version>0.1.2</version>
</dependency>
```

Features
-------------

### Subcommands (methods)

```java

import me.bechberger.minicli.annotations.Command;

@Command(name = "myapp")
class MyApp implements Runnable {
    @Command(name = "status", description = "Show status")
    int status() {
        System.out.println("OK");
        return 0;
    }

    @Override
    public void run() {
    }
}
// Usage: myapp status
```

### Built-in help and version flags

```java
@Command(name = "myapp", version = "1.2.3")
class MyApp implements Runnable {
    @Override public void run() { }
}
// supports: myapp --help, myapp --version
```

### End-of-options marker (`--`)

```java
@Command(name = "rm")
class Rm implements Runnable {
    @Option(names = "-f") boolean force;
    @Parameters(index = "0..*") java.util.List<String> files;
    public void run() { }
}
// rm -- -f   (treat "-f" as a file name, not an option)
```

### Description placeholders

```java
@Option(names = "--count", defaultValue = "5",
        description = "How many times (default: ${DEFAULT-VALUE})")
int count;
```

### Multi-value options

```java
@Option(names = "-I", description = "Include dirs")
java.util.List<java.nio.file.Path> includeDirs; // -I a -I b

@Option(names = "--tags", split = ",", description = "Tags")
java.util.List<String> tags; // --tags=a,b,c
```

### Custom type converters

```java
// global
int exit = MiniCli.builder()
    .registerType(java.time.Duration.class, java.time.Duration::parse)
    .run(new MyApp(), System.out, System.err, args);

// per-option
class Upper implements MiniCli.TypeConverter<String> {
    public String convert(String value) { return value.toUpperCase(); }
}

@Option(names = "--name", converter = Upper.class)
String name;
```

### Custom header and synopsis

```java
@Command(
    name = "mytool",
    header = {"My Tool", "Copyright 2026"},
    customSynopsis = {"Usage: mytool [OPTIONS] <file>"},
    description = "Process files"
)
class MyTool implements Runnable {
    public void run() { }
}
```

### Mixins (reusable options)

```java
class CommonOptions {
    @Option(names = {"-v", "--verbose"}, description = "Verbose output")
    boolean verbose;
}

@Command(name = "myapp")
class MyApp implements Runnable {
    @Mixin CommonOptions common;
    // common.verbose is now available
}
```

### Multi-value options (arrays / lists)

```java
@Option(names = "-f", description = "Files")
String[] files; // repeat -f a -f b

@Option(names = "--tags", split = ",", description = "Tags")
java.util.List<String> tags; // --tags=a,b,c
```

### Positional parameters

```java
@Parameters(index = "0", paramLabel = "FILE", description = "Input file")
String file;

@Parameters(index = "1", arity = "0..1", defaultValue = "out.txt", paramLabel = "OUTPUT")
String output;

@Parameters(index = "2..*", paramLabel = "ARGS", description = "Extra arguments")
java.util.List<String> args;
```

### Required options + arity

```java
@Option(names = "--host", required = true, description = "Server host")
String host;

@Parameters(index = "0", arity = "1", paramLabel = "FILE")
java.nio.file.Path file;
```

### Enums + completion candidates placeholder

```java
enum Mode { fast, safe }

@Option(names = "--mode",
        defaultValue = "safe",
        description = "Mode (${COMPLETION-CANDIDATES}), default: ${DEFAULT-VALUE}")
Mode mode;
```

### Nested subcommands (as classes)

```java
@Command(name = "app", subcommands = { App.Run.class })
class App implements Runnable {
    public void run() { }

    @Command(name = "run", description = "Run the app")
    static class Run implements Runnable {
        @Option(names = "--dry-run") boolean dryRun;
        public void run() { }
    }
}
// Usage: app run --dry-run
```

### Mixins reused across multiple commands

```java
class Common {
    @Option(names = {"-v","--verbose"}) boolean verbose;
}

@Command(name = "a") class A implements Runnable {
    @Mixin Common common; public void run() { }
}

@Command(name = "b") class B implements Runnable {
    @Mixin Common common; public void run() { }
}
```

### Help labels and defaults

Use `paramLabel` to control how a value shows up in help output.
Combine it with `${DEFAULT-VALUE}` to document defaults automatically.

```java
@Option(
    names = "--output",
    paramLabel = "FILE",
    defaultValue = "out.txt",
    description = "Write result to FILE (default: ${DEFAULT-VALUE})"
)
java.nio.file.Path output;

@Parameters(
    index = "0",
    paramLabel = "INPUT",
    description = "Input file"
)
java.nio.file.Path input;

@Parameters(
    index = "1",
    arity = "0..1",
    paramLabel = "LEVEL",
    defaultValue = "info",
    description = "Log level (default: ${DEFAULT-VALUE})"
)
String level;
```

### Global configuration

You can tweak global defaults (in addition to `@Command`/`@Option` annotations) via `CommandConfig`.

```java
int exit = MiniCli.builder()
    .commandConfig(c -> {
        c.version = "1.2.3";
        c.showDefaultValuesInHelp = false;
    })
    .run(new MyApp(), System.out, System.err, args);
```

Testing
-------

Run unit tests with:

```bash
mvn test
```

## Support, Feedback, Contributing

This project is open to feature requests/suggestions, bug reports etc.
via [GitHub](https://github.com/parttimenerd/minicli/issues) issues.
Contribution and feedback are encouraged and always welcome.

## License

MIT, Copyright 2026 SAP SE or an SAP affiliate company, Johannes Bechberger and contributors