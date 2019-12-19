+++
title = "Gateways"
weight = 182
+++

This page describes how gateways and groups of gateways are represented and identified throughout Hono and its APIs.
<!--more-->
 ---

In some circumstances, a device may not directly communicate with Hono. An example is a device that does not have the capabilities to directly communicate with one of the protocol adapters.  Instead, such devices can connect to Hono through a gateway which are devices that act on behalf of other devices. From the perspective of Hono a gateway is another device following the description in [Device Identity]({{< relref "device-identity" >}}).

When a device wants to communicate over a gateway that gateway needs to be registered for the device. One can perform this registration as part of the [Device Registry Management API]({{< relref "/api/management" >}}) of the Device Registry. In that API each device as a 'via' property which is a list with the ids of devices that can act on behalf of the device as a gateway. 

Part of the functionality provided by the  [Device Registration API]({{< relref "/api/device-registration" >}}) is to assert that a device is actually registered to act for a given device as a gateway. Thus, for instance, the protocol adapters use this API to verify that the involved devices and the combination of device and gateway are valid.

In general, it possible to register multiple gateways with a device. Sometimes, for instance, while sending commands to the device, Hono needs to decide which of the possible gateways should be used to transmit that message to the device. One solution is to choose the last known gateway over which the device has communicated in the past. To store and retrieve this information the [Device Connection API]({{< relref "/api/device-connection" >}}) offers the opportunity to set and get the last known gateway for each device.    


## Gateway Groups
For more complex or larger scenarios like with a larger number of gateways per device or where many devices use the same gateways, it can become cumbersome to list all possible gateways in each 'via' property of the affected devices. This becomes even more complex when the gateways change frequently. To overcome such situations it is possible to define gateway groups in the Device Registry of Hono which describe a set of gateways. Instead of listing the ids of all registered gateways for a device in the 'via' property of that device, one can add the id of the gateway group to that 'via' property. 

To add a device to a gateway group and thus possibly make it a gateway for another device, one can add the id of the gateway group to the list in the 'memberOf' property of each device that one wants to be a part of that gateway group. One must not set the 'via' property and the 'memberOf' property for a device at the same time because Hono does currently not support groups of groups.     

It is important to note that the concept of gateway groups is only known in the scope of the Device Registry. This means that the ids of the groups are not known or used by other services within Hono. As a consequence, the [Device Registration API]({{< relref "/api/device-registration" >}}) does not include the ids of gateway groups when it includes the registered gateways for a device in a response message. Instead, the gateway groups are resolved to its members.    
