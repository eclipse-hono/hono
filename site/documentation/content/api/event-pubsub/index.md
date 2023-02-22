---
title: "Event API Specification for Google Pub/Sub"
linkTitle: "Event API (Pub/Sub)"
weight: 421
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
applications that want to consume events from Pub/Sub instead of an Apache Kafka&trade; broker.

{{% notice warning %}}
Support for the Google Pub/Sub based Event API is considered **experimental** and may change without further notice.
{{% /notice %}}

## Southbound Operations

The following operation can be used by *Protocol Adapters* to publish event messages received from devices to
consumers like *Business Applications*.

### Publish Event Data

The protocol adapter writes messages to the tenant-specific topic `projects/${google_project_id}/topics/${tenant_id}.event`
where `${google_project_id}` is the ID of the Google Cloud project and `${tenant_id}` is the
ID of the tenant that the client wants to upload event messages for.

**Preconditions**

1. A Google Cloud Project is set up with the Pub/Sub API enabled.
2. The ID of the Google Cloud Project is declared as mentioned in the [Publisher Configuration]({{< relref "/admin-guide/pubsub-config#publisher-configuration" >}}).
3. The client is authorized to write to the topic.
4. The device for which the adapter wants to send event messages has been registered (see
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

## Northbound Operations

The following operation can be used by *Business Applications* to receive event messages from Pub/Sub.

### Subscribe Events

To receive event messages from Pub/Sub, a subscription must be created to the tenant-specific topic `projects/${google_project_id}/topics/${tenant_id}.event`
where `${google_project_id}` is the ID of the Google Cloud project and `${tenant_id}` is the ID of the tenant the client wants to retrieve event messages for.
Only messages published to the topic after the subscription is created are available to subscriber clients like Business Applications. Please refer to the
[Pub/Sub Subscriber Documentation](https://cloud.google.com/pubsub/docs/subscriber) for more details.

**Preconditions**

1. The topic `projects/${google_project_id}/topics/${tenant_id}.event` exists.
2. A subscription to that topic exists.
3. The client is authorized to Pub/Sub.

**Message Format**

See [Telemetry API for Pub/Sub]({{< relref "/api/telemetry-pubsub#publish-telemetry-data" >}}) for the definition of the
message format. 
