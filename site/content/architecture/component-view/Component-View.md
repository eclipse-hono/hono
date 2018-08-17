+++
title = "Component View"
weight = 510
+++

This page describes the high level components constituting an Eclipse Hono&trade; instance and their relations to each other.
<!--more-->

## Top Level

The diagram below provides an overview of the top level *logical* components.

{{< figure src="../top-level.png" >}}

The *MQTT* and *HTTP Adapters* use the *Device Registry* to authenticate *Devices* connecting to the adapters and asserting their
registration status. The adapters then forward the telemetry data and events received from the devices to the *AMQP 1.0 Messaging Network*
for delivery to *Business Applications*. Business applications also use the messaging network to send commands to connected devices.

The *Device Registry* uses the *Auth Server* to authenticate the protocol adapters during connection establishment.

All interactions between the components are based on AMQP 1.0 message exchanges as defined by the

* [Credentials API]({{< relref "/api/Credentials-API.md" >}}),
* [Tenant API]({{< relref "/api/Tenant-API.md" >}}),
* [Device Registration API]({{< relref "/api/Device-Registration-API.md" >}}),
* [Command & Control (C&C) API]({{< relref "/api/Command-And-Control-API.md" >}}),
* [Telemetry API]({{< relref "/api/Telemetry-API.md" >}}) and
* [Event API]({{< relref "/api/Event-API.md" >}}).

## Device Registry

The diagram below provides an overview of the *Device Registry* component's internal structure.

{{< figure src="../device-registry.png" width="100%" >}}

The *Device Registry* component implements the [Credentials API]({{< relref "/api/Credentials-API.md" >}}), [Tenant API]({{< relref "/api/Tenant-API.md" >}}) and [Device Registration API]({{< relref "/api/Device-Registration-API.md" >}}). Clients opening a connection to *SimpleDeviceRegistryServer* are authenticated by means of an external service accessed via the *Auth* port. The *FileBasedCredentialsService*, *FileBasedTenantService* and *FileBasedRegistrationService* store all data in the local file system. The *Device Registry* is therefore not recommended to be used in production environments because the component cannot easily scale out horizontally. It is mainly intended to be used for demonstration purposes and PoCs. In real world scenarios, a more sophisticated implementation should be used that is designed to scale out, e.g. using a persistent store for keeping device registration information that can be shared by multiple instances.

## AMQP 1.0 Messaging Network

The *AMQP 1.0 Messaging Network* is not *per-se* a component being developed as part of Hono. Instead, Hono comes with a default implementation of the messaging network relying on artifacts provided by other open source projects. The default implementation currently consists of a single [Apache Qpid Dispatch Router](https://qpid.apache.org) instance connected to a single [Apache Artemis](https://activemq.apache.org/artemis) broker instance. Note that this setup is useful for development purposes but will probably not meet requirements regarding e.g. scalability of real world use cases.

The diagram below provides an overview of the default implementation of the Messaging Network component used with Hono.

{{< figure src="../hono-messaging.png" >}}

Scaling out messaging infrastructure is a not a trivial task. Hono **does not** provide an out-of-the-box solution to this problem but instead integrates with the [EnMasse](http://enmasse.io) project which aims at providing *Messaging as a Service* infrastructure.
