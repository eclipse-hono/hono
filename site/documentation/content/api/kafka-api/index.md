---
title: "Kafka-based APIs"
weight: 416
---

The Kafka-based APIs of Eclipse Hono&trade; provide an alternative to the existing APIs based on AMQP 1.0.
With these APIs, clients publish data to as well as consume data from an Apache Kafka&reg; cluster instead of using an
AMQP messaging network.

## Kafka-based Messaging

Using Kafka instead of AMQP comes with slightly different behavior. Kafka provides a Publish/Subscribe messaging style.
Every message is sent by a *producer* to a *topic* in a Kafka cluster, where it will be persisted by (multiple) *brokers*.
Each topic consists of a configurable number of *partitions*. The *key* of a message determines to which partition it
will be written. Each partition is *replicated* to a configurable number of brokers in the cluster to provide
fault-tolerance if a broker goes down.

A Kafka message (also called record) consists of a key, a value, a timestamp, and headers.

Multiple *consumers* can read the messages at the same time and they can read them repeatedly (if an error occurred).
This decoupling of producer and consumers has the consequence that a producer does not get feedback about the consumption
of messages, it does not know *if* and *when* a message will be read by any consumer(s).
For example, a protocol adapter can only confirm to the device that the Kafka cluster successfully persisted a telemetry
message, not if a *Business Application* received it.

Messages are usually deleted from a topic at some point &ndash; regardless of whether they have been processed by a
consumer. Care should therefore be taken to set each topic's *log retention time* to a reasonable value in the Kafka
configuration.

See the [Kafka documentation](https://kafka.apache.org/documentation/#configuration) for details about Kafka's
configuration properties. 

### Quality of Service

The Kafka client's [*acks*](https://kafka.apache.org/documentation/#acks) configuration property is used to
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

For each *partition* of a topic, Kafka guarantees that consumers receive messages in the same order that the producer 
has written them to the partition. Hono's protocol adapters, therefore, use the device identifier as the key when 
writing messages to downstream topics, thus making sure that messages originating from the same device always end up 
in the same partition.
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
This requires that if *retries* is set to a value greater than zero, *max.in.flight.requests.per.connection* must be
set to `1`. Alternatively, *enable.idempotence* can be set to `true`. 
Disabling retries might cause messages to fail in case of high load or transient transmission failures, so this is not
recommended.

The recommended configuration for *AT LEAST ONCE* producers has the following properties:
`enable.idempotence=true`, `acks=all`, `max.in.flight.requests.per.connection=5`, leaving *retries* unset (which defaults to
`Integer.MAX`), and setting *delivery.timeout.ms* to a reasonable value. This configuration is supported from Kafka
version 1.0.0 on.


### AT MOST ONCE Producers

Every producer that does not wait for acknowledgements from all *in-sync replicas* or does not consider them before
sending an acknowledgement back to the client, is considered to provide only *AT MOST ONCE* delivery semantics.
To achieve this, several strategies are possible:

1. The producer can disable acknowledgements (`acks=0`). This is the fastest mode of delivery but has the drawback of
   a potential loss of messages without notice.
1. The producer can enable acknowledgements, but not wait for the acknowledgements from the Kafka cluster before
   acknowledging the message to *its* client.

The producer MUST retain the order in which it received messages from a client. 
This requires to either set *retries* to `0` or to set *max.in.flight.requests.per.connection* to `1`.

{{% notice tip %}}
To send messages with both delivery semantics with the same producer, it MUST be configured for *AT LEAST ONCE*.
Such a producer may ignore the outcome of the *produce* operation for *AT MOST ONCE* messages.
{{% /notice %}}
