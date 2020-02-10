+++
title = "Connect devices"
weight = 390
+++

Eclipse Hono&trade; has the concept of protocol adapters (check the [Component Overview]({{< relref "architecture/component-view/index.md" >}}) for details),
which provide protocol endpoints for devices. The typical case is that devices connect to Hono using supported protocols like AMQP, MQTT, [custom protocols]({{< relref "dev-guide/custom_http_adapter" >}}) and more. In case that a device is not able to communicate in the Hono message abstraction of [Event]({{< relref "api/event/index.md" >}}), [Telemetry]({{< relref "api/telemetry/index.md" >}}) and [Command & Control]({{< relref "concepts/command-and-control.md" >}}), a device gateway can be used to communicate to a Hono adapter in behalf of a device (internally treated itself as a device). If devices communicate to a third party hosted API (referred next as *protocol gateway*), this *protocol gateway* can be used communicate to a Hono adapter in behalf of devices.

## Connect directly to Hono adapters

To connect a device directly southbound to Hono, it have to send and receive messages in Hono message abstraction manner of Event, Telemetry and Command & Control. Connecting to deployed adapters can be done in various protocols. Hono is protocol agnostic as long it fits into the Hono message abstraction, that is needed to parse messages between different protocols. Prior to first communication, a device has to be [registered to the device registry]({{< relref "concepts/device-identity.md#device-registration" >}}), that authenticates devices.

## Connect over device gateways

A device can be connect to Hono adapter using a device gateway. That device gateway have to fullfil the same criterial as a device connecting directly to a Hono adapter. Device and its gateway are both considered as devices internally in the device registry. In contrast to a device directly connecting to a Hono adapter with its own credentials, a device connecting over a device gateway is authenticated by credentials of the gateway. The devices using device gateways, need to be registered with the corresponding device gateways set in "via"-property, which allows the gateway to communicate and authenticate in behalf of devices.

## Connect over protocol adapters

Hono already comes with a set of protocol adapters for some of the well known IoT protocols.
However, in some cases devices do not communicate via standardized protocols but use a proprietary protocol.
To provide the service user of a Hono service the possibility to also connect devices speaking a proprietary protocol
Hono supports the concept of *protocol gateways*.

A *protocol gateway* is a separately deployed micro service, which understands proprietary protocol messages and translates
them to AMQP messages processable by Hono's AMQP protocol adapter.
In that sense a *protocol gateway* behaves like a device gateway speaking the proprietary protocol and forwards their
messages to the AMQP Messaging Network.

The diagram below provides an overview of the *protocol gateway* setup.
{{< figure src="../protocol_gateway.png" >}}  

Devices send telemetry/event messages via their own proprietary protocols to the *protocol gateway*,
which send the messages via AMQP to the Hono AMQP adapter.

A prerequisite for using *protocol gateways* is that devices are registered in Hono's Device Registry
and are configured in such a way that the *protocol gateways* can act on their behalf i.e.
with a device registration "via" property containing the gateway id of the *protocol gateways*.

### Example Code

To illustrate how a *protocol adapter* could connect a proprietary protocol to the AMQP adapter, a simple example can be found in the [example module](https://github.com/eclipse/hono/tree/master/example/protocol-adapter-example).