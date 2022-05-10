---
title: "Telemetry API Specification for Kafka"
linkTitle: "Telemetry API (Kafka)"
weight: 405
resources:
  - src: produce_kafka_qos0.svg
  - src: produce_kafka_qos1.svg
  - src: consume_kafka.svg
---

The *Telemetry* API is used by *Protocol Adapters* to send telemetry data downstream.
*Business Applications* and other consumers use the API to receive data published by devices belonging to a particular
tenant.

See [Kafka-based APIs]({{< relref "/api/kafka-api" >}}) for fundamental information about Hono's Kafka-based APIs.
The statements there apply to this specification.

## Southbound Operations

The following operation can be used by *Protocol Adapters* to send telemetry data received from devices to downstream
consumers like *Business Applications*.

### Produce Telemetry Data

The protocol adapter writes messages to the tenant-specific topic `hono.telemetry.${tenant_id}` where `${tenant_id}` is the
ID of the tenant that the client wants to upload telemetry data for.

**Preconditions**

1. Either the topic `hono.telemetry.${tenant_id}` exists, or the broker is configured to automatically create topics on demand.
1. The client is authorized to write to the topic.
1. The device for which the adapter wants to send telemetry data has been registered (see
   [Device Registration API]({{< relref "/api/device-registration" >}})).

**Message Flow**

It is up to the discretion of the protocol adapter whether it wants to use *AT LEAST ONCE* or *AT MOST ONCE* delivery
semantics according to [AT LEAST ONCE Producers]({{< relref "/api/kafka-api#at-least-once-producers" >}})
respectively [AT MOST ONCE Producers]({{< relref "/api/kafka-api#at-most-once-producers" >}}). 

The following sequence diagram illustrates the flow of messages involved in the *HTTP Adapter* producing a telemetry
data message to the Kafka cluster implementing *AT MOST ONCE* delivery semantics.

{{< figure src="produce_kafka_qos0.svg" title="Produce telemetry data flow (AT MOST ONCE)" >}}

1. *Device* `4711` PUTs telemetry data to the *HTTP Adapter*
   1. *HTTP Adapter* produces telemetry data to the *Kafka Cluster*.
   1. *HTTP Adapter* acknowledges the reception of the data to the *Device*.

The following sequence diagram illustrates the flow of messages involved in the *HTTP Adapter* producing a telemetry
data message to the Kafka cluster implementing *AT LEAST ONCE* delivery semantics.

{{< figure src="produce_kafka_qos1.svg" title="Produce telemetry data flow (AT LEAST ONCE)" >}}

1. *Device* `4711` PUTs telemetry data to the *HTTP Adapter*, indicating *QoS Level* 1.
   1. *HTTP Adapter* produces telemetry data to the *Kafka Cluster*.
   1. *Kafka Cluster* acknowledges reception of the message.
   1. *HTTP Adapter* acknowledges the reception of the data to the *Device*.

When a Kafka producer raises an exception while sending a telemetry message to Kafka, the protocol adapter MUST NOT try
to re-send such rejected messages but SHOULD indicate the failed transfer to the device if the transport protocol
provides means to do so.

**Use *AT LEAST ONCE* and *AT MOST ONCE* in a Protocol Adapter**

If a protocol adapter should support both delivery semantics, a single producer MUST be used and it MUST be configured
for *AT LEAST ONCE*. It may not wait for acknowledgements of *AT MOST ONCE* messages. 

A protocol adapter MUST NOT use two producers sending telemetry data for the same device.
Otherwise, the messages from a device with different QoS levels could be out of order, as Kafka does not guarantee the
message order between multiple producer instances. 

**Message Format**

The key of the message MUST be the device ID.
 
Metadata MUST be set as Kafka headers on a message.
The following table provides an overview of the headers a client needs to set on a *Produce Telemetry Data* message.

| Name            | Mandatory       | Type        | Description |
| :-------------- | :-------------: | :---------- | :---------- |
| *content-type*  | yes             | *string*    | A content type indicating the type and characteristics of the data contained in the message value as a valid MIME type, e.g. `text/plain; charset="utf-8"` for a text message or `application/json` etc. The value may be set to `application/octet-stream` if the message message value is to be considered *opaque* binary data. See [RFC 2046](https://www.ietf.org/rfc/rfc2046.txt) for details. |
| *content-encoding* | no           | *string*    | The content encoding as defined in section 3.5 of [RFC 2616](https://www.ietf.org/rfc/rfc2616.txt). |
| *creation-time* | yes             | *long*      | The instant in time (UTC, milliseconds since the Unix epoch) when the message has been created. |
| *device_id*     | yes             | *string*    | The identifier of the device that the data in the message value is originating from. |
| *qos*           | no              | *int*       | The quality of service level of the message. Supported values are `0` for AT MOST ONCE and `1` for AT LEAST ONCE. |
| *ttd*           | no              | *int*       | The *time 'til disconnect* indicates the number of seconds that the device will remain connected to the protocol adapter. The value of this header must be interpreted relative to the message's *creation-time*. A value of `-1` is used to indicate that the device will remain connected until further notice, i.e. until another message indicates a *ttd* value of `0`. In absence of this property, the connection status of the device is to be considered indeterminate. *Backend Applications* might use this information to determine a time window during which the device will be able to receive a command. |
| *ttl*           | no              | *long*      | The *time-to-live* in milliseconds. A consumer of the message SHOULD discard the message if the sum of *creation-time* and *ttl* is greater than the current time (milliseconds since the Unix epoch). |

Protocol adapters MAY set additional headers on the Kafka record.  Any such headers will be defined in the adapter's
corresponding user guide.

The value of the message MUST consist of a byte array containing the telemetry data. The format and encoding of the
data MUST be indicated by the *content-type* and (optional) *content-encoding* headers of the message.

## Northbound Operations

The following operation can be used by *Business Applications* to receive telemetry data from Kafka.

### Consume Telemetry Data

Hono delivers messages containing telemetry data reported by a particular device in the same order that they have been
received in (using the [Produce Telemetry Data]({{< relref "#produce-telemetry-data" >}}) operation).
*Business Applications* consume messages from the tenant-specific topic `hono.telemetry.${tenant_id}` where `${tenant_id}`
represents the ID of the tenant the client wants to retrieve telemetry data for.

**Preconditions**

1. Either the topic `hono.telemetry.${tenant_id}` exists, or the broker is configured to automatically create topics on demand.
1. The client is authorized to read from the topic.
1. The client subscribes to the topic with a Kafka consumer. 

A consumer can provide *AT MOST ONCE* or *AT LEAST ONCE* delivery semantics depending on the offset commit strategy it
implements. If a message has the *qos* Kafka header with the value `1`, the consumer is expected to provide proper
*AT LEAST ONCE* semantics, and therefore MUST NOT commit the message *offset* before it has been fully processed.

**Message Flow**

The following sequence diagram illustrates the flow of messages involved in a *Business Application* consuming a
telemetry data message from the Kafka cluster. The delivery mode used is *AT LEAST ONCE*.

{{< figure src="consume_kafka.svg" title="Consume Telemetry Data" >}}

1. *Business Application* polls telemetry messages from *Kafka Cluster*.
   1. *Kafka Cluster* returns a batch of messages.
1. *Business Application* processes the messages.
1. *Business Application* commits the offset of the last processed telemetry message.
    1. *Kafka Cluster* acknowledges the commit.


**Message Format**

The format of the messages containing the telemetry data is the same as for the
[Produce Telemetry Data operation]({{< relref "#produce-telemetry-data" >}}).
