---
title: "Event API Specification for Kafka"
linkTitle: "Event API (Kafka)"
weight: 410
resources:
  - src: produce_kafka.svg
  - src: consume_kafka.svg
---

The *Event* API is used by *Protocol Adapters* to send event messages downstream.
*Business Applications* and other consumers use the API to receive messages published by devices belonging to a
particular tenant.

See [Kafka-based APIs]({{< relref "/api/kafka-api" >}}) for fundamental information about Hono's Kafka-based APIs.
The statements there apply to this specification.

## Southbound Operations

The following operation can be used by *Protocol Adapters* to send event messages received from devices to downstream
consumers like *Business Applications*.

### Produce Event

The protocol adapter writes messages to the tenant-specific topic `hono.event.${tenant_id}` where `${tenant_id}` is the ID
of the tenant that the client wants to upload event messages for.


**Preconditions**

1. Either the topic `hono.event.${tenant_id}` exists, or the broker is configured to automatically create topics on demand.
1. The client is authorized to write to the topic.
1. The device for which the adapter wants to send event messages has been registered (see
   [Device Registration API]({{< relref "/api/device-registration" >}})).

**Message Flow**

Hono supports *AT LEAST ONCE* delivery of *Event* messages only, as defined in
[Kafka-based APIs]({{< relref "/api/kafka-api#at-least-once-producers" >}}).

The following sequence diagram illustrates the flow of messages involved in the *MQTT Adapter* producing an event to
the Kafka cluster.

{{< figure src="produce_kafka.svg" title="Produce event flow" >}}

1. *Device* `4711` publishes an event using MQTT QoS 1.
   1. *MQTT Adapter* produces an event message to the *Kafka Cluster*.
   1. *Kafka cluster* acknowledges reception of the message.
   1. *MQTT Adapter* acknowledges the reception of the message to the *Device*.

When a Kafka producer raises an exception while sending an event message to Kafka, the protocol adapter MUST NOT try
to re-send such rejected messages but MUST indicate the failed transfer to the device if the transport protocol provides
means to do so.

**Message Format**

See [Telemetry API for Kafka]({{< relref "/api/telemetry-kafka#produce-telemetry-data" >}}) for the definition of the
message format.

## Northbound Operations

The following operation can be used by *Business Applications* to receive event messages from Kafka.

### Consume Events

Hono delivers messages containing events reported by a particular device in the same order that they have been received
in (using the [Produce Event]({{< relref "#produce-event" >}}) operation).
*Business Applications* consume messages from the tenant-specific topic `hono.event.${tenant_id}` where `${tenant_id}`
represents the ID of the tenant the client wants to retrieve event messages for.

**Preconditions**

1. Either the topic `hono.event.${tenant_id}` exists, or the broker is configured to automatically create topics on demand.
1. The client is authorized to read from the topic.
1. The client subscribes to the topic with a Kafka consumer. 

Hono supports *AT LEAST ONCE* delivery of *Event* messages only. A consumer is expected to provide proper *AT LEAST ONCE* 
semantics and therefore MUST NOT commit offsets of messages that have not yet been fully processed.

**Message Flow**

The following sequence diagram illustrates the flow of messages involved in a *Business Application* consuming an event
message from the Kafka cluster. 

{{< figure src="consume_kafka.svg" title="Consume event flow (success)" >}}

1. *Business Application* polls event messages from *Kafka Cluster*.
    1. *Kafka Cluster* returns a batch of messages.
1. *Business Application* processes the messages.
1. *Business Application* commits the offset of the last processed event message.
    1. *Kafka Cluster* acknowledges the commit.

**Message Format**

See [Telemetry API for Kafka]({{< relref "/api/telemetry-kafka#produce-telemetry-data" >}}) for the definition of the
message format. 

## Well-known Event Message Types

The well-known event message types are defined in the
[Event API for AMQP]({{< relref "/api/event#well-known-event-message-types" >}}) with the following differences:

* The _properties_ and _application-properties_ are set es Kafka headers.
* The _body_ of the AMQP message corresponds to the *value* of the Kafka message.
* The *content-type* header is of type *string*.
