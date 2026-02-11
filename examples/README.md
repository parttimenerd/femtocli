# femtocli examples

This Maven module contains small, self-contained examples for the `femtocli` library for all features.

## Running an example

Compile everything:

```bash
mvn -q package
```

Then run a main class with the normal JVM classpath, for example:

```bash
java -cp target/femtocli-examples.jar me.bechberger.femtocli.examples.AgentCli "start,interval=1ms"
```

You can find all example command classes in the [`me.bechberger.femtocli.examples`](src/main/java/me/bechberger/femtocli/examples) package.