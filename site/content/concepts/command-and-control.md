+++
title = "Command & Control"
weight = 196
+++

Since Hono 0.6 *Business applications* can send commands to devices following the [Command & Control API]({{< ref "/api/Command-And-Control-API.md" >}}). This concept page describes how this API is used by applications to send commands to devices that connected to one of Hono's protocol adapters. These commands follow a request-response pattern and expect an immediate confirmation of their result.
 
<!--more-->

This concept refers to the current implementation of Command & Control that will be extended in the future.

{{% note %}}
This feature is available now as a fully working version but is considered to possibly have some unknown issues that may not make it
fully production ready yet.
{{% /note %}}

You can try the implementation directly by following the [Getting Started]({{< ref "getting-started" >}}) guide.


## Command & Control over HTTP Adapter

The following sequence diagram gives an overview of a device connecting via HTTP, which gets a command from the business application in the response to a downstream message - being an arbitrary event in this example. The application and the adapter connect to the AMQP Network, which forwards the transfer - for clarity this is not shown in the diagram. 
 
![Command & Control over HTTP Adapter](../command_control_concept_http.png) 

With the *hono-ttd* request parameter in (1) the device indicates it will stay connected for max. 30 seconds. In the shown example this means that it can handle the response to the HTTP request for up to 30 seconds before considering the request being expired. 

Internally the application is notified that there is a time interval of 30 seconds to send a command (see [Device notifications]({{< relref "device-notifications.md" >}}) for details).  This notification will be consumed by the application (2) and it now tries to send a command (3) to the device at the given address `control/TENANT/4711`.
If the device is not connected or the time interval is expired already, there is no such link open and the application would get no credits so send the command.

The HTTP Adapter gets the command and writes it in the response of the devices `send event` (4), if the request was successful (status 2xx). The HTTP Adapter sets the following response headers and optionally a payload.

| Response Header         | Description         |
| :---------------------  |  :----------------- |
| *hono-cmd*              | The name of the command to execute. Any input data required will be contained in the response body. |
| *hono-cmd-req-id*       | The unique identifier of the command. This identifier is used to correlate the device's response to the command with the request. |

The *hono-cmd* is the command that should be executed by the device. Typically this command needs to be known by the device and the payload may contain additional details of the command. The *hono-cmd-req-id* is needed for the command response to correlate it. It has to be sent back from the device to the adapter in a following operation (5). 
 
The device needs to respond to the command (5), to inform the business application about the outcome of executing the command. For this purpose 
specific URIs are defined in [HTTP Adapter]({{< relref "user-guide/http-adapter.md#sending-a-response-to-a-previously-received-command" >}}).

The URI contains the *hono-cmd-req-id* and a status code indicating the outcome of executing the command.

The HTTP Adapter will send the payload of the response back to the Business Application (6) by using the receiver link
that was opened by the application. If the response reached the application, the response request will be replied with
`202 Accepted`.

## Command & Control over MQTT Adapter

When the device is connected to the MQTT Adapter it receives commands on the topic:

* `control/[${tenant}]/[${device-id}]/req/${req-id}/${command}`

Authenticated devices typically subscribe to

* `control/+/+/req/#`

while unauthenticated devices have to fully specify their `${tenant}` and `${device-id}` during the subscription.

The response of the command will be sent by the device to 

* `control/[${tenant}]/[${device-id}]/res/${req-id}/${status}`

If the device is authenticated, the `${tenant}` and `${device-id}` are left empty (resulting in 3 subsequent `/`s).

![Command & Control over MQTT Adapter](../command_control_concept_mqtt.png) 

## Command & Control over AMQP Adapter

When a device connected to the AMQP adapter wants to receive commands from the adapter, it opens a receiver link specifying the following source address:

* `control` for authenticated devices
* `control/${tenant}/${device-id}` for unauthenticated devices

Once the receiver link is opened, the AMQP adapter sends command messages to devices through the link. The *subject* property of the request message contains the actual command to be executed on the device.

If the command request is a *one-way command*, then the device need not publish a command response message. However, if the application expects a response, then devices should publish a response back to the application. If an anonymous sender link is already opened by the device (e.g for publishing telemetry or events), then the device can reuse that link to publish the command response message. Otherwise, the device should publish the response by opening an anonymous sender link. The device should set the message address, status and correlation-id properties of the response accordingly. Consult the table below for a list of properties that a device must set on a command response message.

**Command Request Message Properties**

The following properties are set by the AMQP adapter on a command message sent to devices.

| Name              | Mandatory       | Location                 | Type        | Description |
| :--------------   | :-------------: | :----------------------- | :---------- | :---------- |
| *subject*         | yes             | *properties*             | *string*    | Contains the name of the command to be executed on a device.|
| *reply-to*        | yes             | *properties*             | *string*    | Contains the address to which the command response should be published to. This value is empty for one-way commands.|
| *correlation-id*  | yes             | *properties*             | *string*    | Contains the identifier used to correlate the response with the command request. If the command sent by the application contains a correlation-id, then that value is used as the correlation-id of the command request sent to the device. Otherwise, the value of the message-id property is used instead. |

**Command Response Message Properties**

If the application expects a response (i.e the *reply-to* property is set), then the device should set the following properties on a command response message.

| Name              | Mandatory       | Location                 | Type        | Description |
| :--------------   | :-------------: | :----------------------- | :---------- | :---------- |
| *to*              | yes             | *properties*             | *string*    | MUST contain the address to which the command response should be published to, which is the value of the reply-to property of the command request message.|
| *correlation-id*  | yes             | *properties*             | *string*    | MUST contain the identifier used to correlate the response with the original request, which is the value of the correlation-id of the original request. |
| *status*          | yes             | *application-properties* | *string*    | The status code indicating the outcome of processing the command by the device. MUST be set by the device after executing the command. |

![Command & Control over AMQP Adapter](../command_control_concept_amqp.png) 
