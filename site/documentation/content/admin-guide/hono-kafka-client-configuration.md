+++
title = "Hono Kafka Client Configuration"
weight = 342
+++

Protocol adapters can be configured to use Kafka for the messaging. The Kafka client used there can be configured with 
command line options.

## Producer Configuration Properties

The `org.eclipse.hono.kafka.client.CachingKafkaProducerFactory` factory can be used to create Kafka producers for Hono's Kafka based APIs. 
The producers created by the factory are configured with instances of the class `org.eclipse.hono.kafka.client.KafkaProducerConfigProperties`
which can be used to programmatically configure a producer. 

The configuration needs to be provided in the form `hono.kafka.producerConfig.${property}`, where `${property}` is any of the 
Kafka client's [producer properties](https://kafka.apache.org/documentation/#producerconfigs). 
The provided configuration is passed directly to the Kafka producer without Hono parsing or validating it.
The following table shows which properties _are_ changed by Hono.

| Kafka Producer Config Property | Mandatory Value | Description |
| :----------------------------- | :-------------- | :-----------|
| `--hono.kafka.producerConfig.key.serializer` | `org.apache.kafka.common.serialization.StringSerializer` | The record keys in Hono are always strings. Any other specified value is ignored. |
| `--hono.kafka.producerConfig.value.serializer` | `io.vertx.kafka.client.serialization.BufferSerializer` | The record values in Hono are always byte arrays.  Any other specified value is ignored. |
| `--hono.kafka.producerConfig.enable.idempotence` | `true` | The Hono Kafka client uses only idempotent producers.  Any other specified value is ignored. |

{{% note title="Enable Kafka based Messaging" %}}
The Kafka client requires the property `bootstrap.servers` to be provided. 
Since `org.eclipse.hono.kafka.client.KafkaProducerConfigProperties` already sets the *key.serializer* and 
*value.serializer* properties, configuring `hono.kafka.producerConfig.bootstrap.servers` is the minimal configuration 
required to enable Kafka based messaging.
{{% /note %}}

### Using TLS

The factory can be configured to use TLS for

* authenticating the brokers in the Kafka cluster during connection establishment and
* (optionally) authenticating to the broker using a client certificate

To use this, a Kafka Producer configuration as described in 
[Kafka documentation - section "Security"](https://kafka.apache.org/documentation/#security_configclients) needs to be provided. 
The properties must be prefixed with `hono.kafka.producerConfig.` as shown in [Producer Configuration Properties]({{< relref "#producer-configuration-properties" >}}).
The complete reference of available properties and the possible values can be found in [Kafka documentation - section "Producer Configs"](https://kafka.apache.org/documentation/#producerconfigs).
