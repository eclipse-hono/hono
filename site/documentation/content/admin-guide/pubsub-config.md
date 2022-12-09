+++
title = "Google Pub/Sub Messaging Configuration"
weight = 345
+++

Hono can be configured to support Google Pub/Sub as the messaging infrastructure. The Pub/Sub client used for this purpose
can be configured by means of operating system environment variables.

## Publisher Configuration Properties
The `org.eclipse.hono.client.pubsub.CachingPubSubPublisherFactory` factory can be used to create Pub/Sub publishers for Hono's
Pub/Sub based APIs.

The following table provides an overview of the environment variable for configuring a Pub/Sub publisher.
Note that the variable map to the property of the class `org.eclipse.hono.client.pubsub.PubSubConfigProperties` which is
used to configure a publisher.

The variable names contain `${PREFIX}` as a placeholder for the particular *common prefix* being used. The `${prefix}`
placeholder used in the Java system properties is the same as `${PREFIX}`, using all lower case characters and `.`
instead of `_` as the delimiter, e.g. the environment variable prefix `HONO_PROJECTID` corresponds to the Java system
property prefix `hono.projectId`.

| OS Environment Variable<br>Java System Property | Mandatory | Default Value | Description  |
| :---------------------------------------------- | :-------: | :------------ | :------------|
| `${PREFIX}_PROJECTID`<br>`${prefix}.projectId`   | yes | - | The Project Id of the GCP Project where Pub/Sub is set up. |

## Configuring Tenants to use Pub/Sub based Messaging

Hono's components by default support using Kafka based messaging infrastructure to transmit messages hence and forth
between devices and applications. Hono also supports using Pub/Sub as the messaging infrastructure either as a replacement
for or as an alternative in addition to the Kafka based infrastructure.

In most cases Hono's components will be configured to use either Pub/Sub, AMQP 1.0 or Kafka based messaging infrastructure.
However, in cases where both types of infrastructure are being used, Hono's components need to be able to determine,
which infrastructure should be used for messages of a given tenant. For this purpose, the [configuration properties
registered for a tenant]({{< relref "/api/tenant#tenant-information-format" >}}) support the `ext/messaging-type` property
which can have a value of either `pubsub`,`amqp` or `kafka`.

To be able to send messages to Pub/Sub, the tenant-specific topics need to be created before. For this purpose, the [configuration properties
registered for a tenant]({{< relref "/api/tenant#tenant-information-format" >}}) support the `ext/projectId` property
which should have the value of the GCP Project Id where Pub/Sub is set up.

The following example shows a tenant that is configured to use the Pub/Sub messaging infrastructure:

~~~json
{
  "tenant-id": "TEST_TENANT",
  "enabled": true,
  "ext": {
    "messaging-type": "pubsub",
    "projectId": "GCP_PROJECT_ID"
  }
}
~~~

If not explicitly set, the `ext/messaging-type` property's value is `kafka` which indicates that Kafka is to be used
for the tenant.

{{% notice info %}}
If an adapter is configured to connect to only one type of messaging infrastructure, the tenant specific messaging
type configuration is ignored.
{{% /notice %}}

## Used Topics

### For Telemetry & Event Messages

The Hono components publish messages to the following, tenant-specific Pub/Sub topics,
from which downstream applications will consume the messages:

| Topic name                    | Description                           |
|:------------------------------|:--------------------------------------|
| `projects/[projectId]/topics/[tenantId].telemetry`       | Topic for telemetry messages.         |
| `projects/[projectId]/topics/[tenantId].event`          | Topic for event messages.             |
