# minicli examples

This Maven module contains small, self-contained examples for the `minicli` library.

## Running an example

Compile everything:

```bash
mvn -q package
```

Then run a main class with the normal JVM classpath, for example:

```bash
java -cp target/classes:~/.m2/repository/me/bechberger/util/minicli/0.1.10/minicli-0.1.10.jar \
  me.bechberger.minicli.examples.AgentCliExample "start,interval=1ms"
```

(Adjust the jar path if you’re building locally / using a different version.)

## Agent args mode demo

`AgentCliExample` demonstrates comma-separated **agent args mode**:

- `start,interval=1ms`
- `stop,output=file.jfr,verbose`
- `help`
- `version`

In agent mode:
- tokens are separated by `,`
- you can use bare `help` / `version`
- you can pass bare option names like `interval=1ms` (normalized to `--interval=1ms`)