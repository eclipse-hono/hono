+++
title = "Command and Control"
weight = 250
+++

In Hono 0.6 the first implementation of Command and Control is available that enables applications to send a command
upstream to a device.

In the following it is shown how a command is sent to a device by using Hono's example application.

<!--more-->


{{% note %}}
Note that in Hono 0.6 Command and Control is only supported by the HTTP protocol adapter.
{{% /note %}}

Refer to [Command and Control API]({{< relref "api/Command-And-Control-API.md" >}}) and [Command and Control Concepts]({{< relref "concepts/command-and-control.md" >}}) for detailed explanations and specifications.

## Preparations

Please refer to the [Getting started guide]({{< relref "getting-started.md" >}}) for the first steps to start Hono and 
register a device.

To keep things as simple as possible, it is assumed that a device with id `4711`is registered for the `DEFAULT_TENANT`.
This device is also the hard-coded default device in the following example application. 

## Starting the application

Hono comes with an example application that is as small as possible but still covers the main message communication patterns.
For Hono 0.6 this application was extended to also support Command and Control.

Please start (and potentially configure) the application as described [here]({{< relref "dev-guide/java_client_consumer.md" >}}).
The application writes the payload of incoming messages to `System.out` and will serve to view how messages are received
and sent by Hono. 

After the application has been successfully connected to
the AMQP 1.0 network, it is time to send an appropriate downstream message to the HTTP protocol adapter that is responded 
with a command.  

## Sending a downstream message and receive a command in the response

Publish some JSON data for device `4711` and set the *time until disconnect* parameter `hono-ttd`:

    $ curl -i -X POST -u sensor1@DEFAULT_TENANT:hono-secret -H 'Content-Type: application/json' \
    $ --data-binary '{"temp": 5}' http://127.0.0.1:8080/telemetry?hono-ttd=5

Watch the application that receives the message, on it's `stdout` you will find a line like

    Device is ready to receive a command : <TimeUntilDisconnectNotification{tenantId='DEFAULT_TENANT', deviceId='4711', readyUntil=2018-05-22T12:11:35.055Z}>

and some lines below
    
    14:11:30.059 [vert.x-eventloop-thread-0] DEBUG o.e.hono.client.impl.HonoClientImpl - Command client created successfully for [tenantId: DEFAULT_TENANT, deviceId: 4711]

The `curl` command (which simulates an HTTP device in this example) is responded like the following:

    HTTP/1.1 202 Accepted
    hono-command: setBrightness
    hono-cmd-req-id: 47#cmd-client-299c7172-75ce-484d-bee1-cd279755c5fea3c84d64-681e-4ba3-9e74-f8e1ca412d60
    Content-Length: 23
    
    {
      "brightness" : 87
    }
    
Every time the `curl` message is repeated, the response will be differing regarding the value of `brightness` and the
`hono-cmd-req-id`.

The `brightness` is set to a random value inside the application between `0` and `100`, while the `hono-cmd-req-id` is
set to a unique id that can be used by the device to send a response to exactly this command later on. 
To correlate such a respond coming from the device is currently in the responsibility of the application itself.
    
## Summary

The following parts of Hono are involved in the upper scenario:

- HTTP protocol adapter: detects the `hono-ttd` parameter in the request and opens an AMQP 1.0 receiver link for the device
- example application: receives a telemetry message with `hono-ttd` which invokes an application internal callback that
  sends a command to the opened receiver link. Additionally it opens a receiver link for any responses.
- HTTP protocol adapter: receives the command and 
  - responses to the HTTP request and include the command in the HTTP response
  - opens a sender link to the application and sends a successful response (with empty payload) to the application.
 
The [Command and Control Concepts]({{< relref "concepts/command-and-control.md" >}}) page contains sequence diagrams that
explain this in further details.
 
{{% note %}}
The successful response is only the current (incomplete) implementation in Hono 0.6 and is foreseen to be substituted by a response of
the device itself in the future.
{{% /note %}}



