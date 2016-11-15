+++
title = "Dispatch Router"
weight = 310
+++

The Dispatch Router component exposes the *north bound* part of Eclipse Hono&trade;'s Telemetry, Event and Registration endpoints.
The north bound API is used by applications to consume telemetry data and events and register device identities.
<!--more-->

## Configuration

The Dispatch Router is part of the [Apache Qpid project](https://qpid.apache.org). Hono uses Dispatch Router by means of a [Docker image](https://hub.docker.com/r/gordons/qpid-dispatch/) created from the Qpid project source code. 

The Dispatch Router can be configured by means of configuration files. Hono contains a default configuration in the `config/qpid` folder. Please refer to the [Dispatch Router documentation](https://qpid.apache.org/components/dispatch-router/index.html) for details regarding the configuration file format and options. These default configuration files are provided by means of two Docker volume images which can be *mounted* when starting up a Dispatch Router Docker container. 

## Run as a Docker Container

The Dispatch Router can be run as a Docker container from the command line. The following commands first start the volume containers containing the default configuration and then start the Dispatch Router container mounting these volumes in order to be able to read the configuration files from them.

~~~sh
$ docker run -d --name qdrouter-config eclipsehono/qpid-default-config:latest
$ docker run -d --name qdrouter-sasldb eclipsehono/qpid-sasldb:latest
$ docker run -d --name qdrouter -p 15671:5671 -p 15672:5672 -p 15673:5673 -h qdrouter \
> --volumes-from="qdrouter-config" --volumes-from="qdrouter-sasldb" gordons/qpid-dispatch:0.7.0
~~~

## Run using Docker Compose

In most cases it is much easier to start all of Hono's components in one shot using Docker Compose.
See the `example` module for details. The `example` module also contains an example service definition file that
you can use as a starting point for your own configuration.

