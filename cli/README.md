This module contains a command line client for interacting with

1. messaging infrastructure implementing Hono's north bound Telemetry, Event and Command & Control APIs
2. Hono's [AMQP protocol adapter](https://www.eclipse.org/hono/docs/user-guide/amqp-adapter/).

The component is implemented as a Quarkus application using the Picocli library. The CLI provides extensive help
using the `--help` and/or `-h` command line options.

## Building

```bash 
# in directory hono/cli/
mvn clean install
cd target/
```

## Usage

Please refer to [Getting Started](https://www.eclipse.org/hono/docs/getting-started/) and the
[AMQP Adapter User Guide](https://www.eclipse.org/hono/docs/user-guide/amqp-adapter/).

These guides provide extensive examples for the usage of the client with Hono.
