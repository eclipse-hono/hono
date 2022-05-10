---
title: "Telemetry API Specification for AMQP 1.0"
linkTitle: "Telemetry API (AMQP 1.0)"
weight: 417
resources:
  - src: forward_qos0.svg
  - src: forward_qos1.svg
  - src: consume.svg
---

The *Telemetry* API is used by *Protocol Adapters* to send telemetry data downstream. *Business Applications* and other
consumers use the API to receive data published by devices belonging to a particular tenant.
<!--more-->

The Telemetry API is defined by means of AMQP 1.0 message exchanges, i.e. a client needs to connect to Hono using
AMQP 1.0 in order to invoke operations of the API as described in the following sections. Throughout the remainder of
this page we will simply use *AMQP* when referring to AMQP 1.0.

The Telemetry API for AMQP 1.0 is an alternative to the [Telemetry API for Kafka]({{< relref "/api/telemetry-kafka" >}})
for applications that want to consume telemetry data from an AMQP Messaging Network instead of an Apache Kafka&trade;
broker.


## Southbound Operations

The following operations can be used by *Protocol Adapters* to forward telemetry data received from devices to
downstream consumers like *Business Applications*.

### Forward Telemetry Data

**Preconditions**

1. Adapter has established an AMQP connection with the AMQP Messaging Network.
1. Adapter has established an AMQP link in role *sender* with the AMQP Messaging Network using target address
   `telemetry/${tenant_id}` where `${tenant_id}` is the ID of the tenant that the client wants to upload telemetry data for. 
1. The device for which the adapter wants to send telemetry data has been registered (see
   [Device Registration API]({{< relref "/api/device-registration" >}})).

The adapter indicates its preferred message delivery mode by means of the *snd-settle-mode* and *rcv-settle-mode*
fields of its *attach* frame during link establishment.

| snd-settle-mode        | rcv-settle-mode        | Delivery semantics |
| :--------------------- | :--------------------- | :----------------- |
| `unsettled`, `mixed` | `first`               | Using `unsettled` for the *snd-settle-mode* allows for adapters to implement both *AT LEAST ONCE* or *AT MOST ONCE* delivery semantics, depending on whether the adapter waits for and considers the disposition frames it receives from the AMQP Messaging Network or not. This is the recommended mode for forwarding telemetry data. |
| `settled`             | `first`               | Using `settled` for the *snd-settle-mode* allows for adapters to implement *AT MOST ONCE* delivery semantics only. This is the fastest mode of delivery but has the drawback of less reliable end-to-end flow control and potential loss of messages without notice. |

All other combinations are not supported by Hono and may result in the termination of the link or connection (depending on the configuration of the AMQP Messaging Network).

**Message Flow**

As indicated above, it is up to the discretion of the protocol adapter whether it wants to use *AT LEAST ONCE* or
*AT MOST ONCE* delivery semantics.

Hono's HTTP adapter allows devices to indicate, which delivery semantics they want to use when uploading telemetry data.
The HTTP adapter always forwards messages unsettled and either ignores the outcome of the message transfer
(*AT MOST ONCE*) or waits for the downstream peer to accept the message (*AT LEAST ONCE*) before acknowledging the
reception of the message to the device.

The following sequence diagram illustrates the flow of messages involved in the *HTTP Adapter* forwarding an
*unsettled* telemetry data message to the downstream AMQP Messaging Network implementing *AT MOST ONCE* delivery
semantics.

{{< figure src="forward_qos0.svg" title="Forward telemetry data flow (AT MOST ONCE)" >}}

1. *Device* `4711` PUTs telemetry data to the *HTTP Adapter*
   1. *HTTP Adapter* transfers telemetry data to *AMQP 1.0 Messaging Network*.
   1. *HTTP Adapter* acknowledges the reception of the data to the *Device*.
1. *AMQP 1.0 Messaging Network* acknowledges reception of the message which is ignored by the *HTTP Adapter*.

{{% notice info %}}
In the example above the HTTP adapter does not wait for the outcome of the transfer of the message to the AMQP
Messaging Network before sending back the HTTP response to the device.
If the messaging network had sent a disposition frame with the *rejected* instead of the *accepted* outcome, the HTTP
adapter would still have signaled a 202 status code back to the device. In this case the data would have been lost
without the device noticing.
{{% /notice %}}

The following sequence diagram illustrates the flow of messages involved in the *HTTP Adapter* forwarding an
*unsettled* telemetry data message to the downstream AMQP Messaging Network implementing *AT LEAST ONCE* delivery
semantics.

{{< figure src="forward_qos1.svg" title="Forward telemetry data flow (AT LEAST ONCE)" >}}

1. *Device* `4711` PUTs telemetry data to the *HTTP Adapter*, indicating *QoS Level* 1.
   1. *HTTP Adapter* transfers telemetry data to *AMQP 1.0 Messaging Network*.
   1. *AMQP 1.0 Messaging Network* acknowledges reception of the message.
   1. *HTTP Adapter* acknowledges the reception of the data to the *Device*.

When the AMQP Messaging Network fails to settle the transfer of a telemetry message or settles the transfer with any
other outcome than `accepted`, the protocol adapter MUST NOT try to re-send such rejected messages but SHOULD indicate
the failed transfer to the device if the transport protocol provides means to do so.

**Message Format**

The following table provides an overview of the properties a client needs to set on a *Forward Telemetry Data* message.

| Name            | Mandatory       | Location                 | Type        | Description |
| :-------------- | :-------------: | :----------------------- | :---------- | :---------- |
| *content-type*  | yes             | *properties*             | *symbol*    | A content type indicating the type and characteristics of the data contained in the payload, e.g. `text/plain; charset="utf-8"` for a text message or `application/json` etc. The value may be set to `application/octet-stream` if the message payload is to be considered *opaque* binary data. |
| *creation-time* | yes             | *properties*             | *timestamp* | The instant in time when the message has been created (see the [AMQP 1.0 specification](http://docs.oasis-open.org/amqp/core/v1.0/amqp-core-messaging-v1.0.html) for details). |
| *device_id*     | yes             | *application-properties* | *string*    | The identifier of the device that the data in the payload is originating from. |
| *ttd*           | no              | *application-properties* | *int*       | The *time 'til disconnect* indicates the number of seconds that the device will remain connected to the protocol adapter. The value of this property must be interpreted relative to the message's *creation-time*. A value of `-1` is used to indicate that the device will remain connected until further notice, i.e. until another message indicates a *ttd* value of `0`. In absence of this property, the connection status of the device is to be considered indeterminate. *Backend Applications* might use this information to determine a time window during which the device will be able to receive a command. |

Protocol adapters MAY set additional *properties* or *application-properties* on a downstream message. Any such
properties will be defined in the adapter's corresponding user guide.

The body of the message MUST consist of a single AMQP *Data* section containing the telemetry data. The format and
encoding of the data MUST be indicated by the *content-type* and (optional) *content-encoding* properties of the message.

## Northbound Operations

### Receive Telemetry Data

Hono delivers messages containing telemetry data reported by a particular device in the same order that they have been
received in (using the [Forward Telemetry Data]({{< relref "#forward-telemetry-data" >}}) operation). Hono MAY drop
telemetry messages that it cannot deliver to any consumers. Reasons for this include that there are no consumers
connected to Hono or the existing consumers are not able to process the messages from Hono fast enough.

Hono supports multiple non-competing *Business Application* consumers of telemetry data for a given tenant. Hono
allows each *Business Application* to have multiple competing consumers for telemetry data for a given tenant to
share the load of processing the messages.

**Preconditions**

1. Client has established an AMQP connection with Hono.
2. Client has established an AMQP link in role *receiver* with Hono using source address `telemetry/${tenant_id}` where
   `${tenant_id}` represents the ID of the tenant the client wants to retrieve telemetry data for.

Hono supports both *AT MOST ONCE* as well as *AT LEAST ONCE* delivery of telemetry messages. However, clients SHOULD
use *AT LEAST ONCE* delivery in order to support end-to-end flow control and therefore SHOULD set the *snd-settle-mode*
field to `unsettled` and the *rcv-settle-mode* field to `first` in their *attach* frame during link establishment.

A client MAY indicate to Hono during link establishment that it wants to distribute the telemetry messages received
for a given tenant among multiple consumers by including a link property `subscription-name` whose value is shared by all
other consumers of the tenant. Hono ensures that messages from a given device are delivered to the same consumer.
Note that this also means that telemetry messages MAY not be evenly distributed among consumers, e.g. when only a
single device sends data. **NB** This feature is not supported yet.

In addition a client MAY include a boolean link property `ordering-required` with value `false` during link establishment
in order to indicate to Hono that it does not require messages being delivered strictly in order per device but instead
allows for messages being distributed evenly among the consumers. **NB** This feature is not supported yet.

**Message Flow**

The following sequence diagram illustrates the flow of messages involved in a *Business Application* receiving a
telemetry data message from Hono. The delivery mode used is *AT LEAST ONCE*.

{{< figure src="consume.svg" title="Receive Telemetry Data" >}}

1. *AMQP 1.0 Messaging Network* delivers telemetry message to *Business Application*.
   1. *Business Application* acknowledges reception of message.

{{% notice info %}}
The *Business Application* can only consume telemetry messages that have been uploaded to Hono *after* the *Business
Application* has established the link with the *AMQP 1.0 Messaging Network*. This is because telemetry messages are
not *durable*, i.e. they are not persisted in Hono in order to be forwarded at a later time.
{{% /notice %}}

**Message Format**

The format of the messages containing the telemetry data is the same as for the
[Forward Telemetry Data operation]({{< relref "#forward-telemetry-data" >}}).
