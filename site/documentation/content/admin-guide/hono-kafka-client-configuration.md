+++
title = "Hono Kafka Client Configuration"
weight = 342
+++

Protocol adapters and other Hono components can be configured to use Kafka as the messaging infrastructure.
The Kafka client used for that purpose can be configured with environment variables and/or command line options.

{{% note title="Tech preview" %}}
Support for Kafka as the messaging infrastructure is currently considered a *Technology Preview* and should therefore
not (yet) be used in production scenarios.
In particular, the APIs and client implementation may change without prior notice.
{{% /note %}}

## Configuring Kafka based Messaging

The selection of whether to use AMQP or Kafka for the messaging can be configured at the tenant level.
This requires that protocol adapters must have the configurations for both messaging networks.
To configure a tenant to use Kafka, the [tenant configuration]({{< relref "/api/tenant#tenant-information-format" >}}) 
must contain a property `ext/messaging-type` with value `kafka`. If no such property is configured or the property has
value `amqp`, the AMQP 1.0 Messaging Network is used instead.
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
**NB**: If only one messaging network is configured at protocol adapters, make sure that tenants are not configured to use another.

## Configuring Kafka Producers

The `org.eclipse.hono.client.kafka.CachingKafkaProducerFactory` factory can be used to create Kafka *producers* for publishing
messages to Hono's Kafka based APIs.

The following table provides an overview of the configuration variables and corresponding command line options for configuring
the factory. Note that the variables map to the properties of class `org.eclipse.hono.client.kafka.KafkaProducerConfigProperties`
which can be used to programmatically configure the producers being created by the factory.

| Environment Variable<br>Command Line Option | Mandatory | Default Value | Description  |
| :------------------------------------------ | :-------: | :------------ | :------------|
| `HONO_KAFKA_DEFAULTCLIENTIDPREFIX`<br>`--hono.kafka.defaultClientIdPrefix` | no | - | The prefix for the client ID that is passed to the Kafka server to allow application specific server-side request logging.<br>If the configuration properties read from the configured files already contain a value for key `client.id`, that value will be used and this property's value will be ignored. |
| `HONO_KAFKA_PROPERTYFILES`<br>`--hono.kafka.propertyFiles` | yes | - | A comma separated list of paths to files containing [Kafka properties](https://kafka.apache.org/documentation/#configuration) that should be used for configuring the connection to the broker and the producers created. The files will be loaded in the specified order. The provided configuration is passed directly to the Kafka client without Hono parsing or validating it. |

The following properties will be ignored if set in any of the configured property files.
Instead, Hono uses the specified fixed values in order to implement the message delivery semantics defined by Hono's
Telemetry and Event APIs.

| Property Name      | Fixed Value |
| :----------------- | :---------- |
| `key.serializer`     | `org.apache.kafka.common.serialization.StringSerializer` |
| `value.serializer`   | `io.vertx.kafka.client.serialization.BufferSerializer` |
| `enable.idempotence` | `true` |

{{% note title="Minimum required Configuration" %}}
The Kafka client requires at least the `bootstrap.servers` property to be set in one of the configured property files.
{{% /note %}}

### Using TLS

The factory can be configured to use TLS for

* authenticating the brokers in the Kafka cluster during connection establishment and
* (optionally) authenticating to the broker using a client certificate

Please refer to the [Producer Configs](https://kafka.apache.org/documentation/#producerconfigs) section of the Kafka
documentation for a reference of corresponding properties and their possible values.

## Configuring Kafka Consumers

The following table provides an overview of the configuration variables and corresponding command line options for configuring
consumers. Note that the variables map to the properties of class `org.eclipse.hono.client.kafka.consumer.KafkaConsumerConfigProperties`
which can be used to programmatically configure the consumers being created.

| Environment Variable<br>Command Line Option | Mandatory | Default Value | Description  |
| :------------------------------------------ | :-------: | :------------ | :------------|
| `HONO_KAFKA_DEFAULTCLIENTIDPREFIX`<br>`--hono.kafka.defaultClientIdPrefix` | no | - | The prefix for the client ID that is passed to the Kafka server to allow application specific server-side request logging.<br>If the configuration properties read from the configured files already contain a value for key `client.id`, that value will be used and this property's value will be ignored. |
| `HONO_KAFKA_PROPERTYFILES`<br>`--hono.kafka.propertyFiles` | yes | - | A comma separated list of paths to files containing [Kafka properties](https://kafka.apache.org/documentation/#configuration) that should be used for configuring the connection to the broker and the consumers created. The files will be loaded in the specified order. The provided configuration is passed directly to the Kafka client without Hono parsing or validating it. |
| `HONO_KAFKA_POLLTIMEOUT`<br>`--hono.kafka.pollTimeout` | no | `100` | The time to wait for records when polling the Kafka broker in milliseconds. |

The following properties will be ignored if set in any of the configured property files.
Instead, Hono uses the specified fixed values in order to implement the message delivery semantics defined by Hono's
Telemetry and Event APIs.

| Property Name      | Fixed Value |
| :----------------- | :---------- |
| `key.deserializer`   | `org.apache.kafka.common.serialization.StringDeserializer` |
| `value.deserializer` | `io.vertx.kafka.client.serialization.BufferDeserializer` |

{{% note title="Minimum required Configuration" %}}
The Kafka client requires at least the `bootstrap.servers` property to be set in one of the configured property files.
{{% /note %}}

### Using TLS

The factory can be configured to use TLS for

* authenticating the brokers in the Kafka cluster during connection establishment and
* (optionally) authenticating to the broker using a client certificate

Please refer to the [Consumer Configs](https://kafka.apache.org/documentation/#consumerconfigs) section of the Kafka
documentation for a reference of corresponding properties and their possible values.

## Configuring Admin Clients

The following table provides an overview of the configuration variables and corresponding command line options for configuring
an Admin Client. Note that the variables map to the properties of class `org.eclipse.hono.client.kafka.KafkaAdminClientConfigProperties`
which can be used to programmatically configure the admin clients being created.

| Environment Variable<br>Command Line Option | Mandatory | Default Value | Description  |
| :------------------------------------------ | :-------: | :------------ | :------------|
| `HONO_KAFKA_DEFAULTCLIENTIDPREFIX`<br>`--hono.kafka.defaultClientIdPrefix` | no | - | The prefix for the client ID that is passed to the Kafka server to allow application specific server-side request logging.<br>If the configuration properties read from the configured files already contain a value for key `client.id`, that value will be used and this property's value will be ignored. |
| `HONO_KAFKA_PROPERTYFILES`<br>`--hono.kafka.propertyFiles` | yes | - | A comma separated list of paths to files containing [Kafka properties](https://kafka.apache.org/documentation/#configuration) that should be used for configuring the connection to the broker and the clients created. The files will be loaded in the specified order. The provided configuration is passed directly to the Kafka client without Hono parsing or validating it. |

{{% note title="Minimum required Configuration" %}}
The Kafka client requires at least the `bootstrap.servers` property to be set in one of the configured property files.
{{% /note %}}

### Using TLS

The factory can be configured to use TLS for

* authenticating the brokers in the Kafka cluster during connection establishment and
* (optionally) authenticating to the broker using a client certificate

Please refer to the [AdminClient Configs](https://kafka.apache.org/documentation/#adminclientconfigs) section of the Kafka
documentation for a reference of corresponding properties and their possible values.
