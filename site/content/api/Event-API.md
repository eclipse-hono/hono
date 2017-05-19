+++
title = "Event API"
weight = 430
+++

The *Event* API is used by *Devices* to send event messages downstream.
*Business Applications* and other consumers use the API to receive messages published by devices belonging to a particular tenant.
<!--more-->

The Event API is defined by means of AMQP 1.0 message exchanges, i.e. a client needs to connect to Hono using AMQP 1.0 in order to invoke operations of the API as described in the following sections. Throughout the remainder of this page we will simply use *AMQP* when referring to AMQP 1.0.

The *Event* API is identical to the [*Telemetry* API]({{< relref "Telemetry-API.md" >}}) regarding the provided operations, the message format and the message flow. 
However it provides a different quality of service for messages sent to the *Event* endpoint by routing them via a persistent message broker to guarantee *AT LEAST ONCE* delivery.

# Southbound Operations

The following operations can be used by *Devices* and/or *Protocol Adapters* (to which the devices are connected) to publish event messages for consumption by downstream consumers like *Business Applications*.

Both *Devices* as well as *Protocol Adapters* will be referred to as *clients* in the remainder of this section.

## Send Event

**Preconditions**

1. Client has established an AMQP connection with Hono.
1. Client has established an AMQP link in role *sender* with Hono using target address `event/${tenant_id}` where `tenant_id` is the ID of the tenant that the client wants to upload event messages for. 
1. The device for which the client wants to send an event has been registered (see [Device Registration API]({{< relref "Device-Registration-API.md" >}})).
1. Client has obtained a *registration assertion* for the device from the Device Registration service by means of the [assert Device Registration operation]({{< relref "Device-Registration-API.md#assert-device-registration" >}}).

The client indicates its preferred message delivery mode by means of the `snd-settle-mode` and `rcv-settle-mode` fields of its `attach` frame during link establishment. Hono will receive messages using a delivery mode according to the following table:

| snd-settle-mode | rcv-settle-mode | Delivery semantics |
| :-------------- | :-------------- | :----------------- |
| *unsettled*     | *second*        | Hono will acknowledge and settle received messages when the underlying broker accepts and settles the message i.e. Hono forwards the disposition sent from the broker. Hono will accept any re-delivered messages. (TODO: ??) |

All other combinations are not supported by Hono and result in a termination of the link.

**Message Flow**

See [Telemetry API]({{< relref "Telemetry-API.md#upload-telemetry-data" >}}) for a description of the message flow.

**Message Format**

See [Telemetry API]({{< relref "Telemetry-API.md#upload-telemetry-data" >}}) for definition of message format.

# Northbound Operations

## Receive Events

Hono delivers messages containing event messages reported by a particular device in the same order that they have been received in (using the *Events* operation defined above).
Hono supports multiple non-competing *Business Application* consumers of event messages for a given tenant. Hono allows each *Business Application* to have multiple competing consumers for event messages for a given tenant to share the load of processing the messages.

**Preconditions**

1. Client has established an AMQP connection with Hono.
2. Client has established an AMQP link in role *receiver* with Hono using source address `event/${tenant_id}` where `tenant_id` represents the ID of the tenant the client wants to retrieve event messages for.

Hono supports *AT LEAST ONCE* delivery of *Event* messages only. A client therefore MUST use `unsettled` for the `snd-settle-mode` and `second` for the `rcv-settle-mode` fields of its `attach` frame during link establishment. All other combinations are not supported by Hono and result in the termination of the link.

**Message Flow**

See [*Telemetry API*]({{< relref "Telemetry-API.md" >}}) for a description of the message flow.

**Message Format**

See [*Telemetry API*]({{< relref "Telemetry-API.md" >}}) for definition of message format. 
