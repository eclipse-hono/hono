+++
title = "HTTP Adapter"
weight = 210
+++

The HTTP protocol adapter exposes an HTTP based API for Eclipse Hono&trade;'s Telemetry and Event endpoints.
<!--more-->

## Device Authentication

The HTTP adapter by default requires clients (devices or gateway components) to authenticate during connection establishment. The adapter supports both the [Basic HTTP authentication scheme](https://tools.ietf.org/html/rfc7617) as well as client certificate based authentication as part of a TLS handshake for that purpose.

The adapter tries to authenticate the device using these mechanisms in the following order

### Client Certificate

When a device uses a client certificate for authentication during the TLS handshake, the adapter tries to determine the tenant that the device belongs to, based on the *issuer DN* contained in the certificate. In order for the lookup to succeed, the tenant's trust anchor needs to be configured by means of [registering the trusted certificate authority]({{< relref "api/Tenant-API.md#request-payload" >}}). The device's client certificate will then be validated using the registered trust anchor, thus implicitly establishing the tenant that the device belongs to. In a second step, the adapter then uses the Credentials API's *get* operation with the client certificate's *subject DN* and `x509-cert` as the *type* of secret as query parameters.

NB: The HTTP adapter needs to be [configured for TLS]({{< relref "admin-guide/secure_communication.md#http-adapter" >}}) in order to support this mechanism.

### HTTP Basic Auth

The *username* provided in the header must have the form *auth-id@tenant*, e.g. `sensor1@DEFAULT_TENANT`. The adapter verifies the credentials provided by the client against the credentials that the [configured Credentials service]({{< relref "admin-guide/http-adapter-config.md#credentials-service-configuration" >}}) has on record for the client. The adapter uses the Credentials API's *get* operation to retrieve the credentials on record with the *tenant* and *auth-id* provided by the device in the *username* and `hashed-password` as the *type* of secret as query parameters.

When running the Hono example installation as described in the [Getting Started guide]({{< relref "getting-started.md" >}}), the demo Credentials service comes pre-configured with a `hashed-password` secret for devices `4711` and `gw-1` of tenant `DEFAULT_TENANT` having *auth-ids* `sensor1` and `gw1` and (hashed) *passwords* `hono-secret` and `gw-secret` respectively. These credentials are used in the following examples illustrating the usage of the adapter. Please refer to the [Credentials API]({{< relref "api/Credentials-API.md#standard-credential-types" >}}) for details regarding the different types of secrets.

NB: There is a subtle difference between the *device identifier* (*device-id*) and the *auth-id* a device uses for authentication. See [Device Identity]({{< relref "concepts/device-identity.md" >}}) for a discussion of the concepts.

## Publish Telemetry Data (authenticated Device)

* URI: `/telemetry`
* Method: `POST`
* Request Headers:
  * (optional) `Authorization`: The device's *auth-id* and plain text password encoded according to the [Basic HTTP authentication scheme](https://tools.ietf.org/html/rfc7617). If not set, the adapter expects the device to present a client certificate as part of the TLS handshake during connection establishment.
  * (required) `Content-Type`: The type of payload contained in the body.
  * (optional) `hono-ttd`: The number of seconds the device will wait for the response.
  * (optional) `QoS-Level`: The QoS level for publishing telemetry messages. Only QoS 1 is supported by the adapter.
* Request Body:
  * (required) Arbitrary payload encoded according to the given content type.
* Status Codes:
  * 202 (Accepted): The telemetry data has been accepted for processing. Note that if the `QoS-Level` request header is missing, the adapter does not *guarantee* successful delivery to potential consumers.
  However, if the QoS-Level header is set to `AT_LEAST_ONCE(1)`, then the adapter waits for the message to be delivered and accepted by a downstream peer before responding with a 202 status code to the device.
  * 400 (Bad Request): The request cannot be processed. Possible reasons for this might be that:
        * The content type header is missing.
        * The request body is empty.
        * The QoS header value is invalid.
  * 401 (Unauthorized): The request cannot be processed because the request does not contain valid credentials.
  * 403 (Forbidden): The request cannot be processed because the device's registration status cannot be asserted. Possible reasons for this might be:
        * The given tenant is not allowed to use this protocol adapter.
        * The given device is disabled.
  * 503 (Service Unavailable): The request cannot be processed because there is no consumer of telemetry data for the given tenant connected to Hono.

This is the preferred way for devices to publish telemetry data. It is available only if the protocol adapter is configured to require devices to authenticate (which is the default).

**Examples**

Publish some JSON data for device `4711`:

    $ curl -i -X POST -u sensor1@DEFAULT_TENANT:hono-secret -H 'Content-Type: application/json' \
    $ --data-binary '{"temp": 5}' http://127.0.0.1:8080/telemetry

    HTTP/1.1 202 Accepted
    Content-Length: 0

Publish some JSON data for device `4711` using QoS level `AT_LEAST_ONCE`:

    $ curl -i -X POST -u sensor1@DEFAULT_TENANT:hono-secret -H 'Content-Type: application/json' -H 'QoS-Level: 1' \
    $ --data-binary '{"temp": 5}' http://localhost:8080/telemetry

    HTTP/1.1 202 Accepted
    Content-Length: 0

Publish some JSON data for device `4711` using an invalid QoS level:

    $ curl -i -X POST -u sensor1@DEFAULT_TENANT:hono-secret -H 'Content-Type: application/json' -H 'QoS-Level: 2' \
    $ --data-binary '{"temp": 5}' http://localhost:8080/telemetry

    HTTP/1.1 400 Bad QoS Header Value
    Content-Length: 20

## Publish Telemetry Data (unauthenticated Device)

* URI: `/telemetry/${tenantId}/${deviceId}`
* Method: `PUT`
* Request Headers:
  * (required) `Content-Type`: The type of payload contained in the body.
  * (optional) `hono-ttd`: The number of seconds the device will wait for the response.
  * (optional) `QoS-Level`: The QoS level for publishing telemetry messages. Only QoS 1 is supported by the adapter.
* Request Body:
  * (required) Arbitrary payload encoded according to the given content type.
* Status Codes:
  * 202 (Accepted): The telemetry data has been accepted for processing. Note that if the `QoS-Level` request header is missing, the adapter does not *guarantee* successful delivery to potential consumers.
  However, if the QoS-Level header is set to `AT_LEAST_ONCE(1)`, then the adapter waits for the message to be delivered and accepted by a downstream peer before responding with a 202 status code to the device.
  * 400 (Bad Request): The request cannot be processed. Possible reasons for this might be that:
        * The content type header is missing.
        * The request body is empty.
        * The QoS header value is invalid.
  * 403 (Forbidden): The request cannot be processed because the device's registration status cannot be asserted. Possible reasons for this might be:
        * The given tenant is not allowed to use this protocol adapter.
        * The given device does not belong to the given tenant.
        * The given device is disabled.
  * 503 (Service Unavailable): The request cannot be processed because there is no consumer of telemetry data for the given tenant connected to Hono.

This resource MUST be used by devices that have not authenticated to the protocol adapter. Note that this requires the `HONO_HTTP_AUTHENTICATION_REQUIRED` configuration property to be explicitly set to `false`.

**Examples**

Publish some JSON data for device `4711`:

    $ curl -i -X PUT -H 'Content-Type: application/json' \
    $ --data-binary '{"temp": 5}' http://127.0.0.1:8080/telemetry/DEFAULT_TENANT/4711

    HTTP/1.1 202 Accepted
    Content-Length: 0

Publish some JSON data for device `4711` using QoS level `AT_LEAST_ONCE`:

    $ curl -i -X PUT -H 'Content-Type: application/json' -H 'QoS-Level: 1' \
    $ --data-binary '{"temp": 5}' http://127.0.0.1:8080/telemetry/DEFAULT_TENANT/4711

    HTTP/1.1 202 Accepted
    Content-Length: 0

Publish some JSON data for device `4711` using an invalid QoS level:

    $ curl -i -X PUT -H 'Content-Type: application/json' -H 'QoS-Level: 2' \
    $ --data-binary '{"temp": 5}' http://127.0.0.1:8080/telemetry/DEFAULT_TENANT/4711

    HTTP/1.1 400 Bad QoS Header Value
    Content-Length: 20

## Publish Telemetry Data (authenticated Gateway)

* URI: `/telemetry/${tenantId}/${deviceId}`
* Method: `PUT`
* Request Headers:
  * (optional) `Authorization`: The gateway's *auth-id* and plain text password encoded according to the [Basic HTTP authentication scheme](https://tools.ietf.org/html/rfc7617). If not set, the adapter expects the gateway to present a client certificate as part of the TLS handshake during connection establishment.
* Request Headers:
  * (required) `Content-Type`: The type of payload contained in the body.
  * (optional) `hono-ttd`: The number of seconds the device will wait for the response.
  * (optional) `QoS-Level`: The QoS level for publishing telemetry messages. Only QoS 1 is supported by the adapter.
* Request Body:
  * (required) Arbitrary payload encoded according to the given content type.
* Status Codes:
  * 202 (Accepted): The telemetry data has been accepted for processing. Note that if the `QoS-Level` request header is missing, the adapter does not *guarantee* successful delivery to potential consumers.
  However, if the QoS-Level header is set to `AT_LEAST_ONCE(1)`, then the adapter waits for the message to be delivered and accepted by a downstream peer before responding with a 202 status code to the device.
  * 400 (Bad Request): The request cannot be processed. Possible reasons for this might be that:
        * The content type header is missing
        * The request body is empty
        * The QoS header value is invalid
  * 403 (Forbidden): The request cannot be processed because the device's registration status cannot be asserted. Possible reasons for this might be:
        * The given tenant is not allowed to use this protocol adapter.
        * The given device does not belong to the given tenant.
        * The given device is disabled.

  * 503 (Service Unavailable): The request cannot be processed because there is no consumer of telemetry data for the given tenant connected to Hono.

This resource can be used by *gateway* components to publish data *on behalf of* other devices which do not connect to a protocol adapter directly but instead are connected to the gateway, e.g. using some low-bandwidth radio based technology like [SigFox](https://www.sigfox.com) or [LoRa](https://www.lora-alliance.org/). In this case the credentials provided by the gateway during connection establishment with the protocol adapter are used to authenticate the gateway whereas the parameters from the URI are used to identify the device that the gateway publishes data for.

The protocol adapter checks the gateway's authority to publish data on behalf of the device implicitly by means of retrieving a *registration assertion* for the device from the [configured Device Registration service]({{< relref "#device-registration-service-connection-configuration" >}}).

**Examples**

Publish some JSON data for device `4712` via gateway `gw-1`:

    $ curl -i -X PUT -u gw@DEFAULT_TENANT:gw-secret -H 'Content-Type: application/json' \
    $ --data-binary '{"temp": 5}' http://127.0.0.1:8080/telemetry/DEFAULT_TENANT/4712

    HTTP/1.1 202 Accepted
    Content-Length: 0

Publish some JSON data for device `4712` using QoS level `AT_LEAST_ONCE`:

    $ curl -i -X PUT -u gw@DEFAULT_TENANT:gw-secret -H 'Content-Type: application/json' -H 'QoS-Level: 1' \
    $ --data-binary '{"temp": 5}' http://127.0.0.1:8080/telemetry/DEFAULT_TENANT/4712

    HTTP/1.1 202 Accepted
    Content-Length: 0

Publish some JSON data for device `4712` using an invalid QoS level:

    $ curl -i -X POST -u gw@DEFAULT_TENANT:gw-secret -H 'Content-Type: application/json' -H 'QoS-Level: 2' \
    $ --data-binary '{"temp": 5}' http://localhost:8080/telemetry/DEFAULT_TENANT/4712

    HTTP/1.1 400 Bad QoS Header Value
    Content-Length: 20

**NB**: The example above assumes that a gateway device with ID `gw-1` has been registered with `hashed-password` credentials with *auth-id* `gw` and password `gw-secret`.

## Publish an Event (authenticated Device)

* URI: `/event`
* Method: `POST`
* Request Headers:
  * (optional) `Authorization`: The device's *auth-id* and plain text password encoded according to the [Basic HTTP authentication scheme](https://tools.ietf.org/html/rfc7617). If not set, the adapter expects the device to present a client certificate as part of the TLS handshake during connection establishment.
  * (required) `Content-Type`: The type of payload contained in the body.
  * (optional) `hono-ttd`: The number of seconds the device will wait for the response.
* Request Body:
  * (required) Arbitrary payload encoded according to the given content type.
* Status Codes:
  * 202 (Accepted): The event has been accepted and put to a persistent store for delivery to consumers.
  * 400 (Bad Request): The request cannot be processed because the content type header is missing or the request body is empty.
  * 401 (Unauthorized): The request cannot be processed because the request does not contain valid credentials.
  * 403 (Forbidden): The request cannot be processed because the device's registration status cannot be asserted. Possible reasons for this might be:
        * The given tenant is not allowed to use this protocol adapter.
        * The given device is disabled.
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
  * (optional) `hono-ttd`: The number of seconds the device will wait for the response.
* Request Body:
  * (required) Arbitrary payload encoded according to the given content type.
* Status Codes:
  * 202 (Accepted): The event has been accepted and put to a persistent store for delivery to consumers.
  * 400 (Bad Request): The request cannot be processed because the content type header is missing or the request body is empty.
  * 403 (Forbidden): The request cannot be processed because the device's registration status cannot be asserted. Possible reasons for this might be:
        * The given tenant is not allowed to use this protocol adapter.
        * The given device does not belong to the given tenant.
        * The given device is disabled.
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
  * (optional) `Authorization`: The gateway's *auth-id* and plain text password encoded according to the [Basic HTTP authentication scheme](https://tools.ietf.org/html/rfc7617). If not set, the adapter expects the gateway to present a client certificate as part of the TLS handshake during connection establishment.
  * (required) `Content-Type`: The type of payload contained in the body.
  * (optional) `hono-ttd`: The number of seconds the device will wait for the response.
* Request Body:
  * (required) Arbitrary payload encoded according to the given content type.
* Status Codes:
  * 202 (Accepted): The event has been accepted and put to a persistent store for delivery to consumers.
  * 400 (Bad Request): The request cannot be processed because the content type header is missing or the request body is empty.
  * 403 (Forbidden): The request cannot be processed because the device's registration status cannot be asserted. Possible reasons for this might be:
        * The given tenant is not allowed to use this protocol adapter.
        * The given device does not belong to the given tenant.
        * The given device is disabled.
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

## Specifying the time a device will wait for a response

The adapter lets devices specify the number of seconds they will wait for a response by setting a header or a query parameter.

{{% note %}}
This feature has been added in Hono 0.6. Previous versions of the adapter do not support it.
{{% /note %}}

The parameter is available for all variants `authenticated`, `unauthenticated` and `authenticated gateway`. For simplification
only the `authenticated` URI is used below.

### Use a specific HTTP header

The optional header `hono-ttd` can be set for any downstream message.

Example:

    $ curl -i -X POST -u sensor1@DEFAULT_TENANT:hono-secret -H 'Content-Type: application/json' -H 'hono-ttd: 60' \
    $ --data-binary '{"temp": 5}' http://127.0.0.1:8080/telemetry

    HTTP/1.1 202 Accepted
    Content-Length: 0

### Use a query parameter

Alternatively the value for `hono-ttd` can be set by using a query parameter.

Example:

    $ curl -i -X POST -u sensor1@DEFAULT_TENANT:hono-secret -H 'Content-Type: application/json' \
    $ --data-binary '{"temp": 5}' http://127.0.0.1:8080/telemetry?hono-ttd=60

    HTTP/1.1 202 Accepted
    Content-Length: 0

## Sending a response to a previously received command

{{% note %}}
This feature has been added in Hono 0.7. Previous versions of the adapter do not support it.
{{% /note %}}


### Sending a response (authenticated Device)

* URI: `/control/res/${commandRequestId}` or `/control/res/${commandRequestId}?hono-cmd-status=${status}`
* Method: `POST`
* Request Headers:
  * (optional) `Authorization`: The device's *auth-id* and plain text password encoded according to the [Basic HTTP authentication scheme](https://tools.ietf.org/html/rfc7617). If not set, the adapter expects the device to present a client certificate as part of the TLS handshake during connection establishment.
  * (optional) `hono-cmd-status`: The status of the command execution. If not set, the adapter expects that the URI contains it
    as request parameter at the end.
* Request Body:
  * (optional) Arbitrary payload. The type of the payload should be determined by the command that was sent to the device previously
    and is unknown to Hono.
* Status Codes:
  * 202 (Accepted): The response has been accepted and was successfully delivered to the application.
  * 400 (Bad Request): The request cannot be processed because the command status is missing.
  * 401 (Unauthorized): The request cannot be processed because the request does not contain valid credentials.  
  * 403 (Forbidden): The request cannot be processed because the device's registration status cannot be asserted. Possible reasons for this might be:
        * The given tenant is not allowed to use this protocol adapter.
        * The given device is disabled.
  * 503 (Service Unavailable): The request cannot be processed because there is no application listening for a reply to 
    this `commandRequestId`. One possible reason can be that the response is sent later as the timeout the application has set for waiting
    for it.

This is the preferred way for devices to respond to commands. It is available only if the protocol adapter is configured to require devices to authenticate (which is the default).

**Example**

Send a response to a previously received command with the command-request-id `2fcmd-client-bed7bdad-facc-4f72-9b8b-da6e5be8db32dd09ffe2-fa96-4eb1-975f-1e4adb7d191c` for device `4711`:

    $ curl -i -X POST -u sensor1@DEFAULT_TENANT:hono-secret \
    $ --data-binary '{"brightness-changed": true}' \
    $ http://127.0.0.1:8080/control/res/2fcmd-client-bed7bdad-facc-4f72-9b8b-da6e5be8db32dd09ffe2-fa96-4eb1-975f-1e4adb7d191c?hono-cmd-status=200

    HTTP/1.1 202 Accepted
    Content-Length: 0

 

## Sending a response (unauthenticated Device)

* URI: `/control/res/${tenantId}/${deviceId}/${commandRequestId}` or `/control/res/${tenantId}/${deviceId}/${commandRequestId}?hono-cmd-status=${status}`
* Method: `PUT`
* Request Headers:
  * (optional) `hono-cmd-status`: The status of the command execution. If not set, the adapter expects that the URI contains it
    as request parameter at the end.
* Request Body:
  * (optional) Arbitrary payload. The type of the payload should be determined by the command that was sent to the device previously
    and is unknown to Hono.
* Status Codes:
  * 202 (Accepted): The event has been accepted and put to a persistent store for delivery to consumers.
  * 400 (Bad Request): The request cannot be processed because the command status is missing.
  * 403 (Forbidden): The request cannot be processed because the device's registration status cannot be asserted. Possible reasons for this might be:
        * The given tenant is not allowed to use this protocol adapter.
        * The given device does not belong to the given tenant.
        * The given device is disabled.
  * 503 (Service Unavailable): The request cannot be processed because there is no application listening for a reply to 
    this `commandRequestId`. One possible reason can be that the response is sent later as the timeout the application has set to wait
    for it.

This resource MUST be used by devices that have not authenticated to the protocol adapter. Note that this requires the `HONO_HTTP_AUTHENTICATION_REQUIRED` configuration property to be explicitly set to `false`.


**Examples**

Send a response to a previously received command with the command-request-id `2fcmd-client-bed7bdad-facc-4f72-9b8b-da6e5be8db32dd09ffe2-fa96-4eb1-975f-1e4adb7d191c` for the unauthenticated device `4711`:

    $ curl -i -X PUT --data-binary '{"brightness-changed": true}' \
    $ http://127.0.0.1:8080/control/res/DEFAULT_TENANT/4711/2fcmd-client-bed7bdad-facc-4f72-9b8b-da6e5be8db32dd09ffe2-fa96-4eb1-975f-1e4adb7d191c?hono-cmd-status=200

    HTTP/1.1 202 Accepted
    Content-Length: 0


## Sending a response (authenticated Gateway)

* URI: `/control/res/${tenantId}/${deviceId}/${commandRequestId}` or `/control/res/${tenantId}/${deviceId}/${commandRequestId}?hono-cmd-status=${status}`
* Method: `PUT`
* Request Headers:
  * (optional) `Authorization`: The gateway's *auth-id* and plain text password encoded according to the [Basic HTTP authentication scheme](https://tools.ietf.org/html/rfc7617). If not set, the adapter expects the gateway to present a client certificate as part of the TLS handshake during connection establishment.
  * (optional) `hono-cmd-status`: The status of the command execution. If not set, the adapter expects that the URI contains it
    as request parameter at the end.
* Request Body:
  * (optional) Arbitrary payload. The type of the payload should be determined by the command that was sent to the device previously
    and is unknown to Hono.
* Status Codes:
  * 202 (Accepted): The event has been accepted and put to a persistent store for delivery to consumers.
  * 400 (Bad Request): The request cannot be processed because the command status is missing.
  * 403 (Forbidden): The request cannot be processed because the device's registration status cannot be asserted. Possible reasons for this might be:
        * The given tenant is not allowed to use this protocol adapter.
        * The given device does not belong to the given tenant.
        * The given device is disabled.
  * 503 (Service Unavailable): The request cannot be processed because there is no application listening for a reply to 
    this `commandRequestId`. One possible reason can be that the response is sent later as the timeout the application has set to wait
    for it.

This resource can be used by *gateway* components to send the response to a command *on behalf of* other devices which do not connect to a protocol adapter directly but instead are connected to the gateway, e.g. using some low-bandwidth radio based technology like [SigFox](https://www.sigfox.com) or [LoRa](https://www.lora-alliance.org/). In this case the credentials provided by the gateway during connection establishment with the protocol adapter are used to authenticate the gateway whereas the parameters from the URI are used to identify the device that the gateway publishes data for.

The protocol adapter checks the gateway's authority to send responses to a command on behalf of the device implicitly by means of retrieving a *registration assertion* for the device from the [configured Device Registration service]({{< relref "#device-registration-service-connection-configuration" >}}).

**Examples**

Publish some JSON data for device `4712` via gateway `gw-1`:

Send a response to a previously received command with the command-request-id `2fcmd-client-bed7bdad-facc-4f72-9b8b-da6e5be8db32dd09ffe2-fa96-4eb1-975f-1e4adb7d191c` for the unauthenticated device `4711`
 via gateway `gw-1`:

    $ curl -i -X PUT -u gw@DEFAULT_TENANT:gw-secret --data-binary '{"brightness-changed": true}' \
    $ http://127.0.0.1:8080/control/res/DEFAULT_TENANT/4712/2fcmd-client-bed7bdad-facc-4f72-9b8b-da6e5be8db32dd09ffe2-fa96-4eb1-975f-1e4adb7d191c?hono-cmd-status=200

    HTTP/1.1 202 Accepted
    Content-Length: 0


**NB**: The example above assumes that a gateway device with ID `gw-1` has been registered with `hashed-password` credentials with *auth-id* `gw` and password `gw-secret`.




## Tenant specific Configuration

The adapter uses the [Tenant API]({{< relref "api/Tenant-API.md#get-tenant-information" >}}) to retrieve *tenant specific configuration* for adapter type `hono-http`.
The following properties are (currently) supported:

| Name               | Type       | Default Value | Description                                                     |
| :----------------- | :--------- | :------------ | :-------------------------------------------------------------- |
| *enabled*          | *boolean*  | `true`       | If set to `false` the adapter will reject all data from devices belonging to the tenant. |
