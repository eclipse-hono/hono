---
title: "Event API Specification for Pub/Sub"
linkTitle: "Event API (Pub/Sub)"
weight: 405
resources:
- src: publish_pubsub.svg
---

The *Event* API is used by *Protocol Adapters* to send event messages downstream.
*Business Applications* and other consumers use the API to receive messages published by devices belonging to a
particular tenant.

The Event API is defined by means of Google Pub/Sub message exchanges, i.e. a client needs to connect to Hono and configure
Hono for Google Pub/Sub messaging infrastructure in order to use it and invoke operations of the API as described in the following sections. Throughout the remainder of
this page we will simply use *Pub/Sub* when referring to Google Pub/Sub.

The Event API for Pub/Sub is an alternative to the [Event API for Kafka]({{< relref "/api/event-kafka" >}}) for
applications that want to consume events from a Pub/Sub Messaging Network instead of an Apache Kafka&trade; broker.

## Southbound Operations

The following operation can be used by *Protocol Adapters* to publish event data received from devices to
consumers like *Business Applications*.

### Publish Event Data

The protocol adapter writes messages to the tenant-specific topic `projects/${project_id}/topics/${tenant_id}.event`
where `${project_id}` is the ID of the Google project and `${tenant_id}` is the
ID of the tenant that the client wants to upload telemetry data for.

**Preconditions**

1. The projectId is declared in the deployment file.
2. The client is authorized to write to the topic.
3. The device for which the adapter wants to send telemetry data has been registered (see
   [Device Registration API]({{< relref "/api/device-registration" >}})).

**Message Flow**

Hono supports *AT LEAST ONCE* delivery of *Event* messages only.

The following sequence diagram illustrates the flow of messages involved in the *MQTT Adapter* publishing an event to the Pub/Sub API.

{{< figure src="publish_pubsub.svg" title="Publish event flow" >}}

1. *Device* `4711` publishes an event using MQTT QoS 1.
   1. *MQTT Adapter* transfers data to the *Pub/Sub API*.
   1. *Pub/Sub API* acknowledges reception of the message.
   1. *MQTT Adapter* acknowledges the reception of the message to the *Device*.

When a Pub/Sub publisher raises an exception while sending an event message to Pub/Sub, the protocol adapter MUST NOT try
to re-send such rejected messages but MUST indicate the failed transfer to the device if the transport protocol
provides means to do so.

**Message Format**

See [Telemetry API for Pub/Sub]({{< relref "/api/telemetry-pubsub#publish-telemetry-data" >}}) for definition of message format.
