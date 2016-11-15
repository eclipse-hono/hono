+++
title = "MQTT Adapter"
weight = 370
+++

The MQTT protocol adapter exposes an MQTT topic hierarchy for publishing messages and events to Eclipse Hono&trade;'s Telemetry and Event endpoints.
<!--more-->

## Configuration

The adapter is implemented as a Spring Boot application. It can be run either directly from the command line or by means of starting the corresponding Docker image created from it.

The adapter can be configured by means of environment variables or corresponding command line options.
The following table provides an overview of the configuration options the adapter supports:

| Environment Variable<br>Command Line Option | Mandatory | Default Value | Description  |
| :------------------------------------------ | :-------: | :------------ | :------------|
| `HONO_MQTT_BINDADDRESS`<br>`--hono.mqtt.bindAddress` | yes | `0.0.0.0` | The IP address the protocol adapter should bind to. By default the adapter binds to the *wildcard* address, i.e. all network adapters. |
| `HONO_MQTT_LISTENPORT`<br>`--hono.mqtt.listenPort` | yes | `1883` | The port the protocol adapter should listen on. |
| `HONO_CLIENT_HOST`<br>`--hono.client.host` | yes | `localhost` | The IP address or name of the Hono server host. NB: This needs to be set to an address that can be resolved within the network the adapter runs on. When running as a Docker container, use Docker's `--link` command line option to link the adapter container to the host of the *Hono Server* container on the Docker network.
| `HONO_CLIENT_PORT`<br>`--hono.client.port` | yes | `5672` | The port that the Hono server is listening on. |
| `HONO_CLIENT_USERNAME`<br>`--hono.client.username` | yes | - | The username to use for authenticating to the Hono server. |
| `HONO_CLIENT_PASSWORD`<br>`--hono.client.password` | yes | - | The password to use for authenticating to the Hono server. |
| `HONO_CLIENT_TRUST_STORE_PATH`<br>`--hono.client.trustStorePath` | no  | - | The absolute path to the Java key store containing the CA certificates the adapter uses for authenticating the Hono server. This property **must** be set if the Hono server has been configured to support TLS. The key store format can be either `JKS`, `PKCS12` or `PEM` indicated by a `.jks`, `.p12` or `.pem` file suffix. |
| `HONO_CLIENT_TRUST_STORE_PASSWORD`<br>`--hono.client.trustStorePassword` | no | - | The password required to read the contents of the trust store. |

The options only need to be set if the default value does not match your environment.

## Run as a Docker Container

When running the adapter as a Docker container, the preferred way of configuration is to pass environment variables to the container during startup using Docker's `-e` or `--env` command line option.

The following commands first start the volume container containing the example trust store (from the `example` folder) and then start the MQTT adapter container mounting the exposed volume in order to be able to read the configuration files from it.

~~~sh
$ docker run -d --name mqtt-certs eclipsehono/mqtt-adapter-certs:latest
$ docker run -d --name mqtt-adapter --link hono -e 'HONO_CLIENT_HOST=hono' \
> -e 'HONO_CLIENT_USERNAME=hono-client' -e 'HONO_CLIENT_PASSWORD=secret' \
> -e 'HONO_CLIENT_TRUST_STORE_PATH=/etc/hono/certs/trustStore.jks' \
> -e 'HONO_CLIENT_TRUST_STORE_PASSWORD=honotrust' \
> -p1883:1883 --volumes-from=mqtt-certs eclipsehono/hono-adapter-mqtt-vertx:latest

## Run using Docker Compose

In most cases it is much easier to start all of Hono's components in one shot using Docker Compose.
See the `example` module for details. The `example` module also contains an example service definition file that
you can use as a starting point for your own configuration.

## Run the Spring Boot Application

Sometimes it is helpful to run the adapter from its jar file, e.g. in order to attach a debugger more easily or to take advantage of code replacement.
In order to do so, the adapter can be started using the `spring-boot:run` maven goal from the `adapters/mqtt-vertx` folder.
The corresponding command to start up the adapter with the configuration used in the Docker example above looks like this:

~~~sh
~/hono/adapters/mqtt-vertx$ mvn spring-boot:run -Drun.arguments=--hono.client.host=hono,--hono.client.username=hono-client,--hono.client.password=secret
~~~

{{% note %}}
In the example above the `--hono.client.host=hono` command line option indicates that the Hono server is running on a host
with name `hono`. However, if the Hono server has been started as a Docker container then the `hono` host name will most
likely only be resolvable on the network that Docker has created for running the container on, i.e. when you run the REST adapter
from the Spring Boot application and want it to connect to a Hono server run as a Docker container then you need to set the
value of the `--hono.client.host` option to the IP address (or name) of the Docker host running the Hono server container.
{{% /note %}}

## Using the Telemetry Topic Hierarchy

### Upload Telemetry Data

* Topic: `telemetry/${tenantId}/${deviceId}`
* Client-id: ${deviceId}
* Payload:
  * (required) Arbitrary payload

**Example**

Upload a JSON string for device `4711`:

    $ mosquitto_pub -i 4711 -t telemetry/DEFAULT_TENANT/4711 -m '{"temp": 5}'

## Using the Event Topic Hierarchy

### Send Event Message

* Topic: `event/${tenantId}/${deviceId}`
* Client-id: ${deviceId}
* Payload:
  * (required) Arbitrary payload

**Example**

Upload a JSON string for device `4711`:

    $ mosquitto_pub -i 4711 -t event/DEFAULT_TENANT/4711 -m '{"alarm": 1}'
