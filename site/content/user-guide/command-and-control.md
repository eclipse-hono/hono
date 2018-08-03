+++
title = "Command and Control"
weight = 250
+++

Eclipse Hono&trade;'s Command &amp; Control API allows applications to send a command to a device and receive a response containing the result of the command.

This page illustrates the usage of the API based on Hono's example application.

<!--more-->

{{% warning %}}
The Command & Control functionality has been introduced in Hono 0.6 to the HTTP protocol adapter. As of Hono 0.7-M2 it is still considered a *technical preview* only. As such it is not intended to be used in production use cases yet.
{{% /warning %}}

Please refer to [Command &amp; Control API]({{< relref "api/Command-And-Control-API.md" >}}) and [Command &amp; Control Concepts]({{< relref "concepts/command-and-control.md" >}}) for further details.

## Preparations

Please refer to the [Getting started guide]({{< relref "getting-started.md" >}}) for the first steps to start Hono and 
register a device.

To keep things as simple as possible, it is assumed that a device with id `4711`is registered for the `DEFAULT_TENANT`.
This device is also the hard-coded default device in the following example application. 

## Starting the application

Hono comes with an example application (located in the `example` module) that is as small as possible but still covers the main message communication patterns.
Since Hono 0.6 this application also supports Command &amp; Control.

Please start (and potentially configure) the application as described [here]({{< relref "dev-guide/java_client_consumer.md" >}}).
The application writes the payload of incoming messages to standard output and will serve to view how messages are received
and sent by Hono. 

After the application has been successfully connected to the AMQP 1.0 network, it is time to send an appropriate downstream message to the HTTP protocol adapter to trigger the sending of a command. 

Note that it is the responsibility of the application to send a command - to illustrate how this is done, the example application sends a command `setBrightness` when it receives a downstream message that has a valid *time until disconnect* parameter set. Refer to the usage of the helper class `MessageTap` in the example code as a blueprint for writing your own application.

## Uploading Data and receiving a Command

To simulate an HTTP device, we use the standard tool `curl` to publish some JSON data for the device `4711`.
To signal that the device is willing to receive and process a command, the device uploads a telemetry or event message and includes the `hono-ttd` request parameter to indicate the number of seconds it will wait for the response:

    $ curl -i -X POST -u sensor1@DEFAULT_TENANT:hono-secret -H 'Content-Type: application/json' \
    --data-binary '{"temp": 5}' http://127.0.0.1:8080/telemetry?hono-ttd=30

Watch the example application that receives the message - on it's standard output you will find a line like

    Device is ready to receive a command : <TimeUntilDisconnectNotification{tenantId='DEFAULT_TENANT', deviceId='4711', readyUntil=2018-05-22T12:11:35.055Z}>

and some lines below
    
    [vert.x-eventloop-thread-0] DEBUG o.e.hono.client.impl.HonoClientImpl - Command client created successfully for [tenantId: DEFAULT_TENANT, deviceId: 4711]

The response to the `curl` command contains the command from the example application and looks like the following:

    HTTP/1.1 200 OK
    hono-command: setBrightness
    hono-cmd-req-id: 10117f669c12-09ef-416d-88c1-1787f894856d
    Content-Length: 23
    
    {
      "brightness" : 87
    }
    
The example application sets the `brightness` to a random value between 0 and 100 on each invocation. It also generates a unique correlation identifier for each new command to be sent to the device. The device will need to include this identifier in its response to the command so that the application can properly correlate the response with the request.

{{% note %}}
If you are running Hono on another node than the application, e.g. using *Docker Machine*, *Minikube* or *Minishift*, and the clock of that node is not in sync with the node that your (example) application is running on, then the application might consider the *time til disconnect* indicated by the device in its *hono-ttd* parameter to already have expired. This will happen if the application node's clock is ahead of the clock on the HTTP protocol adapter node. Consequently, this will result in the application **not** sending any command to the device.

Thus, you need to make sure that the clocks of the node running the application and the node running the HTTP protocol adapter are synchronized (you may want to search the internet for several solutions to this problem).
{{% /note %}}

## Sending the Response to the Command

After the device has received the command and has processed it, it needs to inform the application about the outcome. For this purpose the device uploads the result to the HTTP adapter using a new HTTP request. The following command simulates the device uploading some JSON payload indicating a successful result:

    $ curl -i -X POST -u sensor1@DEFAULT_TENANT:hono-secret -H 'Content-Type: application/json' \
    -H 'hono-cmd-status: 200' --data-binary '{"success": true}' \
    http://127.0.0.1:8080/control/res/10117f669c12-09ef-416d-88c1-1787f894856d
    
    HTTP/1.1 202 Accepted
    Content-Length: 0

**NB** Make sure to issue the command above before the application gives up on waiting for the response. By default, the example application will wait for as long as indicated in the `hono-ttd` parameter of the uploaded telemetry message. Also make sure to use the actual value of the `hono-cmd-req-id` header from the HTTP response that contained the command.


## Summary

The following parts of Hono are involved in the upper scenario:

* HTTP protocol adapter: detects the `hono-ttd` parameter in the request and opens an AMQP 1.0 receiver link with the AMQP 1.0
  Messaging Network in order to receive commands for the device
* example application: receives a telemetry message with `hono-ttd` which invokes an application internal callback that sends a
  command to the HTTP adapter via the opened receiver link. Additionally it opens a receiver link for any responses.
* HTTP protocol adapter: receives the command and forwards it to the device in the HTTP response body
* Device sends result of processing the command to HTTP adapter which then forwards it to the application

The [Command and Control Concepts]({{< relref "concepts/command-and-control.md" >}}) page contains sequence diagrams that
explain this in more detail.
