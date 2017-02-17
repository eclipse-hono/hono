+++
title = "Getting started"
menu = "main"
weight = 100
+++

Eclipse Hono&trade; consists of a set of micro services provided as Docker images. You can either build the Docker images yourself from the source code or you can run Hono by means of the pre-built Docker images available from our [Docker Hub repositories](https://hub.docker.com/u/eclipsehono/).

This guide will walk you through building the images and example code from source, starting a Hono instance on your local computer and interacting with Hono via its REST adapter.

## Prerequisites

In order to build and run the images you need access to a [Docker](http://www.docker.com) daemon running either locally on your computer or another host you have access to. Please follow the instructions on the [Docker web site](http://www.docker.com) to install *Docker Engine* on your platform.

As noted above, Hono consists of multiple service components that together comprise a Hono instance. The remainder of this guide employs *Docker Compose* for configuring, running and managing all Hono components as a whole. Please follow the instructions on the [Docker Compose site](https://docs.docker.com/compose/install/) to install the Docker Compose client on your computer.

{{% warning %}}
You will need at least Docker version 1.13.0 and Docker Compose version 1.10.0 in order to run the example in this guide. By the time of this writing, the latest released versions of Docker and Docker Compose were 1.13.1 and 1.11.1 respectively.
{{% /warning %}}

### Compiling

If you do not already have a working Maven installation on your system, please follow the [installation instructions on the Maven home page](https://maven.apache.org/).

Then run the following from the project's root folder

    ~/hono$ mvn clean install -Ddocker.host=tcp://${host}:${port} -Pbuild-docker-image

with `${host}` and `${port}` reflecting the name/IP address and port of the host where Docker is running on. This will build all libraries, Docker images and example code. If you are running on Linux and Docker is installed locally or you have set the `DOCKER_HOST` environment variable, you can omit the `-Ddocker.host` property definition.

If you plan to build the Docker images more frequently, e.g. because you want to extend or improve the Hono code, then you should define the `docker.host` property in your Maven `settings.xml` file containing the very same value as you would use on the command line as indicated above. This way you can simply do a `mvn clean install` later on and the Docker images will be built automatically as well because the `build-docker-image` profile is activated automatically if the Maven property `docker.host` is set.

## Starting Hono

The easiest way to start the server components is by using *Docker Compose*. As part of the build process a Docker Compose file is generated under `example/target/hono/docker-compose.yml` that can be used to start up a Hono instance on your Docker host. Simply run the following from the `example/target/hono` directory

~~~sh
~/hono/example/target/hono$ docker-compose up -d
~~~

This will create and start up Docker containers for all components that together comprise a Hono instance, in particular the following containers are started:

{{< figure src="../Hono_instance.png" title="Hono instance containers">}}

* A *Dispatch Router* instance that downstream clients connect to in order to consume telemetry data.
* A *Hono Server* instance that protocol adapters connect to in order to forward data from devices.
* A *REST Adapter* instance that exposes Hono's Telemetry API as RESTful resources.
* An *MQTT Adapter* instance that exposes Hono's Telemetry API as an MQTT topic hierarchy.

## Starting a Consumer

The telemetry data produced by devices is usually consumed by downstream applications that use it to implement their corresponding business functionality.
In this example we will use a simple command line client that logs all telemetry messages to the console.
You can start the client from the `example` folder as follows:

~~~sh
~/hono/example$ mvn spring-boot:run -Drun.arguments=--spring.profiles.active=receiver,--hono.client.host=localhost
~~~

{{% warning %}}
Replace *localhost* with the name or IP address of the host that Docker is running on.
{{% /warning %}}

## Uploading Telemetry Data

Now that the Hono instance is up and running you can use Hono's protocol adapters to upload some telemetry data and watch it being forwarded to the downstream consumer.

The following sections will use the REST adapter to publish the telemetry data because it is more convenient to access using a standard HTTP client like `curl` or [`HTTPie`](https://httpie.org/) from the command line.

Please refer to the [REST Adapter]({{< relref "rest-adapter.md" >}}) documentation for additional information on how to access Hono's functionality via REST.

{{% warning %}}
The following sections assume that the REST adapter Docker container has been started on the local machine. However, if you started the REST adapter on another host or VM then make sure to replace *localhost* with the name or IP address of that (Docker) host.
{{% /warning %}}

### Registering a device using the REST adapter

The first thing to do is registering a device identity with Hono. Hono uses this information to authorize access to the device's telemetry data and functionality.

The following command registers a device with ID `4711`.

~~~sh
$ curl -X POST -i -d 'device_id=4711' http://localhost:8080/registration/DEFAULT_TENANT
~~~

or (using HTTPie):

~~~sh
$ http POST http://localhost:8080/registration/DEFAULT_TENANT device_id=4711
~~~

The result will contain a `Location` header containing the resource path created for the device. In this example it will look like this:

~~~
HTTP/1.1 201 Created
Location: /registration/DEFAULT_TENANT/4711
Content-Length: 0
~~~

You can then retrieve registration data for the device using

~~~sh
$ curl -i http://localhost:8080/registration/DEFAULT_TENANT/4711
~~~

or (using HTTPie):

~~~sh
$ http http://localhost:8080/registration/DEFAULT_TENANT/4711
~~~

which will result in something like this:

~~~json
HTTP/1.1 200 OK
Content-Type: application/json; charset=utf-8
Content-Length: 35

{
  "data" : { },
  "id" : "4711"
}
~~~

### Uploading Telemetry Data using the REST adapter

~~~sh
$ curl -X PUT -i -H 'Content-Type: application/json' --data-binary '{"temp": 5}' \
> http://localhost:8080/telemetry/DEFAULT_TENANT/4711
~~~

or (using HTTPie):

~~~sh
$ http PUT http://localhost:8080/registration/DEFAULT_TENANT/4711 temp:=5
~~~

## Stopping Hono

The Hono instance's containers can be stopped using the following command:

~~~sh
~/hono/example/target/hono$ docker-compose stop
~~~

## Restarting

In order to restart the containers later:

~~~sh
~/hono/example/target/hono$ docker-compose start
~~~
