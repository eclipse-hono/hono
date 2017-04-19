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
The following table provides an overview of the configuration options the adapter supports:

| Environment Variable<br>Command Line Option | Mandatory | Default | Description |
| :------------------------------------------ | :-------: | :------ | :---------- |
| `HONO_AUTHORIZATION_PERMISSIONS_PATH`<br>`--hono.authorization.permissionsPath` | no | `classpath:/permissions.json` | The Spring resource URI of the JSON file defining the permissions to Hono's endpoint resources. The default file bundled with Hono defines permissions for the *DEAFAULT_TENANT* only and should only be used for evaluation purposes. |
| `HONO_DOWNSTREAM_HOST`<br>`--hono.downstream.host` | yes | `localhost` | The IP address or name of the downstream *Dispatch Router* host. NB: This needs to be set to an address that can be resolved within the network the adapter runs on. When running as a Docker container, use Docker's `--link` command line option to link the Hono server container to the host of the *Dispatch Router* container on the Docker network.
| `HONO_DOWNSTREAM_PASSWORD`<br>`--hono.downstream.password` | no | - | The password to use for authenticating to the Hono server. This property (and the corresponding username) needs to be set only if the Dispatch Router component is configured to use `SASL PLAIN` instead of `SASL EXTERNAL` for authenticating the Hono server. |
| `HONO_DOWNSTREAM_PORT`<br>`--hono.downstream.port` | yes | `5672` | The port that the Dispatch Router is listening on for connection from the Hono server.<br>**NB** When using the Dispatch Router image with the example configuration then this property needs to be set to `5673`. This is because in the example configuration the Dispatch Router's *internal* listener used for accepting connections from the Hono server is configured to attach to port 5673. |
| `HONO_DOWNSTREAM_TRUST_STORE_PASSWORD`<br>`--hono.downstream.trustStorePassword` | no | - | The password required to read the contents of the trust store. |
| `HONO_DOWNSTREAM_TRUST_STORE_PATH`<br>`--hono.downstream.trustStorePath` | no  | - | The absolute path to the Java key store containing the CA certificates the Hono server uses for authenticating the Dispatch Router. This property **must** be set if the Dispatch Router has been configured to support TLS. The key store format can be either `JKS`, `PKCS12` or `PEM` indicated by a `.jks`, `.p12` or `.pem` file suffix. |
| `HONO_DOWNSTREAM_USERNAME`<br>`--hono.downstream.username` | no | - | The username to use for authenticating to the Dispatch Router. This property (and the corresponding password) needs to be set only if the Dispatch Router component is configured to use `SASL PLAIN` instead of `SASL EXTERNAL` for authenticating the Hono server. |
| `HONO_REGISTRATION_FILENAME`<br>`--hono.registration.filename` | no | `/home/hono/registration/device-identities.json` | The path to the file where Hono's `FileBasedRegistrationService` stores identities of registered devices. Hono tries to read device identities from this file during start-up and writes out all identities to this file periodically if property `HONO_REGISTRATION_SAVE_TO_FILE` is set to `true`. The `eclipsehono/hono-server` Docker image creates a volume under `/home/hono/registration` so that registration information survives container restarts and/or image updates. If you are running the Hono server from the command line you will probably want to set this variable to a path using an existing folder since Hono will not try to create the path. |
| `HONO_REGISTRATION_MAX_DEVICES_PER_TENANT`<br>`--hono.registration.maxDevicesPerTenant` | no | `100` | The number of devices that can be registered for each tenant. It is an error to set this property to a value <= 0. |
| `HONO_REGISTRATION_MODIFICATION_ENABLED`<br>`--hono.registration.modificationEnabled` | no | `true` | When set to `false` the device information registered cannot be updated nor removed from the registry. |
| `HONO_REGISTRATION_SAVE_TO_FILE`<br>`--hono.registration.saveToFile` | no | `false` | When set to `true` Hono's `FileBasedRegistrationService` will periodically write out the registered device information to the file specified by the `HONO_REGISTRATION_FILENAME` property. |
| `HONO_SERVER_BIND_ADDRESS`<br>`--hono.server.bindAddress` | no | `127.0.0.1` | The IP address the Hono server port shall bind to. By default the server binds to the *loopback device* address, i.e. the server will **not** be accessible from other hosts. |
| `HONO_SERVER_CERT_PATH`<br>`--hono.server.certPath` | no | - | The absolute path to the PEM file containing the certificate the Hono server uses for authenticating to clients. Either this or the `HONO_SERVER_KEY_STORE_PATH` option **must** be set to enable TLS secured connections with clients. |
| `HONO_SERVER_INSECURE_PORT`<br>`--hono.server.insecurePort` | no | `5672` | The insecure port the Hono server shall listen on (if enabled). <br>In order to open an insecure port only, set this property to true but do neither set `HONO_SERVER_KEY_STORE_PATH` nor `HONO_SERVER_KEY_PATH` nor `HONO_SERVER_CERT_PATH`. This will prevent the secure port from being opened. <br> For dual port configurations Hono will not start if this property is set to the same value as `HONO_SERVER_PORT`.|
| `HONO_SERVER_INSECURE_PORT_BIND_ADDRESS`<br>`--hono.server.insecurePortBindAddress` | no | `127.0.0.1` | The IP address the insecure port of the Hono server shall bind to. By default the server binds to the *loopback device* address, i.e. the server will **not** be accessible from other hosts. |
| `HONO_SERVER_INSECURE_PORT_ENABLED`<br>`--hono.server.insecurePortEnabled` | no | `false` | Enables the Hono server to open an insecure port (not secured by TLS). <br>This allows for a dual port setup of the Hono server (secure and insecure port). |
| `HONO_SERVER_KEY_PATH`<br>`--hono.server.keyPath` | no | - | The absolute path to the PEM file containing the private key the Hono server uses for authenticating to clients. Either this or the `HONO_SERVER_KEY_STORE_PATH` option **must** be set to enable TLS secured connections with clients. |
| `HONO_SERVER_KEY_STORE_PASSWORD`<br>`--hono.server.keyStorePassword` | no | - | The password required to read the contents of the key store. |
| `HONO_SERVER_KEY_STORE_PATH`<br>`--hono.server.keyStorePath` | no | - | The absolute path to the Java key store containing the private key and certificate the Hono server uses for authenticating to clients. Either this or the `HONO_SERVER_KEY_PATH` and `HONO_SERVER_CERT_PATH` options **must** be set to enable TLS secured connections with clients. The key store format can be either `JKS` or `PKCS12` indicated by a `.jks` or `.p12` file suffix. |
| `HONO_SERVER_MAX_INSTANCES`<br>`--hono.server.maxInstances` | no | *#CPU cores* | The number of verticle instances to deploy. If not set, one verticle per processor core is deployed. |
| `HONO_SERVER_PORT`<br>`--hono.server.port` | no | `5671` | The secure port the server shall listen on. <br> In order for this to work either `HONO_SERVER_KEY_STORE_PATH` (and `HONO_SERVER_KEY_STORE_PASSWORD`) or `HONO_SERVER_KEY_PATH` and `HONO_SERVER_CERT_PATH` need to be set appropriately. Otherwise the Hono server will not open the configured port. |
| `HONO_SERVER_TRUST_STORE_PASSWORD`<br>`--hono.server.trustStorePassword` | no | - | The password required to read the contents of the trust store. |
| `HONO_SERVER_TRUST_STORE_PATH`<br>`--hono.server.trustStorePath` | no  | - | The absolute path to the Java key store containing the CA certificates the Hono server uses for authenticating clients. The key store format can be either `JKS`, `PKCS12` or `PEM` indicated by a `.jks`, `.p12` or `.pem` file suffix. |

The options only need to be set if the default value does not match your environment.

### Port configuration

Hono server supports a secure and an insecure listening port by means of configuration. This leverages the following configuration scenarios:

- only one secure port (default)
- only one insecure port
- one secure **and** one insecure port (dual port configuration)

If both ports are not configured correctly or are disabled, the Hono server will fail to start. 

#### Only one secure port
This is the default configuration. It only needs the configuration of a keystore (`HONO_SERVER_KEY_STORE_PATH` and `HONO_SERVER_KEY_STORE_PASSWORD`) or PEM files (`HONO_SERVER_KEY_PATH` and `HONO_SERVER_CERT_PATH`).

Hono server then opens the IANA default port number 5671 as TLS secured AMQP 1.0 port. 

If `HONO_SERVER_PORT` is configured explicitly this port will be used instead. 

The `HONO_SERVER_BIND_ADDRESS` property can be used to specify the network interface that the port should be exposed on. Setting this property to 0.0.0.0 will let the Hono server bind to all network interfaces.

#### Only one insecure port

While in production scenarios it is almost certainly necessary that the Hono server communicates via the encrypted AMQP 1.0 port, a setup with an insecure (i.e. unencrypted) AMQP port sometimes is
desirable.
This may ease the debugging of AMQP 1.0 frames by using proxies or network sniffers that log the details while developing client applications (e.g. protocol adapters) for Hono.  

The configuration of an insecure port must be explicitly enabled for the Hono server by either

- setting `HONO_SERVER_INSECURE_PORT_ENABLED` to true: then a port with the IANA default port number 5672 is opened, or by
- setting `HONO_SERVER_INSECURE_PORT` to any valid port number: then a port with this number is opened (even when `HONO_SERVER_INSECURE_PORT_ENABLED` is not configured or set to false).

If `HONO_SERVER_INSECURE_PORT` is set to the IANA port number 5671 (secured AMQP 1.0) a warning is printed.


#### One secure **and** one insecure port (dual port configuration)
 
In test setups and some production scenarios Hono server may be configured to open one secure **and** one insecure port at the same time.
 
This is achieved by configuring both ports correctly (see above). 
If the same port number is configured for both ports it is considered as misconfiguration and Hono server fails to start.

Since the secure port may need different visibility in the network setup compared to the secure port, it has it's own binding address `HONO_SERVER_INSECURE_PORT_BIND_ADDRESS`. 
This can be used to narrow the visibility of the insecure port to a local network e.g., while the secure port may be visible worldwide. 

#### Ephemeral port configuration

Both ports may be explicitly configured to `0`. Hono server will then open arbitrary ports that are available.

## Run as a Docker Container

When running the Hono server as a Docker container, the preferred way of configuration is to pass environment variables to the container during startup using Docker's `-e` or `--env` command line option.

The following command starts the Hono server container using the configuration files included in the image under path `/etc/hono`.

~~~sh
$ docker run -d --name hono --network hono-net -e 'HONO_DOWNSTREAM_HOST=qdrouter' -e 'HONO_DOWNSTREAM_PORT=5673' \
> -e 'HONO_DOWNSTREAM_KEY_STORE_PATH=/etc/hono/certs/honoKeyStore.p12' -e 'HONO_DOWNSTREAM_KEY_STORE_PASSWORD=honokeys' \
> -e 'HONO_DOWNSTREAM_TRUST_STORE_PATH=/etc/hono/certs/trusted-certs.pem' -e 'HONO_AUTHORIZATION_PERMISSIONS_PATH=file:/etc/hono/permissions.json' \
> -e 'HONO_SERVER_KEY_STORE_PATH=/etc/hono/certs/honoKeyStore.p12' -e 'HONO_SERVER_KEY_STORE_PASSWORD=honokeys' \
> -e 'HONO_SERVER_BIND_ADDRESS=0.0.0.0' -p5672:5672 eclipsehono/hono-server:latest
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
> --hono.downstream.keyStorePath=../demo-certs/certs/honoKeyStore.p12,--hono.downstream.keyStorePassword=honokeys,\
> --hono.downstream.trustStorePath=../demo-certs/certs/trusted-certs.pem,\
> --hono.server.keyStorePath=../demo-certs/certs/honoKeyStore.p12,--hono.server.keyStorePassword=honokeys,\
> --hono.server.trustStorePath=../demo-certs/certs/trusted-certs.pem
~~~

{{% note %}}
In the example above the *hono.downstream.host=qdrouter* command line option indicates that the Dispatch Router is running on a host with name *qdrouter*. However, if the Dispatch Router has been started as a Docker container then the *qdrouter* host name will most likely only be resolvable on the network that Docker has created for running the container on, i.e. when you run the Hono server from the Spring Boot application and want it to connect to a Dispatch Router run as a Docker container then you need to set the value of the *hono.downstream.host* option to the IP address (or name) of the Docker host running the Dispatch Router container.
The *hono.downstream.keyStorePath* option is required because the Dispatch Router requires the Hono server to authenticate by means of a client certificate during connection establishment.
The *hono.downstream.hostnameVerificationRequired* parameter is necessary to prevent Hono from validating the Dispatch Router's host name by means of comparing it to the *common name* of the Dispatch Routers's certificate's subject.
You may want to make logging of the Hono server a little more verbose by enabling the *dev* Spring profile.
To do so, append `,--spring.profiles.active=dev` to the command line.
{{% /note %}}
