# minicli examples

This Maven module contains small, self-contained examples for the `minicli` library for all features.

## Running an example

Compile everything:

```bash
mvn -q package
```

Then run a main class with the normal JVM classpath, for example:

```bash
java -cp target/minicli-examples.jar me.bechberger.minicli.examples.AgentCli "start,interval=1ms"
```

You can find all example command classes in the [`me.bechberger.minicli.examples`](src/main/java/me/bechberger/minicli/examples) package.