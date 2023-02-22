+++
title = "Component View"
weight = 510
+++

This page describes the high level components constituting an Eclipse Hono&trade; instance and their relations to each
other.
<!--more-->

## Top Level

The diagram below provides an overview of the top level *logical* components.

{{< figure src="top-level.svg" >}}

The *MQTT* and *HTTP Adapters* use the *Device Registry* to authenticate *Devices* connecting to the adapters and
asserting their registration status. The adapters then forward the telemetry data and events received from the devices
to the *Messaging Infrastructure* for delivery to *Business Applications*. Business applications also use the messaging
infrastructure to send commands to connected devices. Commands are first received by the *Command Router* and then
forwarded to the protocol adapter instance that the target device is connected to.

The *Device Registry* and the *Command Router* use the *Auth Server* to authenticate the protocol adapters during
connection establishment.

All interactions between the components are based on AMQP 1.0 message exchanges as defined by the

* [Credentials API]({{< relref "/api/credentials" >}}),
* [Tenant API]({{< relref "/api/tenant" >}}),
* [Device Registration API]({{< relref "/api/device-registration" >}}),
* [Command Router API]({{< relref "/api/command-router" >}}),
* [Command & Control API]({{< relref "/api/command-and-control-kafka" >}}),
* [Telemetry API]({{< relref "/api/telemetry-kafka" >}}) and
* [Event API]({{< relref "/api/event-kafka" >}}).

## Device Registry

The diagram below provides an overview of the Mongo DB based *Device Registry* component's internal structure.

{{< figure src="mongodb-device-registry.png" width="100%" >}}

The Mongo DB based *Device Registry* component implements the AMQP 1.0 based
[Credentials]({{< relref "/api/credentials" >}}), [Tenant]({{< relref "/api/tenant" >}}) and
[Device Registration]({{< relref "/api/device-registration" >}}) APIs which are used by Hono's protocol adapters to
authenticate devices.
It also implements the HTTP based [Device Registry Management API]({{< relref "/api/management" >}}) which is used by
administrators to provision and manage device data. Clients opening a connection to the *Device Registry AMQP Server*
are authenticated by means of an external service accessed via the *Authentication* port.

Please refer to the [Device Registry]({{< relref "device-registry" >}}) user guide for details.

## Command Router

The diagram below provides an overview of the *Command Router* component's internal structure.

{{< figure src="command-router.png" width="100%" >}}

The *Command Router* component implements the [Command Router API]({{< relref "/api/command-router" >}}).
Clients opening a connection to the *CommandRouterServer* are authenticated by means of an external service accessed
via the *Auth* port.

The *Command Router* component uses the *Device Registry* via the [Tenant API]({{< relref "/api/tenant" >}}) and the
[Device Registration API]({{< relref "/api/device-registration" >}}) and is connected to the *Messaging Infrastructure*
to receive and forward Command & Control messages as defined by the
[Command & Control API]({{< relref "/api/command-and-control-kafka" >}}).

## Messaging Infrastructure

The *Messaging Infrastructure* is not *per se* a component being developed as part of Hono. Instead, Hono supports
Kafka, AMQP 1.0 and/or Google Pub/Sub based messaging infrastructure that is being developed by other open source projects.

### Kafka based Messaging Infrastructure

The example deployment currently employs the [bitnami Kafka Helm chart](https://bitnami.com/stack/kafka/helm) for
installing a single-node [Apache Kafka&trade;](https://kafka.apache.org) broker instance. Note that this setup is
suitable for development purposes but will probably not meet requirements regarding e.g. scalability of real world
use cases.

Scaling out messaging infrastructure is a not a trivial task. Hono **does not** provide an out-of-the-box solution to
this problem. Please refer to the Helm chart's documentation for details regarding the set up of a cluster for production
purposes.

### AMQP 1.0 based Messaging Infrastructure

The example deployment currently uses a single [Apache Qpid&trade; Dispatch Router](https://qpid.apache.org)
instance connected to a single [Apache ActiveMQ&trade; Artemis](https://activemq.apache.org/artemis/) broker instance.
Note that this setup is suitable for development purposes but will probably not meet requirements regarding e.g.
scalability of real world use cases.

The diagram below provides an overview of the default implementation of the Messaging Network component used with Hono.

{{< figure src="hono-messaging.png"  >}}

Scaling out messaging infrastructure is a not a trivial task. Hono **does not** provide an out-of-the-box solution to
this problem but instead integrates with the [EnMasse](https://enmasseproject.github.io) project which aims at
providing *Messaging as a Service* infrastructure.

### Google Pub/Sub based Messaging Infrastructure

To use this setup, a Google project has to be provided with the Pub/Sub API enabled. There is no example deployment for
this Messaging Infrastructure.

Google Pub/Sub is an asynchronous messaging service designed to be highly scalable. There is no need to take care about scaling out
messaging infrastructure.

{{% notice warning %}}
Support for Google Pub/Sub based messaging infrastructure is considered **experimental** and may change without further notice.
{{% /notice %}}
