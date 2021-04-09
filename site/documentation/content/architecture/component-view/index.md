+++
title = "Component View"
weight = 510
+++

This page describes the high level components constituting an Eclipse Hono&trade; instance and their relations to each other.
<!--more-->

## Top Level

The diagram below provides an overview of the top level *logical* components.

{{< figure src="top-level.png" >}}

The *MQTT* and *HTTP Adapters* use the *Device Registry* to authenticate *Devices* connecting to the adapters and asserting their
registration status. The adapters then forward the telemetry data and events received from the devices to the *AMQP 1.0 Messaging Network*
for delivery to *Business Applications*. Business applications also use the messaging network to send commands to connected devices.
Commands are first received by the *Command Router* and then forwarded to the protocol adapter that is connected to the respective devices.

The *Device Registry* and the *Command Router* use the *Auth Server* to authenticate the protocol adapters during connection establishment.

All interactions between the components are based on AMQP 1.0 message exchanges as defined by the

* [Credentials API]({{< relref "/api/credentials" >}}),
* [Tenant API]({{< relref "/api/tenant" >}}),
* [Device Registration API]({{< relref "/api/device-registration" >}}),
* [Command Router API]({{< relref "/api/command-router" >}}),
* [Command & Control API]({{< relref "/api/command-and-control" >}}),
* [Telemetry API]({{< relref "/api/telemetry" >}}) and
* [Event API]({{< relref "/api/event" >}}).

## Device Registry

The diagram below provides an overview of the *Device Registry* component's internal structure.

{{< figure src="device-registry.png" width="100%" >}}

The *Device Registry* component implements the [Credentials API]({{< relref "/api/credentials" >}}), [Tenant API]({{< relref "/api/tenant" >}})
and [Device Registration API]({{< relref "/api/device-registration" >}}).
Clients opening a connection to the *DeviceRegistryServer* are authenticated by means of an external service accessed via the *Auth* port.

Hono provides the following Device Registry implementations:

- [MongoDB Based Device Registry]({{< relref "/user-guide/mongodb-based-device-registry" >}})
- [JDBC Based Device Registry]({{< relref "/user-guide/jdbc-based-device-registry" >}})
- [File Based Device Registry]({{< relref "/user-guide/file-based-device-registry" >}})

## Command Router

The diagram below provides an overview of the *Command Router* component's internal structure.

{{< figure src="command-router.png" width="100%" >}}

The *Command Router* component implements the [Command Router API]({{< relref "/api/command-router" >}}).
Clients opening a connection to the *CommandRouterServer* are authenticated by means of an external service accessed via the *Auth* port.

The *Command Router* component uses the *Device Registry* via the [Tenant API]({{< relref "/api/tenant" >}}) and the [Device Registration API]({{< relref "/api/device-registration" >}})
and is connected to the *AMQP 1.0 Messaging Network* to receive and forward Command & Control messages as defined by the [Command & Control API]({{< relref "/api/command-and-control" >}}).

## AMQP 1.0 Messaging Network

The *AMQP 1.0 Messaging Network* is not *per se* a component being developed as part of Hono. Instead, Hono comes with a default implementation of the messaging network relying on artifacts provided by other open source projects. The default implementation currently consists of a single [Apache Qpid Dispatch Router](https://qpid.apache.org) instance connected to a single [Apache Artemis](https://activemq.apache.org/artemis/) broker instance. Note that this setup is useful for development purposes but will probably not meet requirements regarding e.g. scalability of real world use cases.

The diagram below provides an overview of the default implementation of the Messaging Network component used with Hono.

{{< figure src="hono-messaging.png"  >}}

Scaling out messaging infrastructure is a not a trivial task. Hono **does not** provide an out-of-the-box solution to this problem but instead integrates with the [EnMasse](https://enmasseproject.github.io) project which aims at providing *Messaging as a Service* infrastructure.
