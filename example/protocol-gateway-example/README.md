# Example *Protocol gateway* to connect to the AMQP adapter

> Disclaimer: This example is considered a POC and not intended to be run in a productive environment.

This example show how a external protocol could be connected to a running *Hono AMQP adapter* re-using classes from the [Hono CLI module](https://github.com/eclipse/hono/blob/master/cli/src/main/java/org/eclipse/hono/cli/).
For this example a simple TCP socket is opened and listen to commands that initiate communication with the *Hono AMQP adapter*.

## Prerequisites 
> optional if tenantId, deviceId and device credentials present

From your Hono instance get:
    - AMQP hono adapter ip, referred to as `AMQP_ADAPTER_IP`
    - AMQP hono adapter port, referred to as `AMQP_ADAPTER_PORT` (default: 5672)
    - A device `d` username, a combination of hono deviceId and tenantId, concatenated with `'@'`, , referred to as `USERNAME`
    E.g.: `7c7c9777-2acd-450e-aa61-ab73d37ad0ef@6d12841d-0458-4271-b060-44a46f3417a9`
    - A password for device `d`, referred to as `PASSWORD`

How to get these values, can be found in [Getting started guide](https://www.eclipse.org/hono/getting-started/).

Alternatively, these values can be fetched and created using the script `scripts/create_hono_device.sh`.

## Application properties

As default the TCP server port is `6666`, set in the `application.yml`.

## Dependency

Import Hono Cli dependency to inherit [`org.eclipse.hono.cli.adapter.AmqpCliClient`](https://github.com/eclipse/hono/blob/master/cli/src/main/java/org/eclipse/hono/cli/adapter/AmqpCliClient.java) that provides a connection to interact with the Hono AMQP adapter.

```xml
<dependency>
    <groupId>org.eclipse.hono</groupId>
    <artifactId>hono-cli</artifactId>
    <version>1.1.0-SNAPSHOT</version>
</dependency>
```

## Start demo

- Install maven dependencies
- Run `mvn spring-boot:run`
- Connect to TCP-server-port `6666`
    e.g.: `netcat localhost 6666`

### Example execution

Using variables from [Prerequisites](#Prerequisites)

- AMQP_ADAPTER_IP
- AMQP_ADAPTER_PORT
- MY_TENANT
- MY_DEVICE
- MY_PWD

```bash
echo "initConnection\n${AMQP_ADAPTER_IP}\n${AMQP_ADAPTER_PORT}\n${MY_DEVICE}@${MY_TENANT}\n${MY_PWD}\n" | netcat -c localhost 6666

MESSAGE_ADDRESS="telemetry"
PAYLOAD="{\"data\": \"example message\"}"
echo "sendAMQPMessage\n${MESSAGE_ADDRESS}\n${PAYLOAD}" | netcat localhost 6666

MESSAGE_ADDRESS="event"
PAYLOAD="example message"
echo "sendAMQPMessage\n${MESSAGE_ADDRESS}\n${PAYLOAD}" | netcat localhost 6666

echo "listenCommands" | netcat localhost 6666
```
