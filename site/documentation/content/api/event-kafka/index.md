---
title: "Event API for Kafka Specification"
linkTitle: "Event API for Kafka"
weight: 418
resources:
  - src: produce_kafka.svg
  - src: consume_kafka.svg
---

The *Event* API is used by *Protocol Adapters* to send event messages downstream.
*Business Applications* and other consumers use the API to receive messages published by devices belonging to a particular tenant.

The Event API for Kafka is an alternative to the [Event API for AMQP]({{< relref "/api/event" >}}).
With this API clients publish event messages to an Apache Kafka&reg; cluster instead of an AMQP Messaging Network. 

The definitions in [Telemetry API for Kafka]({{< relref "/api/telemetry-kafka#kafka-based-messaging" >}}) 
also apply to the *Event* API for Kafka.   

{{% note title="Tech preview" %}}
The support of Kafka as a messaging system is currently a preview and not yet ready for production. The APIs may change with the next version. 
{{% /note %}}


## Southbound Operations

The following operation can be used by *Protocol Adapters* to send event messages received from devices to downstream consumers like *Business Applications*.

### Produce Event

The protocol adapter writes messages to the tenant-specific topic `hono.event.${tenant_id}` where `${tenant_id}` is the ID of the tenant that the client wants to upload event messages for.


**Preconditions**

1. Either the topic `hono.event.${tenant_id}` exists, or the broker is configured to automatically create topics on demand.
1. The client is authorized to write to the topic.
1. The device for which the adapter wants to send event messages has been registered (see [Device Registration API]({{< relref "/api/device-registration" >}})).

**Message Flow**

Hono supports *AT LEAST ONCE* delivery of *Event* messages only, as defined in [Telemetry API for Kafka]({{< relref "/api/telemetry-kafka#at-least-once-producers" >}}).

The following sequence diagram illustrates the flow of messages involved in the *MQTT Adapter* producing an event to the Kafka cluster.

{{< figure src="produce_kafka.svg" title="Produce event flow" >}}

1. *Device* `4711` publishes an event using MQTT QoS 1.
   1. *MQTT Adapter* produces an event message to *Kafka Cluster*.
   1. *Kafka cluster* acknowledges reception of the message.
   1. *MQTT Adapter* acknowledges the reception of the message to the *Device*.

When a Kafka producer raises an exception while sending an event message to Kafka, the protocol adapter MUST NOT try to re-send such rejected messages but MUST indicate the failed transfer to the device if the transport protocol provides means to do so.

**Message Format**

See [Telemetry API for Kafka]({{< relref "/api/telemetry-kafka#produce-telemetry-data" >}}) for the definition of the message format.

## Northbound Operations

The following operation can be used by *Business Applications* to receive event messages from Kafka.

### Consume Events

Hono delivers messages containing events reported by a particular device in the same order that they have been received in (using the [Produce Event]({{< relref "#produce-event" >}}) operation).
*Business Applications* consume messages from the tenant-specific topic `hono.event.${tenant_id}` where `${tenant_id}` represents the ID of the tenant the client wants to retrieve event messages for.

**Preconditions**

1. Either the topic `hono.event.${tenant_id}` exists, or the broker is configured to automatically create topics on demand.
1. The client is authorized to read from the topic.
1. The client subscribes to the topic with a Kafka consumer. 

Hono supports *AT LEAST ONCE* delivery of *Event* messages only. A consumer is expected to provide proper *AT LEAST ONCE* 
semantics and therefore MUST NOT commit offsets of messages that have not yet been fully processed.

**Message Flow**

The following sequence diagram illustrates the flow of messages involved in a *Business Application* consuming an event message from the Kafka cluster. 

{{< figure src="consume_kafka.svg" title="Consume event flow (success)" >}}

1. *Business Application* polls event messages from *Kafka Cluster*.
    1. *Kafka Cluster* returns a batch of messages.
1. *Business Application* processes the messages.
1. *Business Application* commits the offset of the last processed event message.
    1. *Kafka Cluster* acknowledges the commit.

**Message Format**

See [Telemetry API for Kafka]({{< relref "/api/telemetry-kafka#produce-telemetry-data" >}}) for the definition of the message format. 



## Well-known Event Message Types

Hono defines several *well-known* event types which have specific semantics. Events of these types are identified by means of the message's *content-type* header.

### Empty Notification

A Kafka message containing this type of event does not have any payload so the value of the message MUST be empty.

The headers an event sender needs to set for an *empty notification* event are defined in the [Telemetry API for Kafka]({{< relref "/api/telemetry-kafka#produce-telemetry-data" >}}).

The relevant headers are listed again in the following table:

| Name            | Mandatory       | Type        | Description |
| :-------------- | :-------------: | :---------- | :---------- |
| *content-type*  | yes             | *string*    | MUST be set to *application/vnd.eclipse-hono-empty-notification* |
| *ttd*           | no              | *int*       | The *time 'til disconnect* as described in the [Telemetry API for Kafka]({{< relref "/api/telemetry-kafka#produce-telemetry-data" >}}). |

**NB** An empty notification can be used to indicate to a *Business Application* that a device is currently ready to receive an upstream message by setting the *ttd* property. 
*Backend Applications* may use this information to determine the time window during which the device will be able to receive a command.

### Connection Event

Protocol Adapters may send this type of event to indicate that a connection with a device has
been established or has ended. Note that such events can only be sent for authenticated devices,
though, because an event is always scoped to a tenant and the tenant of a device is
established as part of the authentication process.

The Kafka message for a connection event MUST contain the following headers in addition to the standard event headers:

| Name            | Mandatory       | Type        | Description |
| :-------------- | :-------------: | :---------- | :---------- |
| *content-type*  | yes             | *string*    | MUST be set to  *application/vnd.eclipse-hono-dc-notification+json*. |
| *device_id*    | yes              | *string*    | The ID of the authenticated device. |

Each connection event's message value MUST contain a UTF-8 encoded string representation of a single JSON object with the following fields:

| Name        | Mandatory | Type      | Description |
| :---------- | :-------: | :-------- | :---------- |
| *cause*     | yes       | *string*  | The cause of the connection event. MUST be set to either `connected` or `disconnected`. |
| *remote-id* | yes       | *string*  | An identifier of the device that is the subject of this event, e.g. an IP address:port, client id etc. The format and semantics of this identifier is specific to the protocol adapter and the transport protocol it supports. |
| *source*    | yes       | *string*  | The type name of the protocol adapter reporting the event, e.g. `hono-mqtt`. |
| *data*      | no        | *object*  | An arbitrary JSON object which may contain additional information about the occurrence of the event. |

The example below might be used by the MQTT adapter to indicate that a connection with a device using client identifier `mqtt-client-id-1` has been established:

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
