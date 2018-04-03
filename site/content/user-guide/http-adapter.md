+++
title = "HTTP Adapter"
weight = 210
+++

The HTTP protocol adapter exposes an HTTP based API for Eclipse Hono&trade;'s Telemetry and Event endpoints.
<!--more-->

The HTTP adapter by default requires clients (devices or gateway components) to authenticate during connection establishment. The adapter supports the [Basic HTTP authentication scheme](https://tools.ietf.org/html/rfc7617) for that purpose. The *username* provided in the header must have the form *auth-id@tenant*, e.g. `sensor1@DEFAULT_TENANT`. The adapter verifies the credentials provided by the client against the credentials that the [configured Credentials service]({{< relref "#credentials-service-configuration" >}}) has on record for the client. The adapter uses the Credentials API's *get* operation to retrieve the credentials on record with the *tenant* and *auth-id* provided by the device in the *username* and `hashed-password` as the *type* of secret as query parameters.

When running the Hono example installation as described in the [Getting Started guide]({{< relref "getting-started.md" >}}), the demo Credentials service comes pre-configured with a `hashed-password` secret for devices `4711` and `gw-1` of tenant `DEFAULT_TENANT` having *auth-ids* `sensor1` and `gw1` and (hashed) *passwords* `hono-secret` and `gw-secret` respectively. These credentials are used in the following examples illustrating the usage of the adapter. Please refer to the [Credentials API]({{< relref "api/Credentials-API.md#standard-credential-types" >}}) for details regarding the different types of secrets.

{{% note %}}
There is a subtle difference between the *device identifier* (*device-id*) and the *auth-id* a device uses for authentication. See [Device Identity]({{< relref "concepts/device-identity.md" >}}) for a discussion of the concepts.
{{% /note %}}

## Publish Telemetry Data (authenticated Device)

* URI: `/telemetry`
* Method: `POST`
* Request Headers:
  * (required) `Authorization`: The device's *auth-id* and plain text password encoded according to the [Basic HTTP authentication scheme](https://tools.ietf.org/html/rfc7617).
  * (required) `Content-Type`: The type of payload contained in the body.
* Request Body:
  * (required) Arbitrary payload encoded according to the given content type.
* Status Codes:
  * 202 (Accepted): The telemetry data has been accepted for processing. Note that this does not *guarantee* successful delivery to potential consumers.
  * 400 (Bad Request): The request cannot be processed because the content type header is missing or the request body is empty.
  * 401 (Unauthorized): The request cannot be processed because the request does not contain valid credentials.
  * 403 (Forbidden): The request cannot be processed because the device's registration status cannot be asserted, i.e. the given device either does not belong to the given tenant or is disabled.
  * 503 (Service Unavailable): The request cannot be processed because there is no consumer of telemetry data for the given tenant connected to Hono.

This is the preferred way for devices to publish telemetry data. It is available only if the protocol adapter is configured to require devices to authenticate (which is the default).

**Examples**

Publish some JSON data for device `4711`:

    $ curl -i -X POST -u sensor1@DEFAULT_TENANT:hono-secret -H 'Content-Type: application/json' \
    $ --data-binary '{"temp": 5}' http://127.0.0.1:8080/telemetry
    
    HTTP/1.1 202 Accepted
    Content-Length: 0

## Publish Telemetry Data (unauthenticated Device)

* URI: `/telemetry/${tenantId}/${deviceId}`
* Method: `PUT`
* Request Headers:
  * (required) `Content-Type`: The type of payload contained in the body.
* Request Body:
  * (required) Arbitrary payload encoded according to the given content type.
* Status Codes:
  * 202 (Accepted): The telemetry data has been accepted for processing. Note that this does not *guarantee* successful delivery to potential consumers.
  * 400 (Bad Request): The request cannot be processed because the content type header is missing or the request body is empty.
  * 403 (Forbidden): The request cannot be processed because the device's registration status cannot be asserted, i.e. the given device either does not belong to the given tenant or is disabled.
  * 503 (Service Unavailable): The request cannot be processed because there is no consumer of telemetry data for the given tenant connected to Hono.

This resource MUST be used by devices that have not authenticated to the protocol adapter. Note that this requires the `HONO_HTTP_AUTHENTICATION_REQUIRED` configuration property to be explicitly set to `false`.

**Examples**

Publish some JSON data for device `4711`:

    $ curl -i -X PUT -H 'Content-Type: application/json' \
    $ --data-binary '{"temp": 5}' http://127.0.0.1:8080/telemetry/DEFAULT_TENANT/4711
    
    HTTP/1.1 202 Accepted
    Content-Length: 0

## Publish Telemetry Data (authenticated Gateway)

* URI: `/telemetry/${tenantId}/${deviceId}`
* Method: `PUT`
* Request Headers:
  * (required) `Authorization`: The gateway's *auth-id* and plain text password encoded according to the [Basic HTTP authentication scheme](https://tools.ietf.org/html/rfc7617).
  * (required) `Content-Type`: The type of payload contained in the body.
* Request Body:
  * (required) Arbitrary payload encoded according to the given content type.
* Status Codes:
  * 202 (Accepted): The telemetry data has been accepted for processing. Note that this does not *guarantee* successful delivery to potential consumers.
  * 400 (Bad Request): The request cannot be processed because the content type header is missing or the request body is empty.
  * 403 (Forbidden): The request cannot be processed because the device's registration status cannot be asserted, i.e. the given device either does not belong to the given tenant or is disabled.
  * 503 (Service Unavailable): The request cannot be processed because there is no consumer of telemetry data for the given tenant connected to Hono.

This resource can be used by *gateway* components to publish data *on behalf of* other devices which do not connect to a protocol adapter directly but instead are connected to the gateway, e.g. using some low-bandwidth radio based technology like [SigFox](https://www.sigfox.com) or [LoRa](https://www.lora-alliance.org/). In this case the credentials provided by the gateway during connection establishment with the protocol adapter are used to authenticate the gateway whereas the parameters from the URI are used to identify the device that the gateway publishes data for.

The protocol adapter checks the gateway's authority to publish data on behalf of the device implicitly by means of retrieving a *registration assertion* for the device from the [configured Device Registration service]({{< relref "#device-registration-service-connection-configuration" >}}).

**Examples**

Publish some JSON data for device `4712` via gateway `gw-1`:

    $ curl -i -X PUT -u gw@DEFAULT_TENANT:gw-secret -H 'Content-Type: application/json' \
    $ --data-binary '{"temp": 5}' http://127.0.0.1:8080/telemetry/DEFAULT_TENANT/4712
    
    HTTP/1.1 202 Accepted
    Content-Length: 0

**NB**: The example above assumes that a gateway device with ID `gw-1` has been registered with `hashed-password` credentials with *auth-id* `gw` and password `gw-secret`.

## Publish an Event (authenticated Device)

* URI: `/event`
* Method: `POST`
* Request Headers:
  * (required) `Authorization`: The device's *auth-id* and plain text password encoded according to the [Basic HTTP authentication scheme](https://tools.ietf.org/html/rfc7617).
  * (required) `Content-Type`: The type of payload contained in the body.
* Request Body:
  * (required) Arbitrary payload encoded according to the given content type.
* Status Codes:
  * 202 (Accepted): The event has been accepted and put to a persistent store for delivery to consumers.
  * 400 (Bad Request): The request cannot be processed because the content type header is missing or the request body is empty.
  * 401 (Unauthorized): The request cannot be processed because the request does not contain valid credentials.
  * 403 (Forbidden): The request cannot be processed because the device's registration status cannot be asserted, i.e. the given device either does not belong to the given tenant or is disabled.
  * 503 (Service Unavailable): The request cannot be processed because there is no consumer of events for the given tenant connected to Hono.

This is the preferred way for devices to publish events. It is available only if the protocol adapter is configured to require devices to authenticate (which is the default).

**Example**

Publish some JSON data for device `4711`:

    $ curl -i -X POST -u sensor1@DEFAULT_TENANT:hono-secret -H 'Content-Type: application/json' \
    $ --data-binary '{"alarm": true}' http://127.0.0.1:8080/event
    
    HTTP/1.1 202 Accepted
    Content-Length: 0

## Publish an Event (unauthenticated Device)

* URI: `/event/${tenantId}/${deviceId}`
* Method: `PUT`
* Request Headers:
  * (required) `Content-Type`: The type of payload contained in the body.
* Request Body:
  * (required) Arbitrary payload encoded according to the given content type.
* Status Codes:
  * 202 (Accepted): The event has been accepted and put to a persistent store for delivery to consumers.
  * 400 (Bad Request): The request cannot be processed because the content type header is missing or the request body is empty.
  * 403 (Forbidden): The request cannot be processed because the device's registration status cannot be asserted, i.e. the given device either does not belong to the given tenant or is disabled.
  * 503 (Service Unavailable): The request cannot be processed because there is no consumer of events for the given tenant connected to Hono.

This resource MUST be used by devices that have not authenticated to the protocol adapter. Note that this requires the `HONO_HTTP_AUTHENTICATION_REQUIRED` configuration property to be explicitly set to `false`.

**Examples**

Publish some JSON data for device `4711`:

    $ curl -i -X PUT -H 'Content-Type: application/json' \
    $ --data-binary '{"alarm": true}' http://127.0.0.1:8080/event/DEFAULT_TENANT/4711
    
    HTTP/1.1 202 Accepted
    Content-Length: 0

## Publish an Event (authenticated Gateway)

* URI: `/event/${tenantId}/${deviceId}`
* Method: `PUT`
* Request Headers:
  * (required) `Authorization`: The gateway's *auth-id* and plain text password encoded according to the [Basic HTTP authentication scheme](https://tools.ietf.org/html/rfc7617).
  * (required) `Content-Type`: The type of payload contained in the body.
* Request Body:
  * (required) Arbitrary payload encoded according to the given content type.
* Status Codes:
  * 202 (Accepted): The event has been accepted and put to a persistent store for delivery to consumers.
  * 400 (Bad Request): The request cannot be processed because the content type header is missing or the request body is empty.
  * 403 (Forbidden): The request cannot be processed because the device's registration status cannot be asserted, i.e. the given device either does not belong to the given tenant or is disabled.
  * 503 (Service Unavailable): The request cannot be processed because there is no consumer of telemetry data for the given tenant connected to Hono.

This resource can be used by *gateway* components to publish data *on behalf of* other devices which do not connect to a protocol adapter directly but instead are connected to the gateway, e.g. using some low-bandwidth radio based technology like [SigFox](https://www.sigfox.com) or [LoRa](https://www.lora-alliance.org/). In this case the credentials provided by the gateway during connection establishment with the protocol adapter are used to authenticate the gateway whereas the parameters from the URI are used to identify the device that the gateway publishes data for.

The protocol adapter checks the gateway's authority to publish data on behalf of the device implicitly by means of retrieving a *registration assertion* for the device from the [configured Device Registration service]({{< relref "#device-registration-service-connection-configuration" >}}).

**Examples**

Publish some JSON data for device `4712` via gateway `gw-1`:

    $ curl -i -X PUT -u gw@DEFAULT_TENANT:gw-secret -H 'Content-Type: application/json' \
    $ --data-binary '{"temp": 5}' http://127.0.0.1:8080/event/DEFAULT_TENANT/4712
    
    HTTP/1.1 202 Accepted
    Content-Length: 0

**NB**: The example above assumes that a gateway device with ID `gw-1` has been registered with `hashed-password` credentials with *auth-id* `gw` and password `gw-secret`.

## Downstream Meta Data

The adapter includes the following meta data in the application properties of messages being sent downstream:

| Name               | Type      | Description                                                     |
| :----------------- | :-------- | :-------------------------------------------------------------- |
| *orig_adapter*     | *string*  | Contains the adapter's *type name* which can be used by downstream consumers to determine the protocol adapter that the message has been received over. The HTTP adapter's type name is `hono-http`. |
| *orig_address*     | *string*  | Contains the (relative) URI that the device has originally posted the data to. |

The adapter also considers [*defaults* registered for the device]({{< relref "api/Device-Registration-API.md#payload-format" >}}). For each default value the adapter checks if a corresponding property is already set on the message and if not, sets the message's property to the registered default value or adds a corresponding application property.

Note that of the standard AMQP 1.0 message properties only the *content-type* can be set this way to a registered default value.

## Tenant specific Configuration

The adapter uses the [Tenant API]({{< relref "api/Tenant-API.md#get-tenant-information" >}}) to retrieve *tenant specific configuration* for adapter type `hono-http`.
The following properties are (currently) supported:

| Name               | Type       | Default Value | Description                                                     |
| :----------------- | :--------- | :------------ | :-------------------------------------------------------------- |
| *enabled*          | *boolean*  | `true`       | If set to `false` the adapter will reject all data from devices belonging to the tenant. |
