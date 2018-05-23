+++
title = "Command & Control"
weight = 196
+++

Since Hono 0.6 *Business applications* can send commands to devices following the [Command & Control API]({{< relref "api/Command-And-Control-API.md" >}}). This concept describes how to use this API to send commands to devices connected to Hono via the Adapters. These direct commands follow a request-response pattern and expect an immediate confirmation of their result.  
 
<!--more-->

This concept refers to the first implementation of Command and Control in Hono 0.6. With new versions this will be extended and documentation parts will be also transferred to the User Guide sections (e.g. HTTP Adapter and MQTT Adapter).

You can try the implementation directly by following the [Command and Control Userguide]({{< relref "user-guide/command-and-control.md" >}}).


## Command & Control over HTTP Adapter

The following sequence diagram gives an overview of a device connected via HTTP, which gets a command from the business application as soon as it gets online. The application and the adapter connect to the AMQP Network, which forwards the transfer - for clarity this is not shown in the diagram. 
 
![Command & Control over HTTP Adapter](../command_control_concept_http.png) 

With the `hono-ttd`-[notification]({{< relref "device-notifications.md" >}}) in (1) the device indicates it will stay connected for max. 30 seconds. This notification will be consumed by the application (2) and it now tries to send a command (3) to the device at the given address `control/TENANT/4711` (If the device is not connected, the adapter has not established a link for it and the application would get no credits so send the command).

The HTTP Adapter gets the command and writes it in the response of the devices `send event` (4), if the request was successful (status 2xx). The HTTP Adapter sets the following response headers and optionally a payload.

| Response Header              | Description         |
| :---------------------       |  :----------------- |
| `hono-cmd`               | The command, which should be executed by the device. It could need further data from the payload. |
| `hono-cmd-req-id`      | Id, which is needed in the command response to correlate the response to the request.       |

 The `hono-cmd` is the command, which need to be known by the device. The device can handle it and may also use the payload. The `hono-cmd-req-id` is needed for the command response to correlate it. It will be send back from the device to the adapter in a following operation (5). 
 
{{% note %}}
This is, what is implemented in Hono 0.6. At the moment the Application does not get a response back from the device as shown in (5), (6) and (7). Instead the adapter sends a response after sending the command to the device.  
{{% /note %}}

The device needs to respond to the command (5), to inform the business application about the success. It typically follows the way described in [HTTP Adapter]({{< relref "user-guide/http-adapter.md" >}}) for telemetry and events with the following changes:

Authenticated Device:

* URI: `/control/res/${reqId}/${status}` 
* Method: `POST`

Unauthenticated Device:

* URI: `/control/res/${tenantId}/${deviceId}/${reqId}/${status}` 
* Method: `PUT`

For authenticated and unauthenticated devices:

* Request Body:
  * (optional) Arbitrary payload as response data.

The HTTP Adapter will then send this response back to the Business Application (6). 

## Command & Control over MQTT Adapter

IMPORTANT: **Not implemented in Hono 0.6 and just a DRAFT**

When the (authenticated) device connects to the MQTT Adapter and wants to get commands, it subscribes to 

* `control/req/${command}/${hono-cmd-req-id}`

The response of the command will be send by the device to 

* `control/res/${hono-cmd-req-id}/${status}`


If the device is not authenticated:

* `control/req/${tenant-id}/${device-id}/${command}/${hono-cmd-req-id}` (request subscription topic)
* `control/res/${tenant-id}/${device-id}/${hono-cmd-req-id}/${status}` (response topic) 


![Command & Control over MQTT Adapter](../command_control_concept_mqtt.png) 
