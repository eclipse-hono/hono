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
| `HONO_DOWNSTREAM_PORT`<br>`--hono.downstream.port` | yes | `5672` | The port that the Dispatch Router is listening on for connection from the Hono server.<br>**NB** When using the Dispatch Router image with the example configuration then this property needs to be set to `5673`. This is because in the example configuration the Dispatch Router's *internal* listener used for accepting connections from the Hono server is configured to attach to port 5673. |
| `HONO_DOWNSTREAM_USERNAME`<br>`--hono.downstream.username` | no | - | The username to use for authenticating to the Dispatch Router. This property (and the corresponding password) needs to be set only if the Dispatch Router component is configured to use `SASL PLAIN` instead of `SASL EXTERNAL` for authenticating the Hono server. |
| `HONO_DOWNSTREAM_PASSWORD`<br>`--hono.downstream.password` | no | - | The password to use for authenticating to the Hono server. This property (and the corresponding username) needs to be set only if the Dispatch Router component is configured to use `SASL PLAIN` instead of `SASL EXTERNAL` for authenticating the Hono server. |
| `HONO_DOWNSTREAM_TRUST_STORE_PATH`<br>`--hono.downstream.trustStorePath` | no  | - | The absolute path to the Java key store containing the CA certificates the Hono server uses for authenticating the Dispatch Router. This property **must** be set if the Dispatch Router has been configured to support TLS. The key store format can be either `JKS`, `PKCS12` or `PEM` indicated by a `.jks`, `.p12` or `.pem` file suffix. |
| `HONO_DOWNSTREAM_TRUST_STORE_PASSWORD`<br>`--hono.downstream.trustStorePassword` | no | - | The password required to read the contents of the trust store. |
| `HONO_SERVER_BIND_ADDRESS`<br>`--hono.server.bindaddress` | yes | `127.0.0.1` | The IP address the Hono server should bind to. By default the adapter binds to the *loopback device* address, i.e. the adapter will **not** be accessible from other hosts. |
| `HONO_SERVER_CERT_PATH`<br>`--hono.server.certPath` | no | - | The absolute path to the PEM file containing the certificate the Hono server uses for authenticating to clients. Either this or the `HONO_SERVER_KEY_STORE_PATH` option **must** be set to enable TLS secured connections with clients. |
| `HONO_SERVER_KEY_PATH`<br>`--hono.server.keyPath` | no | - | The absolute path to the PEM file containing the private key the Hono server uses for authenticating to clients. Either this or the `HONO_SERVER_KEY_STORE_PATH` option **must** be set to enable TLS secured connections with clients. |
| `HONO_SERVER_KEY_STORE_PATH`<br>`--hono.server.keyStorePath` | no | - | The absolute path to the Java key store containing the private key and certificate the Hono server uses for authenticating to clients. Either this or the `HONO_SERVER_KEY_PATH` and `HONO_SERVER_CERT_PATH` options **must** be set to enable TLS secured connections with clients. The key store format can be either `JKS` or `PKCS12` indicated by a `.jks` or `.p12` file suffix. |
| `HONO_SERVER_KEY_STORE_PASSWORD`<br>`--hono.server.keyStorePassword` | no | - | The password required to read the contents of the key store. |
| `HONO_SERVER_MAX_INSTANCES`<br>`--hono.server.maxInstances` | no | *#CPU cores* | The number of verticle instances to deploy. If not set, one verticle per processor core is deployed. |
| `HONO_SERVER_PORT`<br>`--hono.server.port` | yes | `5672` | The port the server should listen on. |
| `HONO_SERVER_TRUST_STORE_PATH`<br>`--hono.server.trustStorePath` | no  | - | The absolute path to the Java key store containing the CA certificates the Hono server uses for authenticating clients. The key store format can be either `JKS`, `PKCS12` or `PEM` indicated by a `.jks`, `.p12` or `.pem` file suffix. |
| `HONO_SERVER_TRUST_STORE_PASSWORD`<br>`--hono.server.trustStorePassword` | no | - | The password required to read the contents of the trust store. |

The options only need to be set if the default value does not match your environment.

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
