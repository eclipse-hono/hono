+++
title = "Dispatch Router"
weight = 310
+++

The Dispatch Router component exposes the *north bound* part of Eclipse Hono&trade;'s Telemetry, Event and Registration endpoints.
The north bound API is used by applications to consume telemetry data and events and register device identities.
<!--more-->

## Configuration

The Dispatch Router is part of the [Apache Qpid project](https://qpid.apache.org). Hono uses Dispatch Router by means of a [Docker image](https://hub.docker.com/r/gordons/qpid-dispatch/) created from the Qpid project source code. 

The Dispatch Router can be configured by means of configuration files. Hono contains a default configuration in the `config/qpid` folder. Please refer to the [Dispatch Router documentation](https://qpid.apache.org/components/dispatch-router/index.html) for details regarding the configuration file format and options.

## Run as a Docker Container

The Dispatch Router can be run as a Docker container from the command line. The following commands first start the volume container containing the [Default Configuration]({{< ref "default-config.md" >}}) and then start the Dispatch Router container mounting the configuration volume in order to be able to read the configuration files from it.

~~~sh
$ docker run -d --name hono-config eclipsehono/hono-default-config:latest
$ docker run -d --name qdrouter --network hono-net -p 15671:5671 -p 15672:5672 -p 15673:5673 \
> --volumes-from=hono-config gordons/qpid-dispatch:0.7.0 /usr/sbin/qdrouterd -c /etc/hono/qdrouter/qdrouterd.json
~~~

{{% note %}}
There are two things noteworthy about the above command to start the Dispatch Router:

1. The *--network* command line switch is used to specify the *user defined* Docker network that the Dispatch Router should attach to. This is important so that other components can use Docker's DNS service to look up the (virtual) IP address of the Dispatch Router when they want to connect to it. Please refer to the [Docker Networking Guide](https://docs.docker.com/engine/userguide/networking/#/user-defined-networks) for details regarding how to create a *user defined* network in Docker. When using a *Docker Compose* file to start up a complete Hono stack as a whole, the compose file will either explicitly define one or more networks that the containers attach to or the *default* network is used which is created automatically by Docker Compose for an application stack.
2. It is important to specify the configuration file on the Docker command line when starting the container. Otherwise, the Dispatch Router uses the default configuration located at */etc/qpid-dispatch/qdrouterd.conf* which provides unrestricted access.
{{% /note %}}

## Run using Docker Compose

In most cases it is much easier to start all of Hono's components in one shot using Docker Compose.
See the `example` module for details. The `example` module also contains an example service definition file that
you can use as a starting point for your own configuration.

