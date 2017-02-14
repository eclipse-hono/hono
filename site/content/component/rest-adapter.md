+++
title = "REST Adapter"
weight = 350
+++

The REST protocol adapter exposes a RESTful API for Eclipse Hono&trade;'s Telemetry, Event and Registration endpoints.
<!--more-->

## Configuration

The adapter is implemented as a Spring Boot application. It can be run either directly from the command line or by means of starting the corresponding Docker image created from it.

The adapter can be configured by means of environment variables or corresponding command line options.
The following table provides an overview of the configuration options the adapter supports:

| Environment Variable<br>Command Line Option | Mandatory | Default | Description |
| :------------------------------------ | :-------: | :------ | :---------- |
| `HONO_HTTP_BINDADDRESS`<br>`--hono.http.bindAddress` | yes | `0.0.0.0` | The IP address the protocol adapter should bind to. By default the adapter binds to the *wildcard* address, i.e. the adapter will bind to all network adapters. |
| `HONO_HTTP_LISTENPORT`<br>`--hono.http.listenPort` | yes | `8080` | The port the protocol adapter should listen on. |
| `HONO_CLIENT_HOST`<br>`--hono.client.host` | yes | `localhost` | The IP address or name of the Hono server host. NB: This needs to be set to an address that can be resolved within the network the adapter runs on. When running as a Docker container, use Docker's `--link` command line option to link the adapter container to the host of the *Hono Server* container on the Docker network.
| `HONO_CLIENT_PORT`<br>`--hono.client.port` | yes | `5672` | The port that the Hono server is listening on. |
| `HONO_CLIENT_USERNAME`<br>`--hono.client.username` | yes | - | The username to use for authenticating to the Hono server. |
| `HONO_CLIENT_PASSWORD`<br>`--hono.client.password` | yes | - | The password to use for authenticating to the Hono server. |
| `HONO_CLIENT_TRUST_STORE_PATH`<br>`--hono.client.trustStorePath` | no  | - | The absolute path to the Java key store containing the CA certificates the adapter uses for authenticating the Hono server. This property **must** be set if the Hono server has been configured to support TLS. The key store format can be either `JKS`, `PKCS12` or `PEM` indicated by a `.jks`, `.p12` or `.pem` file suffix. |
| `HONO_CLIENT_TRUST_STORE_PASSWORD`<br>`--hono.client.trustStorePassword` | no | - | The password required to read the contents of the trust store. |

The options only need to be set if the default value does not match your environment.

## Run as a Docker Container

When running the adapter as a Docker container, the preferred way of configuration is to pass environment variables to the container during startup using Docker's `-e` or `--env` command line option.

The following command starts the REST adapter container using the trusted certificates included in the image under under path `/etc/hono/certs`.

~~~sh
$ docker run -d --name rest-adapter --network hono-net -e 'HONO_CLIENT_HOST=hono' \
> -e 'HONO_CLIENT_USERNAME=hono-client' -e 'HONO_CLIENT_PASSWORD=secret' \
> -e 'HONO_CLIENT_TRUST_STORE_PATH=/etc/hono/certs/trusted-certs.pem' \
> -p8080:8080 eclipsehono/hono-adapter-rest-vertx:latest
~~~

{{% note %}}
The *--network* command line switch is used to specify the *user defined* Docker network that the REST adapter container should attach to. It is important that the REST adapter container is attached to the same network that the Hono server is attached to so that the REST adapter can use the Hono server's host name to connect to it via the Docker network.
Please refer to the [Docker Networking Guide](https://docs.docker.com/engine/userguide/networking/#/user-defined-networks) for details regarding how to create a *user defined* network in Docker. When using a *Docker Compose* file to start up a complete Hono stack as a whole, the compose file will either explicitly define one or more networks that the containers attach to or the *default* network is used which is created automatically by Docker Compose for an application stack.

In cases where the REST adapter container requires a lot of configuration via environment variables (provided by means of *-e* switches), it is more convenient to add all environment variable definitions to a separate *env file* and refer to it using Docker's *--env-file* command line switch when starting the container. This way the command line to start the container is much shorter and can be copied and edited more easily.
{{% /note %}}

## Run using Docker Compose

In most cases it is much easier to start all of Hono's components in one shot using Docker Compose.
See the `example` module for details. The `example` module also contains an example service definition file that
you can use as a starting point for your own configuration.

## Run the Spring Boot Application

Sometimes it is helpful to run the adapter from its jar file, e.g. in order to attach a debugger more easily or to take advantage of code replacement.
In order to do so, the adapter can be started using the `spring-boot:run` maven goal from the `adapters/rest-vertx` folder.
The corresponding command to start up the adapter with the configuration used in the Docker example above looks like this:

~~~sh
~/hono/adapters/rest-vertx$ mvn spring-boot:run -Drun.arguments=--hono.client.host=hono,--hono.client.username=hono-client,--hono.client.password=secret
~~~

{{% note %}}
In the example above the *--hono.client.host=hono* command line option indicates that the Hono server is running on a host
with name *hono*. However, if the Hono server has been started as a Docker container then the *hono* host name will most
likely only be resolvable on the network that Docker has created for running the container on, i.e. when you run the REST adapter
from the Spring Boot application and want it to connect to a Hono server run as a Docker container then you need to set the
value of the *--hono.client.host* option to the IP address (or name) of the Docker host running the Hono server container.
{{% /note %}}

## Using the Registration API

### Register Device

* URI: `/registration/${tenantId}`
* Method: `POST`
* Headers:
  * (required) `Content-Type: application/x-www-url-encoded`
* Parameters (encoded as payload according to the content type):
  * (required) `device_id`: The ID of the device to register.
  * (optional) Arbitrary key/value pairs containing additional data to be registered with the device.

**Example**

The following command registers a device with ID `4711`.

    $ curl -i -X POST -d device_id=4711 -d ep=IMEI4711 http://127.0.0.1:8080/registration/DEFAULT_TENANT

The response will contain a `Location` header containing the resource path created for the device. In this example it will look
like this:

    HTTP/1.1 201 Created
    Location: /registration/DEFAULT_TENANT/4711
    Content-Length: 0

### Read Registration

* URI: `/registration/${tenantId}/${deviceId}`
* Method: `GET`

**Example**

The following command retrieves registration data for device `4711`:

    $ curl -i http://127.0.0.1:8080/registration/DEFAULT_TENANT/4711

The response will look similar to this:

    HTTP/1.1 200 OK
    Content-Type: application/json; charset=utf-8
    Content-Length: 35

    {
      "data" : {
         "ep": "IMEI4711"
      },
      "id" : "4711"
    }

### Find Registration

This resource can be used to look up registration data by one of the additional keys registered for a device.

* URI: `/registration/${tenantId}/find`
* Method: `POST`
* Headers:
  * (required) `Content-Type: application/x-www-url-encoded`
* Parameters (encoded as payload according to the content type):
  * (required) a key/value pair to look up the device by.

**Example**

The following command retrieves registration data for device `4711`:

    $ curl -i -X POST -d ep=IMEI4711 http://127.0.0.1:8080/registration/DEFAULT_TENANT/find

The response will look similar to this:

    HTTP/1.1 200 OK
    Content-Type: application/json; charset=utf-8
    Content-Length: 35

    {
      "data" : {
         "ep": "IMEI4711"
      },
      "id" : "4711"
    }

### Update Registration

* URI: `/registration/${tenantId}/${deviceId}`
* Method: `PUT`
* Headers:
  * (required) `Content-Type: application/x-www-url-encoded`
* Parameters (encoded as payload according to content type):
  * (optional) Arbitrary key/value pairs containing additional data to be registered with the device. The existing key/valule pairs will be replaced with these key/values.

**Example**

    $ curl -i -X PUT -d ep=IMEI4711 -d psk-id=psk4711 http://127.0.0.1:8080/registration/DEFAULT_TENANT/4711

The response will look similar to this:

    HTTP/1.1 200 OK
    Content-Type: application/json; charset=utf-8
    Content-Length: 35

    {
      "data" : {
         "ep": "IMEI4711"
      },
      "id" : "4711"
    }

### Delete Registration

* URI: `/registration/${tenantId}/${deviceId}`
* Method: `DELETE`

**Example**

    $ curl -i -X DELETE http://127.0.0.1:8080/registration/DEFAULT_TENANT/4711

The response will look similar to this:

    HTTP/1.1 200 OK
    Content-Type: application/json; charset=utf-8
    Content-Length: 35

    {
      "data" : {
         "ep": "IMEI4711",
         "psk-id": "psk4711"
      },
      "id" : "4711"
    }

## Using the Telemetry API

### Upload Telemetry Data

* URI: `/telemetry/${tenantId}/${deviceId}`
* Method: `PUT`
* Headers:
  * (required) `Content-Type` - the type of payload contained in the body.
* Body:
  * (required) Arbitrary payload encoded according to the given content type.

**Example**

Upload a JSON string for device `4711`:

    $ curl -i -X PUT -H 'Content-Type: application/json' --data-binary '{"temp": 5}' \
    $ http://127.0.0.1:8080/telemetry/DEFAULT_TENANT/4711

## Using the Event API

### Send an Event

* URI: `/event/${tenantId}/${deviceId}`
* Method: `PUT`
* Headers:
  * (required) `Content-Type` - the type of payload contained in the body.
* Body:
  * (required) Arbitrary payload encoded according to the given content type.

**Example**

Upload a JSON string for device `4711`:

    $ curl -i -X PUT -H 'Content-Type: application/json' --data-binary '{"temp": 5}' \
    $ http://127.0.0.1:8080/event/DEFAULT_TENANT/4711
