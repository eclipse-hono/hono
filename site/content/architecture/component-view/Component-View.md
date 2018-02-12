+++
title = "Component View"
weight = 510
+++

This page describes the high level components constituting an Eclipse Hono&trade; instance and their relations to each other.
<!--more-->

## Top Level

The diagram below provides an overview of the top level *logical* components.

{{< figure src="../Top-Level.jpg" >}}

The *MQTT* and *HTTP Adapters* use the *Device Registry* to authenticate *Devices* connecting to the adapters and asserting their registration status. The *Device Registry* issues a token asserting the status which the protocol adapters include when forwarding telemetry data and events to the *Hono Messaging* component. The *Hono Messaging* component verifies the device registration status by means of validating the token and forwards the messages to the downstream *AMQP 1.0 Messaging Network* for delivery to *Business Applications*.

Both *Hono Messaging* and *Device Registry* use the *Auth Server* to authenticate the protocol adapters during connection establishment.

All interactions between the components are abased on AMQP 1.0 message exchanges as defined by the [Device Registration API]({{< relref "api/Device-Registration-API.md" >}}), [Telemetry API]({{< relref "api/Telemetry-API.md" >}}) and [Event API]({{< relref "api/Event-API.md" >}}).

## Hono Messaging

The diagram below provides an overview of the *Hono Messaging* component's internal structure.

{{< figure src="../Hono-Messaging.jpg" width="80%" >}}

Hono Messaging implements both the Telemetry as well as the Event API. Clients opening a connection to *HonoMessaging* are authenticated by means of an implementation of the Authentication API. Messages received from a client are validated by the *TelemetryEndpoint* and *EventEndpoint* correspondingly. The messages' format is checked for compliance with the APIs and the message originator's registration status is verified by means of a JSON Web Token (JWT) included in the message application properties. Finally, the messages are forwarded to a downstream component by the *ForwardingTelemetryDownstreamAdapter* and *ForwardingEventDownstreamAdapter* respectively.

## Device Registry

The diagram below provides an overview of the *Device Registry* component's internal structure.

{{< figure src="../Device-Registry.jpg" width="70%" >}}

The *Device Registry* component implements the [Device Registration API]({{< relref "api/Device-Registration-API.md" >}}). Clients opening a connection to *SimpleDeviceRegistryServer* are authenticated by means of an external service accessed via the *Auth* port. The *FileBasedRegistrationService* stores all registration information in the local file system. The *Device Registry* is therefore not recommended to be used in production environments because the component cannot easily scale out horizontally. It is mainly intended to be used for demonstration purposes and PoCs. In real world scenarios, a more sophisticated implementation should be used that is designed to scale out, e.g. using a persistent store for keeping device registration information that can be shared by multiple instances.

## AMQP 1.0 Messaging Network

The *AMQP 1.0 Messaging Network* is not *per-se* a component being developed as part of Hono. Instead, Hono comes with a default implementation of the messaging network relying on artifacts provided by other open source projects. The default implementation currently consists of a single [Apache Qpid Dispatch Router](https://qpid.apache.org) instance connected to a single [Apache Artemis](https://activemq.apache.org/artemis) broker instance. Note that this setup is useful for development purposes but will probably not meet requirements regarding e.g. scalability of real world use cases.

The diagram below provides an overview of the default implementation of the Messaging Network component used with Hono.

{{< figure src="../Messaging-Network.png" >}}

Scaling out messaging infrastructure is a not a trivial task. Hono **does not** provide an out-of-the-box solution to this problem but instead integrates with the [EnMasse](http://enmasse.io) project which aims at providing *Messaging as a Service* infrastructure.
