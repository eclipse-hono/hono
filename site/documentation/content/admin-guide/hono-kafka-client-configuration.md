+++
title = "Hono Kafka Client Configuration"
weight = 342
+++

Protocol adapters can be configured to use Kafka for the messaging. The Kafka client used there can be configured with 
operating system environment variables and/or Java system properties.

{{% notice info %}}
The support of Kafka as a messaging system is currently a preview and not yet ready for production.
The implementation as well as its APIs may change with the next version.
{{% /notice %}}

## Configure for Kafka based Messaging

The selection of whether to use AMQP or Kafka for the messaging can be configured on the tenant. This requires that
protocol adapters must have the configurations for both messaging networks. 
To configure a tenant to use Kafka, the [tenant configuration]({{< relref "/api/tenant#tenant-information-format" >}})
must contain a field `ext/messaging-type` with value `kafka` (to use AMQP, the value must be `amqp`).
The following example shows a tenant that is configured to use Kafka for messaging:

~~~json
{
  "tenant-id": "TEST_TENANT",
  "enabled": true,
  "ext": {
    "messaging-type": "kafka"
  }
}
~~~

If the configuration of a protocol adapter contains only the connection to one messaging system, this will be used.
**NB**: If only one messaging network is configured at protocol adapters, make sure that tenants are not configured
to use another.

## Producer Configuration Properties

The `org.eclipse.hono.client.kafka.CachingKafkaProducerFactory` factory can be used to create Kafka producers for Hono's
Kafka based APIs. The producers created by the factory are configured with instances of the class
`org.eclipse.hono.client.kafka.KafkaProducerConfigProperties` which can be used to programmatically configure a producer.

The configuration needs to be provided in the form `HONO_KAFKA_PRODUCERCONFIG_${PROPERTY}` as an environment variable or
as a Java system property in the form `hono.kafka.producerConfig.${property}`, where `${PROPERTY}` respectively
`${property}` is any of the Kafka client's [producer properties](https://kafka.apache.org/documentation/#producerconfigs).
The provided configuration is passed directly to the Kafka producer without Hono parsing or validating it.

The following properties can _not_ be set using this mechanism because the protocol adapters use fixed values instead 
in order to implement the message delivery semantics defined by Hono's Telemetry and Event APIs.

| Kafka Producer Config Property | Fixed Value |
| :----------------------------- | :---------- |
| `key.serializer` | `org.apache.kafka.common.serialization.StringSerializer` |
| `value.serializer` | `io.vertx.kafka.client.serialization.BufferSerializer` |
| `enable.idempotence` | `true` |

{{% notice tip %}}
The Kafka client requires the property `bootstrap.servers` to be provided. This variable is the minimal configuration
required to enable Kafka based messaging.
{{% /notice %}}

### Using TLS

The factory can be configured to use TLS for authenticating the brokers in the Kafka cluster during connection
establishment and optionally for authenticating to the broker using a client certificate.
To use this, a Kafka Producer configuration as described in
[Kafka documentation - section "Security"](https://kafka.apache.org/documentation/#security_configclients) needs to be
provided. The properties must be prefixed with `HONO_KAFKA_PRODUCERCONFIG_` and `hono.kafka.producerConfig.` respectively as
shown in [Producer Configuration Properties]({{< relref "#producer-configuration-properties" >}}).
The complete reference of available properties and the possible values is available in 
[Kafka documentation - section "Producer Configs"](https://kafka.apache.org/documentation/#producerconfigs).

## Consumer Configuration Properties

Consumers for Hono's Kafka based APIs are configured with instances of the class
`org.eclipse.hono.client.kafka.consumer.KafkaConsumerConfigProperties` which can be used to programmatically configure a consumer.

The configuration needs to be provided in the form `HONO_KAFKA_CONSUMERCONFIG_${PROPERTY}` as an environment variable or
as a Java system property in the form `hono.kafka.consumerConfig.${property}`, where `${PROPERTY}` respectively
`${property}` is any of the Kafka client's [consumer properties](https://kafka.apache.org/documentation/#consumerconfigs).
The provided configuration is passed directly to the Kafka consumer without Hono parsing or validating it.

The following properties can _not_ be set using this mechanism because the protocol adapters use fixed values instead.

| Kafka Consumer Config Property | Fixed Value |
| :----------------------------- | :---------- |
| `key.deserializer` | `org.apache.kafka.common.serialization.StringDeserializer` |
| `value.deserializer` | `io.vertx.kafka.client.serialization.BufferDeserializer` |

{{% notice tip %}}
The Kafka client requires the property `bootstrap.servers` to be provided. This variable is the minimal configuration
required to enable Kafka based messaging.
{{% /notice %}}

### Using TLS

The factory can be configured to use TLS for authenticating the brokers in the Kafka cluster during connection establishment
and optionally for authenticating to the broker using a client certificate.
To use this, a Kafka Consumer configuration as described in
[Kafka documentation - section "Security"](https://kafka.apache.org/documentation/#security_configclients) needs to be provided.
The properties must be prefixed with `HONO_KAFKA_CONSUMERCONFIG_` and `hono.kafka.consumerConfig.` respectively as shown in
[Consumer Configuration Properties]({{< relref "#consumer-configuration-properties" >}}).
The complete reference of available properties and the possible values is available in
[Kafka documentation - section "Consumer Configs"](https://kafka.apache.org/documentation/#consumerconfigs).


## Admin Client Configuration Properties

Admin clients for Hono's Kafka based APIs are configured with instances of the class
`org.eclipse.hono.client.kafka.KafkaAdminClientConfigProperties` which can be used to programmatically configure an admin client.

The configuration needs to be provided in the form `HONO_KAFKA_ADMINCLIENTCONFIG_${PROPERTY}` as an environment variable or
as a Java system property in the form `hono.kafka.adminClientConfig.${property}`, where `${PROPERTY}` respectively
`${property}` is any of the Kafka client's [admin client properties](https://kafka.apache.org/documentation/#adminclientconfigs).
The provided configuration is passed directly to the Kafka admin client without Hono parsing or validating it.

{{% notice tip %}}
The Kafka client requires the property `bootstrap.servers` to be provided. This variable is the minimal configuration
required to enable Kafka based messaging.
{{% /notice %}}

### Using TLS

The factory can be configured to use TLS for authenticating the brokers in the Kafka cluster during connection establishment
and optionally for authenticating to the broker using a client certificate.
To use this, a Kafka admin client configuration as described in
[Kafka documentation - section "Security"](https://kafka.apache.org/documentation/#security_configclients) needs to be provided.
The properties must be prefixed with `HONO_KAFKA_ADMINCLIENTCONFIG_` and `hono.kafka.adminClientConfig.` respectively as shown in
[Admin Client Configuration Properties]({{< relref "#admin-client-configuration-properties" >}}).
The complete reference of available properties and the possible values is available in
[Kafka documentation - section "Admin Configs"](https://kafka.apache.org/documentation/#adminclientconfigs).

## Common Configuration Properties

Configuration properties that are common to all the client types described above can be put in a common configuration section.
This will avoid having to define duplicate configuration properties for the different client types.

Relevant properties are `bootstrap.servers` and the properties related to authentication and TLS configuration (see chapters above).

The properties must be prefixed with `HONO_KAFKA_COMMONCLIENTCONFIG_` and `hono.kafka.commonClientConfig.` respectively.

A property with the same name defined in the configuration of one of the specific client types above will have precedence
over the common property.

## Kafka client metrics configuration

Protocol adapters and the Command Router component by default report a set of metrics concerning the Kafka clients used
for sending and receiving messages.

The metrics support can be configured using the following environment variables or corresponding system properties:

| OS Environment Variable<br>Java System Property | Mandatory | Default | Description                                    |
| :---------------------------------------------- | :-------: | :------ | :----------------------------------------------|
| `HONO_KAFKA_METRICS_ENABLED`<br>`hono.kafka.metrics.enabled` | no | `true` | If set to `false`, no Kafka client metrics will be reported.  |
| `HONO_KAFKA_METRICS_USEDEFAULTMETRICS`<br>`hono.kafka.metrics.useDefaultMetrics` | no | `true` | If set to `true`, a set of Kafka consumer and producer related default metrics will be reported. Additional metrics can be added via the `HONO_KAFKA_METRICS_METRICSPREFIXES` property described below. |
| `HONO_KAFKA_METRICS_METRICSPREFIXES`<br>`hono.kafka.metrics.metricsPrefixes`| no | - | A comma separated list of prefixes of the metrics to be reported for the Kafka clients (in addition to the default metrics if these are used). The complete list of metrics can be viewed in the [Kafka documentation](https://kafka.apache.org/documentation.html#selector_monitoring). The metric names to be used here have the form `kafka.[metric group].[metric name]`. The metric group can be obtained from the *type* value in the *MBean name*, omitting the `-metrics` suffix. E.g. for an MBean name containing `kafka.consumer:type=consumer-fetch-manager-metrics`, the group is `consumer.fetch.manager` (all dashes are to be replaced by dots in metric group and name). An example of a corresponding metric name would be `kafka.consumer.fetch.manager.bytes.consumed.total` <br>To include all metrics, the property value can be set to the `kafka` prefix. |


## Kafka Client Version

Hono components include a recent version of the Kafka client as defined by the Quarkus framework. In general, the
included client should work with recent versions of Kafka brokers. However, no cross version testing is being done
as part of the Hono build process. If you experience any issues using Hono with an older Kafka version, please try
to connect it to a recent Kafka cluster instead before raising an issue.
