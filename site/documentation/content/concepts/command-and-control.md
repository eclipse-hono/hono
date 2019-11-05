+++
title = "Command & Control"
weight = 196
+++

*Business applications* can send commands to devices following the [Command & Control API]({{< relref "/api/command-and-control" >}}). This concept page describes how this API is used by applications to send commands and describes how Hono's protocol adapters process the commands so that they reach their target device.
 
<!--more-->

Commands can be sent following a *request/response* or a *one-way* pattern. For *Request/Response* commands, there is always a response expected from the device.

## General concept

The following sequence diagram gives an overview of a device indicating its availability for receiving commands, of a business application sending a command to the device and of the device sending back a command response. 

The application and the adapter connect to the AMQP Network, which forwards the transfer - for clarity this is not shown in the diagram below. 

{{< figure src="../command_control_concept.svg" title="Request/Response Command overview" >}}

In (1) the device subscribes for commands (if connecting to the AMQP or MQTT adapter) or indicates that it will stay connected for a given amount of time to receive a command (if connecting to the HTTP adapter), using the `ttd` ("time til disconnect") parameter.

The protocol adapter will then create the necessary AMQP consumer links with the AMQP messaging network (for more details see [below]({{< relref "#command-consumer-links-in-the-protocol-adapter" >}})) and will send a [notification]({{< relref "/concepts/device-notifications" >}}) to the application that the device is ready to receive commands (2).

After having created a sender link on the `command/TENANT` address, the application can then send commands, with the target device given in the AMQP message `to` address `command/TENANT/4711` (3). If a command response is expected, the command response address `command_response/TENANT/${replyId}` (with `replyId` being an arbitrary identifier chosen by the application) is to be set as `reply-to` property in the command message and a corresponding receiver link is to be opened by the application. For one-way commands, both is to be omitted.

After receiving the command, the protocol adapter instance will forward the command to the device (4) and send a disposition update back to the application (5).

In case of a Request/Response command, the device can then send a command response (6) that will be forwarded by the protocol adapter back to the application (7).

#### Command & Control involving a gateway

Hono has special support for sending commands to devices that are connected via a gateway to Hono.
Such devices are configured in the Hono device registration service in such a way, that certain gateways may act on behalf of the device.
The information, which of the configured gateways for a particular device was the last one through which a device interacted with Hono, is managed in the Hono [device connection service]({{< relref "/api/device-connection" >}}).

When sending commands, the northbound applications do not have to know to which gateway the command target device is connected to.
The application sends the command with the device address and Hono will direct the command to the gateway that the command target device was last connected to.
This mapping information is retrieved via the [device connection service]({{< relref "/api/device-connection" >}}).

#### Command consumer links in the protocol adapter

The protocol adapter opens 2 kinds of consumer links to receive commands.

1. **A tenant-scoped link on the address `command/${tenant}`**.  
This is the link address used by applications to send commands. Upon receiving a command message on this link, the protocol adapter checks whether the command target device id needs to be mapped to a gateway. Furthermore it is checked whether the target device (or mapped gateway) is actually connected to this particular protocol adapter instance. Since this kind of link is opened from all protocol adapter instances receiving commands for the tenant, the choice of which instance will get a particular command is random. Therefore, if necessary, the command (with the mapped gateway id if found) will be delegated to the adapter instance that is connected to the target device/gateway. 
That is done by forwarding the command on a device specific link address `control/${tenant}/${device}` to the AMQP messaging network.

2. **The device-scoped link on the address `control/${tenant}/${device}`**.  
On this link, commands will be received that have been forwarded from another protocol adapter instance. This link is also used for applications using the legacy control endpoint. Once support for the legacy endpoint has been removed, this link address will only be used for communication between protocol adapter instances, so attaching to this link address should not be enabled for applications then.

#### Example with multiple adapters involved

The following diagrams show the message flow if the command message is first received on a protocol adapter instance that the target device is *not* connected to.

{{< figure src="../command_control_concept_delegate_1.svg" title="Command subscription" >}}

In the scenario here there is a protocol adapter instance 2 through which a command subscription for the example tenant was already made (0). 

The device subscribes for commands (1) and the protocol adapter creates the receiver links (2,3) and sends the notification to the application via the AMQP messaging network (4).

{{< figure src="../command_control_concept_delegate_2.svg" title="Command handling" >}}

Upon receiving the notification, the application prepares sender and command response receiver links (1,2) and sends the command message to the AMQP messaging network.
Here it is received by protocol adapter instance 2. It is first checked here, whether the command needs to be mapped to a gateway. This is not the case here. As there is no such device subscribed for handling the command at this adapter instance, the command gets forwarded with the device specific address to the AMQP messaging network (4).
The protocol adapter instance that has opened a receiver link on this device-specific address is instance 1, which will receive the message (5) and forward it to the device (6). As the last step, an "accepted" disposition will be sent back to the application (7).

{{< figure src="../command_control_concept_delegate_3.svg" title="Command response handling" >}}

The command response message is sent back to the application from the protocol adapter via the AMQP messaging network.


## Gateway subscriptions

### Gateway subscribing for commands for a particular device

The following sequence diagrams show the different steps involved in having a gateway subscribe for commands of a particular device.

{{< figure src="../command_control_concept_gateway_1.svg" title="Command subscription" >}}

The gateway "gw-1" is connected to a protocol adapter and subscribes to commands for a device 4711 (1). This device has to be configured so that the gateway may act on its behalf (see [Configuring Gateway Devices]({{< relref "/admin-guide/device-registry-config.md#configuring-gateway-devices" >}}) for details).

The protocol adapter creates the tenant-scoped consumer link on the `command/TENANT` address (if it doesn't already exist) (2) and creates a device-specific consumer link with the gateway id in the address (`control/TENANT/gw-1`) (3).

Just like it is done when a protocol adapter handles any kind of message from a gateway acting on behalf of a device, the protocol adapter updates the [last-known gateway]({{< relref "/api/device-connection#set-last-known-gateway-for-device" >}}) information here, sending a request to the device connection service (4). The notification event is then sent containing the device id 4711 (5)

{{< figure src="../command_control_concept_gateway_2.svg" title="Command handling" >}}

After the application has prepared the sender and consumer links (1,2), it sends the command message on the `command/TENANT` link with the AMQP message `to` property set to `command/TENANT/4711` and `reply-to` set to the address of the command response consumer link (`command_response/TENANT/${replyId}` with `replyId` being an arbitrary identifier chosen by the application) (3).

After receiving the command message, the protocol adapter determines whether the command message needs to be mapped to a gateway. This is done by querying the [last-known gateway]({{< relref "/api/device-connection#get-last-known-gateway-for-device" >}}) operation of the device connection service. In the shown scenario, the determined gateway id is `gw-1`. As the protocol adapter instance has a subscription for the command target device `4711` connected via gateway `gw-1`, the command is published to that gateway (6). It is then the responsibility of the gateway to forward the command to the device `4711`.

{{< figure src="../command_control_concept_gateway_3.svg" title="Command response handling" >}}

When the device sends a command response via the gateway (1), the protocol adapter forwards the command message on the `command_response/TENANT/${replyId}` link to the application (2).


### Gateway subscribing for commands for all its devices

A gateway may also subscribe for commands sent to all the different devices that the gateway acts on behalf of. Such a scenario is shown in the following sequence diagram.

{{< figure src="../command_control_concept_gateway_all_devices_1.svg" title="Command subscription" >}}

The gateway subscribes for commands just like a normal device would, only using its id `gw-1` (1). The protocol adapter creates the tenant-scoped consumer link on the `command/TENANT` address (if it doesn't already exist) (2) and creates a device-specific consumer link with the gateway id in the address (`control/TENANT/gw-1`) (3).
The subscription notification sent to the application contains the gateway id `gw-1`. That means that either the application has to know about the gateway, or that it just assumes that the devices it sends commands to (and that are connected to the gateway) are always available for receiving commands. This may especially be the case for long-lasting command subscriptions (with the MQTT or AMQP adapter).

Note that the steps above do not involve an update of the last-known gateway information in the device connection service.
That means that when an application sends a command message to a particular device (not the gateway), one of the following conditions has to met for the command to be mapped to the gateway:

- The command target device has already sent a message via the gateway, so that the last-known gateway information was set.
- Or, the gateway is configured as the one and only `via` gateway of the command target device.

If one or the other is the case, commands targeted at the particular device will be handled the same way as shown in the chapter above for a gateway subscribing for a particular device. Handling of the command response is also the same and is therefore omitted here as well. 

If a gateway has already subscribed for commands for all its device, it may still subscribe for commands for a particular device (and the other way around).
The particular device subscription has precedence then in choosing over which subscription protocol/channel to send the command to the gateway.
