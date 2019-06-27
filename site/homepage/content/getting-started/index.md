---
title: "Getting started with Eclipse Hono"
linkTitle: "Getting started"
menu: "main"
weight: 100
resources:
  - src: Hono_instance.svg
---

This guide will walk you through an interactive example usage scenario of Eclipse Hono. You will learn how devices can use Hono's protocol adapters to publish telemetry data and events using both HTTP and/or MQTT. You will also see how a downstream application can consume this data using Hono's north bound API without requiring the application to know anything about the specifics of the communication protocols used by the devices.

## Prerequisites for the Getting started Guide

This guide requires several tools to be installed on your computer.
During the course of this guide, the devices publishing data will be represented by means of running some command line tools for posting HTTP requests and for publishing MQTT packets.

#### Curl HTTP Command Line Client

The `curl` command is used in this guide for registering devices with Hono and for simulating a device publishing data using HTTP.
On most *nix like systems `curl` will probably already be installed. Otherwise, please refer to the
[Curl project page](https://curl.haxx.se/) for installation instructions.

#### Mosquitto MQTT Command Line Client

The `mosquitto_pub` command is used in this guide for simulating a device publishing data using the MQTT protocol.
Please refer to the [Mosquitto project page](https://mosquitto.org/) for installation instructions, if you do not
have it installed already.

**NB** The installation of the Mosquitto command line client is optional. If you do not install it then you will not be able to simulate
an MQTT based device but otherwise will be able to get the same results described in this guide.

#### Hono Command Line Client

The Hono command line client is used in this guide for simulating an application that consumes telemetry data and events published by devices.
The client is available from [Hono's download page]({{< relref "downloads" >}}).
Note that running the command line client requires a Java 11 runtime environment being installed locally.

#### Hono Sandbox

The most important prerequisite is, of course, a Hono instance that you can work with.

The most straightforward option to use for this guide is the [Hono Sandbox]({{< relref "sandbox" >}}) which is running on
infrastructure provided by the Eclipse Foundation and which is publicly accessible from the internet.

Using the Sandbox, there is no need to set up your own Hono instance locally. However, it requires several non-standard ports
being accessible from your computer which may not be the case, e.g. if you are behind a firewall restricting internet access to
a few standard ports only.

You can verify if you can access the relevant ports of the Sandbox by running the following command and comparing the output:

~~~sh
curl -sIX GET http://hono.eclipse.org:28080/tenant/DEFAULT_TENANT
~~~

If you get output like this

~~~sh
HTTP/1.1 200 OK
content-type: application/json; charset=utf-8
content-length: 45
~~~

you can use the Sandbox. Run the following commands to set some environment variables which will be used during the guide

~~~sh
export REGISTRY_IP=hono.eclipse.org
export HTTP_ADAPTER_IP=hono.eclipse.org
export MQTT_ADAPTER_IP=hono.eclipse.org
~~~

and then proceed to the [Overview of Hono Components](#overview).

However, if the `curl` command yielded different output, you will need to set up Hono locally as described in the next section.

#### Setting up a local Hono Instance

In case you cannot access the Hono Sandbox as described above, you will need to set up an instance of Hono running on your local computer.
For evaluation purposes a single node *Minikube* cluster is sufficient to deploy Hono to.

1. Please refer to the [installation instructions](https://www.eclipse.org/hono/docs/latest/deployment/create-kubernetes-cluster/#local-development) for setting up a local Minikube cluster, then
1. follow the [Deployment Guide](https://www.eclipse.org/hono/docs/latest/deployment/helm-based-deployment/) in order to install Hono to your local Minikube cluster.

Once Hono has been deployed to your local cluster, run the following commands to set some environment variables which will be used during the guide

~~~sh
export REGISTRY_IP=$(kubectl get service hono-service-device-registry-ext --output='jsonpath={.status.loadBalancer.ingress[0].ip}' -n hono)
export HTTP_ADAPTER_IP=$(kubectl get service hono-adapter-http-vertx --output='jsonpath={.status.loadBalancer.ingress[0].ip}' -n hono)
export MQTT_ADAPTER_IP=$(kubectl get service hono-adapter-mqtt-vertx --output='jsonpath={.status.loadBalancer.ingress[0].ip}' -n hono)
~~~

<a name="overview"></a>
## Overview of Hono Components

Hono consists of a set of microservices which are deployed as Docker containers. The diagram below provides an overview of the containers that are part of the example deployment of Hono on the Sandbox or a local Minikube cluster.

{{< figure src="Hono_instance.svg" title="Components of the example Hono deployment" alt="The Docker containers representing the services of the example Hono deployment" >}}

* Hono Instance
  * An *HTTP Adapter* instance that exposes Hono's Telemetry and Event APIs as URI resources.
  * A *Kura Adapter* instance that exposes Hono's Telemetry and Event APIs as an [Eclipse Kura&trade;](https://www.eclipse.org/kura) compatible MQTT topic hierarchy.
  * An *MQTT Adapter* instance that exposes Hono's Telemetry and Event APIs as a generic MQTT topic hierarchy.
  * An *AMQP Adapter* instance that exposes Hono's Telemetry and Event APIs as a set of AMQP 1.0 addresses.
  * A *Device Registry* instance that manages registration information and issues device registration assertions to protocol adapters.
  * An *Auth Server* instance that authenticates Hono components and issues tokens asserting identity and authorities.
* AMQP Network
  * An *Apache Qpid Dispatch Router* instance that downstream applications connect to in order to consume telemetry data and events from devices.
  * An *Apache ActiveMQ Artemis* instance serving as the persistence store for events.
* Monitoring Infrastructure
  * A *Prometheus* instance for storing metrics data from services and protocol adapters.
  * A *Grafana* instance providing a dash board visualizing the collected metrics data.

In the example scenario used in the remainder of this guide, the devices will connect to the HTTP and MQTT adapters in order to publish telemetry data and events.
The devices will be authenticated using information stored in the Device Registry. The data is then forwarded downstream to the example application via the AMQP Messaging Network.

## Registering Devices

When a device tries to connect to one of Hono's protocol adapters, the protocol adapter first tries to authenticate the device using information kept in the Device Registry. 
The information maintained in the registry includes the *tenant* (a logical scope) that the device belongs to, the device's unique *identity* within the tenant and the *credentials* used by the device for authentication.

Before a device can connect to Hono and publish any data, the corresponding information needs to be added to the Device Registry.

### Creating a new Tenant

Choose a (random) tenant identifier and register it using the Device Registry's HTTP API (replace `my-tenant` with your identifier):
~~~sh
export MY_TENANT=my-tenant
curl -i -H "content-type: application/json" --data-binary '{"tenant-id": "'$MY_TENANT'"}' http://$REGISTRY_IP:28080/tenant

HTTP/1.1 201 Created
Location:  /tenant/my-tenant
Content-Length: 0
~~~

{{% note title="Conflict" %}}
You will receive a response with a `409` status code if a tenant with the given identifier already exists.
In this case, simply pick another (random) identifier and try again.
{{% /note %}}

### Adding a Device to the Tenant

Choose a (random) device identifier and register it using the Device Registry's HTTP API (replace `my-device` with your identifier):
~~~sh
export MY_DEVICE=my-device
curl -i -H "content-type: application/json" --data-binary '{"device-id": "'$MY_DEVICE'"}' http://$REGISTRY_IP:28080/registration/$MY_TENANT

HTTP/1.1 201 Created
Location: /registration/my-tenant/my-device
Content-Length: 0
~~~

### Setting a Password for the Device

Choose a (random) password and register it using the Device Registry's HTTP API (replace `my-pwd` with your password):
~~~sh
export MY_PWD=my-pwd
curl -i -H "content-type: application/json" --data-binary '{
  "device-id": "'$MY_DEVICE'",
  "type": "hashed-password",
  "auth-id": "'$MY_DEVICE'",
  "secrets": [{
      "pwd-plain": "'$MY_PWD'"
  }]
}' http://$REGISTRY_IP:28080/credentials/$MY_TENANT

HTTP/1.1 201 Created
Location: /credentials/my-tenant/my-device/hashed-password
Content-Length: 0
~~~

<a name="starting-a-consumer"></a>
## Starting the example Application

The telemetry data produced by devices is usually consumed by downstream applications that use it to implement their corresponding business functionality.
In this guide we will use the Hono command line client to simulate such an application.
The client will connect to Hono's north bound [Telemetry](https://www.eclipse.org/hono/docs/latest/api/telemetry-api/) and [Event API](https://www.eclipse.org/hono/docs/latest/api/event-api/)s using the AMQP 1.0 transport protocol, subscribe to all telemetry and event messages and log the messages to the console.

Open a new terminal window and set the `AMQP_NETWORK_IP` environment variable.
If you are using the Sandbox server:

~~~sh
export AMQP_NETWORK_IP=hono.eclipse.org
~~~

Otherwise, if you are using a local Minikube cluster:

~~~sh
export AMQP_NETWORK_IP=$(kubectl get service hono-dispatch-router-ext --output='jsonpath={.status.loadBalancer.ingress[0].ip}' -n hono)
~~~

The client can then be started from the command line as follows (make sure to replace `my-tenant` with your tenant identifier):

~~~sh
# in directory where the hono-cli-*-exec.jar file has been downloaded to
export MY_TENANT=my-tenant
java -jar hono-cli-*-exec.jar --hono.client.host=$AMQP_NETWORK_IP --hono.client.port=15672 --hono.client.username=consumer@HONO --hono.client.password=verysecret --spring.profiles.active=receiver --tenant.id=$MY_TENANT
~~~

## Publishing Telemetry Data to the HTTP Adapter

Now that the downstream application is running, devices can start publishing telemetry data and events using Hono's protocol adapters.
First, you will simulate a device publishing data to Hono using the HTTP protocol.
Go back to the original terminal and run:

~~~sh
curl -i -u $MY_DEVICE@$MY_TENANT:$MY_PWD -H 'Content-Type: application/json' --data-binary '{"temp": 5}' http://$HTTP_ADAPTER_IP:8080/telemetry

HTTP/1.1 202 Accepted
Content-Length: 0
~~~

If you have started the downstream application as described above, you should now see the telemetry message being logged to the application's console
in the other terminal. The output should look something like this:

~~~sh
13:36:49.169 [vert.x-eventloop-thread-0] INFO  org.eclipse.hono.cli.Receiver - received telemetry message [device: my-device, content-type: application/json]: {"temp": 5}
13:36:49.170 [vert.x-eventloop-thread-0] INFO  org.eclipse.hono.cli.Receiver - ... with application properties: {orig_adapter=hono-http, device_id=my-device, orig_address=/telemetry, JMS_AMQP_CONTENT_TYPE=application/json}
~~~

You can publish more data simply by re-running the `curl` command above with arbitrary payload.

{{% note title="Service Unavailable" %}}
When you invoke the command above for the first time, you may get the following response:

~~~
HTTP/1.1 503 Service Unavailable
Content-Length: 23
Content-Type: text/plain; charset=utf-8
Retry-After: 2

temporarily unavailable
~~~

This is because the first request to publish data for a given tenant is used as the trigger to establish a tenant specific sender link with
the AMQP 1.0 Messaging Network to forward the data over. However, the HTTP adapter may not receive credits quickly enough for the
request to be served immediately. You can simply ignore this response and re-submit the command.
{{% /note %}}

If you haven't started the application you will always get `503 Resource Unavailable` responses because Hono does not
accept any telemetry data from devices if there aren't any consumers connected that are interested in the data. The reason for this is
that Hono *never* persists Telemetry data and thus it doesn't make any sense to accept and process telemetry data if there is no
consumer to deliver it to.

The HTTP Adapter also supports publishing telemetry messages using *at least once* delivery semantics. For information on how that works
and additional examples for interacting with Hono via HTTP, please refer to the
[HTTP Adapter's User Guide](https://www.eclipse.org/hono/docs/latest/user-guide/http-adapter/).

## Publishing Events to the HTTP Adapter

In a similar way you can upload events:

~~~sh
curl -i -u $MY_DEVICE@$MY_TENANT:$MY_PWD -H 'Content-Type: application/json' --data-binary '{"alarm": "fire"}' http://$HTTP_ADAPTER_IP:8080/event

HTTP/1.1 202 Accepted
Content-Length: 0
~~~

Again, you should see the event being logged to the console of the downstream application.

## Publishing Telemetry Data to the MQTT Adapter

Devices can also publish data to Hono using the MQTT protocol. If you have installed the `mosquitto_pub` command line client, you
can run the following command to publish arbitrary telemetry data to Hono's MQTT adapter using QoS 0:

~~~sh
mosquitto_pub -h $MQTT_ADAPTER_IP -u $MY_DEVICE@$MY_TENANT -P $MY_PWD -t telemetry -m '{"temp": 5}'
~~~

Again, you should now see the telemetry message being logged to console of the downstream application.

The MQTT Adapter also supports publishing telemetry messages using QoS 1. For information on how that works
and additional examples for interacting with Hono via MQTT, please refer to the
[MQTT Adapter's User Guide](https://www.eclipse.org/hono/docs/latest/user-guide/mqtt-adapter/).

## Publishing Events to the MQTT Adapter

In a similar way you can upload events:

~~~sh
mosquitto_pub -h $MQTT_ADAPTER_IP -u $MY_DEVICE@$MY_TENANT -P $MY_PWD -t event -q 1 -m '{"temp": 5}'
~~~

Again, you should now see the telemetry message being logged to console of the downstream application.

{{% note title="Congratulations" %}}
You have successfully connected a device to Hono and published sensor data for consumption by an application connected to Hono's north bound API.
The application used the AMQP 1.0 protocol to receive messages regardless of the transport protocol used by the device to publish the data.

**What to try next?**

* Continue with the next sections to learn how applications can send commands to devices by means of the [Command & Control API](https://www.eclipse.org/hono/docs/latest/api/command-and-control/).
* Take a look at some of the metrics collected by Hono's components by opening the Hono dashboard. On the Sandbox server the dashboard is available at https://hono.eclipse.org:3000. When running a local Minikube cluster, please refer to [Opening the Dashboard](https://www.eclipse.org/hono/docs/latest/deployment/helm-based-deployment/#dashboard) for instructions.
* Check out the [User Guides](https://www.eclipse.org/hono/docs/latest/user-guide/) to explore more options for devices to connect to Hono using different transport protocols.
* Learn more about the managing tenants, devices and credentials using the [Device Registry's HTTP API](https://www.eclipse.org/hono/docs/latest/user-guide/device-registry/).
{{% /note %}}

## Sending Commands to a Device

The following walk-through example will guide you through some of the more advanced functionality of Hono.
In particular you will see how an application can send a command to a device and receive a response containing the outcome of the command.

### Starting the example application

Hono comes with an example application (located in the `example` module) that is as small as possible but still covers the main message communication patterns.
This application also supports Command &amp; Control.

Please start (and potentially configure) the application as described [here](https://www.eclipse.org/hono/docs/latest/dev-guide/java_client_consumer/).
The application writes the payload of incoming messages to standard output and will serve to view how messages are received
and sent by Hono. 

After the application has been successfully connected to the AMQP 1.0 network, it is time to send an appropriate downstream message to the HTTP protocol adapter to trigger the sending of a command. 

Note that it is the responsibility of the application to send a command - to illustrate how this is done, the example application sends a command `setBrightness` when it receives a downstream message that has a valid *time until disconnect* parameter set. Refer to the usage of the helper class `MessageTap` in the example code as a blueprint for writing your own application.

### Uploading Data and receiving a Command

To simulate an HTTP device, we use the standard tool `curl` to publish some JSON data for the device `4711`.
To signal that the device is willing to receive and process a command, the device uploads a telemetry or event message and includes the `hono-ttd` request parameter to indicate the number of seconds it will wait for the response:

    curl -i -X POST -u sensor1@DEFAULT_TENANT:hono-secret -H 'Content-Type: application/json' \
    --data-binary '{"temp": 5}' http://127.0.0.1:8080/telemetry?hono-ttd=30

Watch the example application that receives the message - on the console you will find a line looking similar to the following:

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

**NB:** If the application would send a *one-way command* instead (see [Command and Control Concepts](https://www.eclipse.org/hono/docs/latest/concepts/command-and-control/)), the `hono-cmd-req-id` response header would be missing.

{{% note %}}
If you are running Hono on another node than the application, e.g. using *Minikube* or *Minishift*, and the clock of that node is not in sync with the node that your (example) application is running on, then the application might consider the *time til disconnect* indicated by the device in its *hono-ttd* parameter to already have expired. This will happen if the application node's clock is ahead of the clock on the HTTP protocol adapter node. Consequently, this will result in the application **not** sending any command to the device.

Thus, you need to make sure that the clocks of the node running the application and the node running the HTTP protocol adapter are synchronized (you may want to search the internet for several solutions to this problem).
{{% /note %}}


### Uploading the Response to the Command

If the received command was *not* a *one-way command*, and the device has received the command and has processed it, it needs to inform the application about the outcome. For this purpose the device uploads the result to the HTTP adapter using a new HTTP request. The following command simulates the device uploading some JSON payload indicating a successful result:

    curl -i -X POST -u sensor1@DEFAULT_TENANT:hono-secret -H 'Content-Type: application/json' \
    -H 'hono-cmd-status: 200' --data-binary '{"success": true}' \
    http://127.0.0.1:8080/command/res/10117f669c12-09ef-416d-88c1-1787f894856d
    
    HTTP/1.1 202 Accepted
    Content-Length: 0

**NB** Make sure to issue the command above before the application gives up on waiting for the response. By default, the example application will wait for as long as indicated in the `hono-ttd` parameter of the uploaded telemetry message. Also make sure to use the actual value of the `hono-cmd-req-id` header from the HTTP response that contained the command.

### Using CLI (command line interface) to send Commands and receive Command responses

The command line client from the `cli` module supports the interactive sending of commands to connected devices.
In order to do so, the client needs to be run with the `command` profile as follows:
 
~~~sh
# in directory: hono/cli/
mvn spring-boot:run -Dspring-boot.run.arguments=--hono.client.host=localhost,--hono.client.username=consumer@HONO,--hono.client.password=verysecret -Dspring-boot.run.profiles=command,ssl
~~~

The client will prompt the user to enter the command's name, the payload to send and the payload's content type. For more information about command and payload refer to [Command and Control Concepts](https://www.eclipse.org/hono/docs/latest/concepts/command-and-control/).

The example below illustrates how a command to set the volume with a JSON payload is sent to device `4711`.

    >>>>>>>>> Enter name of command for device [<TenantId>:<DeviceId>] (prefix with 'ow:' to send one-way command):
    setVolume
    >>>>>>>>> Enter command payload:
    {"level": 50}
    >>>>>>>>> Enter content type:
    application/json
    
    INFO  org.eclipse.hono.cli.Commander - Command sent to device... [Command request will timeout in 60 seconds]

In the above example, the client waits up to 60 seconds for the response from the device before giving up.
For more information on how to connect devices, receive commands and send responses refer to [Commands using HTTP](https://www.eclipse.org/hono/docs/latest/user-guide/http-adapter/index.html#specifying-the-time-a-device-will-wait-for-a-response) and [Commands using MQTT](https://www.eclipse.org/hono/docs/latest/user-guide/mqtt-adapter/index.html#command-control).

The received command response `{"result":"success"}` is displayed as shown in the below example. 

    INFO  org.eclipse.hono.cli.Commander - Received Command response : {"result":"success"}

{{% note %}}
The command line client also supports sending of *one-way* commands to a device, i.e. commands for which no response is expected from the device.
In order to send a one-way command, the command name needs to be prefixed with `ow:`, e.g. `ow:setVolume`. The client will then not wait for
a response from the device but will consider the sending of the command successful as soon as the command message has been accepted by Hono.
{{% /note %}}

 The command line argument `command.timeoutInSeconds` can be used to set the timeout period (default is 60 seconds). The command line arguments `device.id` and `tenant.id` provide the device and tenant ID of the device that you want to send commands to.

~~~sh
# in directory: hono/cli/
mvn spring-boot:run -Dspring-boot.run.arguments=--hono.client.host=localhost,--hono.client.username=consumer@HONO,--hono.client.password=verysecret,--command.timeoutInSeconds=10,--device.id=4711,--tenant.id=DEFAULT_TENANT -Dspring-boot.run.profiles=command,ssl
~~~

### Summary

The following parts of Hono are involved in the upper scenario:

* HTTP protocol adapter: detects the `hono-ttd` parameter in the request and opens an AMQP 1.0 receiver link with the AMQP 1.0
  Messaging Network in order to receive commands for the device
* example application: receives a telemetry message with `hono-ttd` which invokes an application internal callback that sends a
  command to the HTTP adapter via the opened receiver link. Additionally it opens a receiver link for any responses.
* HTTP protocol adapter: receives the command and forwards it to the device in the HTTP response body
* Device sends result of processing the command to HTTP adapter which then forwards it to the application

The [Command and Control Concepts](https://www.eclipse.org/hono/docs/latest/concepts/command-and-control/) page contains sequence diagrams that
explain this in more detail.
