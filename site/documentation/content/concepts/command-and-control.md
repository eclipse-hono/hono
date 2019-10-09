+++
title = "Command & Control"
weight = 196
+++

*Business applications* can send commands to devices following the [Command & Control API]({{< relref "/api/command-and-control" >}}). This concept page describes how this API is used by applications to send commands to devices that connected to one of Hono's protocol adapters.
 
<!--more-->

Commands can be sent following a *request/response* or a *one-way* pattern. For *Request/Response* commands, there is always a response expected from the device.

## Command & Control over HTTP Adapter

The following sequence diagrams give an overview of a device connecting via HTTP, which gets a command from the business application in the response to a downstream message - being an arbitrary event in this example. 
[Device Notifications]({{< relref "/concepts/device-notifications" >}}) are used to indicate to the application the period in which the device is ready to receive a command.

The application and the adapter connect to the AMQP Network, which forwards the transfer - for clarity this is not shown in the diagrams. 

{{< figure src="../command_control_concept_one_way_http.svg" title="One-way Command over HTTP Adapter" >}} 

{{< figure src="../command_control_concept_http.svg" title="Request/Response Command over HTTP Adapter" >}}

With the *hono-ttd* request parameter in (1) the device indicates it will stay connected for max. 30 seconds. In the shown example this means that it can handle the response to the HTTP request for up to 30 seconds before considering the request being expired. 

Internally the application is notified that there is a time interval of 30 seconds to send a command (see [Device notifications]({{< relref "device-notifications.md" >}}) for details).  This notification will be consumed by the application (2) and it now tries to send a command (3) to the device at the given address `command/TENANT/4711`.
If the device is not connected or the time interval is expired already, there is no such link open and the application would get no credits so send the command.

The HTTP Adapter gets the command and writes it in the response of the devices `send event` (4), if the request was successful (status 2xx). The HTTP Adapter sets the following response headers and optionally a payload.

| Response Header         | Description         |
| :---------------------  |  :----------------- |
| *hono-cmd*              | The name of the command to execute. Any input data required will be contained in the response body. |
| *hono-cmd-req-id*       | Only set for **Request/Response commands** : The unique identifier of the command. This identifier is used to correlate the device's response to the command with the request. |

The *hono-cmd* is the command that should be executed by the device. Typically this command needs to be known by the device and the payload may contain additional details of the command. 


**For Request/Response commands**:

The *hono-cmd-req-id* response header is needed for the command response to correlate it. It has to be sent back from the device to the adapter in a following operation (5). 
 
The device needs to respond to the command (5), to inform the business application about the outcome of executing the command. For this purpose 
specific URIs are defined in [HTTP Adapter]({{< relref "/user-guide/http-adapter.md#sending-a-response-to-a-previously-received-command" >}}).

The URI contains the *hono-cmd-req-id* and a status code indicating the outcome of executing the command.

The HTTP Adapter will send the payload of the response back to the Business Application (6) by using the receiver link
that was opened by the application. If the response reached the application, the response request will be replied with
`202 Accepted`.

## Command & Control over MQTT Adapter

When the device is connected to the MQTT adapter, it can subscribe to the topic filters described in the [User Guide]({{< relref "/user-guide/mqtt-adapter.md#command-control" >}}).
The topic on which commands are published has the structure `command/[${tenant-id}]/[${device-id}]/req/[${req-id}]/${command}`.

For authenticated devices, the tenant-id and device-id topic levels are empty, and for *one-way* commands, the `${req-id}` level is.
For example, authenticated devices typically subscribe to `command///req/#`.

Gateways (which always need to authenticate) subscribe to commands for all devices they act on behalf of using topic filter
`command//+/req/#`
and will receive commands on a topic that contains the target device id.

In the case of request/response commands, the device must publish the response on the topic `command///res/${req-id}/${status}`, inserting the `${req-id}` from the command.

The following diagrams show the message flow for commands over the MQTT adapter:

{{< figure src="../command_control_concept_one_way_mqtt.svg" title="One-way Command over MQTT Adapter" >}} 

{{< figure src="../command_control_concept_mqtt.svg" title="Request/Response Command over MQTT Adapter" >}} 


## Command & Control over AMQP Adapter

When a device connected to the AMQP adapter wants to receive commands from the adapter, it opens a receiver link specifying the following source address:

* `command` for authenticated devices
* `command/${tenant}/${device-id}` for unauthenticated devices

Once the receiver link is opened, the AMQP adapter sends command messages to devices through the link. The *subject* property of the request message contains the actual command to be executed on the device.

If the command request is a *one-way command*, then the device need not publish a command response message. However, if the application expects a response, then devices should publish a response back to the application. If an anonymous sender link is already opened by the device (e.g for publishing telemetry or events), then the device can reuse that link to publish the command response message. Otherwise, the device should publish the response by opening an anonymous sender link. The device should set the message address, status and correlation-id properties of the response accordingly. 

Consult the [User Guide]({{< relref "/user-guide/amqp-adapter.md#command-control" >}}) for a list of properties in request and response messages.

{{< figure src="../command_control_concept_amqp.svg" title="Command & Control over AMQP Adapter" >}} 
