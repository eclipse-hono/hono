+++
title = "Hono Server"
weight = 320
+++

The Hono server component exposes the *south bound* part of Eclipse Hono&trade;'s Telemetry, Event and Registration endpoints.
The south bound API is used by devices and protocol adapters to upload telemetry data and events to be forwarded
to downstream consumers.
<!--more-->

## Configuration

The Hono server is implemented as a Spring Boot application. It can be run either directly from the command line or by means of starting the corresponding Docker image created from it.

The server can be configured by means of environment variables or corresponding command line options.
The following table provides an overview of the configuration variables and corresponding command line options that the server supports:

| Environment Variable<br>Command Line Option | Mandatory | Default | Description                                                             |
| :------------------------------------------ | :-------: | :------ | :-----------------------------------------------------------------------|
| `HONO_AUTHORIZATION_PERMISSIONS_PATH`<br>`--hono.authorization.permissionsPath` | no | `classpath:/permissions.json` | The Spring resource URI of the JSON file defining the permissions to Hono's endpoint resources. The default file bundled with Hono defines permissions for the *DEAFAULT_TENANT* only and should only be used for evaluation purposes. |
| `HONO_DOWNSTREAM_HOST`<br>`--hono.downstream.host` | yes | `localhost` | The IP address or name of the downstream *Dispatch Router* host. NB: This needs to be set to an address that can be resolved within the network the adapter runs on. When running as a Docker container, use Docker's `--network` command line option to attach the Hono server container to the Docker network that the *Dispatch Router* container is running on.
| `HONO_DOWNSTREAM_PASSWORD`<br>`--hono.downstream.password` | no | - | The password to use for authenticating to the Hono server. This property (and the corresponding username) needs to be set only if the Dispatch Router component is configured to use `SASL PLAIN` instead of `SASL EXTERNAL` for authenticating the Hono server. |
| `HONO_DOWNSTREAM_PORT`<br>`--hono.downstream.port` | yes | `5672` | The port that the Dispatch Router is listening on for connection from the Hono server.<br>**NB** When using the Dispatch Router image with the example configuration then this property needs to be set to `5673`. This is because in the example configuration the Dispatch Router's *internal* listener used for accepting connections from the Hono server is configured to attach to port 5673. |
| `HONO_DOWNSTREAM_TRUST_STORE_PASSWORD`<br>`--hono.downstream.trustStorePassword` | no | - | The password required to read the contents of the trust store. |
| `HONO_DOWNSTREAM_TRUST_STORE_PATH`<br>`--hono.downstream.trustStorePath` | no  | - | The absolute path to the Java key store containing the CA certificates the Hono server uses for authenticating the Dispatch Router. This property **must** be set if the Dispatch Router has been configured to support TLS. The key store format can be either `JKS`, `PKCS12` or `PEM` indicated by a `.jks`, `.p12` or `.pem` file suffix. |
| `HONO_DOWNSTREAM_USERNAME`<br>`--hono.downstream.username` | no | - | The username to use for authenticating to the Dispatch Router. This property (and the corresponding password) needs to be set only if the Dispatch Router component is configured to use `SASL PLAIN` instead of `SASL EXTERNAL` for authenticating the Hono server. |
| `HONO_REGISTRATION_FILENAME`<br>`--hono.registration.filename` | no | `/home/hono/registration/device-identities.json` | The path to the file where Hono's `FileBasedRegistrationService` stores identities of registered devices. Hono tries to read device identities from this file during start-up and writes out all identities to this file periodically if property `HONO_REGISTRATION_SAVE_TO_FILE` is set to `true`. The `eclipsehono/hono-server` Docker image creates a volume under `/home/hono/registration` so that registration information survives container restarts and/or image updates. If you are running the Hono server from the command line you will probably want to set this variable to a path using an existing folder since Hono will not try to create the path. |
| `HONO_REGISTRATION_MAX_DEVICES_PER_TENANT`<br>`--hono.registration.maxDevicesPerTenant` | no | `100` | The number of devices that can be registered for each tenant. It is an error to set this property to a value <= 0. |
| `HONO_REGISTRATION_MODIFICATION_ENABLED`<br>`--hono.registration.modificationEnabled` | no | `true` | When set to `false` the device information registered cannot be updated nor removed from the registry. |
| `HONO_REGISTRATION_SAVE_TO_FILE`<br>`--hono.registration.saveToFile` | no | `false` | When set to `true` Hono's `FileBasedRegistrationService` will periodically write out the registered device information to the file specified by the `HONO_REGISTRATION_FILENAME` property. |
| `HONO_SERVER_BIND_ADDRESS`<br>`--hono.server.bindAddress` | no | `127.0.0.1` | The IP address of the network interface that the secure port should be bound to.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_SERVER_CERT_PATH`<br>`--hono.server.certPath` | no | - | The absolute path to the PEM file containing the certificate that the server should use for authenticating to clients. This option must be used in conjunction with `HONO_SERVER_KEY_PATH`.<br>Alternatively, the `HONO_SERVER_KEY_STORE_PATH` option can be used to configure a key store containing both the key as well as the certificate. |
| `HONO_SERVER_INSECURE_PORT`<br>`--hono.server.insecurePort` | no | - | The insecure port the server should listen on.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_SERVER_INSECURE_PORT_BIND_ADDRESS`<br>`--hono.server.insecurePortBindAddress` | no | `127.0.0.1` | The IP address of the network interface that the insecure port should be bound to.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_SERVER_INSECURE_PORT_ENABLED`<br>`--hono.server.insecurePortEnabled` | no | `false` | If set to `true` the server will open an insecure port (not secured by TLS) using either the port number set via `HONO_SERVER_INSECURE_PORT` or the default AMQP port number (`5672`) if not set explicitly.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_SERVER_KEY_PATH`<br>`--hono.server.keyPath` | no | - | The absolute path to the (PKCS8) PEM file containing the private key that the server should use for authenticating to clients. This option must be used in conjunction with `HONO_SERVER_CERT_PATH`. Alternatively, the `HONO_SERVER_KEY_STORE_PATH` option can be used to configure a key store containing both the key as well as the certificate. |
| `HONO_SERVER_KEY_STORE_PASSWORD`<br>`--hono.server.keyStorePassword` | no | - | The password required to read the contents of the key store. |
| `HONO_SERVER_KEY_STORE_PATH`<br>`--hono.server.keyStorePath` | no | - | The absolute path to the Java key store containing the private key and certificate that the server should use for authenticating to clients. Either this option or the `HONO_SERVER_KEY_PATH` and `HONO_SERVER_CERT_PATH` options need to be set in order to enable TLS secured connections with clients. The key store format can be either `JKS` or `PKCS12` indicated by a `.jks` or `.p12` file suffix respectively. |
| `HONO_SERVER_MAX_INSTANCES`<br>`--hono.server.maxInstances` | no | *#CPU cores* | The number of verticle instances to deploy. If not set, one verticle per processor core is deployed. |
| `HONO_SERVER_PORT`<br>`--hono.server.port` | no | `5671` | The secure port that the server should listen on.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_SERVER_REGISTRATION_ASSERTION_CERT_PATH`<br>`--hono.server.registrationAssertion.certPath` | yes | - | The path to a PEM file containing the *Registration Service*'s certificate. The public key contained in the certificate is used to validate RSA based registration assertion tokens issued by the *Registration Service*. Either this variable or `HONO_SERVER_REGISTRATION_ASSERTION_SIGNING_SECRET` must be set in order for Hono being able to process telemetry data and events received from devices. |
| `HONO_SERVER_REGISTRATION_ASSERTION_KEY_PATH`<br>`--hono.server.registrationAssertion.keyPath` | yes | - | The path to a PKCS8 PEM file containing the RSA private key to use for signing tokens asserting the registration status of devices. Either this variable or `HONO_SERVER_REGISTRATION_ASSERTION_SHARED_SECRET` must be set in order for the file based default *Device Registration* service to start up. | 
| `HONO_SERVER_REGISTRATION_ASSERTION_SHARED_SECRET`<br>`--hono.server.registrationAssertion.sharedSecret` | yes | - | The secret to use for signing and validating tokens asserting the registration status of devices using HmacSHA256. The secret's UTF8 encoding must consist of at least 32 bytes. Either this variable or `HONO_SERVER_ASSERTION_REGISTRATION_CERT_PATH` must be set in order for Hono being able to process telemetry data and events received from devices. When using the file based *Device Registration* service then either this variable or `HONO_SERVER_REGISTRATION_ASSERTION_KEY_PATH` must be set in order for the registration service to start up. |
| `HONO_SERVER_REGISTRATION_ASSERTION_TOKEN_EXPIRATION`<br>`--hono.server.registrationAssertion.tokenExpiration` | no | `10` | The expiration period to use for the tokens asserting the registration status of devices. |
| `HONO_SERVER_TRUST_STORE_PASSWORD`<br>`--hono.server.trustStorePassword` | no | - | The password required to read the contents of the trust store. |
| `HONO_SERVER_TRUST_STORE_PATH`<br>`--hono.server.trustStorePath` | no  | - | The absolute path to the Java key store containing the CA certificates the Hono server uses for authenticating clients. The key store format can be either `JKS`, `PKCS12` or `PEM` indicated by a `.jks`, `.p12` or `.pem` file suffix respectively. |
| `HONO_METRIC_REPORTER_GRAPHITE_ACTIVE`<br>`--hono.metric.reporter.graphite.active` | no  | `false` | Activates the metrics reporter to Graphite (or a graphite compatible system - we use InfluxDB in the `example`). |
| `HONO_METRIC_REPORTER_GRAPHITE_HOST`<br>`--hono.metric.reporter.graphite.host` | no  | `localhost` | Sets the host, to which the metrics will be reported. |
| `HONO_METRIC_REPORTER_GRAPHITE_PORT`<br>`--hono.metric.reporter.graphite.host` | no  | `2003` | Sets the port - 2003 ist standard for Graphite. |
| `HONO_METRIC_REPORTER_GRAPHITE_PERIOD`<br>`--hono.metric.reporter.graphite.period` | no  | `5000` | Sets the time interval for reporting. |
| `HONO_METRIC_REPORTER_GRAPHITE_PREFIX`<br>`--hono.metric.reporter.graphite.prefix` | no  | - | Prefix all metric names with the given string. |
| `HONO_METRIC_REPORTER_CONSOLE_ACTIVE`<br>`--hono.metric.reporter.console.active` | no  | `false` | Activates the metrics reporter to the console/log. |
| `HONO_METRIC_REPORTER_CONSOLE_PERIOD`<br>`--hono.metric.reporter.console.period` | no  | `5000` | Sets the time interval for reporting. |
| `HONO_METRIC_JVM_MEMORY`<br>`--hono.metric.jvm.memory` | no  | `true` | Activates JVM memory metrics (from the Dropwizard JVM Instrumentation). The metric name is `hono.server.jvm.memory`. |
| `HONO_METRIC_JVM_THREAD`<br>`--hono.metric.jvm.thread` | no  | `true` | Activates JVM thread metrics (from the Dropwizard JVM Instrumentation). The metric name is `hono.server.jvm.thread`.|
| `HONO_METRIC_VERTX`<br>`--hono.metric.vertx` | no  | `true` | Activates the Vert.x metrics (from the Vert.x metrics project). The metric name is `hono.server.vertx`. |

The variables only need to be set if the default value does not match your environment.

## Port Configuration

The Hono server can be configured to listen for connections on

* a secure port only (default) or
* an insecure port only or
* both a secure and an insecure port (dual port configuration)

The server will fail to start if none of the ports is configured properly.

### Secure Port Only

The Hono server needs to be configured with a private key and certificate in order to open a TLS secured port.

There are two alternative ways for doing so:

1. either setting the `HONO_SERVER_KEY_STORE_PATH` and the `HONO_SERVER_KEY_STORE_PASSWORD` variables in order to load the key & certificate from a password protected key store, or
1. setting the `HONO_SERVER_KEY_PATH` and `HONO_SERVER_CERT_PATH` variables in order to load the key and certificate from two separate PEM files in PKCS8 format.

When starting up, the server will bind a TLS secured socket to the default secure AMQP port 5671. The port number can also be set explicitly using the `HONO_SERVER_PORT` variable.

The `HONO_SERVER_BIND_ADDRESS` variable can be used to specify the network interface that the port should be exposed on. By default the port is bound to the *loopback device* only, i.e. the port will only be accessible from the local host. Setting this variable to `0.0.0.0` will let the port being bound to **all** network interfaces (be careful not to expose the port unintentionally to the outside world).

### Insecure Port Only

The secure port will mostly be required for production scenarios. However, it might be desirable to expose a non-TLS secured port instead, e.g. for testing purposes. In any case, the non-secure port needs to be explicitly enabled either by

- explicitly setting `HONO_SERVER_INSECURE_PORT` to a valid port number, or by
- implicitly configuring the default AMQP port (5672) by simply setting `HONO_SERVER_INSECURE_PORT_ENABLED` to `true`.

The Hono server issues a warning on the console if `HONO_SERVER_INSECURE_PORT` is set to the default secure AMQP port (5671).

The `HONO_SERVER_INSECURE_PORT_BIND_ADDRESS` variable can be used to specify the network interface that the port should be exposed on. By default the port is bound to the *loopback device* only, i.e. the port will only be accessible from the local host. This variable might be used to e.g. expose the non-TLS secured port on a local interface only, thus providing easy access from within the local network, while still requiring encrypted communication when accessed from the outside over public network infrastructure.

Setting this variable to `0.0.0.0` will let the port being bound to **all** network interfaces (be careful not to expose the port unintentionally to the outside world).

### Dual Port
 
In test setups and some production scenarios Hono server may be configured to open one secure **and** one insecure port at the same time.
 
This is achieved by configuring both ports correctly (see above). 
If the same port number is configured for both ports it is considered as misconfiguration and Hono server fails to start.

Since the secure port may need different visibility in the network setup compared to the secure port, it has it's own binding address `HONO_SERVER_INSECURE_PORT_BIND_ADDRESS`. 
This can be used to narrow the visibility of the insecure port to a local network e.g., while the secure port may be visible worldwide. 

### Ephemeral Ports

The Hono server may be configured to open both a secure and a non-secure port at the same time simply by configuring both ports as described above. For this to work, both ports must be configured to use different port numbers, otherwise startup will fail.

## Run as a Docker Container

When running the Hono server as a Docker container, the preferred way of configuration is to pass environment variables to the container during startup using Docker's `-e` or `--env` command line option.

The following command starts the Hono server container using the configuration files included in the image under path `/etc/hono`.

~~~sh
$ docker run -d --name hono --network hono-net -e 'HONO_DOWNSTREAM_HOST=qdrouter' -e 'HONO_DOWNSTREAM_PORT=5673' \
> -e 'HONO_DOWNSTREAM_KEY_PATH=/etc/hono/certs/hono-key.pem' -e 'HONO_DOWNSTREAM_CERT_PATH=/etc/hono/certs/hono-cert.pem' \
> -e 'HONO_DOWNSTREAM_TRUST_STORE_PATH=/etc/hono/certs/trusted-certs.pem' -e 'HONO_AUTHORIZATION_PERMISSIONS_PATH=file:/etc/hono/permissions.json' \
> -e 'HONO_SERVER_REGISTRATION_ASSERTION_SHARED_SECRET=averylongsharedsecretforsigningassertions' \
> -e 'HONO_SERVER_KEY_PATH=/etc/hono/certs/hono-key.pem' -e 'HONO_SERVER_CERT_PATH=/etc/hono/certs/hono-cert.pem' \
> -e 'HONO_SERVER_INSECURE_PORT_ENABLED=true' -e 'HONO_SERVER_INSECURE_PORT_BIND_ADDRESS=0.0.0.0' \
> -p5672:5672 eclipsehono/hono-server:latest
~~~

{{% note %}}
The *--network* command line switch is used to specify the *user defined* Docker network that the Hono server should attach to. This is important so that other components can use Docker's DNS service to look up the (virtual) IP address of the Hono server when they want to connect to it. For the same reason it is important that the Hono server container is attached to the same network that the Dispatch Router is attached to so that the Hono server can use the Dispatch Router's host name to connect to it via the Docker network.
Please refer to the [Docker Networking Guide](https://docs.docker.com/engine/userguide/networking/#/user-defined-networks) for details regarding how to create a *user defined* network in Docker. When using a *Docker Compose* file to start up a complete Hono stack as a whole, the compose file will either explicitly define one or more networks that the containers attach to or the *default* network is used which is created automatically by Docker Compose for an application stack.

In cases where the Hono server container requires a lot of configuration via environment variables (provided by means of *-e* switches), it is more convenient to add all environment variable definitions to a separate *env file* and refer to it using Docker's *--env-file* command line switch when starting the container. This way the command line to start the container is much shorter and can be copied and edited more easily.
{{% /note %}}

## Run using Docker Compose

In most cases it is much easier to start all of Hono's components in one shot using Docker Compose.
See the `example` module for details. The `example` module also contains an example service definition file that
you can use as a starting point for your own configuration.

## Run the Spring Boot Application

Sometimes it is helpful to run the Hono server from its jar file, e.g. in order to attach a debugger more easily or to take advantage of code replacement.
In order to do so, the server can be started using the `spring-boot:run` maven goal from the `application` folder.
The corresponding command to start up the server with the configuration used in the Docker example above looks like this:

~~~sh
~/hono/application$ mvn spring-boot:run -Drun.arguments=--hono.downstream.host=qdrouter,\
> --hono.downstream.port=5673,--hono.downstream.hostnameVerificationRequired=false,\
> --hono.downstream.keyPath=../demo-certs/certs/hono-key.pem,--hono.downstream.certPath=../demo-certs/certs/hono-cert.pem,\
> --hono.downstream.trustStorePath=../demo-certs/certs/trusted-certs.pem,\
> --hono.server.registrationAssertion.sharedSecret=averylongsharedsecretforsigningassertions,\
> --hono.server.keyPath=../demo-certs/certs/hono-key.pem,--hono.server.certPath=../demo-certs/certs/hono-cert.pem,\
> --hono.server.trustStorePath=../demo-certs/certs/trusted-certs.pem,\
> --hono.server.insecurePortEnabled=true,--hono.server.insecurePortBindAddress=0.0.0.0
~~~

{{% note %}}
In the example above the *hono.downstream.host=qdrouter* command line option indicates that the Dispatch Router is running on a host with name *qdrouter*. However, if the Dispatch Router has been started as a Docker container then the *qdrouter* host name will most likely only be resolvable on the network that Docker has created for running the container on, i.e. when you run the Hono server from the Spring Boot application and want it to connect to a Dispatch Router run as a Docker container then you need to set the value of the *hono.downstream.host* option to the IP address (or name) of the Docker host running the Dispatch Router container.
The *hono.downstream.keyStorePath* option is required because the Dispatch Router requires the Hono server to authenticate by means of a client certificate during connection establishment.
The *hono.downstream.hostnameVerificationRequired* parameter is necessary to prevent Hono from validating the Dispatch Router's host name by means of comparing it to the *common name* of the Dispatch Routers's certificate's subject.
You may want to make logging of the Hono server a little more verbose by enabling the *dev* Spring profile.
To do so, append *,--spring.profiles.active=dev* to the command line.
{{% /note %}}
