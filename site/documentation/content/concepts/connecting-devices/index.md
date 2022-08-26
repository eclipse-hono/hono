---
title: "Connecting Devices"
weight: 190
resources:
- src: device-types.svg
- src: device-via-gateway-auth.svg
- src: device-via-protocol-gw.svg
---

One of the most important features of Eclipse Hono&trade; is to abstract away the specific communication protocols
used by devices. This page describes the different ways of how devices can be connected to Hono.
<!-- more -->

Before a device can connect to Hono and upload data or receive commands from downstream applications,
it needs to be [provisioned]({{< relref "/concepts/device-provisioning" >}}) to the system.
As part of device provisioning, the device is associated with the *tenant* that it belongs to and gets
assigned a logical identifier which is unique within the tenant.

Devices can be generally partitioned into two groups: devices which natively support the Internet Protocol
(IP) for communication and devices that don't.

Devices falling into the former group can connect to Hono directly using any of the IP based protocols supported
by Hono's protocol adapters. Devices from the latter group often use radio based or serial line communication protocols
that are limited to a local area and require a *gateway* in order to connect to one of Hono's protocol
adapters via IP.

The diagram below shows a device that supports the MQTT protocol and connects directly to Hono's MQTT protocol adapter
and another device that uses Bluetooth LE for connecting locally to a gateway which then connects to Hono's MQTT adapter.

{{< figure src="device-types.svg" >}}

## Connecting to a Protocol Adapter directly

The most straight forward scenario is a device connecting to one of Hono's protocol adapters directly via IP based
network infrastructure. For this to work, the device needs to use a communication protocols supported
by one of the adapters and needs to be able to use the resource endpoints exposed by that particular protocol adapter
as described in its [user guide]({{< relref "/user-guide" >}}).

In this case the connected device's identity will be resolved as part of authentication during connection establishment.
For this to work, a set of credentials needs to be provisioned for the device which needs to be appropriate for
usage with one of the adapter's supported authentication schemes.

## Connecting via a Device Gateway

In some cases, a device may not be able to directly connect to one of Hono's protocol adapters.
An example is a device that uses a serial bus or radio waves for local communication.
Such devices can be connected to a protocol adapter by means of a *device gateway* which acts on behalf
of the device(s) when communicating with Hono. A device gateway is often implemented as a (small) hardware box
close to the devices, running some gateway software which translates hence and forth between the device and one
of Hono's protocol adapters.

From the perspective of a protocol adapter, the gateway looks just like any other device having its own device
identity and credentials.

The following diagram illustrates how a gateway publishes data on behalf of a device that uses Bluetooth for local
communication with the gateway.

{{< figure src="device-via-gateway-auth.svg" >}}

1. The device establishes a Bluetooth connection with the gateway.
2. The gateway sends an MQTT CONNECT packet to Hono's MQTT adapter to establish an MQTT connection.
   The packet contains the gateway's credentials.
3. The MQTT adapter determines the tenant from the *username* contained in the CONNECT packet and retrieves
   the hashed password that is on record for the gateway from the Credentials service.
4. The Credentials service returns the hashed password.
5. The MQTT adapter checks the password and accepts the connection request.
6. The device sends some sensor readings via Bluetooth to the gateway.
7. The gateway forwards the sensor data in an MQTT PUBLISH packet to the MQTT adapter.
   The topic name contains the identifier of the device that the gateway acts on behalf of.
8. The MQTT adapter invokes the Device Registration service's *assert Device Registration* operation to
   check if the gateway is authorized to act on behalf of the device.
9. The Device Registration service confirms the gateway's authorization.
10. The MQTT adapter accepts the sensor data from the gateway and forwards it downstream.

Note that the device itself is not authenticated by the MQTT adapter in this case. The responsibility
for establishing and verifying the device identity lies with the gateway in this setup.
It is therefore not necessary to provision credentials for the devices to Hono.

The [Device Registry Management API]({{< relref "/api/management" >}})'s `/devices` resource can be used to
register gateways and devices. The gateways that are authorized to act on behalf of a device can be set by means
of the device's *via* and *viaGroups* properties. This is useful in cases where a device may *roam* among multiple gateways.

When sending commands to a device, Hono needs to determine which of the authorized gateways should be used to forward
the command message to the device. For this purpose, Hono's protocol adapters keep track of the *last known gateway*
which has acted on behalf of each device by means of the [Command Router API]({{< relref "/api/command-router" >}}).


### Gateway Groups

In larger deployments with many gateways it can become cumbersome to list all possible gateways in the *via* property
of each device explicitly. This becomes even more of a burden when gateways are added and/or removed frequently.
To help with such situations it is possible to define groups of gateways using Hono's Device Registry Management API.

A gateway group can be defined implicitly by means of adding the group's identifier to the list in the *memberOf*
property of a (gateway) device that should belong to the group. The gateway group ID can then be added to the *viaGroups*
property of those devices that all gateways in the gateway group are authorized to act on behalf of.

Note that the Device Registration API, which is used by protocol adapters to verify if a gateway may act on behalf of a
device, has no notion of gateway groups. Thus, the response message of the Device Registration API's
[*assert Device Registration*]({{< relref "/api/device-registration#assert-device-registration" >}}) operation **does
not** contain the IDs of gateway groups in its *via* property but instead contains the IDs of all (gateway) devices
that are a member of any of the authorized gateway groups.

{{% notice info %}}
Hono's example device registry does not support nested gateway groups.
{{% /notice %}}

## Connecting via a Protocol Gateway

Hono already comes with a set of standard protocol adapters which support the most widely used (IP based) IoT protocols
like HTTP, MQTT and AMQP 1.0. Devices using one of these protocols might be able to directly connect to the corresponding
adapter as described in the previous section. However, even if the device supports MQTT it might still not be possible
to connect to the MQTT adapter because the device expects to use a topic structure that differs from the one employed
by the MQTT adapter. In other cases devices might use a proprietary, highly optimized, binary (IP based) protocol for
communication with back end infrastructure.

Hono supports connecting such devices to one of the standard protocol adapters by means of a *protocol gateway*.
A protocol gateway is a software service which translates hence and forth between the device's proprietary protocol and
the protocol used by the Hono protocol adapter. This concept is very similar to the *device gateway* described above.
The main difference is that a protocol gateway is usually deployed in the back end (close to the protocol adapter) whereas
a device gateway is usually deployed close to the devices that are connected to the gateway using a mechanism that is
usually constrained to a local area.

The diagram below illustrates how two devices use a proprietary IP based protocol to connect to a protocol gateway in the
back end which in turn is connected to Hono's standard AMQP 1.0 protocol adapter.

{{< figure src="device-via-protocol-gw.svg" >}}  

The devices publish data to the *protocol gateway* using the proprietary IP based protocol. The gateway then puts the
data into AMQP 1.0 messages and forwards them to the Hono AMQP adapter using the AMQP 1.0 protocol.

The requirements and prerequisites for this approach are the same as those for the standard device gateway scenario.
Authentication and authorization of gateways works in the same way.

## Generic MQTT Protocol Gateway Template

The repository [hono-extras](https://github.com/eclipse-hono/hono-extras) contains a generic template for an MQTT protocol gateway. 
This template allows you to develop a production-ready protocol gateway with minimal effort, with which you can connect 
existing MQTT-enabled devices to Hono.
For example, you can use other topic names or structures, or you can transform, enrich, compress, or encrypt the payload.

### Example Code

Hono's [*examples* module](https://github.com/eclipse-hono/hono/tree/master/examples/protocol-gateway-example) contains code for
a simple protocol gateway illustrating how devices using a binary TCP based protocol can be connected to Hono's AMQP adapter.
