---
title: "Command & Control"
weight: 196
resources:
- src: command_control_concept_cmdrouter_1.svg
- src: command_control_concept_cmdrouter_2.svg
- src: command_control_concept_cmdrouter_3.svg
---

*Business applications* can send commands to devices following the
[Command & Control API]({{< relref "/api/command-and-control" >}}). This concept page describes how this API is used
by applications to send commands and describes how Hono's protocol adapters process the commands so that they reach
their target device.
 
<!--more-->

Commands can be sent following a *request/response* or a *one-way* pattern. For *Request/Response* commands, there is
always a response expected from the device.

## General concept

In order for devices to be able to receive commands, they first have to connect to a Hono protocol adapter and indicate
their availability to receive commands. For devices communicating via AMQP or MQTT, this means connecting to an adapter
and explicitly subscribing for commands. For devices sending messages via HTTP or CoAP, this means using the `ttd`
("time till disconnect") parameter when sending an event or telemetry message, thereby indicating how long the device
will wait for a command message.

The protocol adapter will then forward a notification to the Business Application about the device being able to receive
commands, also containing the time limit if the `ttd` parameter was given.

The Business Application can send a command to such a device via the used messaging network. Hono will receive the
command by means of the Command Router component and will forward it to the protocol adapter instance that the device
is connected to. The protocol adapter will then send the command to the device. In case of a *request/response* command,
the device is expected to send back a command response message.

When the device explicitly ends the command subscription, the protocol adapter will send a corresponding notification
to the Business Application.

#### Command & Control involving a gateway

Hono has special support for sending commands to devices that are connected via a gateway to Hono.
Such devices are configured in the Hono device registration service in such a way, that certain gateways may act on
behalf of the device.

When sending commands, the northbound applications do not have to know to which gateway the command target device is
connected to. The application sends the command with the device address and Hono will direct the command to a gateway
that has subscribed for such commands and that is configured to act on behalf of the command target device. If there
are multiple matching gateways, the one that the command target device was last connected to is chosen.
The information about which gateways are subscribed and which gateway a device has last communicated by is managed via
the [Command Router API]({{< relref "/api/command-router" >}}).

## Message flow using AMQP messaging network

The following sections and the contained sequence diagrams give an overview of a device indicating its availability for
receiving commands, of a business application sending a command to the device and of the device sending back a command
response.

### Device indicating availability to receive commands

{{< figure src="command_control_concept_cmdrouter_1.svg" title="Command subscription" >}}

When the device subscribes for commands (1), the protocol adapter
[registers the command consumer]({{< relref "/api/command-router#register-command-consumer-for-device" >}}) with the
Command Router service, associating the device with its protocol adapter instance identifier (2). The Command Router
service creates a receiver link scoped to the device's tenant (3) if it doesn't exist yet. Following that, the
notification about the device subscription is sent to the business application via the AMQP messaging network (4).

### Business application sending a command to the device

{{< figure src="command_control_concept_cmdrouter_2.svg" title="Command handling" >}}

Upon receiving the notification, the application prepares sender and command response receiver links (1,2) and sends
the command message to the AMQP messaging network. The message is received by the Command Router service component (3),
which will determine the protocol adapter instance #1 that is able to handle the command message. The command then gets
forwarded to the AMQP messaging network on the address for adapter instance #1 (4). The protocol adapter instance #1
will receive the message (5) and forward it to the device (6). As the last step, an `accepted` disposition will be sent
back to the application (7).

### Device sending a command response message

{{< figure src="command_control_concept_cmdrouter_3.svg" title="Command response handling" >}}

The command response message is sent back to the application from the protocol adapter via the AMQP messaging network.
