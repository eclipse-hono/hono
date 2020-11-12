+++
title = "Hono Kafka Client Configuration"
weight = 342
+++

Protocol adapters can be configured to use Kafka for the messaging. The Kafka client used there can be configured with 
environment variables and/or command line options.

{{% note title="Tech preview" %}}
The support of Kafka as a messaging system is currently a preview and not yet ready for production. 
The implementation as well as it's APIs may change with the next version. 
{{% /note %}}

## Producer Configuration Properties

The `org.eclipse.hono.kafka.client.CachingKafkaProducerFactory` factory can be used to create Kafka producers for Hono's Kafka based APIs. 
The producers created by the factory are configured with instances of the class `org.eclipse.hono.kafka.client.KafkaProducerConfigProperties`
which can be used to programmatically configure a producer. 

The configuration needs to be provided in the form `HONO_KAFKA_PRODUCERCONFIG_${PROPERTY}` as an environment variable or
as a command line option in the form `hono.kafka.producerConfig.${property}`, where `${PROPERTY}` respectively 
`${property}` is any of the Kafka client's [producer properties](https://kafka.apache.org/documentation/#producerconfigs). 
The provided configuration is passed directly to the Kafka producer without Hono parsing or validating it.

The following properties can _not_ be set using this mechanism because the protocol adapters use fixed values instead 
in order to implement the message delivery semantics defined by Hono's Telemetry and Event APIs.

| Kafka Producer Config Property | Fixed Value |
| :----------------------------- | :---------- |
| `key.serializer` | `org.apache.kafka.common.serialization.StringSerializer` |
| `value.serializer` | `io.vertx.kafka.client.serialization.BufferSerializer` |
| `enable.idempotence` | `true` |

{{% note title="Enable Kafka based Messaging" %}}
The Kafka client requires the property `bootstrap.servers` to be provided. This variable is the minimal configuration 
required to enable Kafka based messaging.
{{% /note %}}

### Using TLS

The factory can be configured to use TLS for

* authenticating the brokers in the Kafka cluster during connection establishment and
* (optionally) authenticating to the broker using a client certificate

To use this, a Kafka Producer configuration as described in 
[Kafka documentation - section "Security"](https://kafka.apache.org/documentation/#security_configclients) needs to be provided. 
The properties must be prefixed with `HONO_KAFKA_PRODUCERCONFIG_` respectively `hono.kafka.producerConfig.` as shown in 
[Producer Configuration Properties]({{< relref "#producer-configuration-properties" >}}).
The complete reference of available properties and the possible values is available in 
[Kafka documentation - section "Producer Configs"](https://kafka.apache.org/documentation/#producerconfigs).
