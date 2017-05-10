+++
title = "Component View"
weight = 510
+++

This page describes the high level components constituting an Eclipse Hono instance and their relations to each other.
<!--more-->

## Top Level

The diagram below provides an overview of the top level *logical* components.

{{< figure src="../Top-Level.jpg" >}}

The *MQTT* and *Rest Potocol Adapters* use the *Hono Server* component to upload telemetry data and events provided by connected *Devices*. The Hono Server authorizes the messages sent by the protocol adapters and forwards the messages to the downstream *AMQP 1.0 Messaging Network* for delivery to consuming *Business Applications*.

All interactions between the components are abased on AMQP 1.0 message exchanges as defined by the [Device Registration API]({{< relref "api/Device-Registration-API.md" >}}), [Telemetry API]({{< relref "api/Telemetry-API.md" >}}) and [Event API]({{< relref "api/Event-API.md" >}}).

## Hono Server

The diagram below provides an overview of the *Hono Server* component's internal structure.

{{< figure src="../Hono-Server.jpg" >}}

Hono Server exposes both an implementation of the Telemetry as well as the Event API. The messages received from the protocol adapters are validated by the *TelemetryEndpoint* and *EventEndpoint* correspondingly. The messages' format is checked for compliance with the APIs and the message originator's registration status is verified by means of a JSON Web Token included in the message application properties.

The Hono Server also provides a *default* implementation of the Device Registration API. The Device Registration implementation stores registration information in the local file system and cannot be scaled out horizontally (across multiple instances of Hono Server). The default implementation is therefore mainly intended to be used for demonstration purposes and PoCs. In real world scenarios, the default implementation can be disabled and the Telemetry and Event endpoints can be configured to be used with a dedicated Device Registration service implementation. Such an implementation should be deployed separately and use a persistent store that can be shared by multiple instances.

## AMQP 1.0 Messaging Network

The *AMQP 1.0 Messaging Network* is not *per-se* a component being developed as part of Hono. Instead, Hono comes with a default implementation of the messaging network relying on artifacts provided by other open source projects. The default implementation currently consists of a single [Apache Qpid Dispatch Router](https://qpid.apache.org) instance connected to a single [Apache Artemis](https://activemq.apache.org/artemis) broker instance. Note that this setup is useful for development purposes but will probably not meet requirements regarding e.g. scalability of real world use cases.

The diagram below provides an overview of the default implementation of the Messaging Network component used with Hono.

{{< figure src="../Messaging-Network.png" >}}

Scaling out messaging infrastructure is a not a trivial task. Hono **does not** provide an out-of-the-box solution to this problem but instead integrates with the [enMasse](http://enmasse.io) project which aims at providing *Messaging as a Service* infrastructure.
