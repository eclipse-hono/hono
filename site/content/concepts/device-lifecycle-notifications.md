+++
title = "Device lifecycle notifications (from 0.6 on)"
weight = 415
+++

This page describes how device lifecycle notifications are handled in the Eclipse Hono project.


{{% warning %}}
This concept is currently part of active development and discussion.
The content here summarizes the current discussion and is being prototyped.
Please join our mailing list or consult the IRC channel in case you have any questions or want to participate.
{{% /warning %}}

<!--more-->

Business applications connected to Hono may want to invoke arbitrary functionality when a device connects or disconnects to Hono.
This is especially necessary to implement Command and Control, but is not limited to this Use Case. Integrating digital twins, like
[Eclipse Ditto] (https://projects.eclipse.org/proposals/eclipse-ditto), also benefit from knowing when a device just connected to Hono.

For these scenarios Hono defines a lifecycle notification concept that covers different patterns that try to cover several classes
of protocols and devices.

# Covered patterns

Due to Hono's support for arbitrary protocols and arbitrary *classes* of devices (from strictly constrained to powerful gateways),
there is no single way of sending lifecycle notifications.

To be distinguished at least are:

1. Connections oriented protocols, like MQTT
1. Connections less protocols, like HTTP
1. Protocols that support the setting of headers (like HTTP), in contrast to others (like MQTT)
1. Devices that stay connected and can be contacted upstream always (e.g. via MQTT)
1. Devices that are strictly constrained and connect only for a very short time per day, being only able to receive a few
bytes upstream then
1. ... and probably more specialties of protocols and devices over the time

Besides that devices that connect to Hono may often not be directly ready for receiving upstream data, but need to separate
their availability from the connection itself.

Considering these different patterns, the main principle for lifecycle notifications is the following:

# Main principle

**The standard responsibility to signal that a device is ready to receive upstream data is at the device itself.**

The availability is defined by a time interval in which the device should be available for receiving upstream data.

There are 2 different ways for a device signalling this:
 
## Piggy-back telemetry data (connect event only)

Some devices may be ready to receive upstream data directly after they sent downstream telemetry data. If this is always
the case or only sometimes, is not important for this scenario.

To support this, the Telemetry API was extended by non mandatory application properties that define a time window in which
the device is available for upstream data reception after having sent downstream telemetry data.

NB: piggy-backing the telemetry data avoids sending special events additionally whenever a device connects - otherwise
for protocols like HTTP this might lead to as many events as telemetry data (since devices are not *connected* via HTTP, but 
*connect* each time again when sending telemetry data). 


![Piggy-backing telemetry data](../lifecycleConnectedTelemetry.png)

## Send a specific event

To signal the availability of devices for receiving upstream data without sending downstream data first, a *well-known* 
event is defined. This is sent downstream via the Event API.

![Send specific event](../lifecycleConnectedAsEvent.png)

## Different sources of lifecyle notifications

Lifecycle notifications may be sent

- directly from the device (either piggy-backing telemetry data or sending the well-known event)
- by a protocol adapter (when a device connects, e.g. via MQTT) 

What is appropriate highly depends on the capabilities of the devices and the used protocols and may be configured differently
per tenant and per protocol adapter.
 
## Northbound application support

If for a device the availability is signaled to receive upstream data, the protocol adapter instance that signaled this
is responsible to create an AMQP 1.0 receiver link for upstream data. This link is used by the *Business application* to
send a command upstream.

When a device disconnects, the link will be closed again.

As soon as a command is sent, a sender link for a response is additionally opened.

