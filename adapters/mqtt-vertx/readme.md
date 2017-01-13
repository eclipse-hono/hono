This module contains a protocol adapter that exposes an MQTT API for Hono's Telemetry API.

# Running the Adapter

The adapter can be run by means of running the corresponding Docker image.

    $ docker run -d --name mqtt-adapter -p1883:1883 eclipsehono/hono-adapter-mqtt-vertx:latest

The MQTT adapter requires some configuration in order to connect to a Hono server. Configuration is done by means
of the following environment variables:

| Variable | Mandatory | Default | Description |
| :------- | :-------- | :------ | :---------- |
| `HONO_MQTT_BINDADDRESS` | yes | `0.0.0.0` | The IP address the protocol adapter should bind to. By default the adapter binds to the *wildcard* address, i.e. all network adapters. |
| `HONO_MQTT_LISTENPORT` | yes | `1883` | The port the protocol adapter should listen on. |
| `HONO_CLIENT_HOST` | yes | `localhost` | The IP address or name of the Hono server host. This shoud be set to an address that can be resolved within the network established by Docker. Use Docker's `--link` command line parameter to link the rest adapter to the *Hono Server* host on the Docker network.
| `HONO_CLIENT_PORT` | yes | `5672` | The port that the Hono server is listening on. |
| `HONO_CLIENT_USERNAME` | yes | - | The username to use for authenticating to the Hono server. |
| `HONO_CLIENT_PASSWORD` | yes | - | The password to use for authenticating to the Hono server. |
| `HONO_CLIENT_TRUST_STORE_PATH` | no | - | The absolute path to the Java key store containing the CA certificates the adapter uses for authenticating the Hono server. The key store format can be either `JKS` or `PKCS12` indicated by a `.jks` or `.p12` file suffix. |
| `HONO_CLIENT_TRUST_STORE_PASSWORD` | if trust store path is set | - | The password required to read the contents of the trust store. |

The environment variable(s) only need to be set if the default value does not match your environment.

Assuming that a Hono server Docker container with name `hono` is already started and listens on the default port (5672), the command to start the MQTT adapter might look like this:

    $ docker run -d --name mqtt-adapter --link hono -e 'HONO_CLIENT_HOST=hono' \
    $ -e 'HONO_CLIENT_USERNAME=hono-client' -e 'HONO_CLIENT_PASSWORD=secret' -p1883:1883 \
    $ eclipsehono/hono-adapter-mqtt-vertx:latest

## Run using Docker Compose

In most cases it is much easier to start all of Hono's components in one shot using Docker Compose.
See the `example` module for details. The `example` module also contains an example service definition file that
you can use as a starting point for your own configuration.

# MQTT API

## Telemetry API

### Upload Telemetry Data

* Topic: `telemetry/${tenantId}/${deviceId}`
* Client-id: ${deviceId}
* Payload:
  * (required) Arbitrary payload

**Example**

Upload a JSON string for device `4711`:

    $ mosquitto_pub -i 4711 -t telemetry/DEFAULT_TENANT/4711 -m '{"temp": 5}'

## Event API

### Send Event Message

* Topic: `event/${tenantId}/${deviceId}`
* Client-id: ${deviceId}
* Payload:
  * (required) Arbitrary payload

**Example**

Upload a JSON string for device `4711`:

    $ mosquitto_pub -i 4711 -t event/DEFAULT_TENANT/4711 -m '{"alarm": 1}'
