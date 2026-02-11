# minicli

[![CI](https://github.com/parttimenerd/minicli/actions/workflows/ci.yml/badge.svg)](https://github.com/parttimenerd/minicli/actions/workflows/ci.yml) [![Maven Central Version](https://img.shields.io/maven-central/v/me.bechberger.util/minicli)](https://central.sonatype.com/artifact/me.bechberger.util/minicli)

Powerful yet minimal command line interface framework for Java applications and Java agents.

A minimal (< 45KB) Java command line interface (CLI) framework for building small command line applications,
using annotations to define (sub)commands, options, and positional parameters. 
It is designed for tools where minimizing dependencies and binary size is important.

While it's small, it should still cover most of the common use cases.

_It's currently in early development, so expect some rough edges._

Requires Java 17 or higher.

Features
--------

- Define commands with `@Command` (classes and subcommand methods)
- Options via `@Option` (short/long names, required, default values, param labels, split, per-option converter)
- Positional parameters via `@Parameters` (index, arity, paramLabel, defaultValue)
- Mixins (reusable option groups) via `@Mixin`
- Nested subcommands (classes and methods)
- Multi-value options: arrays and `List` (repeat option or use `split` delimiter)
- Built-in type conversion for primitive types, `Path`, `Duration`, enums, and support for custom converters
- Automatic `-h/--help` and `-V/--version` flags
- End-of-options marker (`--`)
- Description placeholders (`${DEFAULT-VALUE}`, `${COMPLETION-CANDIDATES}`)
- Custom `header`, `customSynopsis`, and `footer` in help output
- Ability to hide commands and options from help output
- Support for "agent args" mode, like Java agents

Non-Goals
---------
- Replace any existing full-featured CLI library.
- Include more advanced features like command completion, interactive prompts, or complex validation.
- Support for localization or internationalization.
- Extensive error handling or logging mechanisms.

Quick Start
-----------

This is the smallest realistic setup: one top-level command with a subcommand and a required option.

```java
package me.bechberger.minicli.examples;

import me.bechberger.minicli.MiniCli;
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
public class QuickStart implements Runnable {
    public void run() {
        System.out.println("Use 'myapp greet --help'");
    }

    public static void main(String[] args) {
        MiniCli.run(new QuickStart(), args);
    }
}
```


Try it:

```text
Hello, World!
```


Maven dependency
----------------

Add the library as a dependency in your project (< 55KB):

```xml
<dependency>
  <groupId>me.bechberger.util</groupId>
  <artifactId>minicli</artifactId>
  <version>0.1.12</version>
</dependency>
```

And for the minimal version without debug metadata (< 45KB):

```xml
<dependency>
  <groupId>me.bechberger.util</groupId>
  <artifactId>minicli-minimal</artifactId>
  <version>0.1.12</version>
</dependency>
```

Examples
--------

The examples below are generated from the `examples/` subproject and show many of the important
features in action.

### Quick start (subcommands + required options) [(source)](examples/src/main/java/me/bechberger/minicli/examples/QuickStart.java)

A tiny but realistic app: a top-level command with a `greet` subcommand, a required `--name`, and a defaulted `--count`.

<!-- @minicli:include-java path="examples/src/main/java/me/bechberger/minicli/examples/QuickStart.java" -->
```java
package me.bechberger.minicli.examples;

import me.bechberger.minicli.MiniCli;
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
public class QuickStart implements Runnable {
    public void run() {
        System.out.println("Use 'myapp greet --help'");
    }

    public static void main(String[] args) {
        MiniCli.run(new QuickStart(), args);
    }
}
```
<!-- @minicli:end -->

Try it:

<!-- @minicli:run-java class="QuickStart" args=["greet","--name=World","--count=1"] -->
```sh
> ./examples/run.sh QuickStart greet --name=World --count=1
Hello, World!
```
<!-- @minicli:end -->

And its help screens:

<!-- @minicli:run-java class="QuickStart" args=["--help"] -->
```sh
> ./examples/run.sh QuickStart --help
Usage: myapp [-hV] [COMMAND]
My CLI application
  -h, --help       Show this help message and exit.
  -V, --version    Print version information and exit.
Commands:
  greet  Greet a person
```
<!-- @minicli:end -->

<!-- @minicli:run-java class="QuickStart" args=["greet","--help"] -->
```sh
> ./examples/run.sh QuickStart greet --help
Usage: myapp greet [-hV] --name=<name> [--count=<count>]
Greet a person
  -c, --count=<count>    Count (default: 1)
  -h, --help             Show this help message and exit.
  -n, --name=<name>      Name to greet (required)
  -V, --version          Print version information and exit.
```
<!-- @minicli:end -->

### Subcommands as methods [(source)](examples/src/main/java/me/bechberger/minicli/examples/SubcommandMethod.java)

Define a subcommand as a method annotated with `@Command`.

<!-- @minicli:include-java path="examples/src/main/java/me/bechberger/minicli/examples/SubcommandMethod.java" -->
```java
package me.bechberger.minicli.examples;

import me.bechberger.minicli.MiniCli;
import me.bechberger.minicli.annotations.Command;

@Command(name = "myapp")
public class SubcommandMethod implements Runnable {
    @Command(name = "status", description = "Show status")
    int status() {
        System.out.println("OK");
        return 0;
    }

    @Override
    public void run() {
    }

    public static void main(String[] args) {
        MiniCli.run(new SubcommandMethod(), args);
    }
}
```
<!-- @minicli:end -->

<!-- @minicli:run-java class="SubcommandMethod" args=["status"] -->
```sh
> ./examples/run.sh SubcommandMethod status
OK
```
<!-- @minicli:end -->

### Positional parameters [(source)](examples/src/main/java/me/bechberger/minicli/examples/PositionalParameters.java)

Use `@Parameters` for values that are identified by position (instead of an option name).

<!-- @minicli:include-java path="examples/src/main/java/me/bechberger/minicli/examples/PositionalParameters.java" -->
```java
package me.bechberger.minicli.examples;

import me.bechberger.minicli.MiniCli;
import me.bechberger.minicli.annotations.Parameters;

import java.util.List;

/**
 * Shows how to use positional parameters.
 * Positional parameters are defined by their index and are not prefixed by an option name.
 */
public class PositionalParameters implements Runnable {
    @Parameters(index = "0", paramLabel = "FILE", description = "Input file")
    String file;

    @Parameters(index = "1..*", paramLabel = "ARGS", description = "Extra arguments")
    List<String> args;

    @Override
    public void run() {
        System.out.println("File: " + file);
        System.out.println("Args: " + args);
    }

    public static void main(String[] args) {
        MiniCli.run(new PositionalParameters(), args);
    }
}
```
<!-- @minicli:end -->

<!-- @minicli:run-java class="PositionalParameters" args=["in.txt","arg1","arg2"] -->
```sh
> ./examples/run.sh PositionalParameters in.txt arg1 arg2
File: in.txt
Args: [arg1, arg2]
```
<!-- @minicli:end -->

<!-- @minicli:run-java class="PositionalParameters" args=["--help"] -->
```sh
> ./examples/run.sh PositionalParameters --help
Usage: positionalparameters [-hV] FILE [ARGS...]
      FILE         Input file
      [ARGS...]    Extra arguments
  -h, --help       Show this help message and exit.
  -V, --version    Print version information and exit.
```
<!-- @minicli:end -->

### End-of-options marker (`--`) [(source)](examples/src/main/java/me/bechberger/minicli/examples/EndOfOptionsMarker.java)

Use `--` to stop option parsing. Any following tokens are treated as positional parameters, even if they start with `-`.

<!-- @minicli:include-java path="examples/src/main/java/me/bechberger/minicli/examples/EndOfOptionsMarker.java" -->
```java
package me.bechberger.minicli.examples;

import me.bechberger.minicli.MiniCli;
import me.bechberger.minicli.annotations.Command;
import me.bechberger.minicli.annotations.Option;
import me.bechberger.minicli.annotations.Parameters;

import java.util.List;

/**
 * Demonstrates the end-of-options marker ({@code --}).
 * <p>
 * Everything after {@code --} is treated as a positional parameter, even if it starts with {@code -}.
 */
@Command(name = "end-of-options", mixinStandardHelpOptions = true)
public class EndOfOptionsMarker implements Runnable {

    @Option(names = "--name", description = "A normal option")
    String name;

    @Parameters(index = "0..*", paramLabel = "ARGS", description = "Arguments (may start with '-')")
    List<String> args;

    @Override
    public void run() {
        System.out.println("name=" + name);
        System.out.println("args=" + args);
    }

    public static void main(String[] args) {
        System.exit(MiniCli.run(new EndOfOptionsMarker(), args));
    }
}
```
<!-- @minicli:end -->

<!-- @minicli:run-java class="EndOfOptionsMarker" args=["--name","test","--","--not-an-option","-also-not"] -->
```sh
> ./examples/run.sh EndOfOptionsMarker --name test -- --not-an-option -also-not
name=test
args=[--not-an-option, -also-not]
```
<!-- @minicli:end -->

### Multi-value options (lists + split) [(source)](examples/src/main/java/me/bechberger/minicli/examples/MultiValueOptions.java)

Repeat an option (`-I a -I b`) or use `split` for delimited values (`--tags=a,b`).

<!-- @minicli:include-java path="examples/src/main/java/me/bechberger/minicli/examples/MultiValueOptions.java" -->
```java
package me.bechberger.minicli.examples;

import me.bechberger.minicli.MiniCli;
import me.bechberger.minicli.annotations.Option;

import java.nio.file.Path;
import java.util.List;

/**
 * Demonstrates how to use multi-value options. The option value is split by a separator and stored in a list.
 */
public class MultiValueOptions implements Runnable {
    @Option(names = "-I", description = "Include dirs")
    List<Path> includeDirs; // -I a -I b

    @Option(names = "--tags", split = ",", description = "Tags")
    List<String> tags; // --tags=a,b,c

    public void run() {
        System.out.println("Include Dirs: " + includeDirs);
        System.out.println("Tags: " + tags);
    }

    public static void main(String[] args) {
        MiniCli.run(new MultiValueOptions(), args);
    }
}
```
<!-- @minicli:end -->

<!-- @minicli:run-java class="MultiValueOptions" args=["--tags=a,b,c","-I","a","-I","b","--tags","d,e"] -->
```sh
> ./examples/run.sh MultiValueOptions --tags=a,b,c -I a -I b --tags d,e
Include Dirs: [a, b]
Tags: [a, b, c, d, e]
```
<!-- @minicli:end -->

### Arrays and lists [(source)](examples/src/main/java/me/bechberger/minicli/examples/ArraysAndLists.java)

Multi-value options and parameters can be modeled as arrays or `List`.

<!-- @minicli:include-java path="examples/src/main/java/me/bechberger/minicli/examples/ArraysAndLists.java" -->
```java
package me.bechberger.minicli.examples;

import me.bechberger.minicli.MiniCli;
import me.bechberger.minicli.annotations.Command;
import me.bechberger.minicli.annotations.Option;
import me.bechberger.minicli.annotations.Parameters;

import java.util.Arrays;
import java.util.List;

/**
 * Demonstrates multi-value options/parameters with both arrays and lists.
 */
@Command(name = "arrays-and-lists", mixinStandardHelpOptions = true)
public class ArraysAndLists implements Runnable {

    @Option(names = "--xs", split = ",", description = "Comma-separated values into a String[]")
    String[] xs;

    @Option(names = "--ys", split = ",", description = "Comma-separated values into a List<String>")
    List<String> ys;

    @Parameters(index = "0..*", paramLabel = "REST", description = "Remaining args")
    String[] rest;

    @Override
    public void run() {
        System.out.println("xs=" + Arrays.toString(xs));
        System.out.println("ys=" + ys);
        System.out.println("rest=" + Arrays.toString(rest));
    }

    public static void main(String[] args) {
        System.exit(MiniCli.run(new ArraysAndLists(), args));
    }
}
```
<!-- @minicli:end -->

<!-- @minicli:run-java class="ArraysAndLists" args=["--xs=a,b","--ys=c,d","rest1","rest2"] -->
```sh
> ./examples/run.sh ArraysAndLists --xs=a,b --ys=c,d rest1 rest2
xs=[a, b]
ys=[c, d]
rest=[rest1, rest2]
```
<!-- @minicli:end -->

### Mixins (reusable option groups) [(source)](examples/src/main/java/me/bechberger/minicli/examples/MixinsAndSubcommands.java)

Use `@Mixin` to share common option groups across commands/subcommands.

<!-- @minicli:include-java path="examples/src/main/java/me/bechberger/minicli/examples/MixinsAndSubcommands.java" -->
```java
package me.bechberger.minicli.examples;

import me.bechberger.minicli.MiniCli;
import me.bechberger.minicli.annotations.Command;
import me.bechberger.minicli.annotations.Mixin;
import me.bechberger.minicli.annotations.Option;

/**
 * Shows how to use mixins to share options between subcommands. Run with "a -v" or "b -v" to see the effect.
 */
@Command(name = "mixins", subcommands = {MixinsAndSubcommands.A.class, MixinsAndSubcommands.B.class})
public class MixinsAndSubcommands implements Runnable {
    /** Example how to use mixins to share options between commands */
    static class Common {
        @Option(names = {"-v", "--verbose"})
        boolean verbose;
    }

    @Command(name = "a")
    static class A implements Runnable {
        @Mixin
        Common common;

        public void run() {
            System.out.println("Verbose: " + common.verbose);
        }
    }

    @Command(name = "b")
    static class B implements Runnable {
        @Mixin
        Common common;

        public void run() {
            System.out.println("Verbose: " + common.verbose);
        }
    }

    @Override
    public void run() {
    }

    public static void main(String[] args) {
        MiniCli.run(new MixinsAndSubcommands(), args);
    }
}
```
<!-- @minicli:end -->

<!-- @minicli:run-java class="MixinsAndSubcommands" args=["a"] -->
```sh
> ./examples/run.sh MixinsAndSubcommands a
Verbose: false
```
<!-- @minicli:end -->

<!-- @minicli:run-java class="MixinsAndSubcommands" args=["--help"] -->
```sh
> ./examples/run.sh MixinsAndSubcommands --help
Usage: mixins [-hV] [COMMAND]
  -h, --help       Show this help message and exit.
  -V, --version    Print version information and exit.
Commands:
  a  
  b  
```
<!-- @minicli:end -->

<!-- @minicli:run-java class="MixinsAndSubcommands" args=["a","--help"] -->
```sh
> ./examples/run.sh MixinsAndSubcommands a --help
Usage: mixins a [-hV] [--verbose]
  -h, --help       Show this help message and exit.
  -v, --verbose
  -V, --version    Print version information and exit.
```
<!-- @minicli:end -->

### Hide commands and options from help output [(source)](examples/src/main/java/me/bechberger/minicli/examples/HiddenCommandsAndOptions.java)

Use `hidden=true` on `@Command` and `@Option` if you want to keep functionality available but omit it from help output.

<!-- @minicli:include-java path="examples/src/main/java/me/bechberger/minicli/examples/HiddenCommandsAndOptions.java" -->
```java
package me.bechberger.minicli.examples;

import me.bechberger.minicli.MiniCli;
import me.bechberger.minicli.annotations.Command;
import me.bechberger.minicli.annotations.Option;

/**
 * Demonstrates how to hide commands and options from help output.
 */
@Command(
        name = "hidden",
        description = "Hide commands and options in help",
        mixinStandardHelpOptions = true,
        subcommands = {
                HiddenCommandsAndOptions.Status.class,
                HiddenCommandsAndOptions.Internal.class
        }
)
public class HiddenCommandsAndOptions implements Runnable {

    @Option(names = "--verbose", description = "Verbose output")
    boolean verbose;

    @Option(names = "--secret", hidden = true, description = "A hidden option")
    String secret;

    @Override
    public void run() {
        System.out.println("verbose=" + verbose);
        System.out.println("secret=" + secret);
    }

    @Command(name = "status", description = "Show status", mixinStandardHelpOptions = true)
    public static class Status implements Runnable {
        @Override
        public void run() {
            System.out.println("OK");
        }
    }

    @Command(name = "internal", hidden = true, description = "Internal command")
    public static class Internal implements Runnable {
        @Override
        public void run() {
            System.out.println("INTERNAL");
        }
    }

    public static void main(String[] args) {
        System.exit(MiniCli.run(new HiddenCommandsAndOptions(), args));
    }
}
```
<!-- @minicli:end -->

<!-- @minicli:run-java class="HiddenCommandsAndOptions" args=["--help"] -->
```sh
> ./examples/run.sh HiddenCommandsAndOptions --help
Usage: hidden [-hV] [--verbose] [COMMAND]
Hide commands and options in help
  -h, --help       Show this help message and exit.
  -V, --version    Print version information and exit.
      --verbose    Verbose output
Commands:
  status  Show status
```
<!-- @minicli:end -->

### Help labels + defaults [(source)](examples/src/main/java/me/bechberger/minicli/examples/HelpLabelsAndDefaults.java)

Control how values show up in help output (`paramLabel`) and document defaults with `${DEFAULT-VALUE}`.

<!-- @minicli:include-java path="examples/src/main/java/me/bechberger/minicli/examples/HelpLabelsAndDefaults.java" -->
```java
package me.bechberger.minicli.examples;

import me.bechberger.minicli.MiniCli;
import me.bechberger.minicli.annotations.Command;
import me.bechberger.minicli.annotations.Option;
import me.bechberger.minicli.annotations.Parameters;

import java.nio.file.Path;

@Command(name = "help-labels")
public class HelpLabelsAndDefaults implements Runnable {
    @Option(
            names = "--output",
            paramLabel = "FILE",
            defaultValue = "out.txt",
            description = "Write result to FILE (default: ${DEFAULT-VALUE})"
    )
    Path output;

    @Parameters(
            index = "0",
            paramLabel = "INPUT",
            description = "Input file"
    )
    Path input;

    @Parameters(
            index = "1",
            arity = "0..1",
            paramLabel = "LEVEL",
            defaultValue = "info",
            description = "Log level (default: ${DEFAULT-VALUE})"
    )
    String level;

    @Override
    public void run() {
        System.out.println("Input: " + input);
        System.out.println("Output: " + output);
        System.out.println("Level: " + level);
    }

    public static void main(String[] args) {
        MiniCli.run(new HelpLabelsAndDefaults(), args);
    }
}
```
<!-- @minicli:end -->

<!-- @minicli:run-java class="HelpLabelsAndDefaults" args=["in.txt"] -->
```sh
> ./examples/run.sh HelpLabelsAndDefaults in.txt
Input: in.txt
Output: out.txt
Level: info
```
<!-- @minicli:end -->

<!-- @minicli:run-java class="HelpLabelsAndDefaults" args=["--help"] -->
```sh
> ./examples/run.sh HelpLabelsAndDefaults --help
Usage: help-labels [-hV] [--output=FILE] INPUT [LEVEL]
      INPUT        Input file
      [LEVEL]      Log level (default: ${DEFAULT-VALUE})
  -h, --help       Show this help message and exit.
      --output=FILE
                   Write result to FILE (default: out.txt)
  -V, --version    Print version information and exit.
```
<!-- @minicli:end -->

### Spec injection (access to runtime) [(source)](examples/src/main/java/me/bechberger/minicli/examples/SpecInjection.java)

Declare a plain `Spec` field to access configured streams and usage rendering.

<!-- @minicli:include-java path="examples/src/main/java/me/bechberger/minicli/examples/SpecInjection.java" -->
```java
package me.bechberger.minicli.examples;

import me.bechberger.minicli.MiniCli;
import me.bechberger.minicli.Spec;
import me.bechberger.minicli.annotations.Command;
import me.bechberger.minicli.annotations.Option;

import java.time.Duration;

/**
 * Example showcasing injection of the {@link Spec} object.
 * <p>
 * The Spec object contains the configured input and output streams,
 * as well as a method to print usage help with the same formatting as the current MiniCli run.
 */
@Command(name = "inspect", description = "Example that uses Spec", mixinStandardHelpOptions = true)
public class SpecInjection implements Runnable {
    Spec spec; // injected

    @Option(names = {"-i", "--interval"},
            defaultValue = "10ms",
            description = "Sampling interval (default: ${DEFAULT-VALUE})")
    Duration interval;

    @Override
    public void run() {
        // Use the configured streams
        spec.out.println("interval = " + interval.toMillis());
        // Print usage with the same formatting as the current MiniCli run
        spec.usage();
    }

    public static void main(String[] args) {
        MiniCli.run(new SpecInjection(), args);
    }
}
```
<!-- @minicli:end -->

<!-- @minicli:run-java class="SpecInjection" args=["--interval","10ms"] -->
```sh
> ./examples/run.sh SpecInjection --interval 10ms
interval = 10
Usage: inspect [-hV] [--interval=<interval>]
Example that uses Spec
  -h, --help                   Show this help message and exit.
  -i, --interval=<interval>    Sampling interval (default: 10ms)
  -V, --version                Print version information and exit.
```
<!-- @minicli:end -->

### Agent args mode (comma-separated arguments) [(source)](examples/src/main/java/me/bechberger/minicli/examples/AgentCli.java)

Useful when you can only pass a single string (e.g., Java agent arguments) and still want subcommands/options.

**Why this is special:** Java agents typically only get a single `-javaagent:...=ARGSTRING` argument, and that string is commonly encoded as comma-separated key/value pairs. To my knowledge, there isn’t another Java CLI parsing library that supports this “agent args” style parsing out of the box (including escaping/quoting edge cases) while still giving you subcommands, help/version, and type conversion.

<!-- @minicli:include-java path="examples/src/main/java/me/bechberger/minicli/examples/AgentCli.java" -->
```java
package me.bechberger.minicli.examples;

import me.bechberger.minicli.MiniCli;
import me.bechberger.minicli.annotations.Command;
import me.bechberger.minicli.annotations.Option;
import me.bechberger.minicli.annotations.Parameters;

import java.time.Duration;
import java.util.concurrent.Callable;

/**
 * Example showcasing MiniCli agent args mode (comma-separated arguments).
 * <p>
 * Example invocations:
 * <ul>
 *   <li>{@code start,interval=1ms}</li>
 *   <li>{@code stop,output=file.jfr,verbose}</li>
 *   <li>{@code help}</li>
 *   <li>{@code version}</li>
 * </ul>
 */
@Command(
        name = "agent-cli",
        description = "Demo CLI for agent args mode",
        version = "1.0.0",
        subcommands = {AgentCli.Start.class, AgentCli.Stop.class},
        mixinStandardHelpOptions = true
)
public class AgentCli implements Runnable {

    @Override
    public void run() {
        // default action
        System.out.println("Try: start,interval=1ms or stop,output=file.jfr,verbose");
    }

    @Command(name = "start", description = "Start recording", mixinStandardHelpOptions = true)
    public static class Start implements Callable<Integer> {

        @Option(names = "--interval", defaultValue = "1ms", description = "Sampling interval")
        Duration interval;

        @Override
        public Integer call() {
            System.out.println("start: interval=" + interval);
            return 0;
        }
    }

    @Command(name = "stop", description = "Stop recording", mixinStandardHelpOptions = true)
    public static class Stop implements Callable<Integer> {
        @Parameters
        String mode;

        @Option(names = "--output", required = true, description = "Output file")
        String output;

        @Option(names = {"-v", "--verbose"}, description = "Verbose")
        boolean verbose;

        @Override
        public Integer call() {
            System.out.println("stop: mode=" + mode + ", output=" + output + ", verbose=" + verbose);
            return 0;
        }
    }

    public static void main(String[] args) {
        // Demonstrate agent mode if a single agent-args string is passed,
        // otherwise fall back to normal argv parsing.
        if (args.length == 1) {
            System.exit(MiniCli.runAgent(new AgentCli(), args[0]));
        }
        System.exit(MiniCli.run(new AgentCli(), args));
    }
}
```
<!-- @minicli:end -->

<!-- @minicli:run-java class="AgentCli" args=["--help"] -->
```sh
> ./examples/run.sh AgentCli --help
Usage: agent-cli,[hV],[COMMAND]
Options:
  h, help         Show this help message and exit.
  V, version      Print version information and exit.
Commands:
  start  Start recording
  stop   Stop recording
```
<!-- @minicli:end -->

Agent invocations (single comma-separated string):

<!-- @minicli:run-java class="AgentCli" args=["start,interval=1ms"] -->
```sh
> ./examples/run.sh AgentCli start,interval=1ms
start: interval=PT0.001S
```
<!-- @minicli:end -->

<!-- @minicli:run-java class="AgentCli" args=["stop,jfr,output=file.jfr,verbose"] -->
```sh
> ./examples/run.sh AgentCli stop,jfr,output=file.jfr,verbose
stop: mode=jfr, output=file.jfr, verbose=true
```
<!-- @minicli:end -->

<!-- @minicli:run-java class="AgentCli" args=["stop,help"] -->
```sh
> ./examples/run.sh AgentCli stop,help
Usage: agent-cli,stop,[hV],output=<output>,[verbose],<mode>
Options:
      <mode>
  h, help            Show this help message and exit.
      output=<output>
                     Output file (required)
  v, verbose         Verbose
  V, version         Print version information and exit.
```
<!-- @minicli:end -->


### Custom type converters [(source)](examples/src/main/java/me/bechberger/minicli/examples/CustomTypeConverters.java)

Register type converters globally, or declare a per-option converter (class or method).

<!-- @minicli:include-java path="examples/src/main/java/me/bechberger/minicli/examples/CustomTypeConverters.java" -->
```java
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
```
<!-- @minicli:end -->

<!-- @minicli:run-java class="CustomTypeConverters" args=["--name=max","--turn","on","--timeout=PT10S"] -->
```sh
> ./examples/run.sh CustomTypeConverters --name=max --turn on --timeout=PT10S
Name: MAX
Turn: true
Timeout: PT10S
```
<!-- @minicli:end -->

### Enums + completion candidates placeholder [(source)](examples/src/main/java/me/bechberger/minicli/examples/EnumsAndCompletionCandidates.java)

Enum options automatically list completion candidates in help output.

<!-- @minicli:include-java path="examples/src/main/java/me/bechberger/minicli/examples/EnumsAndCompletionCandidates.java" -->
```java
package me.bechberger.minicli.examples;

import me.bechberger.minicli.MiniCli;
import me.bechberger.minicli.annotations.Command;
import me.bechberger.minicli.annotations.Option;

@Command(name = "enums")
public class EnumsAndCompletionCandidates implements Runnable {
    enum Mode { fast, safe }

    @Option(names = "--mode",
            defaultValue = "safe",
            description = "Mode (${COMPLETION-CANDIDATES}), default: ${DEFAULT-VALUE}")
    Mode mode;

    public void run() {
        System.out.println("Mode: " + mode);
    }

    public static void main(String[] args) {
        MiniCli.run(new EnumsAndCompletionCandidates(), args);
    }
}
```
<!-- @minicli:end -->

<!-- @minicli:run-java class="EnumsAndCompletionCandidates" args=[] -->
```sh
> ./examples/run.sh EnumsAndCompletionCandidates
Mode: safe
```
<!-- @minicli:end -->

<!-- @minicli:run-java class="EnumsAndCompletionCandidates" args=["--mode","fast"] -->
```sh
> ./examples/run.sh EnumsAndCompletionCandidates --mode fast
Mode: fast
```
<!-- @minicli:end -->

<!-- @minicli:run-java class="EnumsAndCompletionCandidates" args=["--help"] -->
```sh
> ./examples/run.sh EnumsAndCompletionCandidates --help
Usage: enums [-hV] [--mode=<mode>]
  -h, --help       Show this help message and exit.
      --mode=<mode>
                   Mode (fast, safe), default: safe
  -V, --version    Print version information and exit.
```
<!-- @minicli:end -->

### Custom header + synopsis [(source)](examples/src/main/java/me/bechberger/minicli/examples/CustomHeaderAndSynopsis.java)

Customize the help screen with a header and a fully custom synopsis.

<!-- @minicli:include-java path="examples/src/main/java/me/bechberger/minicli/examples/CustomHeaderAndSynopsis.java" -->
```java
package me.bechberger.minicli.examples;

import me.bechberger.minicli.MiniCli;
import me.bechberger.minicli.annotations.Command;
import me.bechberger.minicli.annotations.Option;

/**
 * A command with a custom header and synopsis.
 * The header is printed above the usage message, and the synopsis replaces the default usage line.
 */
@Command(
        name = "mytool",
        header = {"My Tool", "Copyright 2026"},
        customSynopsis = {"Usage: mytool [OPTIONS] <file>"},
        description = "Process files",
        footer = """
                Examples:
                  mytool --flag
                """
)
public class CustomHeaderAndSynopsis implements Runnable {

    @Option(names = "--flag")
    boolean flag = false;

    public void run() {
    }

    public static void main(String[] args) {
        MiniCli.run(new CustomHeaderAndSynopsis(), args);
    }
}
```
<!-- @minicli:end -->

<!-- @minicli:run-java class="CustomHeaderAndSynopsis" args=["--help"] -->
```sh
> ./examples/run.sh CustomHeaderAndSynopsis --help
My Tool
Copyright 2026
Usage: mytool [OPTIONS] <file>
Process files
      --flag
  -h, --help       Show this help message and exit.
  -V, --version    Print version information and exit.

Examples:
  mytool --flag
```
<!-- @minicli:end -->

### Ignore options (inheritance / mixins) [(source)](examples/src/main/java/me/bechberger/minicli/examples/IgnoreOptionsExample.java)

Filter inherited options (or mixin-provided options) from the effective command surface.

<!-- @minicli:include-java path="examples/src/main/java/me/bechberger/minicli/examples/IgnoreOptionsExample.java" -->
```java
package me.bechberger.minicli.examples;

import me.bechberger.minicli.MiniCli;
import me.bechberger.minicli.annotations.IgnoreOptions;
import me.bechberger.minicli.annotations.Mixin;
import me.bechberger.minicli.annotations.Option;

/**
 * Example for {@code @IgnoreOptions}:
 * <ul>
 *   <li>remove inherited options from a base command</li>
 *   <li>remove options contributed by a {@code @Mixin}</li>
 * </ul>
 */
public class IgnoreOptionsExample {

    @IgnoreOptions(exclude = "--m")
    static class MixinOpts {
        @Option(names = "--m", description = "Mixin option")
        int m;
    }

    static class Base implements Runnable {
        @Option(names = "--a", description = "Inherited option A")
        int a;

        @Option(names = "--b", description = "Inherited option B")
        int b;

        @Mixin
        MixinOpts mixin;

        @Override
        public void run() {
        }
    }

    /**
     * One command that extends a base command and has a mixin.
     * <p>
     * - {@code --a} is inherited from {@link Base} but ignored here
     * - {@code --m} comes from the mixin but is ignored on the mixin class
     */
    @IgnoreOptions(exclude = "--a")
    static class Cmd extends Base {
        @Override
        public void run() {
            System.out.println("b=" + b);
            System.out.println("(mixin is present, but its option is ignored)");
        }
    }

    public static void main(String[] args) {
        // try:
        //   --a 1      (unknown option, ignored from base)
        //   --m 2      (unknown option, ignored from mixin)
        //   --b 3
        MiniCli.run(new Cmd(), args);
    }
}
```
<!-- @minicli:end -->

<!-- @minicli:run-java class="IgnoreOptionsExample" args=["--help"] -->
```sh
> ./examples/run.sh IgnoreOptionsExample --help
Usage: cmd [-hV] [--b=<b>]
      --b=<b>      Inherited option B
  -h, --help       Show this help message and exit.
  -V, --version    Print version information and exit.
```
<!-- @minicli:end -->

### Custom verifiers [(source)](examples/src/main/java/me/bechberger/minicli/examples/CustomTypeVerifiers.java)

Validate values and produce user-friendly errors.

<!-- @minicli:include-java path="examples/src/main/java/me/bechberger/minicli/examples/CustomTypeVerifiers.java" -->
```java
package me.bechberger.minicli.examples;

import me.bechberger.minicli.MiniCli;
import me.bechberger.minicli.Spec;
import me.bechberger.minicli.VerifierException;
import me.bechberger.minicli.annotations.Command;
import me.bechberger.minicli.annotations.Option;

import java.time.Duration;

class Helpers {
    static void checkPort(int p) {
        if (p < 1 || p > 65535) throw new VerifierException("port out of range");
    }
}

@Command(name = "verifiers")
public class CustomTypeVerifiers implements Runnable {

    @Option(names = "--port", verifierMethod = "Helpers#checkPort")
    int port;

    @Override
    public void run() {
        System.out.println("port=" + port);
    }

    public static void main(String[] args) {
        MiniCli.run(new CustomTypeVerifiers(), args);
    }
}
```
<!-- @minicli:end -->

<!-- @minicli:run-java class="CustomTypeVerifiers" args=["--port","0"] -->
```sh
> ./examples/run.sh CustomTypeVerifiers --port 0
Usage: verifiers [-hV] [--port=<port>]
  -h, --help       Show this help message and exit.
      --port=<port>
  -V, --version    Print version information and exit.
Error: port out of range
```
<!-- @minicli:end -->

### Global configuration [(source)](examples/src/main/java/me/bechberger/minicli/examples/GlobalConfiguration.java)

Set global defaults like version strings and usage formatting.

<!-- @minicli:include-java path="examples/src/main/java/me/bechberger/minicli/examples/GlobalConfiguration.java" -->
```java
package me.bechberger.minicli.examples;

import me.bechberger.minicli.MiniCli;

public class GlobalConfiguration implements Runnable {

    @Override
    public void run() {
    }

    public static void main(String[] args) {
        MiniCli.builder()
                .commandConfig(c -> {
                    c.version = "1.2.3";
                })
                .run(new GlobalConfiguration(), args);
    }
}
```
<!-- @minicli:end -->

<!-- @minicli:run-java class="GlobalConfiguration" args=["--version"] -->
```sh
> ./examples/run.sh GlobalConfiguration --version
1.2.3
```
<!-- @minicli:end -->

### Boolean options with explicit values [(source)](examples/src/main/java/me/bechberger/minicli/examples/BooleanExplicitValues.java)

Allow boolean options to accept explicit values (for cases where a pure flag isn’t enough).

<!-- @minicli:include-java path="examples/src/main/java/me/bechberger/minicli/examples/BooleanExplicitValues.java" -->
```java
package me.bechberger.minicli.examples;

import me.bechberger.minicli.MiniCli;
import me.bechberger.minicli.annotations.Command;
import me.bechberger.minicli.annotations.Option;

/**
 * Demonstrates boolean options as flags and with explicit values.
 *
 * <p>Supported forms:</p>
 * <ul>
 *   <li>{@code --prim} (flag style, sets to true)</li>
 *   <li>{@code --prim false} (explicit value as separate token)</li>
 *   <li>{@code --boxed=false} (explicit value with equals)</li>
 * </ul>
 */
@Command(name = "bools", description = "Boolean option parsing example")
public class BooleanExplicitValues implements Runnable {

    @Option(names = "--boxed", description = "Boxed boolean (Boolean)")
    Boolean boxed;

    @Option(names = "--prim", description = "Primitive boolean (boolean)")
    boolean prim;

    @Override
    public void run() {
        System.out.println("boxed=" + boxed);
        System.out.println("prim=" + prim);
    }

    public static void main(String[] args) {
        System.exit(MiniCli.run(new BooleanExplicitValues(), args));
    }
}
```
<!-- @minicli:end -->

<!-- @minicli:run-java class="BooleanExplicitValues" args=["--boxed=false","--prim","false"] -->
```sh
> ./examples/run.sh BooleanExplicitValues --boxed=false --prim false
boxed=false
prim=false
```
<!-- @minicli:end -->

<!-- @minicli:run-java class="BooleanExplicitValues" args=["--help"] -->
```sh
> ./examples/run.sh BooleanExplicitValues --help
Usage: bools [-hV] [--boxed] [--prim]
Boolean option parsing example
      --boxed      Boxed boolean (Boolean)
  -h, --help       Show this help message and exit.
      --prim       Primitive boolean (boolean)
  -V, --version    Print version information and exit.
```
<!-- @minicli:end -->

Support, Feedback, Contributing
-------------------------------

This project is open to feature requests/suggestions, bug reports etc.
via [GitHub](https://github.com/parttimenerd/minicli/issues) issues.
Contribution and feedback are encouraged and always welcome.

License
-------
MIT, Copyright 2026 SAP SE or an SAP affiliate company, Johannes Bechberger and contributors