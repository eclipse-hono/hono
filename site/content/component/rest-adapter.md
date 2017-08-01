+++
title = "REST Adapter"
weight = 350
+++

The REST protocol adapter exposes a RESTful API for Eclipse Hono&trade;'s Telemetry, Event and Registration endpoints.
<!--more-->

## Configuration

The adapter is implemented as a Spring Boot application. It can be run either directly from the command line or by means of starting the corresponding Docker image created from it.

The adapter can be configured by means of environment variables or corresponding command line options.

### Hono Messaging Connection Configuration

The adapter requires a connection to the Hono Messaging component in order to forward telemetry data and events received from devices to downstream consumers.

The following table provides an overview of the configuration variables and corresponding command line options for configuring the connection to the Hono Messaging component.

| Environment Variable<br>Command Line Option | Mandatory | Default Value | Description  |
| :------------------------------------------ | :-------: | :------------ | :------------|
| `HONO_MESSAGING_HOST`<br>`--hono.messaging.host` | yes | `localhost` | The IP address or name of the Hono Messaging host to connect to. NB: This needs to be set to an address that can be resolved within the network the adapter runs on. When running as a Docker container, use Docker's `--network` command line option to attach the adapter container to the Docker network that the *Hono Server* container is running on. |
| `HONO_MESSAGING_PORT`<br>`--hono.messaging.port` | yes | `5671` | The port that the Hono Messaging component is listening on. |
| `HONO_MESSAGING_USERNAME`<br>`--hono.messaging.username` | yes | - | The username to use for authenticating to the Hono Messaging component. |
| `HONO_MESSAGING_PASSWORD`<br>`--hono.messaging.password` | yes | - | The password to use for authenticating to the Hono Messaging component. |
| `HONO_MESSAGING_TRUST_STORE_PATH`<br>`--hono.messaging.trustStorePath` | no  | - | The absolute path to the Java key store containing the CA certificates the adapter uses for authenticating the Hono Messaging component. This property **must** be set if the Hono Messaging component has been configured to support TLS. The key store format can be either `JKS`, `PKCS12` or `PEM` indicated by a `.jks`, `.p12` or `.pem` file suffix respectively. |
| `HONO_MESSAGING_TRUST_STORE_PASSWORD`<br>`--hono.messaging.trustStorePassword` | no | - | The password required to read the contents of the trust store. |

### Device Registration Service Configuration

The adapter requires a connection to an implementation of Hono's Device Registration API in order to retrieve registration status assertions for connected devices.

The following table provides an overview of the configuration variables and corresponding command line options for configuring the connection to the Device Registration service.

| Environment Variable<br>Command Line Option | Mandatory | Default Value | Description  |
| :------------------------------------------ | :-------: | :------------ | :------------|
| `HONO_REGISTRATION_HOST`<br>`--hono.registration.host` | yes | `localhost` | The IP address or name of the Device Registration service. The adapter uses this service to get an assertion regarding a device's registration status, i.e. whether it is enabled and if it is registered with a particular tenant. NB: This variable needs to be set to an address that can be resolved within the network the adapter runs on. When running as a Docker container, use Docker's `--network` command line option to attach the adapter container to the same network the Device Registration service container is running on. |
| `HONO_REGISTRATION_PORT`<br>`--hono.registration.port` | yes | `5671` | The port that the Device Registration service is listening on. |
| `HONO_REGISTRATION_USERNAME`<br>`--hono.registration.username` | yes | - | The username to use for authenticating to the Device Registration service. |
| `HONO_REGISTRATION_PASSWORD`<br>`--hono.registration.password` | yes | - | The password to use for authenticating to the Device Registration service. |
| `HONO_REGISTRATION_TRUST_STORE_PATH`<br>`--hono.registration.trustStorePath` | no  | - | The absolute path to the Java key store containing the CA certificates the adapter uses for authenticating the Device Registration service. This property **must** be set if the Device Registration service has been configured to use TLS. The key store format can be either `JKS`, `PKCS12` or `PEM` indicated by a `.jks`, `.p12` or `.pem` file suffix. |
| `HONO_REGISTRATION_TRUST_STORE_PASSWORD`<br>`--hono.registration.trustStorePassword` | no | - | The password required to read the contents of the trust store. |

### Credentials Service Configuration

The adapter requires a connection to an implementation of Hono's Credentials API in order to retrieve credentials stored for devices that need to be authenticated.

The following table provides an overview of the configuration variables and corresponding command line options for configuring the connection to the Credentials service.

{{% note %}}
Note that the credentials API is expected to be often implemented by the Device Registration component. 
In that case the following configuration variables may be omitted and the client used to access the Device Registration service is used to access the Credentials service as well.
{{% /note %}}

| Environment Variable<br>Command Line Option | Mandatory | Default Value | Description  |
| :------------------------------------------ | :-------: | :------------ | :------------|
| `HONO_CREDENTIALS_HOST`<br>`--hono.credentials.host` | yes | `localhost` | The IP address or name of the Credentials service. The adapter uses this service to get credentials stored for a device to authenticate it. NB: This variable needs to be set to an address that can be resolved within the network the adapter runs on. When running as a Docker container, use Docker's `--network` command line option to attach the adapter container to the same network the Credentials service container is running on. |
| `HONO_CREDENTIALS_PORT`<br>`--hono.credentials.port` | yes | `5671` | The port that the Credentials service is listening on. |
| `HONO_CREDENTIALS_USERNAME`<br>`--hono.credentials.username` | yes | - | The username to use for authenticating to the Credentials service. |
| `HONO_CREDENTIALS_PASSWORD`<br>`--hono.credentials.password` | yes | - | The password to use for authenticating to the Credentials service. |
| `HONO_CREDENTIALS_TRUST_STORE_PATH`<br>`--hono.credentials.trustStorePath` | no  | - | The absolute path to the Java key store containing the CA certificates the adapter uses for authenticating the Credentials service. This property **must** be set if the Credentials service has been configured to use TLS. The key store format can be either `JKS`, `PKCS12` or `PEM` indicated by a `.jks`, `.p12` or `.pem` file suffix. |
| `HONO_CREDENTIALS_TRUST_STORE_PASSWORD`<br>`--hono.credentials.trustStorePassword` | no | - | The password required to read the contents of the trust store. |

### Adapter Configuration

The following table provides an overview of the configuration variables and corresponding command line options for configuring the REST adapter.

| Environment Variable<br>Command Line Option | Mandatory | Default | Description |
| :------------------------------------------ | :-------: | :------ | :---------- |
| `HONO_APP_MAX_INSTANCES`<br>`--hono.app.maxInstances` | no | *#CPU cores* | The number of verticle instances to deploy. If not set, one verticle per processor core is deployed. |
| `HONO_APP_HEALTH_CHECK_PORT`<br>`--hono.app.healthCheckPort` | no | - | The port that the HTTP server, which exposes the service's health check resources, should bind to. If set, the adapter will expose a *readiness* probe at URI `/readiness` and a *liveness* probe at URI `/liveness`. |
| `HONO_APP_HEALTH_CHECK_BIND_ADDRESS`<br>`--hono.app.healthCheckBindAddress` | no | `127.0.0.1` | The IP address of the network interface that the HTTP server, which exposes the service's health check resources, should be bound to. The HTTP server will only be started if `HONO_APP_HEALTH_CHECK_BIND_ADDRESS` is set explicitly. |
| `HONO_HTTP_BIND_ADDRESS`<br>`--hono.http.bindAddress` | no | `127.0.0.1` | The IP address of the network interface that the secure port should be bound to.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_HTTP_CERT_PATH`<br>`--hono.http.certPath` | no | - | The absolute path to the PEM file containing the certificate that the protocol adapter should use for authenticating to clients. This option must be used in conjunction with `HONO_HTTP_KEY_PATH`.<br>Alternatively, the `HONO_HTTP_KEY_STORE_PATH` option can be used to configure a key store containing both the key as well as the certificate. |
| `HONO_HTTP_INSECURE_PORT`<br>`--hono.http.insecurePort` | no | - | The insecure port the protocol adapter should listen on.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_HTTP_INSECURE_PORT_BIND_ADDRESS`<br>`--hono.http.insecurePortBindAddress` | no | `127.0.0.1` | The IP address of the network interface that the insecure port should be bound to.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_HTTP_INSECURE_PORT_ENABLED`<br>`--hono.http.insecurePortEnabled` | no | `false` | If set to `true` the protocol adapter will open an insecure port (not secured by TLS) using either the port number set via `HONO_HTTP_INSECURE_PORT` or the default port number (`8080`) if not set explicitly.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_HTTP_KEY_PATH`<br>`--hono.http.keyPath` | no | - | The absolute path to the (PKCS8) PEM file containing the private key that the protocol adapter should use for authenticating to clients. This option must be used in conjunction with `HONO_HTTP_CERT_PATH`. Alternatively, the `HONO_HTTP_KEY_STORE_PATH` option can be used to configure a key store containing both the key as well as the certificate. |
| `HONO_HTTP_KEY_STORE_PASSWORD`<br>`--hono.http.keyStorePassword` | no | - | The password required to read the contents of the key store. |
| `HONO_HTTP_KEY_STORE_PATH`<br>`--hono.http.keyStorePath` | no | - | The absolute path to the Java key store containing the private key and certificate that the protocol adapter should use for authenticating to clients. Either this option or the `HONO_HTTP_KEY_PATH` and `HONO_HTTP_CERT_PATH` options need to be set in order to enable TLS secured connections with clients. The key store format can be either `JKS` or `PKCS12` indicated by a `.jks` or `.p12` file suffix respectively. |
| `HONO_HTTP_MAX_PAYLOAD_SIZE`<br>`--hono.http.maxPayloadSize` | no | `2048` | The maximum allowed size of an incoming HTTP request's body in bytes. Requests with a larger body size are rejected with a 413 `Request entity too large` response. |
| `HONO_HTTP_PORT`<br>`--hono.http.port` | no | `8443` | The secure port that the protocol adapter should listen on.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |

The variables only need to be set if the default value does not match your environment.

## Port Configuration

The REST protocol adapter can be configured to listen for connections on

* a secure port only (default) or
* an insecure port only or
* both a secure and an insecure port (dual port configuration)

The REST protocol adapter will fail to start if none of the ports is configured properly.

### Secure Port Only

The protocol adapter needs to be configured with a private key and certificate in order to open a TLS secured port.

There are two alternative ways for doing so:

1. either setting the `HONO_HTTP_KEY_STORE_PATH` and the `HONO_HTTP_KEY_STORE_PASSWORD` variables in order to load the key & certificate from a password protected key store, or
1. setting the `HONO_HTTP_KEY_PATH` and `HONO_HTTP_CERT_PATH` variables in order to load the key and certificate from two separate PEM files in PKCS8 format.

When starting up, the protocol adapter will bind a TLS secured socket to the default secure port 8443. The port number can also be set explicitly using the `HONO_HTTP_PORT` variable.

The `HONO_HTTP_BIND_ADDRESS` variable can be used to specify the network interface that the port should be exposed on. By default the port is bound to the *loopback device* only, i.e. the port will only be accessible from the local host. Setting this variable to `0.0.0.0` will let the port being bound to **all** network interfaces (be careful not to expose the port unintentionally to the outside world).

### Insecure Port Only

The secure port will mostly be required for production scenarios. However, it might be desirable to expose a non-TLS secured port instead, e.g. for testing purposes. In any case, the non-secure port needs to be explicitly enabled either by

- explicitly setting `HONO_HTTP_INSECURE_PORT` to a valid port number, or by
- implicitly configuring the default port (8080) by simply setting `HONO_HTTP_INSECURE_PORT_ENABLED` to `true`.

The protocol adapter issues a warning on the console if `HONO_HTTP_INSECURE_PORT` is set to the default secure HTTP port (8443).

The `HONO_HTTP_INSECURE_PORT_BIND_ADDRESS` variable can be used to specify the network interface that the port should be exposed on. By default the port is bound to the *loopback device* only, i.e. the port will only be accessible from the local host. This variable might be used to e.g. expose the non-TLS secured port on a local interface only, thus providing easy access from within the local network, while still requiring encrypted communication when accessed from the outside over public network infrastructure.

Setting this variable to `0.0.0.0` will let the port being bound to **all** network interfaces (be careful not to expose the port unintentionally to the outside world).

### Dual Port
 
The protocol adapter may be configured to open both a secure and a non-secure port at the same time simply by configuring both ports as described above. For this to work, both ports must be configured to use different port numbers, otherwise startup will fail.

### Ephemeral Ports

Both the secure as well as the insecure port numbers may be explicitly set to `0`. The protocol adapter will then use arbitrary (unused) port numbers determined by the operating system during startup.

## Run as a Docker Container

When running the adapter as a Docker container, the preferred way of configuration is to pass environment variables to the container during startup using Docker's `-e` or `--env` command line option.

The following command starts the REST adapter container using the trusted certificates included in the image under path `/etc/hono/certs`.

~~~sh
$ docker run -d --name rest-adapter --network hono-net \
> -e 'HONO_MESSAGING_HOST=hono-service-messaging.hono' \
> -e 'HONO_MESSAGING_USERNAME=rest-adapter@HONO' \
> -e 'HONO_MESSAGING_PASSWORD=rest-secret' \
> -e 'HONO_MESSAGING_TRUST_STORE_PATH=/etc/hono/certs/trusted-certs.pem' \
> -e 'HONO_REGISTRATION_HOST=hono-service-device-registry.hono' \
> -e 'HONO_REGISTRATION_USERNAME=rest-adapter@HONO' \
> -e 'HONO_REGISTRATION_PASSWORD=rest-secret' \
> -e 'HONO_REGISTRATION_TRUST_STORE_PATH=/etc/hono/certs/trusted-certs.pem' \
> -e 'HONO_HTTP_BIND_ADDRESS=0.0.0.0' \
> -e 'HONO_HTTP_KEY_PATH=/etc/hono/certs/rest-adapter-key.pem' \
> -e 'HONO_HTTP_CERT_PATH=/etc/hono/certs/rest-adapter-cert.pem' \
> -e 'HONO_HTTP_INSECURE_PORT_ENABLED=true' \
> -e 'HONO_HTTP_INSECURE_PORT_BIND_ADDRESS=0.0.0.0'
> -p8080:8080 -p8443:8443 eclipsehono/hono-adapter-rest-vertx:latest
~~~

{{% note %}}
The *--network* command line switch is used to specify the *user defined* Docker network that the REST adapter container should attach to. It is important that the REST adapter container is attached to the same network that the Hono Messaging component is attached to so that the REST adapter can use the Hono Messaging component's host name to connect to it via the Docker network.
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
~/hono/adapters/rest-vertx$ mvn spring-boot:run -Drun.arguments=\
> --hono.messaging.host=hono-service-messaging.hono,\
> --hono.messaging.username=rest-adapter@HONO,\
> --hono.messaging.password=rest-secret,\
> --hono.messaging.trustStorePath=target/certs/trusted-certs.pem \
> --hono.registration.host=hono-service-device-registry.hono,\
> --hono.registration.username=rest-adapter@HONO,\
> --hono.registration.password=rest-secret,\
> --hono.registration.trustStorePath=target/certs/trusted-certs.pem \
> --hono.http.bindAddress=0.0.0.0 \
> --hono.http.insecurePortEnabled=true,\
> --hono.http.insecurePortBindAddress=0.0.0.0
~~~

{{% note %}}
In the example above the *--hono.messaging.host=hono-service-messaging.hono* command line option indicates that the Hono Messaging component is running on a host
with name *hono-service-messaging.hono*. However, if the Hono Messaging component has been started as a Docker container then the *hono-service-messaging.hono* host name will most likely only be resolvable on the network that Docker has created for running the container on, i.e. when you run the REST adapter
from the Spring Boot application and want it to connect to a Hono Messaging instance run as a Docker container then you need to set the
value of the *--hono.messaging.host* option to the IP address (or name) of the Docker host running the Hono Messaging container.
The same holds true analogously for the *hono-service-device-registry.hono* address.
{{% /note %}}

## Using the Registration API

### Register Device

* URI: `/registration/${tenantId}`
* Method: `POST`
* Headers:
  * (required) `Content-Type`: either `application/x-www-url-encoded` or `application/json`
* Parameters (encoded as payload according to the content type):
  * (required) `device_id`: The ID of the device to register.
  * (optional) Arbitrary key/value pairs containing additional data to be registered with the device.
* Status Codes:
  * 201 (Created): Device has been registered successfully under resource indicated by `Location` header.
  * 400 (Bad Request): Device has not been registered because the request was malformed, e .g. a required header is missing (the body may contain hints regarding the problem).
  * 409 (Conflict): There already exists a device with the given ID. The request has not been processed.

**Example**

The following command registers a device with ID `4711`

    $ curl -i -X POST -d device_id=4711 -d ep=IMEI4711 http://127.0.0.1:8080/registration/DEFAULT_TENANT

or equivalently using JSON

    $ curl -i -X POST -d '{"device_id":"4711","ep":"IMEI4711"}' -H 'Content-Type: application/json' http://localhost:8080/registration/DEFAULT_TENANT

The response will contain a `Location` header containing the resource path created for the device. In this example it will look
like this:

    HTTP/1.1 201 Created
    Location: /registration/DEFAULT_TENANT/4711
    Content-Length: 0

### Read Registration

* URI: `/registration/${tenantId}/${deviceId}`
* Method: `GET`
* Status Codes:
  * 200 (OK): Device has been found, body contains registration data.
  * 404 (Not Found): No device with the given identifier is registered for the given tenant.

**Example**

The following command retrieves registration data for device `4711`:

    $ curl -i http://127.0.0.1:8080/registration/DEFAULT_TENANT/4711

The response will look similar to this:

    HTTP/1.1 200 OK
    Content-Type: application/json; charset=utf-8
    Content-Length: 35

    {
      "data" : {
         "enabled": true,
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
         "enabled": true,
         "ep": "IMEI4711"
      },
      "id" : "4711"
    }

### Update Registration

* URI: `/registration/${tenantId}/${deviceId}`
* Method: `PUT`
* Headers:
  * (required) `Content-Type`: either `application/x-www-url-encoded` or `application/json`
* Parameters (encoded as payload according to content type):
  * (optional) Arbitrary key/value pairs containing additional data to be registered with the device. The existing key/value pairs will be replaced with these key/values.
* Status Codes:
  * 200 (OK): Device registration data has been updated. The body contains the *previous* data registered for the device.
  * 400 (Bad Request): Device registration has not been updated because the request was malformed, e .g. a required header is missing (the body may contain hints regarding the problem).
  * 404 (Not Found): No device with the given identifier is registered for the given tenant.

**Example**

    $ curl -i -X PUT -d ep=IMEI4711 -d psk-id=psk4711 http://127.0.0.1:8080/registration/DEFAULT_TENANT/4711

The response will look similar to this:

    HTTP/1.1 200 OK
    Content-Type: application/json; charset=utf-8
    Content-Length: 35

    {
      "data" : {
         "enabled": true,
         "ep": "IMEI4711"
      },
      "id" : "4711"
    }

### Delete Registration

* URI: `/registration/${tenantId}/${deviceId}`
* Method: `DELETE`
* Status Codes:
  * 200 (OK): Device registration has been deleted. The payload contains the data that had been registered for the device.
  * 404 (Not Found): No device with the given identifier is registered for the given tenant.

**Example**

    $ curl -i -X DELETE http://127.0.0.1:8080/registration/DEFAULT_TENANT/4711

The response will look similar to this:

    HTTP/1.1 200 OK
    Content-Type: application/json; charset=utf-8
    Content-Length: 35

    {
      "data" : {
         "enabled": true,
         "ep": "IMEI4711",
         "psk-id": "psk4711"
      },
      "id" : "4711"
    }

## Using the Telemetry API

### Upload Telemetry Data

* URI: `/telemetry/${tenantId}/${deviceId}`
* Method: `PUT`
* Request Headers:
  * (required) `Content-Type`: The type of payload contained in the body.
  * (optional) `Hono-Reg-Assertion`: A JSON Web Token asserting the device's registration status (see [assert Device Registration]({{< relref "api/Device-Registration-API.md#assert-device-registration" >}}))
* Request Body:
  * (required) Arbitrary payload encoded according to the given content type.
* Status Codes:
  * 202 (Accepted): The telemetry data has been accepted for processing. Note that this does not *guarantee* successful delivery to potential consumers.
  * 400 (Bad Request): The request cannot be processed because the content type header is missing or the request body is empty.
  * 403 (Forbidden): The request cannot be processed because the device's registration status cannot be asserted, i.e. the given device either does not belong to the given tenant or is disabled.
  * 503 (Service Unavailable): The request cannot be processed because there is no consumer of telemetry data for the given tenant connected to Hono.
* Response Headers:
  * `Hono-Reg-Assertion`: A JSON Web Token asserting the device's registration status (see [assert Device Registration]({{< relref "api/Device-Registration-API.md#assert-device-registration" >}})). A client should include this token on subsequent telemetry or event requests for the same device in order to prevent the REST adapter from requesting a fresh assertion from the *Device Registration* service on each invocation. This header will be included in a response every time a new token has been issued by the *Registration Service*.

**Example**

Upload a JSON string for device `4711`:

    $ curl -i -X PUT -H 'Content-Type: application/json' \
    $ --data-binary '{"temp": 5}' http://127.0.0.1:8080/telemetry/DEFAULT_TENANT/4711

Response:

    HTTP/1.1 202 Accepted
    Content-Length: 0
    Hono-Reg-Assertion: eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI0NzExIiwidGVuIjoiREVGQVVMVF9URU5BTlQiLCJleHAiOjE0OTQ1OTg5Njl9.SefIa2UjNYiWwBfPOkizIlMPb3H2-hy7BHGjTgbX_I0

Subsequent request:

    $ curl -i -X PUT -H 'Content-Type: application/json' \
    $ -H 'Hono-Reg-Assertion: eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI0NzExIiwidGVuIjoiREVGQVVMVF9URU5BTlQiLCJleHAiOjE0OTQ1OTg5Njl9.SefIa2UjNYiWwBfPOkizIlMPb3H2-hy7BHGjTgbX_I0' \
    $ --data-binary '{"temp": 10}' http://127.0.0.1:8080/telemetry/DEFAULT_TENANT/4711

## Using the Event API

### Send an Event

* URI: `/event/${tenantId}/${deviceId}`
* Method: `PUT`
* Request Headers:
  * (required) `Content-Type`: The type of payload contained in the body.
  * (optional) `Hono-Reg-Assertion`: A JSON Web Token asserting the device's registration status (see [assert Device Registration]({{< relref "api/Device-Registration-API.md#assert-device-registration" >}}))
* Request Body:
  * (required) Arbitrary payload encoded according to the given content type.
* Status Codes:
  * 202 (Accepted): The telemetry data has been accepted for processing. Note that this does not *guarantee* successful delivery to potential consumers.
  * 400 (Bad Request): The request cannot be processed because the content type header is missing or the request body is empty.
  * 403 (Forbidden): The request cannot be processed because the device's registration status cannot be asserted, i.e. the given device either does not belong to the given tenant or is disabled.
  * 503 (Service Unavailable): The request cannot be processed because there is no consumer of events for the given tenant connected to Hono.
* Response Headers:
  * `Hono-Reg-Assertion`: A JSON Web Token asserting the device's registration status (see [assert Device Registration]({{< relref "api/Device-Registration-API.md#assert-device-registration" >}})). A client should include this token on subsequent telemetry or event requests for the same device in order to prevent the REST adapter from requesting a fresh assertion from the *Device Registration* service on each invocation. This header will be included in a response every time a new token has been issued by the *Registration Service*.

**Example**

Upload a JSON string for device `4711`:

    $ curl -i -X PUT -H 'Content-Type: application/json' \
    $ --data-binary '{"temp": 5}' http://127.0.0.1:8080/event/DEFAULT_TENANT/4711

Response:

    HTTP/1.1 202 Accepted
    Content-Length: 0
    Hono-Reg-Assertion: eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI0NzExIiwidGVuIjoiREVGQVVMVF9URU5BTlQiLCJleHAiOjE0OTQ1OTg5Njl9.SefIa2UjNYiWwBfPOkizIlMPb3H2-hy7BHGjTgbX_I0

Subsequent request:

    $ curl -i -X PUT -H 'Content-Type: application/json' \
    $ -H 'Hono-Reg-Assertion: eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI0NzExIiwidGVuIjoiREVGQVVMVF9URU5BTlQiLCJleHAiOjE0OTQ1OTg5Njl9.SefIa2UjNYiWwBfPOkizIlMPb3H2-hy7BHGjTgbX_I0' \
    $ --data-binary '{"temp": 10}' http://127.0.0.1:8080/event/DEFAULT_TENANT/4711
