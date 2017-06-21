+++
title = "Hono Messaging"
weight = 320
+++

The Hono Messaging component exposes service endpoints implementing the *south bound* part of Eclipse Hono&trade;'s [Telemetry]({{< relref "api/Telemetry-API.md" >}}) and [Event]({{< relref "api/Event-API.md" >}}) APIs.
The south bound API is used by devices and protocol adapters to upload telemetry data and events to be forwarded
to downstream consumers.
<!--more-->

## Configuration Variables

The component is implemented as a Spring Boot application. It can be run either directly from the command line or by means of starting the corresponding Docker image created from it.

The component can be configured by means of environment variables or corresponding command line options.

### Authentication Service Connection Configuration

The Hono Messaging component requires a connection to an implementation of Hono's Authentication API in order to authenticate and authorize client requests. 
The following table provides an overview of the configuration variables and corresponding command line options for configuring the connection to the Authentication service.

| Environment Variable<br>Command Line Option | Mandatory | Default | Description                                                             |
| :------------------------------------------ | :-------: | :------ | :-----------------------------------------------------------------------|
| `HONO_AUTH_HOST`<br>`--hono.auth.host` | yes | `localhost` | The IP address or name of the Authentication service host. NB: This needs to be set to an address that can be resolved within the network the service runs on. When running as a Docker container, use Docker's `--network` command line option to attach the Hono Messaging container to the Docker network that the Authentication service container is running on. |
| `HONO_AUTH_PORT`<br>`--hono.auth.port` | yes | `5671` | The port that the Authentication service is listening on for connections. |
| `HONO_AUTH_TRUST_STORE_PASSWORD`<br>`--hono.auth.trustStorePassword` | no | - | The password required to read the contents of the trust store. |
| `HONO_AUTH_TRUST_STORE_PATH`<br>`--hono.auth.trustStorePath` | no  | - | The absolute path to the Java key store containing the CA certificates the service uses for authenticating the Authentication service. This property **must** be set if the Authentication service has been configured to use TLS. The key store format can be either `JKS`, `PKCS12` or `PEM` indicated by a `.jks`, `.p12` or `.pem` file suffix. |
| `HONO_AUTH_VALIDATION_CERT_PATH`<br>`--hono.auth.validation.certPath` | no  | - | The absolute path to the PEM file containing the public key that the service should use for validating tokens issued by the Authentication service. Alternatively, a symmetric key can be used for validating tokens by setting the `HONO_AUTH_VALIDATION_SHARED_SECRET` variable. If none of these variables is set, the service falls back to the key indicated by the `HONO_AUTH_CERT_PATH` variable. If that variable is also not set, startup of the service fails. |
| `HONO_AUTH_VALIDATION_SHARED_SECRET`<br>`--hono.auth.validation.sharedSecret` | no  | - | A string to derive a symmetric key from which is used for validating tokens issued by the Authentication service. The key is derived from the string by using the bytes of the String's UTF8 encoding. When setting the validation key using this variable, the Authentication service **must** be configured with the same key. Alternatively, an asymmetric key pair can be used for validating (and signing) by setting the `HONO_AUTH_SIGNING_CERT_PATH` variable. If none of these variables is set, startup of the service fails. |

### AMQP 1.0 Messaging Network Connection Configuration

The Hono Messaging component forwards telemetry data and events produced by devices to an *AMQP 1.0 Messaging Network* for delivery to downstream consumers.
The following table provides an overview of the configuration variables and corresponding command line options for configuring the connection to the *AMQP 1.0 Messaging Network*.

| Environment Variable<br>Command Line Option | Mandatory | Default | Description                                                             |
| :------------------------------------------ | :-------: | :------ | :-----------------------------------------------------------------------|
| `HONO_DOWNSTREAM_HOST`<br>`--hono.downstream.host` | yes | `localhost` | The IP address or name of the downstream *AMQP 1.0 Messaging Network* host. NB: This needs to be set to an address that can be resolved within the network the service runs on. When running as a Docker container, use Docker's `--network` command line option to attach the Hono Messaging container to the Docker network that the *AMQP 1.0 Messaging Network* containers are running on. |
| `HONO_DOWNSTREAM_PASSWORD`<br>`--hono.downstream.password` | no | - | The password to use for authenticating to the *AMQP 1.0 Messaging Network*. This property (and the corresponding *username*) needs to be set only if the Messaging Network is configured to use `SASL PLAIN` for authenticating the Hono Messaging component. |
| `HONO_DOWNSTREAM_PORT`<br>`--hono.downstream.port` | yes | `5671` | The port that the *AMQP 1.0 Messaging Network* is listening on for connections from the Hono Messaging component.<br>**NB** When using the Dispatch Router image with the example configuration then this property needs to be set to `5673`. This is because in the example configuration the Dispatch Router's *internal* listener used for accepting connections from the Hono Messaging component is configured to attach to port 5673. |
| `HONO_DOWNSTREAM_TRUST_STORE_PASSWORD`<br>`--hono.downstream.trustStorePassword` | no | - | The password required to read the contents of the trust store. |
| `HONO_DOWNSTREAM_TRUST_STORE_PATH`<br>`--hono.downstream.trustStorePath` | no  | - | The absolute path to the Java key store containing the CA certificates the Hono Messaging component uses for authenticating the downstream AMQP 1.0 Messaging Network. This property **must** be set if the Messaging Network has been configured to support TLS. The key store format can be either `JKS`, `PKCS12` or `PEM` indicated by a `.jks`, `.p12` or `.pem` file suffix. |
| `HONO_DOWNSTREAM_USERNAME`<br>`--hono.downstream.username` | no | - | The username to use for authenticating to the downstream *AMQP 1.0 Messaging Network*. This property (and the corresponding *password*) needs to be set only if the Messaging Network is configured to use `SASL PLAIN` for authenticating the Hono Messaging component. |

### Service Configuration

The following table provides an overview of the configuration variables and corresponding command line options for configuring the Hono Messaging component.

| Environment Variable<br>Command Line Option | Mandatory | Default | Description                                                             |
| :------------------------------------------ | :-------: | :------ | :-----------------------------------------------------------------------|
| `HONO_MESSAGING_BIND_ADDRESS`<br>`--hono.messaging.bindAddress` | no | `127.0.0.1` | The IP address of the network interface that the secure port should be bound to.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_MESSAGING_CERT_PATH`<br>`--hono.messaging.certPath` | no | - | The absolute path to the PEM file containing the certificate that the service should use for authenticating to clients. This option must be used in conjunction with `HONO_MESSAGING_KEY_PATH`.<br>Alternatively, the `HONO_MESSAGING_KEY_STORE_PATH` option can be used to configure a key store containing both the key as well as the certificate. |
| `HONO_MESSAGING_INSECURE_PORT`<br>`--hono.messaging.insecurePort` | no | - | The insecure port the service should listen on.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_MESSAGING_INSECURE_PORT_BIND_ADDRESS`<br>`--hono.messaging.insecurePortBindAddress` | no | `127.0.0.1` | The IP address of the network interface that the insecure port should be bound to.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_MESSAGING_INSECURE_PORT_ENABLED`<br>`--hono.messaging.insecurePortEnabled` | no | `false` | If set to `true` the service will open an insecure port (not secured by TLS) using either the port number set via `HONO_MESSAGING_INSECURE_PORT` or the default AMQP port number (`5672`) if not set explicitly.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_MESSAGING_KEY_PATH`<br>`--hono.messaging.keyPath` | no | - | The absolute path to the (PKCS8) PEM file containing the private key that the service should use for authenticating to clients. This option must be used in conjunction with `HONO_MESSAGING_CERT_PATH`. Alternatively, the `HONO_MESSAGING_KEY_STORE_PATH` option can be used to configure a key store containing both the key as well as the certificate. |
| `HONO_MESSAGING_KEY_STORE_PASSWORD`<br>`--hono.messaging.keyStorePassword` | no | - | The password required to read the contents of the key store. |
| `HONO_MESSAGING_KEY_STORE_PATH`<br>`--hono.messaging.keyStorePath` | no | - | The absolute path to the Java key store containing the private key and certificate that the service should use for authenticating to clients. Either this option or the `HONO_MESSAGING_KEY_PATH` and `HONO_MESSAGING_CERT_PATH` options need to be set in order to enable TLS secured connections with clients. The key store format can be either `JKS` or `PKCS12` indicated by a `.jks` or `.p12` file suffix respectively. |
| `HONO_MESSAGING_MAX_INSTANCES`<br>`--hono.messaging.maxInstances` | no | *#CPU cores* | The number of verticle instances to deploy. If not set, one verticle per processor core is deployed. |
| `HONO_MESSAGING_PORT`<br>`--hono.messaging.port` | no | `5671` | The secure port that the service should listen on.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_MESSAGING_TRUST_STORE_PASSWORD`<br>`--hono.messaging.trustStorePassword` | no | - | The password required to read the contents of the trust store. |
| `HONO_MESSAGING_TRUST_STORE_PATH`<br>`--hono.messaging.trustStorePath` | no  | - | The absolute path to the Java key store containing the CA certificates the service uses for authenticating clients. The key store format can be either `JKS`, `PKCS12` or `PEM` indicated by a `.jks`, `.p12` or `.pem` file suffix respectively. |
| `HONO_MESSAGING_VALIDATION_CERT_PATH`<br>`--hono.messaging.validation.certPath` | yes | - | The path to a PEM file containing the *Device Registration* service's certificate. The public key contained in the certificate is used to validate RSA based registration assertion tokens issued by the *Device Registration* service. Either this variable or `HONO_MESSAGING_VALIDATION_SHARED_SECRET` must be set in order for the Hono Messaging component being able to process telemetry data and events received from devices. |
| `HONO_MESSAGING_VALIDATION_SHARED_SECRET`<br>`--hono.messaging.validation.sharedSecret` | yes | - | The secret to use for validating tokens asserting the registration status of devices using HmacSHA256. The secret's UTF8 encoding must consist of at least 32 bytes. Either this variable or `HONO_MESSAGING_VALIDATION_CERT_PATH` must be set in order for the Hono Messaging component being able to process telemetry data and events received from devices. |
| `HONO_METRIC_REPORTER_GRAPHITE_ACTIVE`<br>`--hono.metric.reporter.graphite.active` | no  | `false` | Activates the metrics reporter to Graphite (or a graphite compatible system - we use InfluxDB in the `example`). |
| `HONO_METRIC_REPORTER_GRAPHITE_HOST`<br>`--hono.metric.reporter.graphite.host` | no  | `localhost` | Sets the host, to which the metrics will be reported. |
| `HONO_METRIC_REPORTER_GRAPHITE_PORT`<br>`--hono.metric.reporter.graphite.host` | no  | `2003` | Sets the port - 2003 ist standard for Graphite. |
| `HONO_METRIC_REPORTER_GRAPHITE_PERIOD`<br>`--hono.metric.reporter.graphite.period` | no  | `5000` | Sets the time interval for reporting. |
| `HONO_METRIC_REPORTER_GRAPHITE_PREFIX`<br>`--hono.metric.reporter.graphite.prefix` | no  | - | Prefix all metric names with the given string. |
| `HONO_METRIC_REPORTER_CONSOLE_ACTIVE`<br>`--hono.metric.reporter.console.active` | no  | `false` | Activates the metrics reporter to the console/log. |
| `HONO_METRIC_REPORTER_CONSOLE_PERIOD`<br>`--hono.metric.reporter.console.period` | no  | `5000` | Sets the time interval for reporting. |
| `HONO_METRIC_JVM_MEMORY`<br>`--hono.metric.jvm.memory` | no  | `true` | Activates JVM memory metrics (from the Dropwizard JVM Instrumentation). The metric name is `hono.messaging.jvm.memory`. |
| `HONO_METRIC_JVM_THREAD`<br>`--hono.metric.jvm.thread` | no  | `true` | Activates JVM thread metrics (from the Dropwizard JVM Instrumentation). The metric name is `hono.messaging.jvm.thread`.|
| `HONO_METRIC_VERTX`<br>`--hono.metric.vertx` | no  | `true` | Activates the Vert.x metrics (from the Vert.x metrics project). The metric name is `hono.messaging.vertx`. |

The variables only need to be set if the default value does not match your environment.

## Port Configuration

The Hono Messaging component can be configured to listen for connections on

* a secure port only (default) or
* an insecure port only or
* both a secure and an insecure port (dual port configuration)

The server will fail to start if none of the ports is configured properly.

### Secure Port Only

The service needs to be configured with a private key and certificate in order to open a TLS secured port.

There are two alternative ways for doing so:

1. either setting the `HONO_MESSAGING_KEY_STORE_PATH` and the `HONO_MESSAGING_KEY_STORE_PASSWORD` variables in order to load the key & certificate from a password protected key store, or
1. setting the `HONO_MESSAGING_KEY_PATH` and `HONO_MESSAGING_CERT_PATH` variables in order to load the key and certificate from two separate PEM files in PKCS8 format.

When starting up, the service will bind a TLS secured socket to the default secure AMQP port 5671. The port number can also be set explicitly using the `HONO_MESSAGING_PORT` variable.

The `HONO_MESSAGING_BIND_ADDRESS` variable can be used to specify the network interface that the port should be exposed on. By default the port is bound to the *loopback device* only, i.e. the port will only be accessible from the local host. Setting this variable to `0.0.0.0` will let the port being bound to **all** network interfaces (be careful not to expose the port unintentionally to the outside world).

### Insecure Port Only

The secure port will mostly be required for production scenarios. However, it might be desirable to expose a non-TLS secured port instead, e.g. for testing purposes. In any case, the non-secure port needs to be explicitly enabled either by

- explicitly setting `HONO_MESSAGING_INSECURE_PORT` to a valid port number, or by
- implicitly configuring the default AMQP port (5672) by simply setting `HONO_MESSAGING_INSECURE_PORT_ENABLED` to `true`.

The service issues a warning on the console if `HONO_MESSAGING_INSECURE_PORT` is set to the default secure AMQP port (5671).

The `HONO_MESSAGING_INSECURE_PORT_BIND_ADDRESS` variable can be used to specify the network interface that the port should be exposed on. By default the port is bound to the *loopback device* only, i.e. the port will only be accessible from the local host. This variable might be used to e.g. expose the non-TLS secured port on a local interface only, thus providing easy access from within the local network, while still requiring encrypted communication when accessed from the outside over public network infrastructure.

Setting this variable to `0.0.0.0` will let the port being bound to **all** network interfaces (be careful not to expose the port unintentionally to the outside world).

### Dual Port
 
In test setups and some production scenarios the Hono Messaging component may be configured to open a secure **and** an insecure port at the same time.
 
This is achieved by configuring both ports correctly (see above). The service will fail to start if both ports are configured to use the same port number.

Since the secure port may need different visibility in the network setup compared to the secure port, it has it's own binding address `HONO_MESSAGING_INSECURE_PORT_BIND_ADDRESS`. 
This can be used to narrow the visibility of the insecure port to a local network e.g., while the secure port may be visible worldwide. 

### Ephemeral Ports

The service may be configured to open both a secure and a non-secure port at the same time simply by configuring both ports as described above. For this to work, both ports must be configured to use different port numbers, otherwise startup will fail.

## Run as a Docker Container

When running the Hono Messaging component as a Docker container, the preferred way of configuration is to pass environment variables to the container during startup using Docker's `-e` or `--env` command line option.

The following command starts the container using the configuration files included in the image under path `/etc/hono`.

~~~sh
$ docker run -d --name hono-messaging --network hono-net \
> -e 'HONO_AUTH_HOST=auth-server.hono' \
> -e 'HONO_AUTH_TRUST_STORE_PATH=/etc/hono/certs/trusted-certs.pem' \
> -e 'HONO_AUTH_VALIDATION_CERT_PATH=/etc/hono/certs/auth-server-cert.pem' \
> -e 'HONO_DOWNSTREAM_HOST=qdrouter.hono' \
> -e 'HONO_DOWNSTREAM_PORT=5673' \
> -e 'HONO_DOWNSTREAM_KEY_PATH=/etc/hono/certs/hono-key.pem' \
> -e 'HONO_DOWNSTREAM_CERT_PATH=/etc/hono/certs/hono-cert.pem' \
> -e 'HONO_DOWNSTREAM_TRUST_STORE_PATH=/etc/hono/certs/trusted-certs.pem' \
> -e 'HONO_MESSAGING_VALIDATION_SHARED_SECRET=asharedsecretforvalidatingassertions' \
> -e 'HONO_MESSAGING_KEY_PATH=/etc/hono/certs/hono-key.pem' \
> -e 'HONO_MESSAGING_CERT_PATH=/etc/hono/certs/hono-cert.pem' \
> -e 'HONO_MESSAGING_INSECURE_PORT_ENABLED=true' \
> -e 'HONO_MESSAGING_INSECURE_PORT_BIND_ADDRESS=0.0.0.0' \
> -p5672:5672 eclipsehono/hono-server:latest
~~~

{{% note %}}
The *--network* command line switch is used to specify the *user defined* Docker network that the service should attach to. This is important so that other components can use Docker's DNS service to look up the (virtual) IP address of the service when they want to connect to it. For the same reason it is important that the service container is attached to the same network that the Dispatch Router is attached to so that the service can use the Dispatch Router's host name to connect to it via the Docker network.
Please refer to the [Docker Networking Guide](https://docs.docker.com/engine/userguide/networking/#/user-defined-networks) for details regarding how to create a *user defined* network in Docker. When using a *Docker Compose* file to start up a complete Hono stack as a whole, the compose file will either explicitly define one or more networks that the containers attach to or the *default* network is used which is created automatically by Docker Compose for an application stack.

In cases where the Hono Messaging container requires a lot of configuration via environment variables (provided by means of *-e* switches), it is more convenient to put all environment variable definitions into a file and refer to it using Docker's *--env-file* command line switch when starting the container. This way the command line to start the container is much shorter and can be copied and edited more easily.
{{% /note %}}

## Run using Docker Compose

In most cases it is much easier to start all of Hono's components in one shot using Docker Compose.
See the `example` module for details. The `example` module also contains an example service definition file that
you can use as a starting point for your own configuration.

## Run the Spring Boot Application

Sometimes it is helpful to run the Hono Messaging component from its jar file, e.g. in order to attach a debugger more easily or to take advantage of code replacement.
In order to do so, the service can be started using the `spring-boot:run` maven goal from the `services/messaging` folder.
The corresponding command to start up the service with the configuration used in the Docker example above looks like this:

~~~sh
~/hono/services/messaging$ mvn spring-boot:run -Drun.arguments= \
> --hono.auth.host=auth-server.hono,\
> --hono.auth.trustStorePath=target/certs/trusted-certs.pem,\
> --hono.auth.validation.certPath=target/certs/auth-server-cert.pem,\
> --hono.auth.hostnameVerificationRequired=false,\
> --hono.downstream.host=qdrouter.hono,\
> --hono.downstream.port=5673,\
> --hono.downstream.hostnameVerificationRequired=false,\
> --hono.downstream.keyPath=target/certs/hono-key.pem,\
> --hono.downstream.certPath=target/certs/hono-cert.pem,\
> --hono.downstream.trustStorePath=target/certs/trusted-certs.pem,\
> --hono.messaging.validation.sharedSecret=asharedsecretforvalidatingassertions,\
> --hono.messaging.keyPath=target/certs/hono-key.pem,\
> --hono.messaging.certPath=target/certs/hono-cert.pem,\
> --hono.messaging.trustStorePath=target/certs/trusted-certs.pem,\
> --hono.messaging.insecurePortEnabled=true,\
> --hono.messaging.insecurePortBindAddress=0.0.0.0
~~~

{{% note %}}
In the example above the *hono.downstream.host=qdrouter.hono* command line option indicates that the Dispatch Router is running on a host with name *qdrouter.hono*. However, if the Dispatch Router has been started as a Docker container then the *qdrouter.hono* host name will most likely only be resolvable on the network that Docker has created for running the container on, i.e. when you run the Hono Messaging component from the Spring Boot application and want it to connect to a Dispatch Router run as a Docker container then you need to set the value of the *hono.downstream.host* option to the IP address (or name) of the Docker host running the Dispatch Router container. The same is true analogously for the *auth-server.hono* address.
The *hono.downstream.keyPath* option is required because the Dispatch Router requires the Hono Messaging component to authenticate by means of a client certificate during connection establishment.
The *hono.downstream.hostnameVerificationRequired* parameter is necessary to prevent the Hono Messaging component from validating the Dispatch Router's host name by means of comparing it to the *subjectAltNames* of the Dispatch Routers's certificate.
You may want to make logging of the Hono Messaging component a little more verbose by enabling the *dev* Spring profile.
To do so, append *-Drun.profiles=dev* to the command line.
{{% /note %}}
