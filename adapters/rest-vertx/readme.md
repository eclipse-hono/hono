This module contains a protocol adapter that exposes a RESTful API for Hono's Telemetry and Registration API.

# Running the Adapter

The adapter can be run by means of running the corresponding Docker image.

    $ docker run -d --name rest-adapter -p8080:8080 eclipsehono/hono-adapter-rest-vertx:0.5-SNAPSHOT

The rest adapter requires some configuration in order to connect to a Hono server. Configuration is done by means
of the following environment variables:

| Variable | Mandatory | Default | Description |
| :------- | :-------- | :------ | :---------- |
| `HONO_HTTP_BINDADDRESS` | yes | `0.0.0.0` | The IP address the protocol adapter should bind to. By default the adapter binds to the *wildcard* address, i.e. all network adapters. |
| `HONO_HTTP_LISTENPORT` | yes | `8080` | The port the protocol adapter should listen on. |
| `HONO_CLIENT_HOST` | yes | `localhost` | The IP address or name of the Hono server host. This shoud be set to an address that can be resolved within the network established by Docker. Use Docker's `--link` command line parameter to link the rest adapter to the *Hono Server* host on the Docker network.
| `HONO_CLIENT_PORT` | yes | `5672` | The port that the Hono server is listening on. |
| `HONO_CLIENT_USERNAME` | yes | - | The username to use for authenticating to the Hono server. |
| `HONO_CLIENT_PASSWORD` | yes | - | The password to use for authenticating to the Hono server. |

The environment variable(s) only need to be set if the default value does not match your environment.

Assuming that a Hono server Docker container with name `hono` is already started and listens on the default port (5672), the command to start the rest adapter might look like this:

    $ docker run -d --name rest-adapter --link hono -e 'HONO_CLIENT_HOST=hono' \
    $ -e 'HONO_CLIENT_USERNAME=hono-client' -e 'HONO_CLIENT_PASSWORD=secret' -p8080:8080 \
    $ eclipsehono/hono-adapter-rest-vertx:0.5-SNAPSHOT

## Run using Docker Compose

In most cases it is much easier to start all of Hono's components in one shot using Docker Compose.
See the `example` module for details. The `example` module also contains an example service definition file that
you can use as a starting point for your own configuration.

# HTTP API

## Registration API

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

## Telemetry API

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
