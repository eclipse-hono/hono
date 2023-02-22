+++
title = "Google Pub/Sub Messaging Configuration"
weight = 348
+++

Hono can be configured to support Google Pub/Sub as the messaging infrastructure. Google Pub/Sub can only be used to forward
Telemetry and Event messages, but not Command messages. The Pub/Sub client used for this purpose
can be configured by means of operating system environment variables.

Supporting Google Pub/Sub, Hono must run on Google Kubernetes Engine to authenticate to the Google Pub/Sub API.
To authenticate to the Google Pub/Sub API, Workload Identity is used and has to be configured as described in the
[Google Cloud Documentation](https://cloud.google.com/kubernetes-engine/docs/how-to/workload-identity).

{{% notice warning %}}
Support for Google Pub/Sub based messaging infrastructure is considered **experimental** and may change without further notice.
{{% /notice %}}

## Publisher Configuration

The `org.eclipse.hono.client.pubsub.CachingPubSubPublisherFactory` factory can be used to create Pub/Sub publishers for Hono's
Pub/Sub based APIs.

Please refer to the [Quarkus Google Cloud Services extension](https://quarkiverse.github.io/quarkiverse-docs/quarkus-google-cloud-services/main/index.html)
documentation for details regarding configuration of the PubSub client.

## Configuring Tenants to use Pub/Sub based Messaging

Hono's components by default support using Kafka based messaging infrastructure to transmit messages hence and forth
between devices and applications. Hono also supports using Pub/Sub as the messaging infrastructure either as a replacement
for or as an alternative in addition to the Kafka based infrastructure.

In most cases Hono's components will be configured to use either Pub/Sub, AMQP 1.0 or Kafka based messaging infrastructure.
However, in cases where more than one type of infrastructure is being used, Hono's components need to be able to determine,
which infrastructure should be used for messages of a given tenant. For this purpose, the [configuration properties
registered for a tenant]({{< relref "/api/tenant#tenant-information-format" >}}) support the `ext/messaging-type` property
which can have a value of either `pubsub`, `amqp` or `kafka`.

The following example shows a tenant that is configured to use the Pub/Sub based messaging infrastructure:

~~~json
{
  "tenant-id": "TEST_TENANT",
  "enabled": true,
  "ext": {
    "messaging-type": "pubsub"
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
| `projects/${google_project_id}/topics/${tenant_id}.telemetry`       | Topic for telemetry messages.         |
| `projects/${google_project_id}/topics/${tenant_id}.event`          | Topic for event messages.             |
