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
curl -sIX GET http://hono.eclipse.org:28080/v1/tenants/DEFAULT_TENANT
~~~

If you get output like this

~~~sh
HTTP/1.1 200 OK
etag: 89d40d26-5956-4cc6-b978-b15fda5d1823
content-type: application/json; charset=utf-8
content-length: 260
~~~

you can use the Sandbox. Run the following commands to set some environment variables which will be used during the guide

~~~sh
export REGISTRY_IP=hono.eclipse.org
export HTTP_ADAPTER_IP=hono.eclipse.org
export MQTT_ADAPTER_IP=hono.eclipse.org
~~~

and then proceed to the [Overview of Hono Components]({{< relref "#overview-of-hono-components" >}}).

However, if the `curl` command yielded different output, you will need to set up Hono locally as described in the next section.

#### Setting up a local Hono Instance

In case you cannot access the Hono Sandbox as described above, you will need to set up an instance of Hono running on your local computer.
For evaluation purposes a single node *Minikube* cluster is sufficient to deploy Hono to.

1. Please refer to the [installation instructions]({{% doclink "/deployment/create-kubernetes-cluster/#setting-up-a-local-development-environment" %}}) for setting up a local Minikube cluster, then
1. follow the [Deployment Guide]({{% doclink "/deployment/helm-based-deployment/" %}}) in order to install Hono to your local Minikube cluster.

Once Hono has been deployed to your local cluster, run the following commands to set some environment variables which will be used during the guide

~~~sh
export REGISTRY_IP=$(kubectl get service hono-service-device-registry-ext --output='jsonpath={.status.loadBalancer.ingress[0].ip}' -n hono)
export HTTP_ADAPTER_IP=$(kubectl get service hono-adapter-http-vertx --output='jsonpath={.status.loadBalancer.ingress[0].ip}' -n hono)
export MQTT_ADAPTER_IP=$(kubectl get service hono-adapter-mqtt-vertx --output='jsonpath={.status.loadBalancer.ingress[0].ip}' -n hono)
~~~

Verify the last step with

~~~sh
echo $REGISTRY_IP
~~~

If this does not print an IP address, check that `minikube tunnel` is running.

{{% note title="Missing DEFAULT_TENANT" %}}
Due to [a bug in the example device registry](https://github.com/eclipse/hono/issues/1599) you need to
manually create the `DEFAULT_TENANT` in your local Hono instance before proceeding to the
next steps. To do so, please run:
```sh
curl -i -X POST http://$REGISTRY_IP:28080/v1/tenants/DEFAULT_TENANT -H "content-type: application/json"
```
{{% /note %}}

<a name="overview"></a>
## Overview of Hono Components

Hono consists of a set of microservices which are deployed as Docker containers. The diagram below provides an overview of the containers that are part of the example deployment of Hono on the Sandbox or a local Minikube cluster.

{{< figure src="Hono_instance.svg" title="Components of the example Hono deployment" alt="The Docker containers representing the services of the example Hono deployment" >}}

* Hono Instance
  * An *HTTP Adapter* instance that exposes Hono's Telemetry and Event APIs as URI resources.
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

Register a tenant using Hono's Device Registry's management HTTP API (a random tenant identifier will be generated):

~~~sh
curl -i -X POST http://$REGISTRY_IP:28080/v1/tenants

HTTP/1.1 201 Created
etag: becc93d7-ab0f-48ec-ad26-debdf339cbf4
location: /v1/tenants/85f63e23-1b78-4156-8500-debcbd1a8d35
content-type: application/json; charset=utf-8
content-length: 45

{"id":"85f63e23-1b78-4156-8500-debcbd1a8d35"}
~~~

{{% note title="Random tenant ID value" %}}
You will receive a randomly generated tenantId value. It will probably be different than the value given in this example.
Make sure to export it to an environment variable to make the following steps easier:

~~~sh
export MY_TENANT=85f63e23-1b78-4156-8500-debcbd1a8d35
~~~
{{% /note %}}

### Adding a Device to the Tenant

Register a device using Hono's Device Registry's management HTTP API (a random device identifier will be assigned):

~~~sh
curl -i -X POST http://$REGISTRY_IP:28080/v1/devices/$MY_TENANT

HTTP/1.1 201 Created
etag: 68eab243-3df9-457d-a0ab-b702e57c0c17
location: /v1/devices/85f63e23-1b78-4156-8500-debcbd1a8d35/4412abe2-f219-4099-ae14-b446604ae9c6
content-type: application/json; charset=utf-8
content-length: 45

{"id":"4412abe2-f219-4099-ae14-b446604ae9c6"}
~~~

{{% note title="Random device ID value" %}}
You will receive a randomly generated deviceId value. It will probably be different than the value given in this example.
Make sure to export it to an environment variable to make the following steps easier:

~~~sh
export MY_DEVICE=4412abe2-f219-4099-ae14-b446604ae9c6
~~~
{{% /note %}}

### Setting a Password for the Device

Choose a (random) password and register it using Hono's Device Registry's management HTTP API (replace `my-pwd` with your password):

~~~sh
export MY_PWD=my-pwd
curl -i -X PUT -H "content-type: application/json" --data-binary '[{
  "type": "hashed-password",
  "auth-id": "'$MY_DEVICE'",
  "secrets": [{
      "pwd-plain": "'$MY_PWD'"
  }]
}]' http://$REGISTRY_IP:28080/v1/credentials/$MY_TENANT/$MY_DEVICE

HTTP/1.1 204 Updated
etag: cf91fd4d-7111-4e8a-af68-c703993a8be1
Content-Length: 0
~~~

<a name="starting-a-consumer"></a>
## Starting the example Application

The telemetry data produced by devices is usually consumed by downstream applications that use it to implement their corresponding business functionality.
In this guide we will use the Hono command line client to simulate such an application.
The client will connect to Hono's north bound [Telemetry]({{% doclink "/api/telemetry/" %}}) and [Event API]({{% doclink "/api/event/" %}})s using the AMQP 1.0 transport protocol, subscribe to all telemetry and event messages and log the messages to the console.

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
[HTTP Adapter's User Guide]({{% doclink "/user-guide/http-adapter/" %}}).

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
[MQTT Adapter's User Guide]({{% doclink "/user-guide/mqtt-adapter/" %}}).

## Publishing Events to the MQTT Adapter

In a similar way you can upload events:

~~~sh
mosquitto_pub -h $MQTT_ADAPTER_IP -u $MY_DEVICE@$MY_TENANT -P $MY_PWD -t event -q 1 -m '{"alarm": "fire"}'
~~~

Again, you should now see the telemetry message being logged to console of the downstream application.

{{% note title="Congratulations" %}}
You have successfully connected a device to Hono and published sensor data for consumption by an application connected to Hono's north bound API.
The application used the AMQP 1.0 protocol to receive messages regardless of the transport protocol used by the device to publish the data.

**What to try next?**

* Continue with the next sections to learn how applications can send commands to devices by means of the [Command & Control API]({{% doclink "/api/command-and-control/" %}}).
* Take a look at some of the metrics collected by Hono's components by opening the Hono dashboard. On the Sandbox server the dashboard is available at https://hono.eclipse.org:3000. When running a local Minikube cluster, please refer to [Opening the Dashboard]({{% doclink "/deployment/helm-based-deployment/#accessing-the-grafana-dashboard" %}}) for instructions.
* Check out the [User Guides]({{% doclink "/user-guide/" %}}) to explore more options for devices to connect to Hono using different transport protocols.
* Learn more about the managing tenants, devices and credentials using the [Device Registry's HTTP API]({{% doclink "/user-guide/device-registry/" %}}).
{{% /note %}}

## Advanced: Sending Commands to a Device

The following example will guide you through an advanced feature of Hono. You will see how an application can send a command 
to a device and receive a response with the result of processing the command on the device. The communication direction here is exactly the other way round than with telemetry and events. 

The following assumes that the steps in the [Prerequisites for the Getting started Guide]({{< relref "#prerequisites-for-the-getting-started-guide" >}}) 
and [Registering Devices]({{< relref "#registering-devices" >}}) sections above have been completed. 
To simulate the device, you can use the Mosquitto tools again while the Hono Command Line Client simulates the application as before. 

### Receiving a Command

With the `mosquitto_sub` command you simulate an MQTT device that receives a command.
Create a subscription to the command topic in the terminal for the simulated device (don't forget to set the environment variables `MQTT_ADAPTER_IP`, `MY_TENANT` and `MY_DEVICE`)
 
 ~~~sh
 mosquitto_sub -v -h $MQTT_ADAPTER_IP -u $MY_DEVICE@$MY_TENANT -P $MY_PWD -t command/+/+/req/#
 ~~~

Now that the device is waiting to receive commands, the application can start sending them.
Start the Command Line Client in the terminal for the application side (don't forget to set the environment variables `AMQP_NETWORK_IP`, `MY_TENANT` and `MY_DEVICE`)

~~~sh
# in directory where the hono-cli-*-exec.jar file has been downloaded to
java -jar hono-cli-*-exec.jar --hono.client.host=$AMQP_NETWORK_IP --hono.client.port=15672 --hono.client.username=consumer@HONO --hono.client.password=verysecret --tenant.id=$MY_TENANT --device.id=$MY_DEVICE --spring.profiles.active=command
~~~

Note that this time the profile is `command` instead of `receiver`, which enables a different mode of the Command Line Client.

The client will prompt you to enter the command's name, the payload to send and the payload's content type. 
The example below illustrates how a one-way command to set the volume with a JSON payload is sent to the device.

~~~sh
>>>>>>>>> Enter name of command for device [<DeviceId>] in tenant [<TenantId>] (prefix with 'ow:' to send one-way command):
ow:setVolume
>>>>>>>>> Enter command payload:
{"level": 50}
>>>>>>>>> Enter content type:
application/json

INFO  org.eclipse.hono.cli.app.Commander - Command sent to device
~~~

In the terminal for the simulated device you should see the received command as follows

    command///req//setVolume {"level": 50}

### Sending a Response to a Command

Now that you have sent a one-way command to the device,  you may get to know _request/response_ commands where the device sends a response to the application.
A _request/response_ command received from an application contains an identifier that is unique to each new command. 
The device must include this identifier in its response so that the application can correctly correlate the response with the request.

If you send a _request/response_ command like this 

~~~sh
>>>>>>>>> Enter name of command for device [<DeviceId>] in tenant [<TenantId>] (prefix with 'ow:' to send one-way command):
setBrightness
>>>>>>>>> Enter command payload:
{"brightness": 87}
>>>>>>>>> Enter content type:
application/json

INFO  org.eclipse.hono.cli.app.Commander - Command sent to device... [waiting for response for max. 60 seconds]
~~~

the application will wait up to 60 seconds for the device's response. 

In the terminal for the simulated device you should see the received command that looks like this

    command///req/10117f669c12-09ef-416d-88c1-1787f894856d/setBrightness {"brightness": 87}

The element between `req` and `setBrightness` is the request identifier that must be included in the response.

You can cancel the command `mosquitto_sub` in the terminal of the device (press the key combination `Ctrl + C`) to reuse the 
configuration with the  environment variables for sending the response.
The following example shows how an answer can be sent with MQTT. Note that the actual identifier from the received command must be used.

~~~sh
export REQ_ID=10117f669c12-09ef-416d-88c1-1787f894856d
mosquitto_pub -h $MQTT_ADAPTER_IP -u $MY_DEVICE@$MY_TENANT -P $MY_PWD -t command///res/$REQ_ID/200 -m '{"success": true}'
~~~

The `200` at the end of the topic is an HTTP status code that reports the result of processing the command to the application.

If the Command Line Client has successfully received the response in time, it will print it to the console. This looks like this:

~~~sh
INFO  org.eclipse.hono.cli.app.Commander - Received Command response: {"success": true}
~~~

If the 60 seconds have already expired, an error message is logged.
In this case you can send a new command or restart the Command Line Client with a higher timeout (append `--command.timeoutInSeconds=120`).
    
Congratulations. Now you have successfully sent commands to a device and responded to them. 
For more information on Command &amp; Control refer to [Commands using HTTP]({{% doclink "/user-guide/http-adapter/#specifying-the-time-a-device-will-wait-for-a-response" %}}) 
and [Commands using MQTT]({{% doclink "/user-guide/mqtt-adapter/#command-control" %}}).
The [Command and Control Concepts]({{% doclink "/concepts/command-and-control/" %}}) page contains sequence diagrams that
explain this in more detail.
