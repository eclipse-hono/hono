+++
title = "Event API"
weight = 430
+++

The *Event* API is used by *Devices* to send event messages downstream.
*Business Applications* and other consumers use the API to receive messages published by devices belonging to a particular tenant.
<!--more-->

The Event API is defined by means of AMQP 1.0 message exchanges, i.e. a client needs to connect to Hono using AMQP 1.0 in order to invoke operations of the API as described in the following sections. Throughout the remainder of this page we will simply use *AMQP* when referring to AMQP 1.0.

The *Event* API is identical to the [*Telemetry* API]({{< relref "Telemetry-API.md" >}}) regarding the provided operations and the message flow.

Events provide a different quality of service for messages sent to the *Event* endpoint by 
setting the <em>durable</em> property of the message header to `true`.

There are well-known events that are distinguished by their *content-type* which are defined [here]({{< relref "#well-known-event-message-types" >}}).   


# Southbound Operations

The following operations can be used by *Devices* and/or *Protocol Adapters* (to which the devices are connected) to publish event messages for consumption by downstream consumers like *Business Applications*.

Both *Devices* as well as *Protocol Adapters* will be referred to as *clients* in the remainder of this section.

## Upload Event Data

**Preconditions**

1. Client has established an AMQP connection with Hono's Event endpoint.
1. Client has established an AMQP link in role *sender* with Hono using target address `event/${tenant_id}` where `${tenant_id}` is the ID of the tenant that the client wants to upload event messages for. 
1. The device for which the client wants to send an event has been registered (see [Device Registration API]({{< relref "Device-Registration-API.md" >}})).
1. Client has obtained a *registration assertion* for the device from the Device Registration service by means of the [assert Device Registration operation]({{< relref "Device-Registration-API.md#assert-device-registration" >}}).

Hono supports *AT LEAST ONCE* delivery of *Event* messages only. A client therefore MUST use `unsettled` for the *snd-settle-mode* and `first` for the *rcv-settle-mode* fields of its *attach* frame during link establishment. All other combinations are not supported by Hono and result in the termination of the link.

**Message Flow**

The following sequence diagram illustrates the flow of messages involved in the *MQTT Adapter* uploading an event data message to Hono Messaging's Event endpoint.

![Upload event data flow](../uploadEvent_Success.png)

1. *MQTT Adapter* sends event data for device `4711`.
   1. *Hono Messaging* successfully verifies that device `4711` of `TENANT` exists and is enabled by means of validating the *registration assertion* included in the message (see [Device Registration]({{< relref "api/Device-Registration-API.md#assert-device-registration" >}})) and forwards data to *AMQP 1.0 Messaging Network*.
1. *AMQP 1.0 Messaging Network* acknowledges reception of the message.
   1. *Hono Messaging* acknowledges reception of the message. In contrast to telemetry data, the protocol adapter awaits the disposition from the AMQP 1.0 Messaging Network before signaling to the device that it has accepted the message for processing.                                                             
    
**Message Format**

See [Telemetry API]({{< relref "Telemetry-API.md#upload-telemetry-data" >}}) for definition of message format.

Note that Hono does not return any *application layer* message back to the client in order to signal the outcome of the operation. Instead, Hono signals reception of the message by means of the AMQP `ACCEPTED` outcome if the message complies with the formal requirements and the downstream AMQP 1.0 messaging network has also *accepted* the event. Note that it depends on the configuration of the messaging network what *accepted* actually means. By default, event messages are marked as *durable* and the messaging network will thus at least have persisted the event successfully when it signals the `ACCEPTED` outcome to Hono.

Whenever a client sends an event that cannot be processed because it does not conform to the message format defined above, Hono settles the message transfer using the AMQP `REJECTED` outcome containing an `amqp:decode-error`. Clients **should not** try to re-send such rejected messages unaltered.

If the event passes formal verification, the outcome signaled to the client is the one received from the downstream AMQP 1.0 messaging network. In this case, clients may try to re-send messages for which a delivery state other than `ACCEPTED` has been returned.

# Northbound Operations

## Receive Event Data

Hono delivers messages containing event messages reported by a particular device in the same order that they have been received in (using the *Events* operation defined above).
Hono supports multiple non-competing *Business Application* consumers of event messages for a given tenant. Hono allows each *Business Application* to have multiple competing consumers for event messages for a given tenant to share the load of processing the messages.

**Preconditions**

1. Client has established an AMQP connection with Hono.
2. Client has established an AMQP link in role *receiver* with Hono using source address `event/${tenant_id}` where `${tenant_id}` represents the ID of the tenant the client wants to retrieve event messages for.

Hono supports *AT LEAST ONCE* delivery of *Event* messages only. A client therefore MUST use `unsettled` for the *snd-settle-mode* and `first` for the *rcv-settle-mode* fields of its *attach* frame during link establishment. All other combinations are not supported by Hono and result in the termination of the link.

**Message Flow**

The following sequence diagram illustrates the flow of messages involved in a *Business Application* receiving an event data message from Hono. 


![Receive event data flow](../consumeEvent_Success.png)

1. *AMQP 1.0 Messaging Network* delivers event message to *Business Application*.
1. *Business Application* acknowledges reception of message.

**Message Format**

**Arbitrary events from the device**

See [*Telemetry API*]({{< relref "Telemetry-API.md" >}}) for definition of message format. 

## Well-known event message types

Hono defines *well-known* events that are of a specific *content-type*.

NB: currently there is only one such *well-known* event.

### Lifecycle Notifications

A specific event can signal that a device is `connected` (e.g. to signal that it is ready to receive a command for a specific time interval) 
or that a device is `disconnected` (e.g to signal that it is not ready to receive a command anymore).
 
*Business Applications* may want to react to such an event by sending a command upstream to the device.
For that purpose, the protocol adapter instance that received such an event opens a receiver link for the device (scoped to its tenant) that
is used to send the command to the appropriate protocol adapter instance.

Such an event may be issued by the device itself or by a protocol adapter detecting that a device is connected or disconnected.
 For further details on which part of a setup issues such an event see (... concept page).


*Lifecycle notifications* may be decided to not being durable, but this is left to the setup of the *AMQP network* and is not further specified in the following.


***Lifecycle Notification Payload***

The following table provides an overview of the properties a client needs to set for a *lifecycle notification* event.

| Name           | Mandatory | Location                 | Type      | Description |
| :------------- | :-------: | :----------------------- | :-------- | :---------- |
| *content-type* | yes       | *properties*             | *symbol*  | Must be set to *application/vnd.eclipse-hono-notification+json* |

The body of the event request MUST consist of a single *AMQP Value* section containing a UTF-8 encoded string representation of a single JSON object having the following members:

| Name                | Mandatory | Type       | Default Value | Description |
| :-----------------  | :-------: | :--------- | :----------   | :---------- |
| *tenant-id*         | *yes*     | *string*   |               | The tenant identifier to send the notification for. |
| *device-id*         | *yes*     | *string*   |               | The device identifier to send the notification for. |
| *cause*             | *yes*     | *string*   |               | Value must be either `connected` or `disconnected` . |
| *source*            | *yes*      | *string*   |               | Value can be either the name of a protocol adapter (`hono-mqtt`, `hono-http`, `hono-kura`), if the device connected/disconnected to the appropriate protocol adapter,  or `device`, if the device sent the notfication itself.
| *creation-timestamp*  | *no*      | *string*   |   null        | The point in time when the event was created (set by the issuer). If not null, the value MUST be an ISO 8601 compliant combined date and time representation in extended format. **NB:** due to network latencies this timestamp will typically be some time in the past already when the event is received by the application. |
| *ttl*               | *no*      | *long*   |   0        | The `time to live` for the event in milliseconds. In context with the `creation-timestamp`  it defines the time interval until the event shall be considered invalid again. If the value is `0`, the validity period of the event shall be considered as not being limited. |
| *data*              | *no*      | *string*   |               | Optional data the issuer of the event can set (e.g.  a a last known revision of a value when integrating with [Eclipse Ditto] (https://projects.eclipse.org/proposals/eclipse-ditto)).


Examples:

The following notification payload may be sent if a device connects to an *MQTT adapter* :

~~~json
{
  "tenant-id": "my-tenant",
  "device-id": "4711",
  "cause": "connect",
  "source": "hono-mqtt"
}
~~~

The MQTT protocol adapter may typically not set the `creation-timestamp`  and `ttl` fields (since it is connection oriented and issue a `disconnected` event as soon as a device disconnects again).


The following notification payload may be sent if a device itself sends a lifecycle notification, e.g. to signal that it
now is able to receive a command for one minute:

~~~json
{
  "tenant-id": "my-tenant",
  "device-id": "sensor1",
  "cause": "connect",
  "source": "device",
  "creation-timestamp": "2018-04-06T10:00:00+0100",
  "ttl": "60000",
  "data": "last-state: on"
}
~~~
NB: note that a device does not necessarily know its *device-id*, but only its *auth-id* (please refer to the 
[Credentials API]({{< relref "Credentials-API.md" >}}) for details). 