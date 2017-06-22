+++
title = "Dispatch Router"
weight = 310
+++

The Dispatch Router component exposes service endpoints implementing the *north bound* part of Eclipse Hono&trade;'s [Telemetry]({{< relref "api/Telemetry-API.md" >}}) and [Event]({{< relref "api/Event-API.md" >}}) APIs.
The north bound API is used by applications to consume telemetry data and events from connected devices.
<!--more-->

The Dispatch Router, together with the *Apache Artemis* message broker serves as the default *AMQP 1.0 Messaging Network* that is used in Hono's example deployment as described in the [Getting Started Guide]({{< relref "getting-started.md" >}}).

## Configuration

The Dispatch Router is part of the [Apache Qpid project](https://qpid.apache.org). Hono uses Dispatch Router by means of a [Docker image](https://hub.docker.com/r/gordons/qpid-dispatch/) created from the Qpid project source code.

The Dispatch Router can be configured by means of configuration files. Hono contains a default configuration in the `dispatchrouter/qpid` folder and in the `dispatchrouter/sasl` for enabling authentication through SASL. Please refer to the [Dispatch Router documentation](https://qpid.apache.org/components/dispatch-router/index.html) for details regarding the configuration file format and options.

The provided configuration files are the following.

| File                                     | Description                                                      |
| :--------------------------------------- | :--------------------------------------------------------------- |
| `/etc/hono/qdrouter/qdrouterd.json`  | The Dispatch Router configuration file. |
| `/etc/hono/qdrouter/qdrouter-sasl.conf` | The configuration file for the [Cyrus SASL Library](http://www.cyrusimap.org/sasl/getting_started.html) used by Dispatch Router for authenticating clients. This configuration file can be adapted to e.g. configure LDAP or a database for verifying credentials.
| `/etc/hono/qdrouter/qdrouterd.sasldb` | The Berkley DB file used by Cyrus SASL that contains the example users which are supported by the Dispatch Router.

The file paths are related to the Docker images where such configuration files are needed.

## Run as a Docker Container

The Dispatch Router can be run as a Docker container from the command line. The following command starts the Dispatch Router container using using the default configuration files included in the Dispatch Router image.

~~~sh
$ docker run -d --name qdrouter --network hono-net -p 15671:5671 -p 15672:5672 -p 15673:5673 \
> eclipsehono/dispatch-router:latest /usr/sbin/qdrouterd -c /etc/hono/qdrouter/qdrouterd.json
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
