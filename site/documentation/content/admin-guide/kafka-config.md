+++
title = "Kafka Configuration"
weight = 347
+++

Hono may use an *Apache Kafka cluster* as messaging infrastructure.
<!--more-->

In order to configure the Hono components for using a Kafka cluster, see the
[protocol adapter]({{< relref "/admin-guide/common-config#kafka-based-messaging-configuration" >}}),
[command router]({{< relref "/admin-guide/command-router-config#kafka-based-messaging-configuration" >}}) and device
registry admin guides.

## Kafka Broker Configuration

Hono requires the following broker configuration properties to be set:

| Broker config property | Mandatory | Description |
|:---------------------- |:----------|:------------|
| `delete.topic.enable`     | `true`     | Enables deletion of topics. This is needed for the automatic deletion of `hono.command_internal.[adapterInstanceId]` topics after the corresponding protocol adapter instance was stopped. |

## Used Topics

### For Telemetry, Event and Command & Control Messages

The Hono components publish messages to the following, tenant-specific Kafka topics, 
from which downstream applications will consume the messages:

| Topic name                    | Description                           |
|:------------------------------|:--------------------------------------|
| `hono.telemetry.[tenantId]`       | Topic for telemetry messages.         |
| `hono.event.[tenantId]`          | Topic for event messages.             |
| `hono.command_response.[tenantId]` | Topic for command response messages.  |

For command & control messages published by downstream applications and consumed by the Hono command router,
the following topic is used:

| Topic name            | Description                           |
|:----------------------|:--------------------------------------|
| `hono.command.[tenantId]` | Topic for command & control messages. |

The above topics may be created in advance with appropriate replication factor and partition count settings. Otherwise,
the `auto.create.topics.enable` broker configuration property needs to be set to `true` to enable auto-creation of these
topics.

### For Hono internal messages

For messages published and consumed only by Hono components, the following topics are used:

| Topic name                           | Description |
|:-------------------------------------|:------------|
| `hono.command_internal.[adapterInstanceId]` | Topic used for routing of command & control messages between Hono components. |
| `hono.notification.registry-tenant`       | Topic used for notification messages between Hono components about changes to tenant registration data. |
| `hono.notification.registry-device`       | Topic used for notification messages between Hono components about changes to device registration data. |

The `hono.command_internal.[adapterInstanceId]` topic name contains a unique identifier as suffix, created dynamically
on protocol adapter start. Therefore this topic cannot be created in advance. It has to be made sure that the Kafka
admin clients in the protocol adapters are able to create this kind of topic.

The `hono.notification.[suffix]` topics either need to be created in advance, or the `auto.create.topics.enable` broker
configuration property needs to be set to `true` to enable auto-creation of these topics.
