# Example *Protocol gateway* to connect to the AMQP adapter

> Disclaimer: This example is considered a POC and not intended to be run in a production environment.

This example illustrates how devices using a custom TCP based protocol can be connected to Hono's standard AMQP
adapter by means of implementing a *protocol gateway*. The gateway listens on a TCP socket for commands to be executed
on behalf of a device and maps them to interactions with the AMQP adapter using the AMQP device client classes from
Hono's [Device AMQP Client module](https://github.com/eclipse-hono/hono/tree/master/clients/device-amqp).

## Prerequisites

The protocol gateway requires a Hono instance running the standard AMQP adapter to connect to.
By default, the gateway will connect to the AMQP adapter of the [Hono Sandbox](https://www.eclipse.org/hono/sandbox/).
However, it can also be configured to connect to a local instance.

## Gateway Configuration

By default, the gateway listens on port `6666` on the loopback device (`127.0.0.1`). This can be changed in the
`application.yml` file or by means of corresponding command line arguments. In the same way the connection to the AMQP
adapter can be configured. By default, the gateway connects to the AMQP adapter of the Hono Sandbox using 
`gw@DEFAULT_TENANT` as the username.

## Starting the Gateway

```bash
java -jar target/quarkus-app/quarkus-run.jar
```

## Connecting to the Gateway

The following example illustrates how a device can connect and interact with the protocol gateway.
The *netcat* command line tool is used to redirect the console's *stdin* and *stdout* to the protocol gateway.

```bash
nc 127.0.0.1 6666
Welcome to the Example Protocol Gateway, please enter a command
```

The device identifier can be set using the `login` command which requires the device identifier as a parameter:

```bash
login 4712
device [4712] logged in
OK
```

Once logged in, the device can send events

```bash
event Hello there ...
OK
```

and telemetry messages using QoS 0 (at-most-once)

```bash
telemetry 0 Unimportant data ...
OK
```
or using QoS 1 (at-least-once)

```bash
telemetry 1 Unimportant data ...
OK
```

Note that this only works if a downstream consumer is connected for the device's tenant.

The device can also subscribe for commands

```bash
subscribe
OK
```

and if an application sends a one-way command then the command is logged to the console

```bash
ONE-WAY COMMAND [name: setBrightness]: {"brightness": 25}
```

## Commands

The gateway supports the following commands

* **login** *deviceId* - Authenticates the given device. All subsequent commands are executed in the context of the
  logged in device. This command can be used to *switch* the device context by logging in using a different device
  identifier.
* **event** *payload* - Sends the *payload* as an event.
* **telemetry** *qos* *payload* - Sends the *payload* as a telemetry message using the delivery semantics indicated by
  the qos. Supported values are `0` (at most once) and `1` (at least once).
* **subscribe** - Starts receiving commands. Only one-way commands sent by applications will be forwarded to the console.
* **unsubscribe** - Stops receiving commands
