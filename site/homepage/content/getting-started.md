+++
title = "Getting started"
menu = "main"
weight = 100
+++

Eclipse Hono&trade; consists of a set of micro services provided as Docker images. You can either build the Docker images yourself from the source code or you can run Hono by means of the pre-built Docker images available from our [Docker Hub repositories](https://hub.docker.com/u/eclipse/).

This guide will walk you through building the images and example code from source, starting a Hono instance on your local computer and interacting with Hono via its HTTP adapter.

## Prerequisites

In order to build and run the images you need access to a [Docker](http://www.docker.com) daemon running either locally on your computer or another host you have access to. Please follow the instructions on the [Docker web site](http://www.docker.com) to install *Docker Engine* on your platform.

As noted above, Hono consists of multiple service components that together comprise a Hono instance. The remainder of this guide employs Docker's *Swarm Mode* for configuring, running and managing all Hono components as a whole.
In order to enable *Swarm Mode* on your *Docker Engine* run the following command

    docker swarm init

Please refer to the [Docker Swarm Mode documentation](https://docs.docker.com/engine/swarm/swarm-mode/) for details.

{{% warning %}}
You will need at least Docker Engine version 1.13.1 in order to run the example in this guide. By the time of writing, the latest released version of Docker was 18.09.0.
{{% /warning %}}

### Compiling

Recently released versions of OpenJDK 1.8.0 and 10 contain several bugs that prevent Hono from being compiled successfully using any of these JDKs.
It is therefore recommended to use OpenJDK 9 or 11.0.1 instead. Note that using any of these JDKs for compiling the source code will still result in class files that can be run on any JVM >= 1.8.0.

If you do not already have a working Maven installation on your system, please follow the [installation instructions on the Maven home page](https://maven.apache.org/).

Then run the following from the project's root folder

    mvn clean install -Ddocker.host=tcp://${host}:${port} -Pbuild-docker-image,metrics-prometheus

with `${host}` and `${port}` reflecting the name/IP address and port of the host where Docker is running on. This will build all libraries, Docker images and example code. If you are running on Linux and Docker is installed locally or you have set the `DOCKER_HOST` environment variable, you can omit the `-Ddocker.host` property definition.

If you plan to build the Docker images more frequently, e.g. because you want to extend or improve the Hono code, then you should define the `docker.host` property in your Maven `settings.xml` file containing the very same value as you would use on the command line as indicated above. This way you can simply do a `mvn clean install` later on and the Docker images will be built automatically as well because the `build-docker-image` profile is activated automatically if the Maven property `docker.host` is set.

{{% note %}}
The first build might take several minutes because Docker will need to download all the base images that Hono is relying on. However, most of these will be cached by Docker so that subsequent builds will be running much faster.
{{% /note %}}

## Starting Hono

As part of the build process, a set of scripts for deploying and undeploying Hono to/from a Docker Swarm is generated in the `deploy/target/deploy/docker` folder.
To deploy and start Hono simply run the following from the `deploy/target/deploy/docker` directory

~~~sh
# in base directory of Hono repository:
chmod +x deploy/target/deploy/docker/swarm_*.sh
deploy/target/deploy/docker/swarm_deploy.sh
~~~

The first command makes the generated scripts executable. This needs to be done once after each build.
The second command creates and starts up Docker Swarm *services* for all components that together comprise a Hono instance, in particular the following services are started:

{{< figure src="/hono/img/Hono_instance.svg" title="Hono Instance Containers">}}

* Hono Instance
  * An *HTTP Adapter* instance that exposes Hono's Telemetry and Event APIs as URI resources.
  * A *Kura Adapter* instance that exposes Hono's Telemetry and Event APIs as an [Eclipse Kura&trade;](https://www.eclipse.org/kura) compatible MQTT topic hierarchy.
  * A *MQTT Adapter* instance that exposes Hono's Telemetry and Event APIs as a generic MQTT topic hierarchy.
  * An *AMQP Adapter* instance that exposes Hono's Telemetry and Event APIs as a set of AMQP 1.0 addresses.
  * A *Device Registry* instance that manages registration information and issues device registration assertions to protocol adapters.
  * An *Auth Server* instance that authenticates Hono components and issues tokens asserting identity and authorities.
* AMQP Network
  * An *Apache Qpid Dispatch Router* instance that downstream applications connect to in order to consume telemetry data and events from devices.
  * An *Apache ActiveMQ Artemis* instance serving as the persistence store for events.
* Monitoring Infrastructure
  * A *Prometheus* instance for storing metrics data from services and protocol adapters.
  * A *Grafana* instance providing a dash board visualizing the collected metrics data.

You can list all services by executing

~~~sh
docker service ls
~~~

## Starting a Consumer

The telemetry data produced by devices is usually consumed by downstream applications that use it to implement their corresponding business functionality.
In this example we will use a simple command line client that logs all telemetry and event messages to the console.
You can start the client from the `cli` folder as follows:

~~~sh
# in directory: hono/cli/
mvn spring-boot:run -Drun.arguments=--hono.client.host=localhost,--hono.client.username=consumer@HONO,--hono.client.password=verysecret,--message.type=telemetry
~~~

Event messages are very similar to telemetry ones, except that they use `AT LEAST ONCE` quality of service. You can receive and log event messages uploaded to Hono using the same client.

In order to do so, run the client from the `cli` folder as follows:

~~~sh
# in directory: hono/cli/
mvn spring-boot:run -Drun.arguments=--hono.client.host=localhost,--hono.client.username=consumer@HONO,--hono.client.password=verysecret,--message.type=event
~~~

In order to receive and log both telemetry and event messages, run the client from the `cli` folder as follows:

~~~sh
# in directory: hono/cli/
mvn spring-boot:run -Drun.arguments=--hono.client.host=localhost,--hono.client.username=consumer@HONO,--hono.client.password=verysecret
~~~

{{% warning %}}
Replace *localhost* with the name or IP address of the host that Docker is running on.
{{% /warning %}}

## Publishing Data

Now that the Hono instance is up and running you can use Hono's protocol adapters to upload some telemetry data and watch it being forwarded to the downstream consumer.

The following sections will use the HTTP adapter to publish the telemetry data because it is very easy to access using a standard HTTP client like `curl` or [`HTTPie`](https://httpie.org/) from the command line.

Please refer to the [HTTP Adapter](https://www.eclipse.org/hono/docs/latest/user-guide/http-adapter/) documentation for additional information on how to access Hono's functionality via HTTP.

{{% warning %}}
The following sections assume that the HTTP adapter Docker container has been started on the local machine. However, if you started the HTTP adapter on another host or VM then make sure to replace *localhost* with the name or IP address of that (Docker) host.
{{% /warning %}}

### Registering a Device

The first thing to do is registering a device identity with Hono. Hono uses this information to authorize access to the device's telemetry data and functionality.

The following command registers a device with ID `4711` with the Device Registry.

~~~sh
curl -X POST -i -H 'Content-Type: application/json' -d '{"device-id": "4711"}' \
http://localhost:28080/registration/DEFAULT_TENANT
~~~

or (using HTTPie):

~~~sh
http POST http://localhost:28080/registration/DEFAULT_TENANT device-id=4711
~~~

The result will contain a `Location` header containing the resource path created for the device. In this example it will look like this:

~~~
HTTP/1.1 201 Created
Location: /registration/DEFAULT_TENANT/4711
Content-Length: 0
~~~

You can then retrieve registration data for the device using

~~~sh
curl -i http://localhost:28080/registration/DEFAULT_TENANT/4711
~~~

or (using HTTPie):

~~~sh
http http://localhost:28080/registration/DEFAULT_TENANT/4711
~~~

which will result in something like this:

~~~json
HTTP/1.1 200 OK
Content-Type: application/json; charset=utf-8
Content-Length: 35

{
  "data" : {
      "enabled": true
  },
  "device-id" : "4711"
}
~~~

### Uploading Telemetry Data using the HTTP Adapter

~~~sh
curl -X POST -i -u sensor1@DEFAULT_TENANT:hono-secret -H 'Content-Type: application/json' \
--data-binary '{"temp": 5}' http://localhost:8080/telemetry
~~~

or (using HTTPie):

~~~sh
http --auth sensor1@DEFAULT_TENANT:hono-secret POST http://localhost:8080/telemetry temp:=5
~~~

The username and password used above for device `4711` are part of the example configuration that comes with Hono. See [Device Identity](https://www.eclipse.org/hono/docs/latest/concepts/device-identity/) for an explanation of how devices are identified in Hono and how device identity is related to authentication.

When you first invoke any of the two commands above after you have started up your Hono instance, you may get the following response:

~~~
HTTP/1.1 503 Service Unavailable
Content-Length: 23
Content-Type: text/plain; charset=utf-8
Retry-After: 2

temporarily unavailable
~~~

This is because the first request to publish data for a tenant (`DEFAULT_TENANT` in the example) is used as the trigger to establish a tenant specific sender link with the AMQP 1.0 Messaging Network to forward the data over. However, the HTTP adapter may not receive credits quickly enough for the request to be served immediately.
You can simply ignore this response and re-submit the command. You should then get a response like this:

~~~
HTTP/1.1 202 Accepted
Content-Length: 0
~~~

If you have started the consumer as described above, you should now see the telemetry message being logged to the console. You can publish more data simply by issuing additional requests.

If you haven't started a consumer you will continue to get `503 Resource Unavailable` responses because Hono does not accept any telemetry data from devices if there aren't any consumers connected that are interested in the data. Telemetry data is *never* persisted within Hono, thus it doesn't make any sense to accept and process telemetry data if there is no consumer to deliver it to.

The HTTP Adapter also supports publishing telemetry messages using *at least once* delivery semantics. For information on how that works and additional examples for interacting with Hono via HTTP, please refer to the [HTTP adapter's User Guide](https://www.eclipse.org/hono/docs/latest/user-guide/http-adapter/) .

### Uploading Event Data using the HTTP Adapter

In a similar way you can upload event data, using curl

~~~sh
curl -X POST -i -u sensor1@DEFAULT_TENANT:hono-secret -H 'Content-Type: application/json' \
--data-binary '{"alarm": "fire"}' http://localhost:8080/event
~~~

or (using HTTPie):

~~~sh
http --auth sensor1@DEFAULT_TENANT:hono-secret POST http://localhost:8080/event alarm=fire
~~~

## Using Command & Control

Command & Control is available since Hono 0.8.

The following walk-through example shows how to use it.

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
If you are running Hono on another node than the application, e.g. using *Docker Machine*, *Minikube* or *Minishift*, and the clock of that node is not in sync with the node that your (example) application is running on, then the application might consider the *time til disconnect* indicated by the device in its *hono-ttd* parameter to already have expired. This will happen if the application node's clock is ahead of the clock on the HTTP protocol adapter node. Consequently, this will result in the application **not** sending any command to the device.

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
mvn spring-boot:run -Drun.arguments=--hono.client.host=localhost,--hono.client.username=consumer@HONO,--hono.client.password=verysecret -Drun.profiles=command,ssl
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
mvn spring-boot:run -Drun.arguments=--hono.client.host=localhost,--hono.client.username=consumer@HONO,--hono.client.password=verysecret,--command.timeoutInSeconds=10,--device.id=4711,--tenant.id=DEFAULT_TENANT -Drun.profiles=command,ssl
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

## Adding Tenants

In the above examples, we have always used the `DEFAULT_TENANT`, which is pre-configured in the example setup.

You can add more tenants to Hono by using the [Tenant management HTTP endpoints](https://www.eclipse.org/hono/docs/latest/user-guide/device-registry/index.html#managing-tenants) of the Device Registry. Each tenant you create can have its own configuration, e.g. for specifying which protocol adapters the tenant is allowed to use.

## Viewing Metrics

Open the [Grafana dashboard](http://localhost:3000) in a browser using `admin/admin` as login credentials.

{{% warning %}}
If you do not run Docker on localhost, replace *localhost* in the link with the correct name or IP address of the Docker host that the Grafana container is running on.
{{% /warning %}}

## Stopping Hono

The Hono instance's Docker services can be stopped and removed using the following command:

~~~sh
hono/deploy/target/deploy/docker/swarm_undeploy.sh
~~~

Please refer to the [Docker Swarm documentation](https://docs.docker.com/engine/swarm/services/) for details regarding the management of individual services.

## Restarting

In order to start up the instance again:

~~~sh
hono/deploy/target/deploy/docker/swarm_deploy.sh
~~~
