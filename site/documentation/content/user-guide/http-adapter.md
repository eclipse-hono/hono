+++
title = "HTTP Adapter"
weight = 210
+++

The HTTP protocol adapter exposes HTTP based endpoints for Eclipse Hono&trade;'s south bound Telemetry, Event and
Command & Control APIs.
<!--more-->

## Device Authentication

The HTTP adapter by default requires clients (devices or gateway components) to authenticate during connection
establishment. The adapter supports both the
[Basic HTTP authentication scheme](https://tools.ietf.org/html/rfc7617) as well as client certificate based
authentication as part of a TLS handshake for that purpose.

The adapter tries to authenticate the device using these mechanisms in the following order

### Client Certificate

The HTTP adapter supports authenticating clients based on TLS cipher suites using a digital signature based key
exchange algorithm as described in [RFC 5246 (TLS 1.2)](https://datatracker.ietf.org/doc/html/rfc5246) and
[RFC 8446 (TLS 1.3)](https://datatracker.ietf.org/doc/html/rfc8446). This requires a client to provide an X.509
certificate containing a public key that can be used for digital signature. The adapter uses the information in the
client certificate to verify the device's identity as described in
[Client Certificate based Authentication]({{< relref "/concepts/device-identity#client-certificate-based-authentication" >}}).

**NB** The HTTP adapter needs to be [configured for TLS]({{< relref "/admin-guide/secure_communication#http-adapter" >}})
in order to support this mechanism.

### HTTP Basic Auth

The HTTP adapter supports authenticating clients using the *Basic* HTTP authentication scheme. This means that clients
need to provide a *user-id* and a *password* encoded in the *authorization* HTTP request header as defined in
[RFC 7617, Section 2](https://datatracker.ietf.org/doc/html/rfc7617#section-2) when connecting to the HTTP adapter.
The *user-id* component of the header value must match the pattern *auth-id@tenant*, e.g. `sensor1@DEFAULT_TENANT`.

The adapter extracts the *auth-id*, *tenant* and password from the request header and verifies them using the
credentials that the
[configured Credentials service]({{< relref "/admin-guide/common-config#credentials-service-connection-configuration" >}})
has on record for the client as described in
[Username/Password based Authentication]({{< relref "/concepts/device-identity#usernamepassword-based-authentication" >}}).
If the credentials match, the client has been authenticated successfully and the request is being processed.

**NB** There is a subtle difference between the *device identifier* (*device-id*) and the *auth-id* a device uses
for authentication. See [Device Identity]({{< relref "/concepts/device-identity.md" >}}) for a discussion of the
concepts.

## Message Limits

The adapter rejects

* a client's request to upload data with status code `429 Too Many Requests` and
* any AMQP 1.0 message containing a command sent by a north bound application

if the [message limit]({{< relref "/concepts/resource-limits.md" >}}) that has been configured for the device's tenant
is exceeded.

## Publish Telemetry Data (authenticated Device)

* URI: `/telemetry`
* Method: `POST`
* Request Headers:
  * (optional) *authorization*: The device's *auth-id* and plain text password encoded according to the
    [Basic HTTP authentication scheme](https://tools.ietf.org/html/rfc7617). If not set, the adapter expects the device
    to present a client certificate as part of the TLS handshake during connection establishment.
  * (required) *content-type*: The type of payload contained in the request body.
  * (optional) *hono-ttd*: The number of seconds the device will wait for the response.
  * (optional) *qos-level*: The QoS level for publishing telemetry messages. The adapter supports *at most once* (`0`)
    and *at least once* (`1`) QoS levels. The default value of `0` is assumed if this header is omitted.
* Request Body:
  * (required) Arbitrary payload encoded according to the given content type.
* Response Headers:
  * (optional) *content-type*: A media type describing the semantics and format of payload contained in the response body.
    This header will only be present if the response contains a command to be executed by the device which requires input
    data or if the request failed and the response body contains error details.
  * (optional) *hono-command*: The name of the command to execute. This header will only be present if the response
    contains a command to be executed by the device.
  * (optional) *hono-cmd-req-id*: An identifier that the device must include in its response to a command. This header
    will only be present if the response contains a command to be executed by the device.
  * (optional) *hono-cmd-target-device*: The id of the device that shall execute the command. This header will only be
    present if the response contains a command to be executed by the device and if the response goes to a gateway that
    acts on behalf of the target device.
* Response Body:
  * (optional) Arbitrary data serving as input to a command to be executed by the device, if status code is 200.
  * (optional) Error details, if status code is >= 400.
* Status Codes:
  * 200 (OK): The telemetry data has been accepted for processing. The response contains a command for the device to
    execute.
  * 202 (Accepted): The telemetry data has been accepted for processing. Note that if the *qos-level* request header is
    omitted (*at most once* semantics), this status code does **not** mean that the message has been delivered to any
    potential consumer. However, if the QoS level header is set to `1` (*at least once* semantics), then the adapter
    waits for the message to be delivered and accepted by a downstream consumer before responding with this status code.
  * 400 (Bad Request): The request cannot be processed. Possible reasons for this include:
    * The content type header is missing.
    * The request body is empty.
    * The QoS header value is invalid.
  * 401 (Unauthorized): The request cannot be processed because the request does not contain valid credentials.
  * 403 (Forbidden): The request cannot be processed because the device's registration status cannot be asserted.
    Possible reasons for this include:
    * The given tenant is not allowed to use this protocol adapter.
  * 404 (Not Found): The request cannot be processed because the device is disabled or does not exist.
  * 413 (Request Entity Too Large): The request cannot be processed because the request body exceeds the maximum
    supported size.
  * 429 (Too Many Requests): The request cannot be processed because the tenant's message limit for the current period
    is exceeded.
  * 503 (Service Unavailable): The request cannot be processed. Possible reasons for this include:
    * There is no consumer of telemetry data for the given tenant connected to Hono, or the consumer has not indicated
      that it may receive further messages (not giving credits).
    * If the QoS level header is set to `1` (*at least once* semantics), the reason may be:
      * The consumer has indicated that it didn't process the telemetry data.
      * The consumer failed to indicate in time whether it has processed the telemetry data. 

This is the preferred way for devices to publish telemetry data. It is available only if the protocol adapter is
configured to require devices to authenticate (which is the default).

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

**NB** The example above assumes that the HTTP adapter is
[configured for TLS]({{< relref "/admin-guide/secure_communication#http-adapter" >}}) and the secure port is used.

## Publish Telemetry Data (unauthenticated Device)

* URI: `/telemetry/${tenantId}/${deviceId}`
* Method: `PUT`
* Request Headers:
  * (required) *content-type*: The type of payload contained in the request body.
  * (optional) *hono-ttd*: The number of seconds the device will wait for the response.
  * (optional) *qos-level*: The QoS level for publishing telemetry messages. The adapter supports *at most once* (`0`)
    and *at least once* (`1`) QoS levels. The default value of `0` is assumed if this header is omitted.
* Request Body:
  * (required) Arbitrary payload encoded according to the given content type.
* Response Headers:
  * (optional) *content-type*: A media type describing the semantics and format of payload contained in the response body.
    This header will only be present if the response contains a command to be executed by the device which requires input
    data or if the request failed and the response body contains error details.
  * (optional) *hono-command*: The name of the command to execute. This header will only be present if the response
    contains a command to be executed by the device.
  * (optional) *hono-cmd-req-id*: An identifier that the device must include in its response to a command. This header
    will only be present if the response contains a command to be executed by the device.
* Response Body:
  * (optional) Arbitrary data serving as input to a command to be executed by the device, if status code is 200.
  * (optional) Error details, if status code is >= 400.
* Status Codes:
  * 200 (OK): The telemetry data has been accepted for processing. The response contains a command for the device to
    execute.
  * 202 (Accepted): The telemetry data has been accepted for processing. Note that if the *qos-level* request header is
    omitted (*at most once* semantics), this status code does **not** mean that the message has been delivered to any
    potential consumer. However, if the QoS level header is set to `1` (*at least once* semantics), then the adapter
    waits for the message to be delivered and accepted by a downstream consumer before responding with this status code.
  * 400 (Bad Request): The request cannot be processed. Possible reasons for this include:
    * The content type header is missing.
    * The request body is empty.
    * The QoS header value is invalid.
  * 403 (Forbidden): The request cannot be processed because the device's registration status cannot be asserted.
    Possible reasons for this include:
    * The given tenant is not allowed to use this protocol adapter.
    * The given device does not belong to the given tenant.
  * 404 (Not Found): The request cannot be processed because the device is disabled or does not exist.
  * 413 (Request Entity Too Large): The request cannot be processed because the request body exceeds the maximum
    supported size.
  * 429 (Too Many Requests): The request cannot be processed because the tenant's message limit for the current period
    is exceeded.
  * 503 (Service Unavailable): The request cannot be processed. Possible reasons for this include:
    * There is no consumer of telemetry data for the given tenant connected to Hono, or the consumer has not indicated
      that it may receive further messages (not giving credits).
    * If the QoS level header is set to `1` (*at least once* semantics), the reason may be:
      * The consumer has indicated that it didn't process the telemetry data.
      * The consumer failed to indicate in time whether it has processed the telemetry data. 

This resource MUST be used by devices that have not authenticated to the protocol adapter. Note that this requires the
*HONO_HTTP_AUTHENTICATION_REQUIRED* configuration property to be explicitly set to `false`.

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
curl -i -X PUT -H 'content-type: application/json' -H 'hono-ttd: 10' --data-binary '{"temp": 5}' http://localhost:8080/telemetry/DEFAULT_TENANT/4711

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
  * (optional) *authorization*: The gateway's *auth-id* and plain text password encoded according to the
    [Basic HTTP authentication scheme](https://tools.ietf.org/html/rfc7617). If not set, the adapter expects the gateway
    to present a client certificate as part of the TLS handshake during connection establishment.
  * (required) *content-type*: The type of payload contained in the request body.
  * (optional) *hono-ttd*: The number of seconds the device will wait for the response.
  * (optional) *qos-level*: The QoS level for publishing telemetry messages. The adapter supports *at most once* (`0`)
    and *at least once* (`1`) QoS levels. The default value of `0` is assumed if this header is omitted.
* Request Body:
  * (required) Arbitrary payload encoded according to the given content type.
* Response Headers:
  * (optional) *content-type*: A media type describing the semantics and format of payload contained in the response body.
    This header will only be present if the response contains a command to be executed by the device which requires input
    data or if the request failed and the response body contains error details.
  * (optional) *hono-command*: The name of the command to execute. This header will only be present if the response
    contains a command to be executed by the device.
  * (optional) *hono-cmd-req-id*: An identifier that the device must include in its response to a command. This header
    will only be present if the response contains a command to be executed by the device.
  * (optional) *hono-cmd-target-device*: The id of the device that shall execute the command. This header will only be
    present if the response contains a command to be executed by the device.
* Response Body:
  * (optional) Arbitrary data serving as input to a command to be executed by the device, if status code is 200.
  * (optional) Error details, if status code is >= 400.
* Status Codes:
  * 200 (OK): The telemetry data has been accepted for processing. The response contains a command for the device to
    execute.
  * 202 (Accepted): The telemetry data has been accepted for processing. Note that if the *qos-level* request header is
    omitted (*at most once* semantics), this status code does **not** mean that the message has been delivered to any
    potential consumer. However, if the QoS level header is set to `1` (*at least once* semantics), then the adapter
    waits for the message to be delivered and accepted by a downstream consumer before responding with this status code.
  * 400 (Bad Request): The request cannot be processed. Possible reasons for this include:
    * The content type header is missing.
    * The request body is empty.
    * The QoS header value is invalid.
  * 401 (Unauthorized): The request cannot be processed because the request does not contain valid credentials.
  * 403 (Forbidden): The request cannot be processed because the device's registration status cannot be asserted.
    Possible reasons for this include:
    * The tenant that the gateway belongs to is not allowed to use this protocol adapter.
    * The device belongs to another tenant than the gateway.
    * The gateway is not authorized to act *on behalf of* the device.
    * The gateway associated with the device is not registered or disabled.
  * 404 (Not Found): The request cannot be processed because the device is disabled or does not exist.
  * 413 (Request Entity Too Large): The request cannot be processed because the request body exceeds the maximum
    supported size.
  * 429 (Too Many Requests): The request cannot be processed because the tenant's message limit for the current period
    is exceeded.
  * 503 (Service Unavailable): The request cannot be processed. Possible reasons for this include:
    * There is no consumer of telemetry data for the given tenant connected to Hono, or the consumer has not indicated
      that it may receive further messages (not giving credits).
    * If the QoS level header is set to `1` (*at least once* semantics), the reason may be:
      * The consumer has indicated that it didn't process the telemetry data.
      * The consumer failed to indicate in time whether it has processed the telemetry data. 

This resource can be used by *gateway* components to publish data *on behalf of* other devices which do not connect to
a protocol adapter directly but instead are connected to the gateway, e.g. using some low-bandwidth radio based
technology like [SigFox](https://www.sigfox.com) or [LoRa](https://lora-alliance.org/). In this case the credentials
provided by the gateway during connection establishment with the protocol adapter are used to authenticate the gateway
whereas the parameters from the URI are used to identify the device that the gateway publishes data for.

The protocol adapter checks the gateway's authority to publish data on behalf of the device implicitly by means of
retrieving a *registration assertion* for the device from the configured
[Device Registration service]({{< relref "/admin-guide/common-config#device-registration-service-connection-configuration" >}}).

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

**NB** The example above assumes that a gateway device has been registered with `hashed-password` credentials with
*auth-id* `gw` and password `gw-secret` which is authorized to publish data *on behalf of* device `4712`.

## Publish an Event (authenticated Device)

* URI: `/event`
* Method: `POST`
* Request Headers:
  * (optional) *authorization*: The device's *auth-id* and plain text password encoded according to the
    [Basic HTTP authentication scheme](https://tools.ietf.org/html/rfc7617). If not set, the adapter expects the
    device to present a client certificate as part of the TLS handshake during connection establishment.
  * (required) *content-type*: The type of payload contained in the request body.
  * (optional) *hono-ttd*: The number of seconds the device will wait for the response.
  * (optional) *hono-ttl*: The *time-to-live* in number of seconds for event messages.  
* Request Body:
  * (required) Arbitrary payload encoded according to the given content type.
* Response Headers:
  * (optional) *content-type*: A media type describing the semantics and format of payload contained in the response body.
    This header will only be present if the response contains a command to be executed by the device which requires input
    data or if the request failed and the response body contains error details.
  * (optional) *hono-command*: The name of the command to execute. This header will only be present if the response
    contains a command to be executed by the device.
  * (optional) *hono-cmd-req-id*: An identifier that the device must include in its response to a command. This header
    will only be present if the response contains a command to be executed by the device.
  * (optional) *hono-cmd-target-device*: The id of the device that shall execute the command. This header will only be
    present if the response contains a command to be executed by the device and if the response goes to a gateway that
    acts on behalf of the target device.
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
  * 403 (Forbidden): The request cannot be processed because the device's registration status cannot be asserted.
    Possible reasons for this include:
    * The given tenant is not allowed to use this protocol adapter.
  * 404 (Not Found): The request cannot be processed because the device is disabled or does not exist.
  * 413 (Request Entity Too Large): The request cannot be processed because the request body exceeds the maximum
    supported size.
  * 429 (Too Many Requests): The request cannot be processed because the tenant's message limit for the current period
    is exceeded.
  * 503 (Service Unavailable): The request cannot be processed because there is no consumer of events for the given
    tenant connected to Hono, or the consumer didn't process the event.

This is the preferred way for devices to publish events. It is available only if the protocol adapter is configured to
require devices to authenticate (which is the default).

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
  * (required) *content-type*: The type of payload contained in the request body.
  * (optional) *hono-ttd*: The number of seconds the device will wait for the response.
  * (optional) *hono-ttl*: The *time-to-live* in number of seconds for event messages.
* Request Body:
  * (required) Arbitrary payload encoded according to the given content type.
* Response Headers:
  * (optional) *content-type*: A media type describing the semantics and format of payload contained in the response body.
    This header will only be present if the response contains a command to be executed by the device which requires input
    data or if the request failed and the response body contains error details.
  * (optional) *hono-command*: The name of the command to execute. This header will only be present if the response
    contains a command to be executed by the device.
  * (optional) *hono-cmd-req-id*: An identifier that the device must include in its response to a command. This header
    will only be present if the response contains a command to be executed by the device.
* Response Body:
  * (optional) Arbitrary data serving as input to a command to be executed by the device, if status code is 200.
  * (optional) Error details, if status code is >= 400.
* Status Codes:
  * 200 (OK): The event has been accepted and put to a persistent store for delivery to consumers. The response contains
    a command for the device to execute.
  * 202 (Accepted): The event has been accepted and put to a persistent store for delivery to consumers.
  * 400 (Bad Request): The request cannot be processed. Possible reasons for this include:
    * The content type header is missing.
    * The request body is empty but the event is not of type [empty-notification]({{< relref "/api/event#empty-notification" >}}).
  * 403 (Forbidden): The request cannot be processed because the device's registration status cannot be asserted.
    Possible reasons for this include:
    * The given tenant is not allowed to use this protocol adapter.
    * The given device does not belong to the given tenant.
  * 404 (Not Found): The request cannot be processed because the device is disabled or does not exist.
  * 413 (Request Entity Too Large): The request cannot be processed because the request body exceeds the maximum
    supported size.
  * 429 (Too Many Requests): The request cannot be processed because the tenant's message limit for the current period
    is exceeded.
  * 503 (Service Unavailable): The request cannot be processed because there is no consumer of events for the given
    tenant connected to Hono, or the consumer didn't process the event.

This resource MUST be used by devices that have not authenticated to the protocol adapter. Note that this requires the
*HONO_HTTP_AUTHENTICATION_REQUIRED* configuration property to be explicitly set to `false`.

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
  * (optional) *authorization*: The gateway's *auth-id* and plain text password encoded according to the
    [Basic HTTP authentication scheme](https://tools.ietf.org/html/rfc7617). If not set, the adapter expects the
    gateway to present a client certificate as part of the TLS handshake during connection establishment.
  * (required) *content-type*: The type of payload contained in the request body.
  * (optional) *hono-ttd*: The number of seconds the device will wait for the response.
  * (optional) *hono-ttl*: The *time-to-live* in number of seconds for event messages.
* Request Body:
  * (required) Arbitrary payload encoded according to the given content type.
* Response Headers:
  * (optional) *content-type*: A media type describing the semantics and format of payload contained in the response body.
    This header will only be present if the response contains a command to be executed by the device which requires input
    data or if the request failed and the response body contains error details.
  * (optional) *hono-command*: The name of the command to execute. This header will only be present if the response
    contains a command to be executed by the device.
  * (optional) *hono-cmd-req-id*: An identifier that the device must include in its response to a command. This header
    will only be present if the response contains a command to be executed by the device.
  * (optional) *hono-cmd-target-device*: The id of the device that shall execute the command. This header will only be
    present if the response contains a command to be executed by the device.
* Response Body:
  * (optional) Arbitrary data serving as input to a command to be executed by the device, if status code is 200.
  * (optional) Error details, if status code is >= 400.
* Status Codes:
  * 200 (OK): The event has been accepted and put to a persistent store for delivery to consumers. The response
    contains a command for the device to execute.
  * 202 (Accepted): The event has been accepted and put to a persistent store for delivery to consumers.
  * 400 (Bad Request): The request cannot be processed. Possible reasons for this include:
    * The content type header is missing.
    * The request body is empty but the event is not of type [empty-notification]({{< relref "/api/event#empty-notification" >}}).
  * 401 (Unauthorized): The request cannot be processed because the request does not contain valid credentials.
  * 403 (Forbidden): The request cannot be processed because the device's registration status cannot be asserted.
    Possible reasons for this include:
    * The tenant that the gateway belongs to is not allowed to use this protocol adapter.
    * The device belongs to another tenant than the gateway.
    * The gateway is not authorized to act *on behalf of* the device.
    * The gateway associated with the device is not registered or disabled.
  * 404 (Not Found): The request cannot be processed because the device is disabled or does not exist.
  * 413 (Request Entity Too Large): The request cannot be processed because the request body exceeds the maximum
    supported size.
  * 429 (Too Many Requests): The request cannot be processed because the tenant's message limit for the current period
    is exceeded.
  * 503 (Service Unavailable): The request cannot be processed because there is no consumer of events for the given
    tenant connected to Hono, or the consumer didn't process the event.

This resource can be used by *gateway* components to publish data *on behalf of* other devices which do not connect to
a protocol adapter directly but instead are connected to the gateway, e.g. using some low-bandwidth radio based
technology like [SigFox](https://www.sigfox.com) or [LoRa](https://lora-alliance.org/). In this case the credentials
provided by the gateway during connection establishment with the protocol adapter are used to authenticate the gateway
whereas the parameters from the URI are used to identify the device that the gateway publishes data for.

The protocol adapter checks the gateway's authority to publish data on behalf of the device implicitly by means of
retrieving a *registration assertion* for the device from the configured
[Device Registration service]({{< relref "/admin-guide/common-config#device-registration-service-connection-configuration" >}}).

**Examples**

Publish some JSON data for device `4712`:

~~~sh
curl -i -X PUT -u gw@DEFAULT_TENANT:gw-secret -H 'content-type: application/json' --data-binary '{"temp": 5}' http://127.0.0.1:8080/event/DEFAULT_TENANT/4712

HTTP/1.1 202 Accepted
content-length: 0
~~~

**NB** The example above assumes that a gateway device has been registered with `hashed-password` credentials with
*auth-id* `gw` and password `gw-secret` which is authorized to publish data *on behalf of* device `4712`.

## Command & Control

The HTTP adapter enables devices to receive commands that have been sent by business applications. Commands are
delivered to the device by means of an HTTP response message. That means a device first has to send a request,
indicating how long it will wait for the response. That request can either be a telemetry or event message, with a
*hono-ttd* header or query parameter (`ttd` for `time till disconnect`) specifying the number of seconds the device will
wait for the response. The business application can react on that message by sending a command message, targeted at
the device. The HTTP adapter will then send the command message as part of the HTTP response message with status `200`
(OK) to the device. If the HTTP adapter receives no command message in the given time period, a `202` (Accepted)
response will be sent to the device (provided the request was valid).

### Specifying the Time a Device will wait for a Response

The adapter lets devices indicate the number of seconds they will wait for a response by setting a header or a query
parameter.

#### Using an HTTP Header

The (optional) *hono-ttd* header can be set in requests for publishing telemetry data or events.

Example:

~~~sh
curl -i -u sensor1@DEFAULT_TENANT:hono-secret -H 'content-type: application/json' -H 'hono-ttd: 60' --data-binary '{"temp": 5}' http://127.0.0.1:8080/telemetry

HTTP/1.1 202 Accepted
content-length: 0
~~~

#### Using a Query Parameter

Alternatively the *hono-ttd* query parameter can be used:

~~~sh
curl -i -u sensor1@DEFAULT_TENANT:hono-secret -H 'content-type: application/json' --data-binary '{"temp": 5}' http://127.0.0.1:8080/telemetry?hono-ttd=60

HTTP/1.1 202 Accepted
content-length: 0
~~~

### Commands handled by gateways

Authenticated gateways will receive commands for devices which do not connect to a protocol adapter directly but
instead are connected to the gateway. Corresponding devices have to be configured so that they can be used with a
gateway. See
[Configuring Gateway Devices]({{< relref "/admin-guide/file-based-device-registry-config#configuring-gateway-devices" >}})
for details.

A gateway can send a request with the *hono-ttd* header or query parameter on the `/event` or `/telemetry` URI, indicating
its readiness to receive a command for *any* device it acts on behalf of. Note that in this case, the business
application will be notified with the gateway id in the `device_id` property of the downstream message.

An authenticated gateway can also indicate its readiness to receive a command targeted at a *specific* device. For that,
the `/event/${tenantId}/${deviceId}` or `/telemetry/${tenantId}/${deviceId}` URI is to be used, containing the id of the device
to receive a command for. The business application will receive a notification with that device id.

If there are multiple concurrent requests with a *hono-ttd* header or query parameter, sent by the command target device
and/or one or more of its potential gateways, the HTTP adapter will choose the device or gateway to send the command to
as follows:

* A request done by the command target device or by a gateway specifically done for that device, has precedence. If
  there are multiple, concurrent such requests, the last one will get the command message (if received) in its response.
  Note that the other requests won't be answered with a command message in their response event if the business
  application sent multiple command messages. That means commands for a single device can only be requested sequentially,
  not in parallel.
* If the above doesn't apply, a single *hono-ttd* request on the `/event` or `/telemetry` URI, sent by a gateway that the
  command target device is configured for, will get the command message in its response.
* If there are multiple, concurrent such requests by different gateways, all configured for the command target device,
  the request by the gateway will be chosen, through which the target device has last sent a telemetry or event message.
  If the target device hasn't sent a message yet and it is thereby unknown via which gateway the device communicates,
  then one of the requests will be chosen randomly to set the command in its response. 

### Sending a Response to a Command (authenticated Device)

* URI: `/command/res/${commandRequestId}` or `/command/res/${commandRequestId}?hono-cmd-status=${status}`
* Method: `POST`
* Request Headers:
  * (optional) *authorization*: The device's *auth-id* and plain text password encoded according to the
    [Basic HTTP authentication scheme](https://tools.ietf.org/html/rfc7617). If not set, the adapter expects the
    device to present a client certificate as part of the TLS handshake during connection establishment.
  * (optional) *content-type*: A media type describing the semantics and format of the payload contained in the request
    body. This header may be set if the result of processing the command on the device is non-empty. In this case the
    result data is contained in the request body.
  * (optional) *hono-cmd-status*: The status of the command execution. If not set, the adapter expects that the URI
    contains it as a query parameter.
* Request Body:
  * (optional) Arbitrary data representing the result of processing the command on the device.
* Response Headers:
  * (optional) *content-type*: A media type describing the semantics and format of payload contained in the response body.
    This header will only be present if the request failed and the response body contains error details.
* Response Body:
  * (optional) Error details, if status code is >= 400.
* Status Codes:
  * 202 (Accepted): The response has been successfully delivered to the application that has sent the command.
  * 400 (Bad Request): The request cannot be processed because the command status is missing.
  * 401 (Unauthorized): The request cannot be processed because the request does not contain valid credentials.
  * 403 (Forbidden): The request cannot be processed because the device's registration status cannot be asserted.
    Possible reasons for this include:
    * The given tenant is not allowed to use this protocol adapter.
  * 404 (Not Found): The request cannot be processed because the device is disabled or does not exist.
  * 413 (Request Entity Too Large): The request cannot be processed because the request body exceeds the maximum
    supported size.
  * 429 (Too Many Requests): The request cannot be processed because the tenant's message limit for the current period
    is exceeded.
  * 503 (Service Unavailable): The request cannot be processed. Possible reasons for this include:
    * There is no application listening for a reply to the given *commandRequestId*.
    * The application has already given up on waiting for a response.

This is the preferred way for devices to respond to commands. It is available only if the protocol adapter is
configured to require devices to authenticate (which is the default).

**Example**

Send a response to a previously received command with the command-request-id `req-id-uuid` for device `4711`:

~~~sh
curl -i -u sensor1@DEFAULT_TENANT:hono-secret -H 'content-type: application/json' --data-binary '{"brightness-changed": true}' http://127.0.0.1:8080/command/res/req-id-uuid?hono-cmd-status=200

HTTP/1.1 202 Accepted
content-length: 0
~~~

### Sending a Response to a Command (unauthenticated Device)

* URI: `/command/res/${tenantId}/${deviceId}/${commandRequestId}` or `/command/res/${tenantId}/${deviceId}/${commandRequestId}?hono-cmd-status=${status}`
* Method: `PUT`
* Request Headers:
  * (optional) *content-type*: A media type describing the semantics and format of the payload contained in the request
    body (the outcome of processing the command).
  * (optional) *hono-cmd-status*: The status of the command execution. If not set, the adapter expects that the URI
    contains it as a query parameter.
* Request Body:
  * (optional) Arbitrary data representing the result of processing the command on the device.
* Response Headers:
  * (optional) *content-type*: A media type describing the semantics and format of payload contained in the response body.
    This header will only be present if the request failed and the response body contains error details.
* Response Body:
  * (optional) Error details, if status code is >= 400.
* Status Codes:
  * 202 (Accepted): The response has been successfully delivered to the application that has sent the command.
  * 400 (Bad Request): The request cannot be processed because the command status is missing.
  * 403 (Forbidden): The request cannot be processed because the device's registration status cannot be asserted.
    Possible reasons for this might be:
    * The given tenant is not allowed to use this protocol adapter.
    * The given device does not belong to the given tenant.
  * 404 (Not Found): The request cannot be processed because the device is disabled or does not exist.
  * 413 (Request Entity Too Large): The request cannot be processed because the request body exceeds the maximum
    supported size.
  * 429 (Too Many Requests): The request cannot be processed because the tenant's message limit for the current period
    is exceeded.
  * 503 (Service Unavailable): The request cannot be processed. Possible reasons for this include:
    * There is no application listening for a reply to the given *commandRequestId*.
    * The application has already given up on waiting for a response.

This resource MUST be used by devices that have not authenticated to the protocol adapter. Note that this requires the
*HONO_HTTP_AUTHENTICATION_REQUIRED* configuration property to be explicitly set to `false`.

**Examples**

Send a response to a previously received command with the command-request-id `req-id-uuid` for the unauthenticated
device `4711`:

~~~sh
curl -i -X PUT -H 'content-type: application/json' --data-binary '{"brightness-changed": true}' http://127.0.0.1:8080/command/res/DEFAULT_TENANT/4711/req-id-uuid?hono-cmd-status=200

HTTP/1.1 202 Accepted
content-length: 0
~~~

### Sending a Response to a Command (authenticated Gateway)

* URI: `/command/res/${tenantId}/${deviceId}/${commandRequestId}` or
  `/command/res/${tenantId}/${deviceId}/${commandRequestId}?hono-cmd-status=${status}`
* Method: `PUT`
* Request Headers:
  * (optional) *authorization*: The gateway's *auth-id* and plain text password encoded according to the
    [Basic HTTP authentication scheme](https://tools.ietf.org/html/rfc7617). If not set, the adapter expects the
    gateway to present a client certificate as part of the TLS handshake during connection establishment.
  * (optional) *content-type*: A media type describing the semantics and format of the payload contained in the request
    body (the outcome of processing the command).
  * (optional) *hono-cmd-status*: The status of the command execution. If not set, the adapter expects that the URI
    contains it as a query parameter.
* Request Body:
  * (optional) Arbitrary data representing the result of processing the command on the device.
* Response Headers:
  * (optional) *content-type*: A media type describing the semantics and format of payload contained in the response body.
    This header will only be present if the request failed and the response body contains error details.
* Response Body:
  * (optional) Error details, if status code is >= 400.
* Status Codes:
  * 202 (Accepted): The response has been successfully delivered to the application that has sent the command.
  * 400 (Bad Request): The request cannot be processed because the command status is missing.
  * 403 (Forbidden): The request cannot be processed because the device's registration status cannot be asserted.
    Possible reasons for this might be:
    * The given tenant is not allowed to use this protocol adapter.
    * The given device does not belong to the given tenant.
    * The gateway is not authorized to act *on behalf of* the device.
    * The gateway associated with the device is not registered or disabled.
  * 404 (Not Found): The request cannot be processed because the device is disabled or does not exist.
  * 413 (Request Entity Too Large): The request cannot be processed because the request body exceeds the maximum
    supported size.
  * 429 (Too Many Requests): The request cannot be processed because the tenant's message limit for the current period
    is exceeded.
  * 503 (Service Unavailable): The request cannot be processed. Possible reasons for this include:
    * There is no application listening for a reply to the given *commandRequestId*.
    * The application has already given up on waiting for a response.

This resource can be used by *gateway* components to send the response to a command *on behalf of* other devices which
do not connect to a protocol adapter directly but instead are connected to the gateway, e.g. using some low-bandwidth
radio based technology like [SigFox](https://www.sigfox.com) or [LoRa](https://lora-alliance.org/). In this case the
credentials provided by the gateway during connection establishment with the protocol adapter are used to authenticate
the gateway whereas the parameters from the URI are used to identify the device that the gateway publishes data for.

The protocol adapter checks the gateway's authority to send responses to a command on behalf of the device implicitly
by means of retrieving a *registration assertion* for the device from the configured
[Device Registration service]({{< relref "/admin-guide/common-config#device-registration-service-connection-configuration" >}}).

**Examples**

Send a response to a previously received command with the command-request-id `req-id-uuid` for device `4712`:

~~~sh
curl -i -X PUT -u gw@DEFAULT_TENANT:gw-secret -H 'content-type: application/json' --data-binary '{"brightness-changed": true}' http://127.0.0.1:8080/command/res/DEFAULT_TENANT/4712/req-id-uuid?hono-cmd-status=200

HTTP/1.1 202 Accepted
content-length: 0
~~~

**NB** The example above assumes that a gateway device has been registered with `hashed-password` credentials with
*auth-id* `gw` and password `gw-secret` which is authorized to publish data *on behalf of* device `4712`.

## Downstream Meta Data

The adapter includes the following meta data in messages being sent downstream:

| Name               | Type      | Description                                                     |
| :----------------- | :-------- | :-------------------------------------------------------------- |
| *device_id*        | *string*  | The identifier of the device that the message originates from.  |
| *orig_adapter*     | *string*  | Contains the adapter's *type name* which can be used by downstream consumers to determine the protocol adapter that the message has been received over. The HTTP adapter's type name is `hono-http`. |
| *orig_address*     | *string*  | Contains the (relative) URI that the device has originally posted the data to. |
| *ttd*              | *integer* | Contains the effective number of seconds that the device will wait for a response. This property is only set if the HTTP request contains the *hono-ttd* header or request parameter. |

The adapter also considers *defaults* registered for the device at either the
[tenant]({{< relref "/api/tenant#tenant-information-format" >}}) or the
[device level]({{< relref "/api/device-registration#assert-device-registration" >}}).
The values of the default properties are determined as follows:

1. If the message already contains a non-empty property of the same name, the value if unchanged.
2. Otherwise, if a default property of the same name is defined in the device's registration information,
   that value is used.
3. Otherwise, if a default property of the same name is defined for the tenant that the device belongs to,
   that value is used.

Note that of the standard AMQP 1.0 message properties only the *content-type* and *ttl* can be set this way to a
default value.

### Event Message Time-to-live

Events published by devices will usually be persisted by the messaging infrastructure in order to support deferred
delivery to downstream consumers.

In most cases, the messaging infrastructure can be configured with a maximum *time-to-live* to apply to the events so
that the events will be removed from the persistent store if no consumer has attached to receive the event before
the message expires.

In order to support environments where the messaging infrastructure cannot be configured accordingly, the protocol
adapter supports setting a downstream event message's *ttl* property based on the default *ttl* and *max-ttl* values
configured for a tenant/device as described in the [Tenant API]({{< relref "/api/tenant#resource-limits-configuration-format" >}}).

## Tenant specific Configuration

The adapter uses the [Tenant API]({{< ref "/api/tenant#get-tenant-information" >}}) to retrieve *tenant specific configuration* for adapter type `hono-http`.
The following properties are (currently) supported:

| Name               | Type       | Default Value | Description                                                     |
| :----------------- | :--------- | :------------ | :-------------------------------------------------------------- |
| *enabled*          | *boolean*  | `true`        | If set to `false` the adapter will reject all data from devices belonging to the tenant. |
| *max-ttd*          | *integer*  | `60`          | Defines a tenant specific upper limit for the *time until disconnect* property that devices may include in requests for uploading telemetry data or events. Please refer to the [Command & Control concept page]({{< relref "/concepts/command-and-control/index.md" >}}) for a discussion of this parameter's purpose and usage.<br>This property can be set for the `hono-http` adapter type as an *extension* property in the adapter section of the tenant configuration.<br>If it is not set, then the default value of `60` seconds is used.|
