---
title: "Device Notifications"
weight: 192
resources:
- src: device_commandReadinessImplicit.svg
- src: device_commandReadinessExplicit.svg
---

*Business Applications* need to know when an attempt to send a command to device is feasible, e.g. because the device
is then known to be connected to a protocol adapter. *Devices* and *Protocol Adapters* can indicate to
*Business Applications* a device's intent to receive commands using specific *notifications*.
  
<!--more-->

## Time until Disconnect Notification

*Devices* and *Protocol Adapters* can notify an application about the fact that a device is connected and ready
to receive one or more commands by means of including a *time till disconnect* (*ttd*) property in telemetry or
event messages.

The *ttd* property value indicates the amount of time (in seconds) that the device will stay connected to the protocol
adapter. Using this value together with the *creation-time* of the message, an application can determine whether an
attempt to send a command to the device has a reasonable chance of succeeding.
The *ttd* property can be included in any regular telemetry or event message. However, if a device does not have any
telemetry data or event to upload to the adapter, it can also use an
[empty notification]({{< relref "/api/event#empty-notification" >}}) instead.

Hono includes utility classes that application developers can use to register a callback to be notified when a device
sends a *ttd* notification.
See Hono's example module for details where such a notification callback is used.
Please refer to the [Telemetry API]({{< relref "/api/telemetry" >}}) and the [Event API]({{< relref "/api/event" >}})
for further details.

The following table defines the possible values of the *ttd* property and their semantics:

| TTD  | Description  |
| :--- | :----------- |
| > 0  | The value indicates the number of seconds that the device will stay connected. Devices using a stateless protocol like HTTP will be able to receive a single command only before disconnecting. |
| -1   | The device is now connected (i.e. available to receive upstream messages) until further notice. |
| 0    | The device is now disconnected (i.e. not available anymore to receive upstream messages). |

### Determining a Device's Connection Status 

An application receiving a downstream message containing a *ttd* property can check if the device is currently connected
(and thus ready to receive a command) by

- adding the *ttd* value to the *creation-time* to determine the *expiration* time, and then
- comparing the *current* time with the *expiration* time

If the *current* time is *after* the *expiration* time, the device should be assumed to already have disconnected again.

### Source of the *ttd* Value

While it seems to be natural that a device itself indicates when it is ready to receive a command, it may not always be
possible or desirable to do so.
A device could e.g. be not capable to specify the value for *ttd* in its message, or all devices of a particular setup
would always use the same value for *ttd*, so it would not make much sense to provide this value with each request.
Additionally, different protocols may not allow the sender to set specific values for a message, so a device using a
specific protocol may not be able to provide a value for the *ttd* property at all.
For these reasons there are additional ways of specifying the *ttd*:

- Hono's Tenant and Device Registration APIs support the inclusion of *default values* for application-properties in the
  AMQP 1.0 message. This way, a device can be configured to always have a specific value for *ttd* set in messages
  originating from the device.
- In a future extension there may be a configuration value per tenant and protocol adapter that sets the value of *ttd*
  if it was not provided by other means already (like provided to the protocol adapter or by setting a default value).

### CoAP protocol adapter

Hono's CoAP protocol adapter supports the setting of the *ttd* value in requests explicitly. Please refer to the
[CoAP Adapter user guide]({{< relref "/user-guide/coap-adapter.md" >}}) for details.
Alternatively, a default *ttd* property value can be specified for devices as mentioned above.

### HTTP protocol adapter

Hono's HTTP protocol adapter supports the setting of the *ttd* value in requests explicitly.  Please refer to the
[HTTP Adapter user guide]({{< relref "/user-guide/http-adapter.md" >}}) for details.
Alternatively, a default *ttd* property value can be specified for devices as mentioned above.

The following sequence diagram shows a *Time till disconnect notification* while sending a telemetry message downstream
via the HTTP protocol adapter:

{{< figure src="device_commandReadinessImplicit.svg" title="Device command readiness with telemetry data" >}}

The following sequence diagram shows a *Time till disconnect notification* by sending an empty event message downstream
via the HTTP protocol adapter:

{{< figure src="device_commandReadinessExplicit.svg" title="Device command readiness with explicit event" >}}

### AMQP protocol adapter

The AMQP protocol adapter automatically initiates sending  a *Time till disconnect notification* via the *Command Router*
with a *ttd* value of `-1` for a device that opens a receiver link for the command source address. Please refer to the
[AMQP Adapter user guide]({{< relref "/user-guide/amqp-adapter.md" >}}) for details.

When a device closes the receiver link again, the adapter automatically initiates a *Time until disconnect notification*
via the *Command Router* with a *ttd* value of `0`.

### MQTT protocol adapter

The MQTT protocol adapter automatically initiates sending a *Time till disconnect notification* via the *Command Router*
with a *ttd* value of `-1` for a device that subscribes to the appropriate command topic. Please refer to the
[MQTT Adapter user guide]({{< relref "/user-guide/mqtt-adapter.md" >}}) for details.

When a device unsubscribes again, the adapter automatically initiates a *Time till disconnect notification* via the
*Command Router* with a *ttd* value of `0`.

