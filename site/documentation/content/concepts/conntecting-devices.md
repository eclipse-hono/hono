+++
title = "Connecting Devices"
weight = 190
+++

Eclipse Hono&trade; has the concept of protocol adapters (check the
[Component Overview]({{< relref "architecture/component-view/index.md" >}}) for details), which provide protocol
endpoints for devices.

The typical case is that devices connect to Hono using supported protocols like AMQP, MQTT, HTTP etc.
In case that a device is not able to communicate in the Hono message abstraction of [Event]({{< relref "api/event/index.md" >}}),
[Telemetry]({{< relref "api/telemetry/index.md" >}}) and [Command & Control]({{< relref "concepts/command-and-control.md" >}}),
a *device gateway* can be used to communicate to a Hono adapter on behalf of a device (internally treated itself as a device).
If devices communicate to a third party hosted API (referred next as *protocol gateway*), this *protocol gateway* can be
used communicate to a Hono adapter on behalf of devices.

## Connecting directly to Protocol Adapters

To connect a device directly southbound to Hono, it have to send and receive messages in Hono message abstraction
manner of Event, Telemetry and Command & Control. Connecting to deployed adapters can be done in various protocols.
Hono is protocol agnostic as long it fits into the Hono message abstraction, that is needed to parse messages
between different protocols. Prior to first communication, a device has to be
[registered to the device registry]({{< relref "concepts/device-identity.md#device-registration" >}}),
that authenticates devices.

## Connecting via a Device Gateway

In some circumstances, a device may not directly communicate with Hono. An example is a device that does not have
the capabilities to directly communicate with one of the protocol adapters.
Instead, such devices can connect to Hono through a *gateway*. A gateway is a device or software system that acts
on behalf of other devices. From the perspective of Hono a gateway is another device following the description
in [Device Identity]({{< relref "device-identity" >}}).

When a device wants to communicate over a gateway that gateway needs to be registered as gateway for the device.
One can perform this registration as part of the [Device Registry Management API]({{< relref "/api/management" >}})
of the Device Registry. In that API each device representation has a 'via' property which is a list with the ids of
the devices that can act on behalf of the represented device as a gateway. 

Part of the functionality provided by the  [Device Registration API]({{< relref "/api/device-registration" >}})
is to assert that a device is actually registered to act for a given device as a gateway. Thus, for instance,
the protocol adapters use this API to verify that the involved devices and the combination of device and gateway are valid.

In general, it is possible to register multiple gateways with a device. Sometimes, for instance, while sending
commands to the device, Hono needs to decide which of the possible gateways should be used to transmit that message
to the device. One solution is to choose the last known gateway over which the device has communicated in the past.
To store and retrieve this information the [Device Connection API]({{< relref "/api/device-connection" >}}) offers
the opportunity to set and get the last known gateway for each device.


### Gateway Groups

For more complex or larger scenarios like with a larger number of gateways per device or where many devices use
the same gateways, it can become cumbersome to list all possible gateways in each *via* property of the affected devices.
This becomes even more complex when the gateways change frequently. To overcome such situations it is possible to define
groups of gateways using Hono's Device Registry Management API.

To add a (gateway) device to a gateway group, one can add the ID of the gateway group to the list in the *memberOf*
property of that (gateway) device. In the representation of the device that shall communicate via one of the members
of a gateway group one can add the ID of the group to the list in *viaGroups* property of the device representation.

It is important to note that the Device Registration API has no notion of gateway groups.
Thus, the response message of the Device Registration API's
[*assert Device Registration*]({{< relref "/api/device-registration#assert-device-registration" >}}) operation does
not contain the IDs of gateway groups in its *via* property but the IDs of all (gateway) devices that are a member of
any of the referenced gateway groups.

{{% note %}}
Hono's example device registry does not support nested gateway groups.
{{% /note %}}

## Connecting via a Protocol Gateway

Hono already comes with a set of standard protocol adapters for some of the well known IoT protocols.
However, in some cases devices do not communicate via standardized protocols but use a proprietary protocol.
To provide the service user of a Hono service the possibility to also connect devices speaking a proprietary protocol
Hono supports the concept of *protocol gateways*.

A *protocol gateway* is a separately deployed micro service, which understands proprietary protocol messages and translates
them to AMQP messages processable by Hono's AMQP protocol adapter.
In that sense a *protocol gateway* behaves like a device gateway speaking the proprietary protocol and forwards their
messages to the AMQP Messaging Network.

The diagram below provides an overview of the *protocol gateway* setup.
{{< figure src="../protocol_gateway.png" >}}  

Devices send telemetry/event messages via their own proprietary protocols to the *protocol gateway*,
which send the messages via AMQP to the Hono AMQP adapter.

A prerequisite for using a protocol gateway is that devices are registered in Hono's Device Registry
and are configured in such a way that the protocol gateway can act on their behalf i.e.
with a device registration *via* property containing the gateway id of the protocol gateway.

### Example Code

To illustrate how a *protocol adapter* could connect a proprietary protocol to the AMQP adapter, a simple example can be found in the [example module](https://github.com/eclipse/hono/tree/master/example/protocol-adapter-example).
