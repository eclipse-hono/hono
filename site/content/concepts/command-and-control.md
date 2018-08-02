+++
title = "Command & Control"
weight = 196
+++

Since Hono 0.6 *Business applications* can send commands to devices following the [Command & Control API]({{< relref "api/Command-And-Control-API.md" >}}). This concept describes how to use this API to send commands to devices connected to Hono via the Adapters. These direct commands follow a request-response pattern and expect an immediate confirmation of their result.  
 
<!--more-->

This concept refers to the first implementation of Command and Control. With new versions this will be extended and documentation parts will be also transferred to the User Guide sections (e.g. HTTP Adapter and MQTT Adapter).

{{% note %}}
This feature is available now as a first fully working version but is considered to possibly have some unknown issues that may not make it
fully production ready yet.
{{% /note %}}

You can try the implementation directly by following the [Command and Control Userguide]({{< relref "user-guide/command-and-control.md" >}}).


## Command & Control over HTTP Adapter

The following sequence diagram gives an overview of a device connecting via HTTP, which gets a command from the business application in the response to a downstream message - being an arbitrary event in this example. The application and the adapter connect to the AMQP Network, which forwards the transfer - for clarity this is not shown in the diagram. 
 
![Command & Control over HTTP Adapter](../command_control_concept_http.png) 

With the `hono-ttd` request parameter in (1) the device indicates it will stay connected for max. 30 seconds. In the shown example this means that it can handle the response to the HTTP request for up to 30 seconds before considering the request being expired. 

Internally the application is notified that there is a time interval of 30 seconds to send a command (see [Device notifications]({{< relref "device-notifications.md" >}}) for details).  This notification will be consumed by the application (2) and it now tries to send a command (3) to the device at the given address `control/TENANT/4711`.
If the device is not connected or the time interval is expired already, there is no such link open and the application would get no credits so send the command.

The HTTP Adapter gets the command and writes it in the response of the devices `send event` (4), if the request was successful (status 2xx). The HTTP Adapter sets the following response headers and optionally a payload.

| Response Header         | Description         |
| :---------------------  |  :----------------- |
| `hono-cmd`              | The command, which should be executed by the device. It could need further data from the payload. |
| `hono-cmd-req-id`       | Id, which is needed in the command response to correlate the response to the request.       |

 The `hono-cmd` is the command that should be executed by the device. Typically this command needs to be known by the device and the payload may contain additional details of the command. The `hono-cmd-req-id` is needed for the command response to correlate it. It has to be sent back from the device to the adapter in a following operation (5). 
 
The device needs to respond to the command (5), to inform the business application about the success. For this purpose 
specific URIs are defined in [HTTP Adapter]({{< relref "user-guide/http-adapter.md#sending-a-response-to-a-previously-received-command" >}}).

The URI contains the `hono-cmd-req-id` and a status that contains the outcome of the command.

The HTTP Adapter will send the payload of the response back to the Business Application (6) by using the receiver link
that was opened by the application. If the response reached the application, the response request will be replied with
`202 Accepted`. If the application has not kept the
link open, the response request will be replied with `503 Service unavailable`. 
If the `command-request-id` or the `hono-cmd-status` value is invalid, the response request will be 
replied with `400 Bad request`.

## Command & Control over MQTT Adapter

When the device is connected to the MQTT Adapter it receives commands on the topic:

* `control/[tenant]/[device-id]/req/<req-id>/<command>`

It depends on the authentication of the device if it is needed to give `tenant` and `device-id` at the subscription or just a `+`. 

The response of the command will be send by the device to 

* `control/[tenant]/[device-id]/res/<req-id>/<status>`

![Command & Control over MQTT Adapter](../command_control_concept_mqtt.png) 
