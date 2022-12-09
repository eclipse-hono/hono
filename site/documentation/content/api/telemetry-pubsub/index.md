---
title: "Telemetry API Specification for Pub/Sub"
linkTitle: "Telemetry API (Pub/Sub)"
weight: 405
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
for applications that want to consume telemetry data from a Pub/Sub Messaging Network instead of an Apache Kafka&trade;
broker.

## Southbound Operations

The following operation can be used by *Protocol Adapters* to publish telemetry data received from devices to
consumers like *Business Applications*.

### Publish Telemetry Data

The protocol adapter writes messages to the tenant-specific topic `projects/${project_id}/topics/${tenant_id}.telemetry`
where `${project_id}` is the ID of the Google project and `${tenant_id}` is the
ID of the tenant that the client wants to upload telemetry data for.

**Preconditions**

1. The projectId is declared in the deployment file.
1. The client is authorized to write to the topic.
1. The device for which the adapter wants to send telemetry data has been registered (see
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

The key of the message MUST be the device ID.

Metadata MUST be set as attributes on a Pub/Sub message.
The following table provides an overview a client needs to set on a *Publish Telemetry Data* message.

| Name            | Mandatory       | Type        | Description |
| :-------------- | :-------------: | :---------- | :---------- |
| *content-type*  | yes             | *string*    | A content type indicating the type and characteristics of the data contained in the message value as a valid MIME type, e.g. `text/plain; charset="utf-8"` for a text message or `application/json` etc. The value may be set to `application/octet-stream` if the message message value is to be considered *opaque* binary data. See [RFC 2046](https://www.ietf.org/rfc/rfc2046.txt) for details. |
| *creation-time* | no             | *long*      | The instant in time (UTC, milliseconds since the Unix epoch) when the message has been created. |
| *device_id*     | yes             | *string*    | The identifier of the device that the data in the message value is originating from. |
| *tenant_id*     | yes             | *string*    | The identifier of the tenant the device belongs to. |
| *qos*           | no              | *int*       | The quality of service level of the message. Supported values are `0` for AT MOST ONCE and `1` for AT LEAST ONCE. |

Protocol adapters MAY set additional attributes on the Pub/Sub message.

The value of the message MUST consist of a byte array containing the telemetry data. The format and encoding of the
data MUST be indicated by the *content-type* and (optional) *content-encoding* attributes of the message.
