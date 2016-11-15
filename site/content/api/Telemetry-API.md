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

### Upload Telemetry Data

**Preconditions**

1. Client has established an AMQP connection with Hono.
2. Client has established an AMQP link in role *sender* with Hono using target address `telemetry/${tenant_id}` where `tenant_id` is the ID of the tenant that the client wants to upload telemetry data for. 
3. The device for which the client wants to upload telemetry data has been registered (see [Device Registration API](../Device-Registration-API)).

The client indicates its preferred message delivery mode by means of the `snd-settle-mode` and `rcv-settle-mode` fields of its `attach` frame during link establishment. Hono will receive messages using a delivery mode according to the following table:

| snd-settle-mode        | rcv-settle-mode        | Delivery semantics |
| :--------------------- | :--------------------- | :----------------- |
| *unsettled*, *mixed*   | *first*                | Hono will acknowledge and settle received messages spontaneously. Hono will accept any re-delivered messages. |
| *settled*              | *first*                | Hono will acknowledge and settle received messages spontaneously. This is the fastest mode of delivery. This corresponds to *AT MOST ONCE* delivery. |

All other combinations are not supported by Hono and result in a termination of the link. In particular, Hono does **not** support reliable transmission of telemetry data, i.e. messages containing telemetry data MAY be lost.

**Message Flow**

The following sequence diagram illustrates the flow of messages involved in a *Protocol Adapter* uploading a telemetry data message to Hono.

Note that the sequence diagram is based on the [Hybrid Connection Model]({{< relref "Topology-Options.md" >}}) topology option. 

![Upload telemetry data flow](../Upload_Telemetry_Data.png)

1. *Telemetry Endpoint* connects to *Dispatch Router*. *Dispatch Router* successfully verifies credentials.
2. *Protocol Adapter* connects to *Telemetry Endpoint*.
   1. *Telemetry Endpoint* successfully verifies credentials using *Auth Service*.
3. *Protocol Adapter* establishes link with *Telemetry Endpoint* for sending telemetry data for tenant `TENANT`.
   1. *Telemetry Endpoint* establishes link with *Dispatch Router* for sending telemetry data for tenant `TENANT`.
4. *Protocol Adapter* sends temperature reading for device `4711`.
   1. *Telemetry Endpoint* successfully verifies that client is allowed to publish data for device `4711` of tenant `TENANT` using *Auth Service*.

    **Open issue**: should the Telemetry Endpoint also check if device really belongs to tenant?
 1. *Telemetry Endpoint* sends temperature reading to *Dispatch Router*.

**Message Format**

The following table provides an overview of the properties a client needs to set on an *Upload Telemetry Data* message.

| Name           | Mandatory | Location     | Type      | Description |
| :------------- | :-------: | :----------- | :-------- | :---------- |
| *content-type* | yes       | *properties* | *symbol*  | SHOULD be set to *application/octet-stream* if the message payload is to be considered *opaque* binary data. In most cases though, the client should be able to set a more specific content type indicating the type and characteristics of the data contained in the payload, e.g. `text/plain; charset="utf-8"` for a text message or `application/json` etc. |
| *device_id*    | yes       | *application-properties* | UTF-8 *string* | MUST contain the ID of the device the data in the payload has been reported by. |

The body of the message MUST consist of a single AMQP *Data* section containing the telemetry data. The format and encoding of the data MUST be indicated by the *content-type* and (optional) *content-encoding* properties of the message.

Any additional properties set by the client in either the *properties* or *application-properties* sections are preserved by Hono, i.e. these properties will also be contained in the message delivered to consumers.
 
Note that Hono does not return any *application layer* message back to the client in order to signal the outcome of the operation. Instead, Hono signals reception of the message by means of the AMQP `ACCEPTED` delivery state.

Whenever a client sends a telemetry message that cannot be processed, e.g. because it does not conform to the message format defined above, Hono terminates the link with the client in order to prevent further processing of such messages being re-sent by the client.

# Northbound Operations

### Receive Telemetry Data

Hono delivers messages containing telemetry data reported by a particular device in the same order that they have been received in (using the *Upload Telemetry Data* operation defined above).
Hono MAY drop telemetry messages that it cannot deliver to any consumers. Reasons for this include that there are no consumers connected to Hono or the existing consumers are not able to process the messages from Hono fast enough.
Hono supports multiple non-competing *Business Application* consumers of telemetry data for a given tenant. Hono allows each *Business Application* to have multiple competing consumers for telemetry data for a given tenant to share the load of processing the messages.

**Preconditions**

1. Client has established an AMQP connection with Hono.
2. Client has established an AMQP link in role *receiver* with Hono using source address `telemetry/${tenant_id}` where `tenant_id` represents the ID of the tenant the client wants to retrieve telemetry data for.

Hono supports *AT MOST ONCE* delivery of messages only. A client therefore MUST use `settled` for the `snd-settle-mode` and `first` for the `rcv-settle-mode` fields of its `attach` frame during link establishment. All other combinations are not supported by Hono and result in the termination of the link.

A client MAY indicate to Hono during link establishment that it wants to distribute the telemetry messages received for a given tenant among multiple consumers by including a link property `subscription-name` whose value is shared by all other consumers of the tenant. Hono ensures that messages from a given device are delivered to the same consumer. Note that this also means that telemetry messages MAY not be evenly distributed among consumers, e.g. when only a single device sends data.

In addition a client MAY include a boolean link property `ordering-required` with value `false` during link establishment in order to indicate to Hono that it does not require messages being delivered strictly in order per device but instead allows for messages being distributed evenly among the consumers. 

**Message Flow**

The following sequence diagram illustrates the flow of messages involved in a *Business Application* receiving a telemetry data message from Hono.

Note that the sequence diagram is based on the [Hybrid Connection Model]({{< relref "Topology-Options.md" >}}) topology option. 

![Receive Telemetry Data](../Receive_Telemetry_Data.png)

1. *Business Application* connects to *Dispatch Router*. *Dispatch Router* successfully verifies credentials.
1. *Business Application* establishes link with *Dispatch Router* for receiving telemetry data for tenant `TENANT`. *Dispatch Router* successfully verifies that client is allowed to receive data for tenant `TENANT` (How?).
1. *Dispatch Router* delivers telemetry message to *Business Application*.

{{% note %}}
The *Business Application* can only receive telemetry messages that have been uploaded to Hono *after* the *Business Application* has established the link with the *Dispatch Router* because telemetry messages are not *durable*, i.e. they are not stored and forwarded.
{{% /note %}}

**Message Format**

The format of the messages containing the telemetry data is the same as for the *Upload Telemetry Data* operation.
