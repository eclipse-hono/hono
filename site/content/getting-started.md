+++
title = "Getting started"
menu = "main"
weight = 100
+++

Eclipse Hono&trade; consists of a set of micro services provided as Docker images. You can either build the Docker images yourself from the source code or you can run Hono by means of the pre-built Docker images available from our [Docker Hub repositories](https://hub.docker.com/u/eclipsehono/).

This guide will walk you through building the images and example code from source, starting a Hono instance on your local computer and interacting with Hono via its REST adapter.

## Prerequisites

In order to build and run the images you need access to a [Docker](http://www.docker.com) daemon running either locally on your computer or another host you have access to. Please follow the instructions on the [Docker web site](http://www.docker.com) to install *Docker Engine* on your platform.

As noted above, Hono consists of multiple service components that together comprise a Hono instance. The remainder of this guide employs Docker's *Swarm Mode* for configuring, running and managing all Hono components as a whole.
In order to enable *Swarm Mode* on your *Docker Engine* run the following command

    $ docker swarm init

Please refer to the [Docker Swarm Mode documentation](https://docs.docker.com/engine/swarm/swarm-mode/) for details.

{{% warning %}}
You will need at least Docker version 1.13.0 in order to run the example in this guide. By the time of writing, the latest released version of Docker was 1.13.1.
{{% /warning %}}

### Compiling

If you do not already have a working Maven installation on your system, please follow the [installation instructions on the Maven home page](https://maven.apache.org/).

Then run the following from the project's root folder

    ~/hono$ mvn clean install -Ddocker.host=tcp://${host}:${port} -Pbuild-docker-image

with `${host}` and `${port}` reflecting the name/IP address and port of the host where Docker is running on. This will build all libraries, Docker images and example code. If you are running on Linux and Docker is installed locally or you have set the `DOCKER_HOST` environment variable, you can omit the `-Ddocker.host` property definition.

If you plan to build the Docker images more frequently, e.g. because you want to extend or improve the Hono code, then you should define the `docker.host` property in your Maven `settings.xml` file containing the very same value as you would use on the command line as indicated above. This way you can simply do a `mvn clean install` later on and the Docker images will be built automatically as well because the `build-docker-image` profile is activated automatically if the Maven property `docker.host` is set.

## Starting Hono

The easiest way to start the server components is by deploying them as a *stack*. As part of the build process, a *Docker Compose* file is generated under `example/target/hono/docker-compose.yml` which can be used to start up a Hono instance on your Docker host. Simply run the following from the `example/target/hono` directory

~~~sh
~/hono/example/target/hono$ docker stack deploy -c docker-compose.yml hono
~~~

This will create and start up Docker Swarm *services* for all components that together comprise a Hono instance, in particular the following services are started:

{{< figure src="../Hono_instance.svg" title="Hono instance containers">}}

* Hono Instance
  * A *Hono Server* instance that protocol adapters connect to in order to forward data from devices.
  * A *REST Adapter* instance that exposes Hono's Telemetry API as RESTful resources.
  * An *MQTT Adapter* instance that exposes Hono's Telemetry API as an MQTT topic hierarchy.
  * A *Device Registry* instance that manages device data and is used for token assertion.
  * An *Auth Server* instance that authenticates Hono components and delivers authorization tokens.  
* AMQP Network
  * A *Dispatch Router* instance that downstream clients connect to in order to consume telemetry data.
  * An *Artemis* instance that is the default persistence store for events.
* Monitoring Infrastructure
  * An *InfluxDB* instance to store metrics data from the Hono Server.
  * A *Grafana* instance to show a default dashboard with the Hono Server metrics.
 
## Starting a Consumer

The telemetry data produced by devices is usually consumed by downstream applications that use it to implement their corresponding business functionality.
In this example we will use a simple command line client that logs all telemetry messages to the console.
You can start the client from the `example` folder as follows:

~~~sh
~/hono/example$ mvn spring-boot:run -Drun.arguments=--hono.client.host=localhost,--hono.client.username=user1@HONO,--hono.client.password=pw
~~~

Event messages are very similar to telemetry ones, except that they use `AT LEAST ONCE` quality of service. You can receive and log event messages uploaded to Hono using the same client.

In order to do so, run the client from the `example` folder as follows:

~~~sh
mvn spring-boot:run -Drun.arguments=--hono.client.host=localhost,--hono.client.username=user1@HONO,--hono.client.password=pw -Drun.profiles=receiver,ssl,event
~~~

{{% warning %}}
Replace *localhost* with the name or IP address of the host that Docker is running on.
{{% /warning %}}

## Uploading Telemetry Data

Now that the Hono instance is up and running you can use Hono's protocol adapters to upload some telemetry data and watch it being forwarded to the downstream consumer.

The following sections will use the REST adapter to publish the telemetry data because it is very easy to access using a standard HTTP client like `curl` or [`HTTPie`](https://httpie.org/) from the command line.

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
  "data" : {
      "enabled": true
  },
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
$ http PUT http://localhost:8080/telemetry/DEFAULT_TENANT/4711 temp:=5
~~~

When you first invoke any of the two commands above after you have started up your Hono instance, you may get the following response:

~~~
HTTP/1.1 503 Service Unavailable
Content-Length: 47
Content-Type: text/plain

resource limit exceeded, please try again later
~~~

This is because the first request to publish data for a tenant (`DEFAULT_TENANT` in the example) is used as the trigger to establish a tenant specific link with the Hono server to forward the data over. However, the REST adapter may not receive credits from the Hono Server quickly enough for the request to be served successfully.
You can simply ignore this response and re-submit the command. You should then get a response like this:

~~~
HTTP/1.1 202 Accepted
Content-Length: 0
Hono-Reg-Assertion: eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI0NzExIiwidGVuIjoiREVGQVVMVF9URU5BTlQiLCJleHAiOjE0OTQ1OTg5Njl9.SefIa2UjNYiWwBfPOkizIlMPb3H2-hy7BHGjTgbX_I0

~~~

If you have started the consumer as described above, you should now see the telemetry message being logged to the console. You can publish more data simply by issuing additional requests.

If you haven't started a consumer you will continue to get `503 Resource Unavailable` responses because Hono does not accept any telemetry data from devices if there aren't any consumers connected that are interested in the data. Telemetry data is never persisted within Hono, thus it doesn't make any sense to accept and process telemetry data if there is no destination to deliver it to.

Please refer to the [REST Adapter documentation]({{< relref "rest-adapter.md" >}}) for additional information and examples for interacting with Hono via HTTP.

### Uploading Event Data using the REST adapter

In a similar way you can upload event data, using curl

~~~sh
$ curl -X PUT -i -H 'Content-Type: application/json' --data-binary '{"temp": 5}' \
> http://localhost:8080/event/DEFAULT_TENANT/4711
~~~

or (using HTTPie):

~~~sh
$ http PUT http://localhost:8080/event/DEFAULT_TENANT/4711 temp:=5
~~~

## Stopping Hono

The Hono instance's services can be stopped and removed using the following command:

~~~sh
~/hono/example/target/hono$ docker stack rm hono
~~~

Please refer to the [Docker Swarm documentation](https://docs.docker.com/engine/swarm/services/) for details regarding the management of services.

## Restarting

In order to start up the instance again:

~~~sh
~/hono/example/target/hono$ docker stack deploy -c docker-compose.yml hono
~~~

## View metrics

Open the [Grafana dashboard](http://localhost:3000/dashboard/db/hono?orgId=1) in a browser. Login is `admin/admin`.

{{% warning %}}
If you do not run Docker on localhost, replace *localhost* in the link with the correct name or IP address of the host that Docker is running on.
{{% /warning %}}
