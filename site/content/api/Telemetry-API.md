+++
title = "Telemetry API"
weight = 420
+++

The *Telemetry* API is used by *Devices* to send data downstream.
*Business Applications* and other consumers use the API to receive data published by devices belonging to a particular tenant.
<!--more-->

The Telemetry API is defined by means of AMQP 1.0 message exchanges, i.e. a client needs to connect to Hono using AMQP 1.0 in order to invoke operations of the API as described in the following sections. Throughout the remainder of this page we will simply use *AMQP* when referring to AMQP 1.0.

# Southbound Operations

The following operations can be used by *Devices* and/or *Protocol Adapters* (to which the devices are connected) to publish telemetry data for consumption by downstream consumers like *Business Applications*.

Both *Devices* as well as *Protocol Adapters* will be referred to as *clients* in the remainder of this section.

## Upload Telemetry Data

**Preconditions**

1. Client has established an AMQP connection with Hono's Telemetry endpoint.
1. Client has established an AMQP link in role *sender* with Telemetry endpoint using target address `telemetry/${tenant_id}` where `${tenant_id}` is the ID of the tenant that the client wants to upload telemetry data for. 
1. The device for which the client wants to upload telemetry data has been registered (see [Device Registration API]({{< relref "Device-Registration-API.md" >}})).
1. Client has obtained a *registration assertion* for the device from the Device Registration service by means of the [assert Device Registration operation]({{< relref "Device-Registration-API.md#assert-device-registration" >}}).

The client indicates its preferred message delivery mode by means of the *snd-settle-mode* and *rcv-settle-mode* fields of its *attach* frame during link establishment. Hono will receive messages using a delivery mode according to the following table:

| snd-settle-mode        | rcv-settle-mode        | Delivery semantics |
| :--------------------- | :--------------------- | :----------------- |
| `unsettled`, `mixed` | `first`               | Hono will acknowledge and settle received messages spontaneously. Hono will accept any re-delivered messages. Using `unsettled` for the *snd-settle-mode* results in *AT LEAST ONCE* delivery semantics and is the recommended mode for clients uploading telemetry data. |
| `settled`             | `first`               | Hono will acknowledge and settle received messages spontaneously. This is the fastest mode of delivery but has the drawback of less reliable end-to-end flow control. This corresponds to *AT MOST ONCE* delivery. |

All other combinations are not supported by Hono and result in a termination of the link. In particular, Hono does **not** support reliable transmission of telemetry data, i.e. messages containing telemetry data MAY be lost.

**Message Flow**

The following sequence diagram illustrates the flow of messages involved in the *MQTT Adapter* uploading a telemetry data message to Hono Messaging's Telemetry endpoint. The delivery mode being used is *AT LEAST ONCE*.

![Upload telemetry data flow](../uploadTelemetry_Success.png)

1. *MQTT Adapter* sends telemetry data for device `4711`.
   1. *Hono Messaging* successfully verifies that device `4711` of `TENANT` exists and is enabled by means of validating the *registration assertion* included in the message (see [Device Registration]({{< relref "api/Device-Registration-API.md#assert-device-registration" >}})) and forwards data to *AMQP 1.0 Messaging Network*.
1. *AMQP 1.0 Messaging Network* acknowledges reception of the message.
   1. *Hono Messaging* acknowledges reception of the message.

{{% note %}}
Telemetry messages are delivered unreliably by definition. Thus, the *MQTT Adapter* does not wait for the *disposition* frame sent by *Hono Messaging* to indicate successful delivery of the message but simply discards any disposition frame it receives.
{{% /note %}}

**Message Format**

The following table provides an overview of the properties a client needs to set on an *Upload Telemetry Data* message.

| Name           | Mandatory | Location                 | Type      | Description |
| :------------- | :-------: | :----------------------- | :-------- | :---------- |
| *content-type* | yes       | *properties*             | *symbol*  | SHOULD be set to *application/octet-stream* if the message payload is to be considered *opaque* binary data. In most cases though, the client should be able to set a more specific content type indicating the type and characteristics of the data contained in the payload, e.g. `text/plain; charset="utf-8"` for a text message or `application/json` etc. |
| *device_id*    | yes       | *application-properties* | *string*  | MUST contain the ID of the device the data in the payload has been reported by. |
| *reg_assertion*| yes       | *application-properties* | *string*  | A [JSON Web Token](https://jwt.io/introduction/) issued by the [Device Registration service]({{< relref "api/Device-Registration-API.md#assert-device-registration" >}}) asserting the device's registration status. |

The body of the message MUST consist of a single AMQP *Data* section containing the telemetry data. The format and encoding of the data MUST be indicated by the *content-type* and (optional) *content-encoding* properties of the message.

Any additional properties set by the client in either the *properties* or *application-properties* sections are preserved by Hono, i.e. these properties will also be contained in the message delivered to consumers. However, the *reg_assertion* contained in the *application-properties* will **not** be propagated downstream.

Note that Hono does not return any *application layer* message back to the client in order to signal the outcome of the operation. Instead, Hono signals reception of the message by means of the AMQP `ACCEPTED` outcome if the message complies with the formal requirements. Note that this does **not** mean that the telemetry message has been successfully forwarded to the AMQP 1.0 messaging network.

Whenever a client sends a telemetry message that cannot be processed because it does not conform to the message format defined above, Hono settles the message transfer using the AMQP `REJECTED` outcome containing an `amqp:decode-error`. Clients should **not** try to re-send such rejected messages unaltered.

Note that the outcomes above will *not* be transferred back to the sender if the sender uses *snd-settle-mode* `settled` (which is recommended).

# Northbound Operations

## Receive Telemetry Data

Hono delivers messages containing telemetry data reported by a particular device in the same order that they have been received in (using the *Upload Telemetry Data* operation defined above).
Hono MAY drop telemetry messages that it cannot deliver to any consumers. Reasons for this include that there are no consumers connected to Hono or the existing consumers are not able to process the messages from Hono fast enough.
Hono supports multiple non-competing *Business Application* consumers of telemetry data for a given tenant. Hono allows each *Business Application* to have multiple competing consumers for telemetry data for a given tenant to share the load of processing the messages.

**Preconditions**

1. Client has established an AMQP connection with Hono.
2. Client has established an AMQP link in role *receiver* with Hono using source address `telemetry/${tenant_id}` where `${tenant_id}` represents the ID of the tenant the client wants to retrieve telemetry data for.

Hono supports both *AT MOST ONCE* as well as *AT LEAST ONCE* delivery of telemetry messages. However, clients SHOULD use *AT LEAST ONCE* delivery in order to support end-to-end flow control and therefore SHOULD set the *snd-settle-mode* field to `unsettled` and the *rcv-settle-mode* field to `first` in their *attach* frame during link establishment.

A client MAY indicate to Hono during link establishment that it wants to distribute the telemetry messages received for a given tenant among multiple consumers by including a link property `subscription-name` whose value is shared by all other consumers of the tenant. Hono ensures that messages from a given device are delivered to the same consumer. Note that this also means that telemetry messages MAY not be evenly distributed among consumers, e.g. when only a single device sends data. **NB** This feature is not supported yet.

In addition a client MAY include a boolean link property `ordering-required` with value `false` during link establishment in order to indicate to Hono that it does not require messages being delivered strictly in order per device but instead allows for messages being distributed evenly among the consumers. **NB** This feature is not supported yet.

**Message Flow**

The following sequence diagram illustrates the flow of messages involved in a *Business Application* receiving a telemetry data message from Hono. The delivery mode used is *AT LEAST ONCE*.

![Receive Telemetry Data](../consumeTelemetry_Success.png)

1. *AMQP 1.0 Messaging Network* delivers telemetry message to *Business Application*.
1. *Business Application* acknowledges reception of message.

{{% note %}}
The *Business Application* can only consume telemetry messages that have been uploaded to Hono *after* the *Business Application* has established the link with the *AMQP 1.0 Messaging Network*. This is because telemetry messages are not *durable*, i.e. they are not persisted in Hono in order to be forwarded at a later time.
{{% /note %}}

**Message Format**

The format of the messages containing the telemetry data is the same as for the [Upload Telemetry Data operation]({{< relref "#upload-telemetry-data" >}}).
