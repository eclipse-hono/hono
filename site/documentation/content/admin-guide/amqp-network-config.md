+++
title = "AMQP 1.0 Messaging Network Configuration"
weight = 345
+++

The *Qpid Dispatch Router*, together with the *Apache Artemis* message broker, serves as the default *AMQP 1.0
Messaging Network* that is used in Hono's example deployment as described in the
[Deployment Guides]({{< relref "deployment" >}}).
<!--more-->

The Dispatch Router component exposes service endpoints implementing the *north bound* part of Hono's
[Telemetry]({{< relref "/api/telemetry" >}}), [Event]({{< relref "api/event" >}}) and
[Command & Control]({{< relref "/api/command-and-control" >}}) APIs which are used by applications to interact
with devices.

## Configuring Tenants to use AMQP 1.0 based Messaging

Hono's components by default support using Kafka based messaging infrastructure to transmit messages hence and forth
between devices and applications. Hono also supports using AMQP 1.0 as the messaging infrastructure either as a replacement
for or as an alternative in addition to the Kafka based infrastructure.

In most cases Hono's components will be configured to use either AMQP 1.0 or Kafka based messaging infrastructure.
However, in cases where both types of infrastructure are being used, Hono's components need to be able to determine,
which infrastructure should be used for messages of a given tenant. For this purpose, the [configuration properties
registered for a tenant]({{< relref "/api/tenant#tenant-information-format" >}}) support the `ext/messaging-type` property
which can have a value of either `amqp` or `kafka`.

The following example shows a tenant that is configured to use the AMQP 1.0 messaging infrastructure:

~~~json
{
  "tenant-id": "TEST_TENANT",
  "enabled": true,
  "ext": {
    "messaging-type": "amqp"
  }
}
~~~

If not explicitly set, the `ext/messaging-type` property's value is `kafka` which indicates that Kafka is to be used
for the tenant.

{{% notice info %}}
If an adapter is configured to connect to only one type of messaging infrastructure, the tenant specific messaging
type configuration is ignored.
{{% /notice %}}

## Dispatch Router Configuration

The Dispatch Router is part of the [Apache Qpid project](https://qpid.apache.org). Hono uses the Dispatch Router by
means of the [EnMasse](https://enmasseproject.github.io) project's
[Dispatch Router Docker image](https://quay.io/repository/enmasse/qdrouterd-base) created from the Qpid project
source code.

The Dispatch Router can be configured by means of configuration files.
The [Eclipse IoT Packages](https://www.eclipse.org/packages/) project hosts an
[example configuration](https://github.com/eclipse/packages/blob/master/charts/hono/config/router/qdrouterd.json).
Please refer to the [Dispatch Router documentation](https://qpid.apache.org/components/dispatch-router/index.html)
for details regarding the configuration file format and options.

## Artemis Broker Configuration

The Artemis Broker is part of the [Apache ActiveMQ project](https://activemq.apache.org). Hono uses Artemis by means
of the [EnMasse](https://enmasseproject.github.io) project's
[Artemis Docker image](https://hub.docker.com/r/enmasseproject/activemq-artemis) created from the Artemis project
source code.

The Artemis Broker can be configured by means of configuration files.
The [Eclipse IoT Packages](https://www.eclipse.org/packages/) project hosts an
[example configuration](https://github.com/eclipse/packages/blob/master/charts/hono/config/artemis/broker.xml).
Please refer to the [Artemis documentation](https://activemq.apache.org/components/artemis/documentation/) for details
regarding the configuration file format and options.
