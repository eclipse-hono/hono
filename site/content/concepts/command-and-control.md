+++
title = "Command & Control"
weight = 196
+++

*Business applications* can send commands to devices. The intended cases as well the state of implementation are explained here. 
 
<!--more-->

This concept refers to the first version of Command and Control in Hono 0.6. With new versions this will be extended.

## Sending a command

**Use Cases**

1. The application sends a command to a Device and expects a direct response from the device.
2. The application sends a command, but does not need a direct response. Maybe it gets later an normal event indicating a response.

## Command & Control over HTTP Adapter

The following sequence diagram gives an overview of case 1. with a device connected via HTTP.
 
![Command & Control over HTTP Adapter](../command_control_concept_http.png) 

With the `ttd`-[notification]({{< relref "device-notifications.md" >}}) in (1) the device indicates it will stay connected for max. 30 seconds. This notification will be consumed by the application (2) and it now tries to send a command (3) to the device at the given address `control/TENANT/4711` (If the device would not be connected and the adapter has not established a link for it, the application would got no credits so send the command).

The HTTP Adapter gets the command and writes it in the response of the devices `send event` (4).

{{% note %}}
This is, what is implemented in Hono 0.6. At the moment the Application does not get a response back from the device as shown in (5) and (6). 
{{% /note %}}
