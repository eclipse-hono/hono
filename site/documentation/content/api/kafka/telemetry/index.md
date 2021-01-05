---
title: "Telemetry API for Kafka Specification"
linkTitle: "Telemetry API - Kafka"
weight: 417
resources:
  - src: produce_kafka_qos0.svg
  - src: produce_kafka_qos1.svg
  - src: consume_kafka.svg
---

The *Telemetry* API is used by *Protocol Adapters* to send telemetry data downstream.
*Business Applications* and other consumers use the API to receive data published by devices belonging to a particular tenant.

The Telemetry API for Kafka is an alternative to the [Telemetry API for AMQP]({{< relref "/api/telemetry" >}}).
With this API clients publish telemetry data to an Apache Kafka&reg; cluster instead of an AMQP Messaging Network. 

{{% note title="Tech preview" %}}
The support of Kafka as a messaging system is currently a preview and not yet ready for production. The APIs may change with the next version. 
{{% /note %}}


## Kafka-based Messaging

Using Kafka instead of AMQP comes with slightly different behavior. Kafka provides a Publish/Subscribe messaging style.
Every message is sent by a *producer* to a *topic* in a Kafka cluster, where it will be persisted by (multiple) *brokers*.
Each topic consists of a configurable number of *partitions*. The *key* of a message determines to which partition it will be written.
Each partition is *replicated* to a configurable number of brokers in the cluster to provide fault-tolerance if a broker goes down.

A Kafka message (also called record) consists of a key, a value, a timestamp, and headers.

Multiple *consumers* can read the messages at the same time and they can read them repeatedly (if an error occurred).
This decoupling of producer and consumers has the consequence that a producer does not get feedback about the consumption of messages, 
it does not "know" *when* a message will be read by a consumer (or if there are many or no consumer at all).
For example, a protocol adapter can only confirm to the device that the Kafka cluster successfully persisted a telemetry message, 
not if a *Business Application* received it.

Messages are usually deleted from a topic at some point &ndash; regardless of whether they have been processed by a consumer. 
The duration of this *log retention time* should be configured in Kafka to reasonable values for each topic.  

See the [Kafka documentation](https://kafka.apache.org/documentation/#configuration) for details about Kafka's configuration properties. 

### Quality of Service

The configuration property [*acks*](https://kafka.apache.org/documentation/#acks) of the Kafka client is used to 
configure what acknowledgements a producer expects from the cluster for each message.
The value of this property determines the maximum *Quality of Service* level the producer can achieve.
Kafka supports the following settings:

* `0`: the producer does not wait for any acknowledgements. This provides no guarantees.
* `1`: the producer requests only an acknowledgement from one broker. If the broker goes down before others finished 
  replication, then the message will be lost.
* `all` (or equivalent `-1`): the producer requests an acknowledgement that confirms that the message has successfully 
  been replicated to the required number of brokers. This guarantees that the message will not be lost as long as 
  at least one of the replicas remains alive.


### Message Ordering

Hono requires that producers send messages in the right order. Each producer MUST always send messages with the same key
to the same partition. 
For example, each protocol adapter must always send telemetry data reported by a particular device to the same partition 
(e.g. by using the same [partitioner](https://kafka.apache.org/documentation/#partitioner.class) implementation) 
in the same order that they have been received in.

The following producer configuration properties influence if the order of the messages can be guaranteed by Kafka:

* [*retries*](https://kafka.apache.org/documentation/#retries)
* [*max.in.flight.requests.per.connection*](https://kafka.apache.org/documentation/#max.in.flight.requests.per.connection)
* [*enable.idempotence*](https://kafka.apache.org/documentation/#enable.idempotence)

Setting *acks* to `0` effectively disables retries. If *retries* are set to a value greater than zero and 
*max.in.flight.requests.per.connection* is set to a value greater than `1`, Kafka no longer guarantees that messages 
are stored in the order they have been sent. 
The only exception is the *idempotent* producer, which can handle *max.in.flight.requests.per.connection* to be up to `5` 
with retries enabled while still maintaining the message order (since Kafka version 1.0.0).
Note that the *idempotent* producer option requires special privileges for the producer's user to be configured.


### AT LEAST ONCE Producers

To provide *AT LEAST ONCE* delivery semantics, a producer MUST wait for the acknowledgements from all *in-sync replicas* 
before sending the acknowledgement back to the client.
This can be achieved e.g. by setting *acks* to `all` or, implicitly, by setting *enable.idempotence* to `true`.

The producer MUST retain the order in which it received messages from a client. 
This requires that if *retries* is set to a value greater than zero, *max.in.flight.requests.per.connection* must be set to `1`.
Alternatively, *enable.idempotence* can be set to `true`. 
Disabling retries might cause messages to fail in case of high load or transient transmission failures, so this is not recommended.

The recommended configuration for *AT LEAST ONCE* producers has the following properties:
`enable.idempotence=true`, `acks=all`, `max.in.flight.requests.per.connection=5`, leaving *retries* unset (which defaults to `Integer.MAX`), 
and setting *delivery.timeout.ms* to a reasonable value. This configuration is supported from Kafka version 1.0.0 on. 


### AT MOST ONCE Producers

Every producer that does not wait for acknowledgements from all *in-sync replicas* or does not consider them before sending
an acknowledgement back to the client, is considered to provide only *AT MOST ONCE* delivery semantics.
To achieve this, several strategies are possible:

1. The producer can disable acknowledgements (`acks=0`). This is the fastest mode of delivery but has the drawback of a potential loss of messages without notice.
1. The producer can enable acknowledgements, but not wait for the acknowledgements from the Kafka cluster before acknowledging the message to *its* client.  

The producer MUST retain the order in which it received messages from a client. 
This requires to either set *retries* to `0` or to set *max.in.flight.requests.per.connection* to `1`.

**NB:** To send messages with both delivery semantics with the same producer, it MUST be configured for *AT LEAST ONCE*. 
Such a producer may ignore the outcome of the *produce* operation for *AT MOST ONCE* messages.


## Southbound Operations

The following operation can be used by *Protocol Adapters* to send telemetry data received from devices to downstream consumers like *Business Applications*.

### Produce Telemetry Data

The protocol adapter writes messages to the tenant-specific topic `hono.telemetry.${tenant_id}` where `${tenant_id}` is the ID of the tenant that the client wants to upload telemetry data for.

**Preconditions**

1. Either the topic `hono.telemetry.${tenant_id}` exists, or the broker is configured to automatically create topics on demand.
1. The client is authorized to write to the topic.
1. The device for which the adapter wants to send telemetry data has been registered (see [Device Registration API]({{< relref "/api/device-registration" >}})).

**Message Flow**

It is up to the discretion of the protocol adapter whether it wants to use *AT LEAST ONCE* or *AT MOST ONCE* delivery semantics 
according to [AT LEAST ONCE Producers]({{< relref "#at-least-once-producers" >}}) respectively [AT MOST ONCE Producers]({{< relref "#at-most-once-producers" >}}). 

The recommended Kafka producer properties for *AT LEAST ONCE* delivery are: `enable.idempotence=true`, `acks=all`, `max.in.flight.requests.per.connection=5`, 
leaving *retries* unset, and setting *delivery.timeout.ms* to a reasonable value (supported from Kafka version 1.0.0 on). 

The following sequence diagram illustrates the flow of messages involved in the *HTTP Adapter* producing a telemetry data message to the Kafka cluster implementing *AT MOST ONCE* delivery semantics.

{{< figure src="produce_kafka_qos0.svg" title="Produce telemetry data flow (AT MOST ONCE)" >}}

1. *Device* `4711` PUTs telemetry data to the *HTTP Adapter*
   1. *HTTP Adapter* produces telemetry data to *Kafka Cluster*.
   1. *HTTP Adapter* acknowledges the reception of the data to the *Device*.

The following sequence diagram illustrates the flow of messages involved in the *HTTP Adapter* producing a telemetry data message to the Kafka cluster implementing *AT LEAST ONCE* delivery semantics.

{{< figure src="produce_kafka_qos1.svg" title="Produce telemetry data flow (AT LEAST ONCE)" >}}

1. *Device* `4711` PUTs telemetry data to the *HTTP Adapter*, indicating *QoS Level* 1.
   1. *HTTP Adapter* produces telemetry data to *Kafka Cluster*.
   1. *Kafka Cluster* acknowledges reception of the message.
   1. *HTTP Adapter* acknowledges the reception of the data to the *Device*.

When a Kafka producer raises an exception while sending a telemetry message to Kafka, the protocol adapter MUST NOT try to re-send such rejected messages but SHOULD indicate the failed transfer to the device if the transport protocol provides means to do so.

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
| *content-type*  | yes             | *string*    | A content type indicating the type and characteristics of the data contained in the message value as a valid MIME type, e.g. `text/plain; charset="utf-8"` for a text message or `application/json` etc. The value may be set to `application/octet-stream` if the message message value is to be considered *opaque* binary data. See [RFC 2046](http://www.ietf.org/rfc/rfc2046.txt) for details. |
| *content-encoding* | no           | *string*    | The content encoding as defined in section 3.5 of [RFC 2616](http://www.ietf.org/rfc/rfc2616.txt). |
| *creation-time* | no              | *long*      | The instant in time (milliseconds since the Unix epoch) when the message has been created. This header is mandatory if *ttd* or *ttl* is set, otherwise, it is optional. |
| *device_id*     | yes             | *string*    | The identifier of the device that the data in the message value is originating from. |
| *qos*           | no              | *int*       | The quality of service level of the message. Supported values are `0` for AT MOST ONCE and `1` for AT LEAST ONCE. |
| *ttd*           | no              | *int*       | The *time 'til disconnect* indicates the number of seconds that the device will remain connected to the protocol adapter. The value of this header must be interpreted relative to the message's *creation-time*. A value of `-1` is used to indicate that the device will remain connected until further notice, i.e. until another message indicates a *ttd* value of `0`. In absence of this property, the connection status of the device is to be considered indeterminate. *Backend Applications* might use this information to determine a time window during which the device will be able to receive a command. |
| *ttl*           | no              | *long*      | The *time-to-live* in milliseconds. A consumer of the message SHOULD discard the message if the sum of *creation-time* and *ttl* is greater than the current time (milliseconds since the Unix epoch). |

Protocol adapters MAY add additional headers to the Kafka record. 

The value of the message MUST consist of a byte array containing the telemetry data. The format and encoding of the data MUST be indicated by the *content-type* and (optional) *content-encoding* headers of the message.

## Northbound Operations

The following operation can be used by *Business Applications* to receive telemetry data from Kafka.

### Consume Telemetry Data

Hono delivers messages containing telemetry data reported by a particular device in the same order that they have been received in (using the [Produce Telemetry Data]({{< relref "#produce-telemetry-data" >}}) operation).
*Business Applications* consume messages from the tenant-specific topic `hono.telemetry.${tenant_id}` where `${tenant_id}` represents the ID of the tenant the client wants to retrieve telemetry data for.

**Preconditions**

1. Either the topic `hono.telemetry.${tenant_id}` exists, or the broker is configured to automatically create topics on demand.
1. The client is authorized to read from the topic.
1. The client subscribes to the topic with a Kafka consumer. 

A consumer can provide *AT MOST ONCE* or *AT LEAST ONCE* delivery semantics depending on the offset commit strategy it implements.
If a message has the *qos* Kafka header with the value `1`, the consumer is expected to provide proper 
*AT LEAST ONCE* semantics, and therefore MUST NOT commit the message *offset* before it has been fully processed.

**Message Flow**

The following sequence diagram illustrates the flow of messages involved in a *Business Application* consuming a telemetry data message from the Kafka cluster. The delivery mode used is *AT LEAST ONCE*.

{{< figure src="consume_kafka.svg" title="Consume Telemetry Data" >}}

1. *Business Application* polls telemetry messages from *Kafka Cluster*.
   1. *Kafka Cluster* returns a batch of messages.
1. *Business Application* processes the messages.
1. *Business Application* commits the offset of the last processed telemetry message.
    1. *Kafka Cluster* acknowledges the commit.


**Message Format**

The format of the messages containing the telemetry data is the same as for the [Produce Telemetry Data operation]({{< relref "#produce-telemetry-data" >}}).
