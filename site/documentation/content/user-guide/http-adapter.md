+++
title = "HTTP Adapter"
weight = 210
+++

The HTTP protocol adapter exposes HTTP based endpoints for Eclipse Hono&trade;'s south bound Telemetry, Event and Command & Control APIs.
<!--more-->

## Device Authentication

The HTTP adapter by default requires clients (devices or gateway components) to authenticate during connection establishment. The adapter supports both the [Basic HTTP authentication scheme](https://tools.ietf.org/html/rfc7617) as well as client certificate based authentication as part of a TLS handshake for that purpose.

The adapter tries to authenticate the device using these mechanisms in the following order

### Client Certificate

When a device uses a client certificate for authentication during the TLS handshake, the adapter tries to determine the tenant that the device belongs to, based on the *issuer DN* contained in the certificate. In order for the lookup to succeed, the tenant's trust anchor needs to be configured by means of [registering the trusted certificate authority]({{< relref "/api/tenant#tenant-information-format" >}}). The device's client certificate will then be validated using the registered trust anchor, thus implicitly establishing the tenant that the device belongs to. In a second step, the adapter then uses the Credentials API's *get* operation with the client certificate's *subject DN* as the *auth-id* and `x509-cert` as the *type* of secret as query parameters.

**NB** The HTTP adapter needs to be [configured for TLS]({{< relref "/admin-guide/secure_communication.md#http-adapter" >}}) in order to support this mechanism.

### HTTP Basic Auth

The *username* provided in the header must have the form *auth-id@tenant*, e.g. `sensor1@DEFAULT_TENANT`. The adapter verifies the credentials provided by the client against the credentials that the [configured Credentials service]({{< relref "/admin-guide/http-adapter-config.md#credentials-service-connection-configuration" >}}) has on record for the client. The adapter uses the Credentials API's *get* operation to retrieve the credentials on record with the *tenant* and *auth-id* provided by the device in the *username* and `hashed-password` as the *type* of secret as query parameters.

The examples below refer to devices `4711` and `gw-1` of tenant `DEFAULT_TENANT` using *auth-ids* `sensor1` and `gw1` and corresponding passwords. The example deployment as described in the [Deployment Guides]({{< relref "deployment" >}}) comes pre-configured with the corresponding entities in its device registry component.
Please refer to the [Credentials API]({{< relref "/api/credentials#standard-credential-types" >}}) for details regarding the different types of secrets.

**NB** There is a subtle difference between the *device identifier* (*device-id*) and the *auth-id* a device uses for authentication. See [Device Identity]({{< relref "/concepts/device-identity.md" >}}) for a discussion of the concepts.

## Message Limits

Before accepting any telemetry or event or command messages, the HTTP adapter verifies that the configured [message limit] ({{< relref "/concepts/resource-limits.md" >}}) is not exceeded. If the limit is exceeded then the incoming message is discarded with the status code `429 Too Many Requests`. 

## Publish Telemetry Data (authenticated Device)

* URI: `/telemetry`
* Method: `POST`
* Request Headers:
  * (optional) `authorization`: The device's *auth-id* and plain text password encoded according to the [Basic HTTP authentication scheme](https://tools.ietf.org/html/rfc7617). If not set, the adapter expects the device to present a client certificate as part of the TLS handshake during connection establishment.
  * (required) `content-type`: The type of payload contained in the request body.
  * (optional) `hono-ttd`: The number of seconds the device will wait for the response.
  * (optional) `qos-level`: The QoS level for publishing telemetry messages. The adapter supports *at most once* (`0`) and *at least once* (`1`) QoS levels. The default value of `0` is assumed if this header is omitted.
* Request Body:
  * (required) Arbitrary payload encoded according to the given content type.
* Response Headers:
  * (optional) `content-type`: A media type describing the semantics and format of payload contained in the response body. This header will only be present if the response contains a command to be executed by the device which requires input data.
  * (optional) `hono-command`: The name of the command to execute. This header will only be present if the response contains a command to be executed by the device.
  * (optional) `hono-cmd-req-id`: An identifier that the device must include in its response to a command. This header will only be present if the response contains a command to be executed by the device.
  * (optional) `hono-cmd-target-device`: The id of the device that shall execute the command. This header will only be present if the response contains a command to be executed by the device and if the response goes to a gateway that acts on behalf of the target device.
* Response Body:
  * (optional) Arbitrary data serving as input to a command to be executed by the device, if status code is 200.
  * (optional) Error details, if status code is >= 400.
* Status Codes:
  * 200 (OK): The telemetry data has been accepted for processing. The response contains a command for the device to execute.
  * 202 (Accepted): The telemetry data has been accepted for processing. Note that if the `qos-level` request header is omitted (*at most once* semantics),
    this status code does **not** mean that the message has been delivered to any potential consumer. However, if the QoS level header is set to `1`
    (*at least once* semantics), then the adapter waits for the message to be delivered and accepted by a downstream consumer before responding with
    this status code.
  * 400 (Bad Request): The request cannot be processed. Possible reasons for this include:
        * The content type header is missing.
        * The request body is empty.
        * The QoS header value is invalid.
  * 401 (Unauthorized): The request cannot be processed because the request does not contain valid credentials.
  * 403 (Forbidden): The request cannot be processed because the device's registration status cannot be asserted. Possible reasons for this include:
        * The given tenant is not allowed to use this protocol adapter.
  * 404 (Not Found): The request cannot be processed because the device is disabled or does not exist.
  * 429 (Too Many Requests): The request cannot be processed because the tenant's message limit for the current period is exceeded.
  * 503 (Service Unavailable): The request cannot be processed because there is no consumer of telemetry data for the given tenant connected to Hono.

This is the preferred way for devices to publish telemetry data. It is available only if the protocol adapter is configured to require devices to authenticate (which is the default).

If the `hono-ttd` header is set in order to receive a command and if the authenticated device is actually a gateway, the returned command will be the first command that the northbound application has sent to either the gateway itself or (if not using the legacy control endpoint) to *any* device that has last sent a telemetry or event message via this gateway.

**Examples**

Publish some JSON data for device `4711`:

~~~sh
curl -i -u sensor1@DEFAULT_TENANT:hono-secret -H 'content-type: application/json' --data-binary '{"temp": 5}' http://127.0.0.1:8080/telemetry

HTTP/1.1 202 Accepted
content-length: 0
~~~

Publish some JSON data for device `4711` using *at least once* QoS:

~~~sh
curl -i -u sensor1@DEFAULT_TENANT:hono-secret -H 'content-type: application/json' -H 'qos-level: 1' --data-binary '{"temp": 5}' http://localhost:8080/telemetry

HTTP/1.1 202 Accepted
content-length: 0
~~~

Publish some JSON data for device `4711`, indicating that the device will wait for 10 seconds to receive the response:

~~~sh
curl -i -u sensor1@DEFAULT_TENANT:hono-secret -H 'content-type: application/json' -H 'hono-ttd: 10' --data-binary '{"temp": 5}' http://localhost:8080/telemetry

HTTP/1.1 200 OK
hono-command: set
hono-cmd-req-id: 1010a7249aa5-f742-4376-8458-bbfc88c72d92
content-length: 23

{
  "brightness": 87
}
~~~

Publish some JSON data for device `4711` using a client certificate for authentication:

~~~sh
# in base directory of Hono repository:
curl -i --cert demo-certs/certs/device-4711-cert.pem --key demo-certs/certs/device-4711-key.pem --cacert demo-certs/certs/trusted-certs.pem -H 'content-type: application/json' --data-binary '{"temp": 5}' https://localhost:8443/telemetry

HTTP/1.1 202 Accepted
content-length: 0
~~~

**NB** The example above assumes that the HTTP adapter is [configured for TLS]({{< relref "/admin-guide/secure_communication.md#http-adapter" >}}) and the secure port is used.

## Publish Telemetry Data (unauthenticated Device)

* URI: `/telemetry/${tenantId}/${deviceId}`
* Method: `PUT`
* Request Headers:
  * (required) `content-type`: The type of payload contained in the request body.
  * (optional) `hono-ttd`: The number of seconds the device will wait for the response.
  * (optional) `qos-level`: The QoS level for publishing telemetry messages. The adapter supports *at most once* (`0`) and *at least once* (`1`) QoS levels. The default value of `0` is assumed if this header is omitted.
* Request Body:
  * (required) Arbitrary payload encoded according to the given content type.
* Response Headers:
  * (optional) `content-type`: A media type describing the semantics and format of payload contained in the response body. This header will only be present if the response contains a command to be executed by the device which requires input data.
  * (optional) `hono-command`: The name of the command to execute. This header will only be present if the response contains a command to be executed by the device.
  * (optional) `hono-cmd-req-id`: An identifier that the device must include in its response to a command. This header will only be present if the response contains a command to be executed by the device.
* Response Body:
  * (optional) Arbitrary data serving as input to a command to be executed by the device, if status code is 200.
  * (optional) Error details, if status code is >= 400.
* Status Codes:
  * 200 (OK): The telemetry data has been accepted for processing. The response contains a command for the device to execute.
  * 202 (Accepted): The telemetry data has been accepted for processing. Note that if the `qos-level` request header is omitted (*at most once* semantics),
    this status code does **not** mean that the message has been delivered to any potential consumer. However, if the QoS level header is set to `1`
    (*at least once* semantics), then the adapter waits for the message to be delivered and accepted by a downstream consumer before responding with
    this status code.
  * 400 (Bad Request): The request cannot be processed. Possible reasons for this include:
        * The content type header is missing.
        * The request body is empty.
        * The QoS header value is invalid.
  * 403 (Forbidden): The request cannot be processed because the device's registration status cannot be asserted. Possible reasons for this include:
        * The given tenant is not allowed to use this protocol adapter.
        * The given device does not belong to the given tenant.
  * 404 (Not Found): The request cannot be processed because the device is disabled or does not exist.
  * 429 (Too Many Requests): The request cannot be processed because the tenant's message limit for the current period is exceeded.
  * 503 (Service Unavailable): The request cannot be processed because there is no consumer of telemetry data for the given tenant connected to Hono.

This resource MUST be used by devices that have not authenticated to the protocol adapter. Note that this requires the `HONO_HTTP_AUTHENTICATION_REQUIRED` configuration property to be explicitly set to `false`.

**Examples**

Publish some JSON data for device `4711`:

~~~sh
curl -i -X PUT -H 'content-type: application/json' --data-binary '{"temp": 5}' http://127.0.0.1:8080/telemetry/DEFAULT_TENANT/4711

HTTP/1.1 202 Accepted
content-length: 0
~~~

Publish some JSON data for device `4711` using *at least once* QoS:

~~~sh
curl -i -X PUT -H 'content-type: application/json' -H 'qos-level: 1' --data-binary '{"temp": 5}' http://127.0.0.1:8080/telemetry/DEFAULT_TENANT/4711

HTTP/1.1 202 Accepted
content-length: 0
~~~

Publish some JSON data for device `4711`, indicating that the device will wait for 10 seconds to receive the response:

~~~sh
curl -i -u sensor1@DEFAULT_TENANT:hono-secret -H 'content-type: application/json' -H 'hono-ttd: 10' --data-binary '{"temp": 5}' http://localhost:8080/telemetry

HTTP/1.1 200 OK
hono-command: set
hono-cmd-req-id: 1010a7249aa5-f742-4376-8458-bbfc88c72d92
content-length: 23

{
  "brightness": 87
}
~~~

## Publish Telemetry Data (authenticated Gateway)

* URI: `/telemetry/${tenantId}/${deviceId}`
* Method: `PUT`
* Request Headers:
  * (optional) `authorization`: The gateway's *auth-id* and plain text password encoded according to the [Basic HTTP authentication scheme](https://tools.ietf.org/html/rfc7617). If not set, the adapter expects the gateway to present a client certificate as part of the TLS handshake during connection establishment.
  * (required) `content-type`: The type of payload contained in the request body.
  * (optional) `hono-ttd`: The number of seconds the device will wait for the response.
  * (optional) `qos-level`: The QoS level for publishing telemetry messages. The adapter supports *at most once* (`0`) and *at least once* (`1`) QoS levels. The default value of `0` is assumed if this header is omitted.
* Request Body:
  * (required) Arbitrary payload encoded according to the given content type.
* Response Headers:
  * (optional) `content-type`: A media type describing the semantics and format of payload contained in the response body. This header will only be present if the response contains a command to be executed by the device which requires input data.
  * (optional) `hono-command`: The name of the command to execute. This header will only be present if the response contains a command to be executed by the device.
  * (optional) `hono-cmd-req-id`: An identifier that the device must include in its response to a command. This header will only be present if the response contains a command to be executed by the device.
  * (optional) `hono-cmd-target-device`: The id of the device that shall execute the command. This header will only be present if the response contains a command to be executed by the device.
* Response Body:
  * (optional) Arbitrary data serving as input to a command to be executed by the device, if status code is 200.
  * (optional) Error details, if status code is >= 400.
* Status Codes:
  * 200 (OK): The telemetry data has been accepted for processing. The response contains a command for the device to execute.
  * 202 (Accepted): The telemetry data has been accepted for processing. Note that if the `qos-level` request header is omitted (*at most once* semantics),
    this status code does **not** mean that the message has been delivered to any potential consumer. However, if the QoS level header is set to `1`
    (*at least once* semantics), then the adapter waits for the message to be delivered and accepted by a downstream consumer before responding with
    this status code.
  * 400 (Bad Request): The request cannot be processed. Possible reasons for this include:
        * The content type header is missing.
        * The request body is empty.
        * The QoS header value is invalid.
  * 401 (Unauthorized): The request cannot be processed because the request does not contain valid credentials.
  * 403 (Forbidden): The request cannot be processed because the device's registration status cannot be asserted. Possible reasons for this include:
        * The tenant that the gateway belongs to is not allowed to use this protocol adapter.
        * The device belongs to another tenant than the gateway.
        * The gateway is not authorized to act *on behalf of* the device.
        * The gateway associated with the device is not registered or disabled.
  * 404 (Not Found): The request cannot be processed because the device is disabled or does not exist.
  * 429 (Too Many Requests): The request cannot be processed because the tenant's message limit for the current period is exceeded.
  * 503 (Service Unavailable): The request cannot be processed because there is no consumer of telemetry data for the given tenant connected to Hono.

This resource can be used by *gateway* components to publish data *on behalf of* other devices which do not connect to a protocol adapter directly but instead are connected to the gateway, e.g. using some low-bandwidth radio based technology like [SigFox](https://www.sigfox.com) or [LoRa](https://lora-alliance.org/). In this case the credentials provided by the gateway during connection establishment with the protocol adapter are used to authenticate the gateway whereas the parameters from the URI are used to identify the device that the gateway publishes data for.

The protocol adapter checks the gateway's authority to publish data on behalf of the device implicitly by means of retrieving a *registration assertion* for the device from the [configured Device Registration service]({{< relref "/admin-guide/http-adapter-config#device-registration-service-connection-configuration" >}}).

**Examples**

Publish some JSON data for device `4712`:

~~~sh
curl -i -X PUT -u gw@DEFAULT_TENANT:gw-secret -H 'content-type: application/json' --data-binary '{"temp": 5}' http://127.0.0.1:8080/telemetry/DEFAULT_TENANT/4712

HTTP/1.1 202 Accepted
content-length: 0
~~~

Publish some JSON data for device `4712` using *at least once* QoS:

~~~sh
curl -i -X PUT -u gw@DEFAULT_TENANT:gw-secret -H 'content-type: application/json' -H 'qos-level: 1' --data-binary '{"temp": 5}' http://127.0.0.1:8080/telemetry/DEFAULT_TENANT/4712

HTTP/1.1 202 Accepted
content-length: 0
~~~

Publish some JSON data for device `4712`, indicating that the gateway will wait for 10 seconds to receive the response:

~~~sh
curl -i -X PUT -u gw@DEFAULT_TENANT:gw-secret -H 'content-type: application/json' -H 'hono-ttd: 10' --data-binary '{"temp": 5}' http://localhost:8080/telemetry/DEFAULT_TENANT/4712

HTTP/1.1 200 OK
hono-command: set
hono-cmd-req-id: 1010a7249aa5-f742-4376-8458-bbfc88c72d92
content-length: 23

{
  "brightness": 87
}
~~~

**NB** The example above assumes that a gateway device has been registered with `hashed-password` credentials with *auth-id* `gw` and password `gw-secret` which is authorized to publish data *on behalf of* device `4712`.

## Publish an Event (authenticated Device)

* URI: `/event`
* Method: `POST`
* Request Headers:
  * (optional) `authorization`: The device's *auth-id* and plain text password encoded according to the [Basic HTTP authentication scheme](https://tools.ietf.org/html/rfc7617). If not set, the adapter expects the device to present a client certificate as part of the TLS handshake during connection establishment.
  * (required) `content-type`: The type of payload contained in the request body.
  * (optional) `hono-ttd`: The number of seconds the device will wait for the response.
  * (optional) `hono-ttl`: The *time-to-live* in number of seconds for event messages.  
* Request Body:
  * (required) Arbitrary payload encoded according to the given content type.
* Response Headers:
  * (optional) `content-type`: A media type describing the semantics and format of payload contained in the response body. This header will only be present if the response contains a command to be executed by the device which requires input data.
  * (optional) `hono-command`: The name of the command to execute. This header will only be present if the response contains a command to be executed by the device.
  * (optional) `hono-cmd-req-id`: An identifier that the device must include in its response to a command. This header will only be present if the response contains a command to be executed by the device.
  * (optional) `hono-cmd-target-device`: The id of the device that shall execute the command. This header will only be present if the response contains a command to be executed by the device and if the response goes to a gateway that acts on behalf of the target device.
* Response Body:
  * (optional) Arbitrary data serving as input to a command to be executed by the device, if status code is 200.
  * (optional) Error details, if status code is >= 400.
* Status Codes:
  * 200 (OK): The event has been accepted for processing. The response contains a command for the device to execute.
  * 202 (Accepted): The event has been accepted for processing.
  * 400 (Bad Request): The request cannot be processed. Possible reasons for this include:
        * The content type header is missing.
        * The request body is empty but the event is not of type [empty-notification]({{< relref "/api/event#empty-notification" >}}).
  * 401 (Unauthorized): The request cannot be processed because the request does not contain valid credentials.
  * 403 (Forbidden): The request cannot be processed because the device's registration status cannot be asserted. Possible reasons for this include:
        * The given tenant is not allowed to use this protocol adapter.
  * 404 (Not Found): The request cannot be processed because the device is disabled or does not exist.
  * 429 (Too Many Requests): The request cannot be processed because the tenant's message limit for the current period is exceeded.
  * 503 (Service Unavailable): The request cannot be processed because there is no consumer of events for the given tenant connected to Hono.

This is the preferred way for devices to publish events. It is available only if the protocol adapter is configured to require devices to authenticate (which is the default).

If the `hono-ttd` header is set in order to receive a command and if the authenticated device is actually a gateway, the returned command will be the first command that the northbound application has sent to either the gateway itself or (if not using the legacy control endpoint) to *any* device that has last sent a telemetry or event message via this gateway.

**Example**

Publish some JSON data for device `4711`:

~~~sh
curl -i -u sensor1@DEFAULT_TENANT:hono-secret -H 'content-type: application/json' --data-binary '{"alarm": true}' http://127.0.0.1:8080/event

HTTP/1.1 202 Accepted
content-length: 0
~~~

## Publish an Event (unauthenticated Device)

* URI: `/event/${tenantId}/${deviceId}`
* Method: `PUT`
* Request Headers:
  * (required) `content-type`: The type of payload contained in the request body.
  * (optional) `hono-ttd`: The number of seconds the device will wait for the response.
  * (optional) `hono-ttl`: The *time-to-live* in number of seconds for event messages.
* Request Body:
  * (required) Arbitrary payload encoded according to the given content type.
* Response Headers:
  * (optional) `content-type`: A media type describing the semantics and format of payload contained in the response body. This header will only be present if the response contains a command to be executed by the device which requires input data.
  * (optional) `hono-command`: The name of the command to execute. This header will only be present if the response contains a command to be executed by the device.
  * (optional) `hono-cmd-req-id`: An identifier that the device must include in its response to a command. This header will only be present if the response contains a command to be executed by the device.
* Response Body:
  * (optional) Arbitrary data serving as input to a command to be executed by the device, if status code is 200.
  * (optional) Error details, if status code is >= 400.
* Status Codes:
  * 200 (OK): The event has been accepted and put to a persistent store for delivery to consumers. The response contains a command for the device to execute.
  * 202 (Accepted): The event has been accepted and put to a persistent store for delivery to consumers.
  * 400 (Bad Request): The request cannot be processed. Possible reasons for this include:
        * The content type header is missing.
        * The request body is empty but the event is not of type [empty-notification]({{< relref "/api/event#empty-notification" >}}).
  * 403 (Forbidden): The request cannot be processed because the device's registration status cannot be asserted. Possible reasons for this include:
        * The given tenant is not allowed to use this protocol adapter.
        * The given device does not belong to the given tenant.
  * 404 (Not Found): The request cannot be processed because the device is disabled or does not exist.
  * 429 (Too Many Requests): The request cannot be processed because the tenant's message limit for the current period is exceeded.
  * 503 (Service Unavailable): The request cannot be processed because there is no consumer of events for the given tenant connected to Hono.

This resource MUST be used by devices that have not authenticated to the protocol adapter. Note that this requires the `HONO_HTTP_AUTHENTICATION_REQUIRED` configuration property to be explicitly set to `false`.

**Examples**

Publish some JSON data for device `4711`:

~~~sh
curl -i -X PUT -H 'content-type: application/json' --data-binary '{"alarm": true}' http://127.0.0.1:8080/event/DEFAULT_TENANT/4711

HTTP/1.1 202 Accepted
content-length: 0
~~~

## Publish an Event (authenticated Gateway)

* URI: `/event/${tenantId}/${deviceId}`
* Method: `PUT`
* Request Headers:
  * (optional) `authorization`: The gateway's *auth-id* and plain text password encoded according to the [Basic HTTP authentication scheme](https://tools.ietf.org/html/rfc7617). If not set, the adapter expects the gateway to present a client certificate as part of the TLS handshake during connection establishment.
  * (required) `content-type`: The type of payload contained in the request body.
  * (optional) `hono-ttd`: The number of seconds the device will wait for the response.
  * (optional) `hono-ttl`: The *time-to-live* in number of seconds for event messages.
* Request Body:
  * (required) Arbitrary payload encoded according to the given content type.
* Response Headers:
  * (optional) `content-type`: A media type describing the semantics and format of payload contained in the response body. This header will only be present if the response contains a command to be executed by the device which requires input data.
  * (optional) `hono-command`: The name of the command to execute. This header will only be present if the response contains a command to be executed by the device.
  * (optional) `hono-cmd-req-id`: An identifier that the device must include in its response to a command. This header will only be present if the response contains a command to be executed by the device.
  * (optional) `hono-cmd-target-device`: The id of the device that shall execute the command. This header will only be present if the response contains a command to be executed by the device.
* Response Body:
  * (optional) Arbitrary data serving as input to a command to be executed by the device, if status code is 200.
  * (optional) Error details, if status code is >= 400.
* Status Codes:
  * 200 (OK): The event has been accepted and put to a persistent store for delivery to consumers. The response contains a command for the device to execute.
  * 202 (Accepted): The event has been accepted and put to a persistent store for delivery to consumers.
  * 400 (Bad Request): The request cannot be processed. Possible reasons for this include:
        * The content type header is missing.
        * The request body is empty but the event is not of type [empty-notification]({{< relref "/api/event#empty-notification" >}}).
  * 401 (Unauthorized): The request cannot be processed because the request does not contain valid credentials.
  * 403 (Forbidden): The request cannot be processed because the device's registration status cannot be asserted. Possible reasons for this include:
        * The tenant that the gateway belongs to is not allowed to use this protocol adapter.
        * The device belongs to another tenant than the gateway.
        * The gateway is not authorized to act *on behalf of* the device.
        * The gateway associated with the device is not registered or disabled.
  * 404 (Not Found): The request cannot be processed because the device is disabled or does not exist.
  * 429 (Too Many Requests): The request cannot be processed because the tenant's message limit for the current period is exceeded.
  * 503 (Service Unavailable): The request cannot be processed because there is no consumer of events for the given tenant connected to Hono.

This resource can be used by *gateway* components to publish data *on behalf of* other devices which do not connect to a protocol adapter directly but instead are connected to the gateway, e.g. using some low-bandwidth radio based technology like [SigFox](https://www.sigfox.com) or [LoRa](https://lora-alliance.org/). In this case the credentials provided by the gateway during connection establishment with the protocol adapter are used to authenticate the gateway whereas the parameters from the URI are used to identify the device that the gateway publishes data for.

The protocol adapter checks the gateway's authority to publish data on behalf of the device implicitly by means of retrieving a *registration assertion* for the device from the [configured Device Registration service]({{< relref "/admin-guide/http-adapter-config#device-registration-service-connection-configuration" >}}).

**Examples**

Publish some JSON data for device `4712`:

~~~sh
curl -i -X PUT -u gw@DEFAULT_TENANT:gw-secret -H 'content-type: application/json' --data-binary '{"temp": 5}' http://127.0.0.1:8080/event/DEFAULT_TENANT/4712

HTTP/1.1 202 Accepted
content-length: 0
~~~

**NB** The example above assumes that a gateway device has been registered with `hashed-password` credentials with *auth-id* `gw` and password `gw-secret` which is authorized to publish data *on behalf of* device `4712`.

## Specifying the Time a Device will wait for a Response

The adapter lets devices indicate the number of seconds they will wait for a response by setting a header or a query parameter.

### Using an HTTP Header

The (optional) *hono-ttd* header can be set in requests for publishing telemetry data or events.

Example:

~~~sh
curl -i -u sensor1@DEFAULT_TENANT:hono-secret -H 'content-type: application/json' -H 'hono-ttd: 60' --data-binary '{"temp": 5}' http://127.0.0.1:8080/telemetry

HTTP/1.1 202 Accepted
content-length: 0
~~~

### Using a Query Parameter

Alternatively the *hono-ttd* query parameter can be used:

~~~sh
curl -i -u sensor1@DEFAULT_TENANT:hono-secret -H 'content-type: application/json' --data-binary '{"temp": 5}' http://127.0.0.1:8080/telemetry?hono-ttd=60

HTTP/1.1 202 Accepted
content-length: 0
~~~

## Sending a Response to a Command (authenticated Device)

* Since: 0.7
* URI: `/command/res/${commandRequestId}` or `/command/res/${commandRequestId}?hono-cmd-status=${status}`
* Method: `POST`
* Request Headers:
  * (optional) `authorization`: The device's *auth-id* and plain text password encoded according to the [Basic HTTP authentication scheme](https://tools.ietf.org/html/rfc7617). If not set, the adapter expects the device to present a client certificate as part of the TLS handshake during connection establishment.
  * (optional) `content-type`: A media type describing the semantics and format of the payload contained in the request body. This header may be set if the result of processing the command on the device is non-empty. In this case the result data is contained in the request body.
  * (optional) `hono-cmd-status`: The status of the command execution. If not set, the adapter expects that the URI contains it
    as request parameter at the end.
* Request Body:
  * (optional) Arbitrary data representing the result of processing the command on the device.
* Status Codes:
  * 202 (Accepted): The response has been successfully delivered to the application that has sent the command.
  * 400 (Bad Request): The request cannot be processed because the command status is missing.
  * 401 (Unauthorized): The request cannot be processed because the request does not contain valid credentials.  
  * 403 (Forbidden): The request cannot be processed because the device's registration status cannot be asserted. Possible reasons for this include:
        * The given tenant is not allowed to use this protocol adapter.
  * 404 (Not Found): The request cannot be processed because the device is disabled or does not exist.
  * 429 (Too Many Requests): The request cannot be processed because the tenant's message limit for the current period is exceeded.
  * 503 (Service Unavailable): The request cannot be processed. Possible reasons for this include:
         * There is no application listening for a reply to the given *commandRequestId*.
         * The application has already given up on waiting for a response.

This is the preferred way for devices to respond to commands. It is available only if the protocol adapter is configured to require devices to authenticate (which is the default).

{{% note title="Deprecation" %}}
Previous versions of Hono used `control` instead of `command` as prefix in the command response URI. Using the `control` prefix is still supported but deprecated. 
{{% /note %}}

**Example**

Send a response to a previously received command with the command-request-id `req-id-uuid` for device `4711`:

~~~sh
curl -i -u sensor1@DEFAULT_TENANT:hono-secret -H 'content-type: application/json' --data-binary '{"brightness-changed": true}' http://127.0.0.1:8080/command/res/req-id-uuid?hono-cmd-status=200

HTTP/1.1 202 Accepted
content-length: 0
~~~

## Sending a Response to a Command (unauthenticated Device)

* Since: 0.7
* URI: `/command/res/${tenantId}/${deviceId}/${commandRequestId}` or `/command/res/${tenantId}/${deviceId}/${commandRequestId}?hono-cmd-status=${status}`
* Method: `PUT`
* Request Headers:
  * (optional) `content-type`: A media type describing the semantics and format of the payload contained in the request body (the outcome of processing the command).
  * (optional) `hono-cmd-status`: The status of the command execution. If not set, the adapter expects that the URI contains it
    as request parameter at the end.
* Request Body:
  * (optional) Arbitrary data representing the result of processing the command on the device.
* Status Codes:
  * 202 (Accepted): The response has been successfully delivered to the application that has sent the command.
  * 400 (Bad Request): The request cannot be processed because the command status is missing.
  * 403 (Forbidden): The request cannot be processed because the device's registration status cannot be asserted. Possible reasons for this might be:
        * The given tenant is not allowed to use this protocol adapter.
        * The given device does not belong to the given tenant.
  * 404 (Not Found): The request cannot be processed because the device is disabled or does not exist.
  * 429 (Too Many Requests): The request cannot be processed because the tenant's message limit for the current period is exceeded.
  * 503 (Service Unavailable): The request cannot be processed. Possible reasons for this include:
         * There is no application listening for a reply to the given *commandRequestId*.
         * The application has already given up on waiting for a response.

This resource MUST be used by devices that have not authenticated to the protocol adapter. Note that this requires the `HONO_HTTP_AUTHENTICATION_REQUIRED` configuration property to be explicitly set to `false`.

{{% note title="Deprecation" %}}
Previous versions of Hono used `control` instead of `command` as prefix in the command response URI. Using the `control` prefix is still supported but deprecated. 
{{% /note %}}

**Examples**

Send a response to a previously received command with the command-request-id `req-id-uuid` for the unauthenticated device `4711`:

~~~sh
curl -i -X PUT -H 'content-type: application/json' --data-binary '{"brightness-changed": true}' http://127.0.0.1:8080/command/res/DEFAULT_TENANT/4711/req-id-uuid?hono-cmd-status=200

HTTP/1.1 202 Accepted
content-length: 0
~~~

## Sending a Response to a Command (authenticated Gateway)

* Since: 0.7
* URI: `/command/res/${tenantId}/${deviceId}/${commandRequestId}` or `/command/res/${tenantId}/${deviceId}/${commandRequestId}?hono-cmd-status=${status}`
* Method: `PUT`
* Request Headers:
  * (optional) `authorization`: The gateway's *auth-id* and plain text password encoded according to the [Basic HTTP authentication scheme](https://tools.ietf.org/html/rfc7617). If not set, the adapter expects the gateway to present a client certificate as part of the TLS handshake during connection establishment.
  * (optional) `content-type`: A media type describing the semantics and format of the payload contained in the request body (the outcome of processing the command).
  * (optional) `hono-cmd-status`: The status of the command execution. If not set, the adapter expects that the URI contains it
    as request parameter at the end.
* Request Body:
  * (optional) Arbitrary data representing the result of processing the command on the device.
* Status Codes:
  * 202 (Accepted): The response has been successfully delivered to the application that has sent the command.
  * 400 (Bad Request): The request cannot be processed because the command status is missing.
  * 403 (Forbidden): The request cannot be processed because the device's registration status cannot be asserted. Possible reasons for this might be:
        * The given tenant is not allowed to use this protocol adapter.
        * The given device does not belong to the given tenant.
        * The gateway is not authorized to act *on behalf of* the device.
        * The gateway associated with the device is not registered or disabled.
  * 404 (Not Found): The request cannot be processed because the device is disabled or does not exist.
  * 429 (Too Many Requests): The request cannot be processed because the tenant's message limit for the current period is exceeded.
  * 503 (Service Unavailable): The request cannot be processed. Possible reasons for this include:
         * There is no application listening for a reply to the given *commandRequestId*.
         * The application has already given up on waiting for a response.

This resource can be used by *gateway* components to send the response to a command *on behalf of* other devices which do not connect to a protocol adapter directly but instead are connected to the gateway, e.g. using some low-bandwidth radio based technology like [SigFox](https://www.sigfox.com) or [LoRa](https://lora-alliance.org/). In this case the credentials provided by the gateway during connection establishment with the protocol adapter are used to authenticate the gateway whereas the parameters from the URI are used to identify the device that the gateway publishes data for.

The protocol adapter checks the gateway's authority to send responses to a command on behalf of the device implicitly by means of retrieving a *registration assertion* for the device from the [configured Device Registration service]({{< relref "/admin-guide/http-adapter-config#device-registration-service-connection-configuration" >}}).

{{% note title="Deprecation" %}}
Previous versions of Hono used `control` instead of `command` as prefix in the command response URI. Using the `control` prefix is still supported but deprecated. 
{{% /note %}}

**Examples**

Send a response to a previously received command with the command-request-id `req-id-uuid` for device `4712`:

~~~sh
curl -i -X PUT -u gw@DEFAULT_TENANT:gw-secret -H 'content-type: application/json' --data-binary '{"brightness-changed": true}' http://127.0.0.1:8080/command/res/DEFAULT_TENANT/4712/req-id-uuid?hono-cmd-status=200

HTTP/1.1 202 Accepted
content-length: 0
~~~

**NB** The example above assumes that a gateway device has been registered with `hashed-password` credentials with *auth-id* `gw` and password `gw-secret` which is authorized to publish data *on behalf of* device `4712`.

## Downstream Meta Data

The adapter includes the following meta data in the application properties of messages being sent downstream:

| Name               | Type      | Description                                                     |
| :----------------- | :-------- | :-------------------------------------------------------------- |
| *device_id*        | *string*  | The identifier of the device that the message originates from.  |
| *orig_adapter*     | *string*  | Contains the adapter's *type name* which can be used by downstream consumers to determine the protocol adapter that the message has been received over. The HTTP adapter's type name is `hono-http`. |
| *orig_address*     | *string*  | Contains the (relative) URI that the device has originally posted the data to. |
| *ttd*              | *integer* | Contains the effective number of seconds that the device will wait for a response. This property is only set if the HTTP request contains the `hono-ttd` header or request parameter. |

The adapter also considers *defaults* registered for the device at either the [tenant]({{< relref "/api/tenant#tenant-information-format" >}}) or the [device level]({{< relref "/api/device-registration#assert-device-registration" >}}). The values of the default properties are determined as follows:

1. If the message already contains a non-empty property of the same name, the value if unchanged.
2. Otherwise, if a default property of the same name is defined in the device's registration information, that value is used.
3. Otherwise, if a default property of the same name is defined for the tenant that the device belongs to, that value is used.

Note that of the standard AMQP 1.0 message properties only the *content-type* and *ttl* can be set this way to a default value.

### Event Message Time-to-live

Events published by devices will usually be persisted by the AMQP Messaging Network in order to support deferred delivery to downstream consumers.
In most cases the AMQP Messaging Network can be configured with a maximum *time-to-live* to apply to the events so that the events will be removed
from the persistent store if no consumer has attached to receive the event before the message expires.

In order to support environments where the AMQP Messaging Network cannot be configured accordingly, the protocol adapter supports setting a
downstream event message's *ttl* property based on the *hono-ttl* property set as a header or a query parameter in the event requests by the devices.
Also the default *ttl* and *max-ttl* values can be configured for a tenant/device as described in the [Tenant API]
({{< relref "/api/tenant#resource-limits-configuration-format" >}}).


## Tenant specific Configuration

The adapter uses the [Tenant API]({{< ref "/api/tenant#get-tenant-information" >}}) to retrieve *tenant specific configuration* for adapter type `hono-http`.
The following properties are (currently) supported:

| Name               | Type       | Default Value | Description                                                     |
| :----------------- | :--------- | :------------ | :-------------------------------------------------------------- |
| *enabled*          | *boolean*  | `true`       | If set to `false` the adapter will reject all data from devices belonging to the tenant. |
| *max-ttd*          | *integer*  | `60`         | Defines a tenant specific upper limit for the *time until disconnect* property that devices may include in requests for uploading telemetry data or events. Please refer to the [Command & Control concept page]({{< relref "/concepts/command-and-control.md" >}}) for a discussion of this parameter's purpose and usage.<br>This property can be set for the `hono-http` adapter type as an *extension* property in the adapter section of the tenant configuration.<br>If it is not set, then the default value of `60` seconds is used.|
