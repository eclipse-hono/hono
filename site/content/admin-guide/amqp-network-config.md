+++
title = "AMQP 1.0 Messaging Network Configuration"
weight = 345
+++

The *Dispatch Router*, together with the *Apache Artemis* message broker, serves as the default *AMQP 1.0 Messaging Network* that is used in Hono's example deployment as described in the [Getting Started Guide]({{< relref "getting-started.md" >}}).
<!--more-->

The Dispatch Router component exposes service endpoints implementing the *north bound* part of Eclipse Hono&trade;'s [Telemetry]({{< relref "api/Telemetry-API.md" >}}) and [Event]({{< relref "api/Event-API.md" >}}) APIs.
The north bound API is used by applications to consume telemetry data and events from connected devices.


## Dispatch Router Configuration

The Dispatch Router is part of the [Apache Qpid project](https://qpid.apache.org). Hono uses Dispatch Router by means of the [EnMasse project's Dispatch Router Docker image](https://hub.docker.com/r/enmasseproject/qdrouterd-base/) created from the Qpid project source code.

The Dispatch Router can be configured by means of configuration files. Hono contains a default configuration in the `dispatchrouter/qpid` folder and in the `dispatchrouter/sasl` for enabling authentication through SASL. Please refer to the [Dispatch Router documentation](https://qpid.apache.org/components/dispatch-router/index.html) for details regarding the configuration file format and options.

The provided configuration files are the following.

| File                                                    | Description                                                      |
| :------------------------------------------------------ | :--------------------------------------------------------------- |
| `dispatchrouter/qpid/qdrouterd-with-broker.json`  | A configuration file setting up SASL based authentication and an auto link for handling event messages using a message broker. |
| `dispatchrouter/sasl/qdrouter-sasl.conf`           | A configuration file for the [Cyrus SASL Library](http://www.cyrusimap.org/sasl/getting_started.html) used by Dispatch Router for authenticating clients. This configuration file can be adapted to e.g. configure LDAP or a database for verifying credentials.
| `dispatchrouter/sasl/qdrouterd.sasldb`             | A Berkley DB file used by Cyrus SASL which contains the example users that are supported by the Dispatch Router.

## Run Dispatch Router as a Docker Swarm Service

The Dispatch Router can be run as a Docker container from the command line. The following commands create and start the Dispatch Router as a Docker Swarm service using the default keys and configuration files contained in the `dispatchrouter` and `demo-certs` modules:

~~~sh
~/hono$ docker secret create qdrouter-key.pem demo-certs/certs/qdrouter-key.pem
~/hono$ docker secret create qdrouter-cert.pem demo-certs/certs/qdrouter-cert.pem
~/hono$ docker secret create trusted-certs.pem demo-certs/certs/trusted-certs.pem
~/hono$ docker secret create qdrouterd.json dispatchrouter/qpid/qdrouterd-with-broker.json
~/hono$ docker secret create qdrouter-sasl.conf dispatchrouter/sasl/qdrouter-sasl.conf
~/hono$ docker secret create qdrouterd.sasldb dispatchrouter/sasl/qdrouterd.sasldb
~/hono$ docker service create --detach --name hono-dispatch-router --network hono-net -p 15671:5671 -p 15672:5672 -p 15673:5673 \
>  --secret qdrouter-key.pem \
>  --secret qdrouter-cert.pem \
>  --secret trusted-certs.pem \
>  --secret qdrouterd.json \
>  --secret qdrouter-sasl.conf \
>  --secret qdrouterd.sasldb \
>  enmasseproject/qdrouterd-base:0.8.0-1 /sbin/qdrouterd -c /run/secrets/qdrouterd.json
~~~

{{% note %}}
There are several things noteworthy about the above command to start the Dispatch Router:

1. The *secrets* need to be created once only, i.e. they only need to be removed and re-created if they are changed.
1. The *--network* command line switch is used to specify the *user defined* Docker network that the Dispatch Router should attach to. This is important so that other components can use Docker's DNS service to look up the (virtual) IP address of the Dispatch Router when they want to connect to it. Please refer to the [Docker Networking Guide](https://docs.docker.com/engine/userguide/networking/#/user-defined-networks) for details regarding how to create a *user defined* network in Docker.
1. It is important to specify the configuration file on the Docker command line when starting the container. Otherwise, the Dispatch Router uses the default configuration located at */etc/qpid-dispatch/qdrouterd.conf* which provides unrestricted access.
{{% /note %}}

## Run Dispatch Router using the Docker Swarm Deployment Script

In most cases it is much easier to start all of Hono's components in one shot using the Docker Swarm deployment script provided in the `example/target/deploy/docker` folder.
