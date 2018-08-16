+++
title = "Device notifications"
weight = 190
+++

*Business applications* can be informed by Hono about specific states of a device. This concept - referred to as
 **Device notifications** - is described in detail here.
 
 
<!--more-->

{{% note %}}
This concept has been added in Hono 0.6. Previous versions of Hono do not support it.
{{% /note %}}

For implementing specific behaviour *Business applications* need to know if
a device is currently ready to receive an upstream message.

This is the purpose of the following *Time until disconnect notification*.

## Time until disconnect notification

This notification can be triggered in an application by any downstream message as long as the following AMQP 1.0 properties are set:

- the property `creation-time` (automatically set by protocol adapters to the current time when creating the message)
- an additional application-property `ttd` (the number of seconds the device will stay connected to the protocol adapter)

A device that wants to only trigger this notification (without sending other downstream data) may send an 
[empty notification]({{< relref "api/Event-API.md#empty-notification" >}}) event.

The application can register a callback for this notification that is invoked if the contained
timestamp is not expired at the time the message was received. The callback provides an instance of `TimeUntilDisconnectNotification`
that contains all necessary data to let the application send an upstream message to the device.

See Hono's example module for details where such a notification callback is used.

 
Please refer to the [Telemetry API]({{< relref "api/Telemetry-API.md" >}}) and the [Event API]({{< relref "api/Event-API.md" >}}) for further details.

### Time based validation of a notification 

An application receiving a downstream message containing the notification relevant properties can verify at any point in
time if a device is ready to receive an upstream message by:

- adding the values of the `creation-time` and the `ttd` properties to build an *expiration timestamp*
- comparing the current time with the *expiration timestamp*

If the current time is *after* the *expiration timestamp*, the notification shall be regarded as being expired.

### Source of the `ttd` value

While it seems to be natural that a device itself indicates when it is ready to receive a command, it may not always be
possible or desirable to do so.

A device could e.g. be not capable to specify the value for `ttd` in it's message, or all devices of a particular setup would always use the same value
for `ttd`, so it would not make much sense to provide this value always again.

Additionally different protocols may or may not let a sender set specific values for a message, so a device using a 
specific protocol may not be able to
provide a value for the `ttd` property at all.

For these reasons there are (resp. may be) additional ways of setting the value of `ttd`:

- Hono's Device Registry supports default values for any application-properties in the AMQP 1.0 message. By this means
  a device can be configured to always have a specific value for `ttd`.
- In a future extension there may be a configuration value per tenant and protocol adapter that sets the value of `ttd`
  if it was not provided by other means already (like provided to the protocol adapter or by using a default value of the 
  Device Registry).
  
### Hono's HTTP protocol adapter

Hono's HTTP protocol adapter supports the setting of the `ttd` value in requests explicitly - please refer to the
[HTTP Adapter]({{< relref "user-guide/http-adapter.md" >}}) for details.

Alternatively the default property values for devices from the Device Registry can be used (described above).
  
### Hono's MQTT protocol adapter

The MQTT protocol adapter automatically sends a `Time until disconnect notification` with a `ttd` value of `-1`
for a device that subscribes to
the appropriate command topic (refer to the [MQTT Adapter]({{< relref "user-guide/mqtt-adapter.md" >}}) for details).

If it unsubscribes again, the adapter automatically sends a `Time until disconnect notification` with a `ttd` value of `0`.

### Sequence diagrams of Device command readiness notifications

The following sequence diagram shows a **device command readiness notification** while sending a telemetry message downstream
via the HTTP protocol adapter:

![Device command readiness with telemetry data](../device_commandReadinessImplicit.png)

The following sequence diagram shows a **device command readiness notification** by sending an empty event message downstream
via the HTTP protocol adapter:

![Device command readiness with explict event](../device_commandReadinessExplicit.png)
