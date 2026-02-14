---
title: "Event API Specification for AMQP 1.0"
linkTitle: "Event API (AMQP 1.0)"
weight: 418
resources:
  - src: forward.svg
  - src: consume.svg
---

The *Event* API is used by *Protocol Adapters* to send event messages downstream.
*Business Applications* and other consumers use the API to receive messages published by devices belonging to a
particular tenant.
<!--more-->

The Event API is defined by means of AMQP 1.0 message exchanges, i.e. a client needs to connect to Hono using AMQP 1.0
in order to invoke operations of the API as described in the following sections. Throughout the remainder of this page
we will simply use *AMQP* when referring to AMQP 1.0.

The Event API for AMQP 1.0 is an alternative to the [Event API for Kafka]({{< relref "/api/event" >}}) for
applications that want to consume events from an AMQP Messaging Network instead of an Apache Kafka&trade; broker.


## Southbound Operations

The following operations can be used by *Protocol Adapters* to forward event messages received from devices to
downstream consumers like *Business Applications*.

### Forward Event

**Preconditions**

1. Adapter has established an AMQP connection with the AMQP Messaging Network.
1. Adapter has established an AMQP link in role *sender* with the AMQP Messaging Network using target address
   `event/${tenant_id}` where `${tenant_id}` is the ID of the tenant that the client wants to upload event messages for. 
1. The device for which the adapter wants to send an event has been registered (see
   [Device Registration API]({{< relref "/api/device-registration" >}})).

Hono supports *AT LEAST ONCE* delivery of *Event* messages only. A client therefore MUST use `unsettled` for the
*snd-settle-mode* and `first` for the *rcv-settle-mode* fields of its *attach* frame during link establishment. All
other combinations are not supported by Hono and may result in the termination of the link or connection (depending
on the configuration of the AMQP Messaging Network).

The AMQP messages used to forward events to the AMQP Messaging Network MUST have their <em>durable</em> property set
to `true`. The AMQP Messaging Network is expected to write such messages to a persistent store before settling the
transfer with the `accepted` outcome.

**Message Flow**

The following sequence diagram illustrates the flow of messages involved in the *MQTT Adapter* forwarding an event
to the downstream AMQP Messaging Network.

{{< figure src="forward.svg" title="Forward event flow" >}}

1. *Device* `4711` publishes an event using MQTT QoS 1.
   1. *MQTT Adapter* transfers data to *AMQP 1.0 Messaging Network*.
   1. *AMQP 1.0 Messaging Network* acknowledges reception of the message.
   1. *MQTT Adapter* acknowledges the reception of the message to the *Device*.

When the AMQP Messaging Network fails to settle the transfer of an event message or settles the transfer with any
other outcome than `accepted`, the protocol adapter MUST NOT try to re-send such rejected messages but MUST indicate
the failed transfer to the device if the transport protocol provides means to do so.

**Message Format**

See [Telemetry API]({{< relref "/api/telemetry#forward-telemetry-data" >}}) for definition of message format.

## Northbound Operations

### Receive Events

Hono delivers messages containing events reported by a particular device in the same order that they have been
received in (using the [Forward Event]({{< relref "#forward-event" >}}) operation).

Hono supports multiple non-competing *Business Application* consumers of event messages for a given tenant. Hono
allows each *Business Application* to have multiple competing consumers for event messages for a given tenant to share
the load of processing the messages.

**Preconditions**

1. Client has established an AMQP connection with Hono.
1. Client has established an AMQP link in role *receiver* with Hono using source address `event/${tenant_id}` where
   `${tenant_id}` represents the ID of the tenant the client wants to retrieve event messages for.

Hono supports *AT LEAST ONCE* delivery of *Event* messages only. A client therefore MUST use `unsettled` for the
*snd-settle-mode* and `first` for the *rcv-settle-mode* fields of its *attach* frame during link establishment.
All other combinations are not supported by Hono and result in the termination of the link.

**Message Flow**

The following sequence diagram illustrates the flow of messages involved in a *Business Application* receiving an
event message from Hono. 

{{< figure src="consume.svg" title="Receive event data flow (success)" >}}

1. *AMQP 1.0 Messaging Network* delivers event message to *Business Application*.
   1. *Business Application* acknowledges reception of message.

**Message Format**

See [Telemetry API]({{< relref "/api/telemetry#forward-telemetry-data" >}}) for definition of message format. 


## Well-known Event Message Types

Hono defines several *well-known* event types which have specific semantics. Events of these types are identified
by means of the AMQP message's *content-type*.

### Empty Notification

An AMQP message containing this type of event does not have any payload, so the body of the message MUST be empty.

The AMQP 1.0 properties an event sender needs to set for an *empty notification* event are defined in the
[Telemetry API]({{< relref "/api/telemetry" >}}). 

The relevant properties are listed again in the following table:

| Name           | Mandatory        | Location                 | Type      | Description |
| :------------- | :--------------: | :----------------------- | :-------- | :---------- |
| *content-type* | yes              | *properties*             | *symbol*  | MUST be set to *application/vnd.eclipse-hono-empty-notification* |
| *ttd*          | no               | *application-properties* | *int*     | The *time 'til disconnect* as described in the [*Telemetry API*]({{< relref "/api/telemetry" >}}). |

{{% notice tip %}}
An empty notification can be used to indicate to a *Business Application* that a device is currently ready to
receive an upstream message by setting the *ttd* property. *Backend Applications* may use this information to determine
the time window during which the device will be able to receive a command.
{{% /notice %}}

### Connection Event

Protocol Adapters may send this type of event to indicate that a connection with a device has
been established or has ended. Note that such events can only be sent for authenticated devices,
though, because an event is always scoped to a tenant and the tenant of a device is
established as part of the authentication process.

The AMQP message for a connection event MUST contain the following properties in addition to the standard event
properties:

| Name           | Mandatory | Location                 | Type      | Description |
| :------------- | :-------: | :----------------------- | :-------- | :---------- |
| *content-type* | yes       | *properties*             | *symbol*  | Must be set to  *application/vnd.eclipse-hono-dc-notification+json* |
| *device_id*    | yes       | *application-properties* | *string*  | The ID of the authenticated device |

Each connection event's payload MUST contain a UTF-8 encoded string representation of a single JSON object with the
following fields:

| Name        | Mandatory | Type      | Description |
| :---------- | :-------: | :-------- | :---------- |
| *cause*     | yes       | *string*  | The cause of the connection event. MUST be set to either `connected` or `disconnected`. |
| *remote-id* | yes       | *string*  | An identifier of the device that is the subject of this event, e.g. an IP address:port, client id etc. The format and semantics of this identifier is specific to the protocol adapter and the transport protocol it supports. |
| *source*    | yes       | *string*  | The type name of the protocol adapter reporting the event, e.g. `hono-mqtt`. |
| *data*      | no        | *object*  | An arbitrary JSON object which may contain additional information about the occurrence of the event. |

The example below might be used by the MQTT adapter to indicate that a connection with a device using client
identifier `mqtt-client-id-1` has been established:

~~~json
{
  "cause": "connected",
  "remote-id": "mqtt-client-id-1",
  "source": "hono-mqtt",
  "data": {
    "foo": "bar"
  }
}
~~~

### Device Provisioning Notification

Device registries may send this event to convey provisioning related changes regarding a device. This may be used as
a hook for north bound applications if further application specific logic should be implemented upon provisioning
changes.

An AMQP message containing this type of event does not have any payload, so the body of the message MUST be empty.

The relevant properties are listed again in the following table:

| Name                       | Mandatory        | Location                 | Type      | Description |
| :------------------------- | :--------------: | :----------------------- | :-------- | :---------- |
| *content-type*             | yes              | *properties*             | *symbol*  | MUST be set to *application/vnd.eclipse-hono-device-provisioning-notification* |
| *hono_registration_status* | yes              | *application-properties* | *string*  | Contains NEW if the device has been newly provisioned. |
| *tenant_id*                | yes              | *application-properties* | *string*  | The tenant id denoting the tenant of the device. |
| *device_id*                | yes              | *application-properties* | *string*  | The id of the device. |
| *gateway_id*               | no               | *application-properties* | *string*  | This property contains a value only if the device's registration status has been changed by a gateway acting on behalf of the device. In such a case, the property contains the identifier of the gateway, otherwise the property will not be included. |
