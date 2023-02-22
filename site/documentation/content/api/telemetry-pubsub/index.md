---
title: "Telemetry API Specification for Google Pub/Sub"
linkTitle: "Telemetry API (Pub/Sub)"
weight: 420
resources:
- src: publish_pubsub_qos0.svg
- src: publish_pubsub_qos1.svg
---

The *Telemetry* API is used by *Protocol Adapters* to send telemetry data downstream.
*Business Applications* and other consumers use the API to receive data published by devices belonging to a particular
tenant.

The Telemetry API is defined by means of Google Pub/Sub message exchanges, i.e. a client needs to connect to Hono and configure
Hono for Google Pub/Sub messaging infrastructure in order to use it and invoke operations of the API as described in the following sections. Throughout the remainder of
this page we will simply use *Pub/Sub* when referring to Google Pub/Sub.

The Telemetry API for Pub/Sub is an alternative to the [Telemetry API for Kafka]({{< relref "/api/telemetry-kafka" >}})
for applications that want to consume telemetry data from Pub/Sub instead of an Apache Kafka&trade;
broker.

{{% notice warning %}}
Support for the Google Pub/Sub based Telemetry API is considered **experimental** and may change without further notice.
{{% /notice %}}

## Southbound Operations

The following operation can be used by *Protocol Adapters* to publish telemetry data received from devices to
consumers like *Business Applications*.

### Publish Telemetry Data

The protocol adapter writes messages to the tenant-specific topic `projects/${google_project_id}/topics/${tenant_id}.telemetry`
where `${google_project_id}` is the ID of the Google Cloud project and `${tenant_id}` is the
ID of the tenant that the client wants to upload telemetry data for.

**Preconditions**

1. A Google Cloud Project is set up with the Pub/Sub API enabled.
2. The ID of the Google Cloud Project is declared as mentioned in the [Publisher Configuration]({{< relref "/admin-guide/pubsub-config#publisher-configuration" >}}).
3. The client is authorized to write to the topic.
4. The device for which the adapter wants to send telemetry data has been registered (see
   [Device Registration API]({{< relref "/api/device-registration" >}})).

**Message Flow**

It is up to the discretion of the protocol adapter whether it wants to use *AT LEAST ONCE* or *AT MOST ONCE* delivery
semantics.

The following sequence diagram illustrates the flow of messages involved in the *HTTP Adapter* publishing a telemetry
data message to the Pub/Sub API implementing *AT MOST ONCE* delivery semantics.

{{< figure src="publish_pubsub_qos0.svg" title="Publish telemetry data flow (AT MOST ONCE)" >}}

1. *Device* `4711` PUTs telemetry data to the *HTTP Adapter*
    1. *HTTP Adapter* publishes telemetry data to the *Pub/Sub API*.
    1. *HTTP Adapter* acknowledges the reception of the data to the *Device*.

The following sequence diagram illustrates the flow of messages involved in the *HTTP Adapter* publishing a telemetry
data message to the Pub/Sub API implementing *AT LEAST ONCE* delivery semantics.

{{< figure src="publish_pubsub_qos1.svg" title="Publish telemetry data flow (AT LEAST ONCE)" >}}

1. *Device* `4711` PUTs telemetry data to the *HTTP Adapter*, indicating *QoS Level* 1.
    1. *HTTP Adapter* publishes telemetry data to the *Pub/Sub API*.
    1. *Pub/Sub* acknowledges reception of the message.
    1. *HTTP Adapter* acknowledges the reception of the data to the *Device*.

When a Pub/Sub publisher raises an exception while sending a telemetry message to Pub/Sub, the protocol adapter MUST NOT try
to re-send such rejected messages but SHOULD indicate the failed transfer to the device if the transport protocol
provides means to do so.

**Message Format**

The following table provide an overview of the relevant properties of the message format for a Pub/Sub message as defined in the
[Google Pub/Sub Documentation](https://cloud.google.com/pubsub/docs/reference/rest/v1/PubsubMessage). The message must contain a non-empty data field or at least one attribute (or both).

| Name            | Type        | Description |
| :-------------- | :---------- | :---------- |
| *data*  | *string*    | The message data field contains the base64-encoded string representation of the payload. |
| *attributes* | *map*      | Attributes for this message, which can be used to filter messages on the subscription. |
| *orderingKey* | *string*      | Identifies related messages for which publish order should be respected. If a Subscription has enableMessageOrdering set to true, messages published with the same non-empty orderingKey value will be delivered to subscribers in the order in which they are received by the Pub/Sub system. The orderingKey MUST be the device ID. |

Metadata MUST be set as attributes on a Pub/Sub message.
The following table provides an overview a client needs to set on a *Publish Telemetry Data* message.

| Name            | Mandatory       | Type        | Description |
| :-------------- | :-------------: | :---------- | :---------- |
| *content-type*  | yes             | *string*    | A content type indicating the type and characteristics of the data contained in the message value as a valid MIME type, e.g. `text/plain; charset="utf-8"` for a text message or `application/json` etc. The value may be set to `application/octet-stream` if the message message value is to be considered *opaque* binary data. See [RFC 2046](https://www.ietf.org/rfc/rfc2046.txt) for details. |
| *content-encoding* | no           | *string*    | The content encoding as defined in section 3.5 of [RFC 2616](https://www.ietf.org/rfc/rfc2616.txt). |
| *creation-time* | yes             | *long*      | The instant in time (UTC, milliseconds since the Unix epoch) when the message has been created. |
| *device_id*     | yes             | *string*    | The identifier of the device that the data in the message value is originating from. |
| *qos*           | no              | *int*       | The quality of service level of the message. Supported values are `0` for AT MOST ONCE and `1` for AT LEAST ONCE. |
| *ttd*           | no              | *int*       | The *time 'til disconnect* indicates the number of seconds that the device will remain connected to the protocol adapter. The value of this header must be interpreted relative to the message's *creation-time*. A value of `-1` is used to indicate that the device will remain connected until further notice, i.e. until another message indicates a *ttd* value of `0`. In absence of this property, the connection status of the device is to be considered indeterminate. *Backend Applications* might use this information to determine a time window during which the device will be able to receive a command. |
| *ttl*           | no              | *long*      | The *time-to-live* in milliseconds. A consumer of the message SHOULD discard the message if the sum of *creation-time* and *ttl* is greater than the current time (milliseconds since the Unix epoch). |

Protocol adapters MAY set additional attributes on the Pub/Sub message.

## Northbound Operations

The following operation can be used by *Business Applications* to receive telemetry data from Pub/Sub.

### Subscribe Telemetry Data

To receive telemetry messages from Pub/Sub, a subscription must be created to the tenant-specific topic `projects/${google_project_id}/topics/${tenant_id}.telemetry`
where `${google_project_id}` is the ID of the Google Cloud project and `${tenant_id}` is the ID of the tenant the client wants to retrieve telemetry data for. Only messages published
to the topic after the subscription is created are available to subscriber clients like Business Applications. Please refer to the
[Pub/Sub Subscriber Documentation](https://cloud.google.com/pubsub/docs/subscriber) for more details.

**Preconditions**

1. The topic `projects/${google_project_id}/topics/${tenant_id}.telemetry` exists.
2. A subscription to that topic exists.
3. The client is authorized to Pub/Sub.

**Message Format**

The format of the messages containing the telemetry data is the same as for the
[Publish Telemetry Data operation]({{< relref "#publish-telemetry-data" >}}).