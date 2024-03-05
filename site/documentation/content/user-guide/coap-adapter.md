+++
title = "CoAP Adapter"
weight = 225
+++

The CoAP protocol adapter exposes [CoAP](https://tools.ietf.org/html/rfc7252) based endpoints for Eclipse Hono&trade;'s
south bound Telemetry, Event and Command & Control APIs.
<!--more-->

## Device Authentication

The CoAP adapter by default requires clients (devices or gateway components) to authenticate during connection
establishment. The adapter (currently) supports the use of pre-shared keys (PSK) and X.509 certificates as part of a
DTLS handshake for that purpose. Additional variants mentioned in
[Securing CoAP](https://tools.ietf.org/html/rfc7252#section-9) might be added in the future.

### Pre-Shared Key

The CoAP adapter supports authenticating clients using PSK based cipher suites as part of the DTLS handshake.
This requires a client to provide a *psk_identity* in its *ClientKeyExchange* message as defined in
[RFC 4279, Section 2](https://datatracker.ietf.org/doc/html/rfc4279#section-2) during the DTLS handshake with
the CoAP adapter. The adapter uses the provided information to verify the client's identity as described in
[Pre-Shared Key based Authentication]({{< relref "/concepts/device-identity#pre-shared-key-based-authentication" >}}).

The examples below refer to devices `4711` and `gw-1` of tenant `DEFAULT_TENANT` using *auth-ids* `sensor1` and `gw1` and
corresponding secrets. The example deployment as described in the [Deployment Guides]({{< relref "deployment" >}})
comes pre-configured with the corresponding entities in its device registry component.

{{% notice info %}}
There is a subtle difference between the *device identifier* (*device-id*) and the *auth-id* a device uses
for authentication. See [Device Identity]({{< relref "/concepts/device-identity.md" >}}) for a discussion of the
concepts.
{{% /notice %}}

### X.509

The CoAP adapter supports authenticating clients based on TLS cipher suites using the `ECDHE_ECDSA` key exchange algorithm
as described in [RFC 4492](https://datatracker.ietf.org/doc/html/rfc4492). This requires a client to provide an
X.509 client certificate containing an ECDSA-capable public key. The adapter uses the information in the client
certificate to verify the device's identity as described in
[Client Certificate based Authentication]({{< relref "/concepts/device-identity#client-certificate-based-authentication" >}}).

{{% notice info %}}
The CoAP adapter needs to be [configured for DTLS]({{< relref "/admin-guide/secure_communication#coap-adapter" >}})
in order to support this mechanism.
{{% /notice %}}

## Message Limits

The adapter rejects

* a client's request to upload data with status code `429 Too Many Requests` and
* any AMQP 1.0 message containing a command sent by a north bound application

if the [message limit]({{< relref "/concepts/resource-limits.md" >}}) that has been configured for the device's tenant
is exceeded.

## CoAP Content Format Codes

CoAP doesn't use a textual identifier for content types. Instead, numbers are used which are maintained by the
[IANA](https://www.iana.org/).
The [IANA - CoAP Content Formats](https://www.iana.org/assignments/core-parameters/core-parameters.xhtml#content-formats)
page lists all (currently) registered codes and the corresponding media types.

## Publish Telemetry Data (authenticated Device)

* URI:
  * `/t`
  * `/telemetry`
* Method: `POST`
* Type:
  * `CON`: *at least once* delivery semantics
  * `NON`: *at most once* delivery semantics
* Request Options:
  * (optional) *content-format*: The type of payload contained in the request body. Required, if request body is empty
    but message is not an [empty notification]({{< relref "/api/event#empty-notification" >}}).
    The content type corresponding to the given content format, if not empty, will be used in the message being forwarded
    downstream. Otherwise, the content type of the downstream message will be set to `application/octet-stream`, if the payload
    is not empty and no default content type has been defined for the origin device or its tenant (see
    [Downstream Meta Data]({{< relref "#downstream-meta-data" >}})).
  * (optional) *experimental option 0xfde8 (time)*: Request that the response include the server time.
    If this option is present in the request, the server will add the same option to its response where the value will
    indicate the server time (as milliseconds since UNIX epoch). The request option does not need to have a value, and 
    any value will, in fact, be ignored.
* Query Parameters:
  * (optional) *hono-ttd*: The number of seconds the device will wait for the response.
  * (optional) *hono-time*: Indicates that the client wants to receive the server time as a response option.
  * (optional) *empty*: Marks the request as an [empty notification]({{< relref "/api/event#empty-notification" >}}).
* Request Body:
  * (optional) Arbitrary payload matching the given content type.
* Response Options:
  * (optional) *content-format*: A media type describing the semantics and format of payload contained in the response body.
    This option will only be present if the response contains a command to be executed by the device which requires input
    data. Note that this option will be empty if the media type contained in the command (AMQP) message's *content-type*
    property cannot be mapped to one of the registered CoAP *content-format* codes.
  * (optional) *location-query*: The *hono-command* query parameter contains the name of the command to execute.
    This option will only be present if the response contains a command to be executed by the device.
  * (optional) *location-path*: This option will only be present if the response contains a command to be executed
    by the device. The location path is `c` or `command` for one-way-commands and `cr/<command-request-id>` or
    `command_response/<command-request-id>` for commands expecting a response.
    In the latter case, the *location-path* option contains exactly the URI-path that the device must use when sending
    its response to the command. `command` and `command_response` will be used if the request also uses the fully spelled
    out endpoint name.
  * (optional) *experimental option 0xfde8 (time)*: This option will only be present if the request contained this
    option (with or without value) or the request contained the `hono-time` query parameter. The value of this option
    in a response will indicate the server timestamp (as milliseconds since UNIX epoch) at the time the response was
    sent.
* Response Body:
  * (optional) Arbitrary data serving as input to a command to be executed by the device.
  * (optional) Error details, if status code is >= 4.00.
* Response Codes:
  * 2.04 (Changed): The data in the request body has been accepted for processing. The response may contain a command
    for the device to execute. Note that if the message type is `NON` (*at most once* semantics), this status code does
    **not** mean that the message has been delivered to any potential consumer (yet). However, if the message type is
    `CON` (*at least once* semantics), then the adapter waits for the message to be delivered and accepted by a
    downstream consumer before responding with this status code.
  * 4.00 (Bad Request): The request cannot be processed. Possible reasons include:
    * The *URI-query* option contains the *empty* parameter but the request body is not empty.
    * The request body is empty but the request has no *content-format* set nor does its
      *URI-query* option contain the *empty* parameter.
  * 4.03 (Forbidden): The request cannot be processed because the device's registration status cannot be asserted.
    Possible reasons for this include:
    * The given tenant is not allowed to use this protocol adapter.
  * 4.04 (Not Found): The request cannot be processed because the device is disabled or does not exist.
  * 4.13 (Request Entity Too Large): The request cannot be processed because the request body exceeds the maximum
    supported size.
  * 4.29 (Too Many Requests): The request cannot be processed because the tenant's message limit for the current period
    is exceeded.
  * 5.03 (Service Unavailable): The request cannot be processed. Possible reasons for this include:
    * There is no consumer of telemetry data for the given tenant connected to Hono, or the consumer has not indicated
      that it may receive further messages (not giving credits).
    * If the message type is `CON` (*at least once* semantics), the reason may be:
      * The consumer has indicated that it didn't process the telemetry data.
      * The consumer failed to indicate in time whether it has processed the telemetry data. 

This is the preferred way for devices to publish telemetry data. It is available only if the protocol adapter is
configured to require devices to authenticate (which is the default).

**Examples**

The examples provided below make use of the *coap-client* command line tool which is part of the
[libcoap project](https://libcoap.net/). Precompiled packages should be available for different Linux variants.

Publish some JSON data for device `4711` using default message type `CON` (*at least once*):

~~~sh
coap-client -u sensor1@DEFAULT_TENANT -k hono-secret -m POST coaps://hono.eclipseprojects.io/t -t application/json -e '{"temp": 5}'
~~~

{{% notice tip %}}
*coap-client* only reports error response-codes, so the expected 2.04 response code will not be printed to the terminal.
{{% /notice %}}

Publish some JSON data for device `4711` using message type `NON` (*at most once*):

~~~sh
coap-client -u sensor1@DEFAULT_TENANT -k hono-secret -N -m POST coaps://hono.eclipseprojects.io/t -t application/json -e '{"temp": 5}'
~~~

Publish some JSON data for device `4711`, indicating that the device will wait for 10 seconds to receive the response:

~~~sh
coap-client -u sensor1@DEFAULT_TENANT -k hono-secret -m POST coaps://hono.eclipseprojects.io/t?hono-ttd=10 -t application/json -e '{"temp": 5}'

{
  "brightness": 87
}
~~~

{{% notice info %}}
In the example above the response actually contains payload that should be used as input to a command to be executed
by the device. This is just for illustrative purposes. You will usually get an empty response because there is no
downstream application attached which could send any commands to the device.
{{% /notice %}}

## Publish Telemetry Data (unauthenticated Device)

* URI:
  * `/t/${tenantId}/${deviceId}`
  * `/telemetry/${tenantId}/${deviceId}`
* Method: `PUT`
* Type:
  * `CON`: *at least once* delivery semantics
  * `NON`: *at most once* delivery semantics
* Request Options:
  * (optional) *content-format*: The type of payload contained in the request body. Required, if request body is empty
    but message is not an [empty notification]({{< relref "/api/event#empty-notification" >}}).
    The content type corresponding to the given content format, if not empty, will be used in the message being forwarded
    downstream. Otherwise, the content type of the downstream message will be set to `application/octet-stream`, if the payload
    is not empty and no default content type has been defined for the origin device or its tenant (see
    [Downstream Meta Data]({{< relref "#downstream-meta-data" >}})).
  * (optional) *experimental option 0xfde8 (time)*: Request that the response include the server time.
    If this option is present in the request, the server will add the same option to its response where the value will
    indicate the server time (as milliseconds since UNIX epoch). The request option does not need to have a value, and
    any value will, in fact, be ignored.
* Query Parameters:
  * (optional) *hono-ttd*: The number of seconds the device will wait for the response.
  * (optional) *hono-time*: Indicates that the client wants to receive the server time as a response option.
  * (optional) *empty*: Marks the request as an [empty notification]({{< relref "/api/event#empty-notification" >}}).
* Request Body:
  * (optional) Arbitrary payload matching the given content type.
* Response Options:
  * (optional) *content-format*: A media type describing the semantics and format of payload contained in the response body.
    This option will only be present if the response contains a command to be executed by the device which requires input
    data. Note that this option will be empty if the media type contained in the command (AMQP) message's *content-type*
    property cannot be mapped to one of the registered CoAP *content-format* codes.
  * (optional) *location-query*: The *hono-command* query parameter contains the name of the command to execute.
    This option will only be present if the response contains a command to be executed by the device.
  * (optional) *location-path*: This option will only be present if the response contains
    a command to be executed by the device. The location path is `c` or `command` for one-way-commands and
    `cr/${tenantId}/${deviceId}/<command-request-id>` or `command_response/${tenantId}/${deviceId}/<command-request-id>` for commands
    expecting  a response. In the latter case, the *location-path* option contains exactly the URI-path that the
    device must use when sending its response to the command. `command` and `command_response` will be used if the request
    also uses the fully spelled out endpoint name.
  * (optional) *experimental option 0xfde8 (time)*: This option will only be present if the request contained this
    option (with or without value) or the request contained the `hono-time` query parameter. The value of this option
    in a response will indicate the server timestamp (as milliseconds since UNIX epoch) at the time the response was
    sent.
* Response Body:
  * (optional) Arbitrary data serving as input to a command to be executed by the device, if status code is 2.05 (Content).
  * (optional) Error details, if status code is >= 4.00.
* Response Codes:
  * 2.04 (Changed): The data in the request body has been accepted for processing. The response may contain a command
    for the device to execute. Note that if the message type is `NON` (*at most once* semantics), this status code does
    **not** mean that the message has been delivered to any potential consumer (yet). However, if the message type is
    `CON` (*at least once* semantics), then the adapter waits for the message to be delivered and accepted by a
    downstream consumer before responding with this status code.
  * 4.00 (Bad Request): The request cannot be processed. Possible reasons include:
    * The *URI-query* option contains the *empty* parameter but the request body is not empty.
    * The request body is empty but the request has no *content-format* set nor does its
      *URI-query* option contain the *empty* parameter.
  * 4.03 (Forbidden): The request cannot be processed because the device's registration status cannot be asserted.
    Possible reasons for this include:
    * The given tenant is not allowed to use this protocol adapter.
    * The given device does not belong to the given tenant.
  * 4.04 (Not Found): The request cannot be processed because the device is disabled or does not exist.
  * 4.13 (Request Entity Too Large): The request cannot be processed because the request body exceeds the maximum
    supported size.
  * 4.29 (Too Many Requests): The request cannot be processed because the tenant's message limit for the current period
    is exceeded.
  * 5.03 (Service Unavailable): The request cannot be processed. Possible reasons for this include:
    * There is no consumer of telemetry data for the given tenant connected to Hono, or the consumer has not indicated
      that it may receive further messages (not giving credits).
    * If the message type is `CON` (*at least once* semantics), the reason may be:
      * The consumer has indicated that it didn't process the telemetry data.
      * The consumer failed to indicate in time whether it has processed the telemetry data.

This resource MUST be used by devices that have not authenticated to the protocol adapter.

{{% notice info %}}
In order to allow devices to use this resource, the protocol adapter's *HONO_COAP_AUTHENTICATIONREQUIRED*
configuration property needs to be set to `false` explicitly.
{{% /notice %}}

**Examples**

Publish some JSON data for device `4711` using default message type `CON` (*at least once*):

~~~sh
coap-client -m PUT coap://hono.eclipseprojects.io/t/DEFAULT_TENANT/4711 -t application/json -e '{"temp": 5}'
~~~

Publish some JSON data for device `4711` using message type `NON` (*at most once*):

~~~sh
coap-client -N -m PUT coap://hono.eclipseprojects.io/t/DEFAULT_TENANT/4711 -t application/json -e '{"temp": 5}'
~~~

Publish some JSON data for device `4711`, indicating that the device will wait for 10 seconds to receive the response:

~~~sh
coap-client -m PUT coap://hono.eclipseprojects.io/t/DEFAULT_TENANT/4711?hono-ttd=10 -t application/json -e '{"temp": 5}'

{
  "brightness": 87
}
~~~

{{% notice info %}}
The example commands above actually do not work with the Hono Sandbox because it requires devices to always
authenticate. However, these commands can be used with a custom Hono installation that allows connections from
unauthenticated devices.
{{% /notice %}}

## Publish Telemetry Data (authenticated Gateway)

* URI:
  * `/t//${deviceId}`
  * `/telemetry//${deviceId}`
  * `/t/${tenantId}/${deviceId}`
  * `/telemetry/${tenantId}/${deviceId}`
* Method: `PUT`
* Type:
  * `CON`: *at least once* delivery semantics
  * `NON`: *at most once* delivery semantics
* Request Options:
  * (optional) *content-format*: The type of payload contained in the request body. Required, if request body is empty
    but message is not an [empty notification]({{< relref "/api/event#empty-notification" >}}).
    The content type corresponding to the given content format, if not empty, will be used in the message being forwarded
    downstream. Otherwise, the content type of the downstream message will be set to `application/octet-stream`, if the payload
    is not empty and no default content type has been defined for the origin device or its tenant (see
    [Downstream Meta Data]({{< relref "#downstream-meta-data" >}})).
  * (optional) *experimental option 0xfde8 (time)*: Request that the response include the server time.
    If this option is present in the request, the server will add the same option to its response where the value will
    indicate the server time (as milliseconds since UNIX epoch). The request option does not need to have a value, and
    any value will, in fact, be ignored.
* Query Parameters:
  * (optional) *hono-ttd*: The number of seconds the device will wait for the response.
  * (optional) *hono-time*: Indicates that the client wants to receive the server time as a response option.
  * (optional) *empty*: Marks the request as an [empty notification]({{< relref "/api/event#empty-notification" >}}).
* Request Body:
  * (optional) Arbitrary payload matching the given content type.
* Response Options:
  * (optional) *content-format*: A media type describing the semantics and format of payload contained in the response body.
    This option will only be present if the response contains a command to be executed by the device which requires input
    data. Note that this option will be empty if the media type contained in the command (AMQP) message's *content-type*
    property cannot be mapped to one of the registered CoAP *content-format* codes.
  * (optional) *location-query*: The *hono-command* query parameter contains the name of the command to execute.
    This option will only be present if the response contains a command to be executed by the device.
  * (optional) *location-path*: This option will only be present if the response
    contains a command to be executed by the device. The location path is `c//${deviceId}` or `command//${deviceId}` for
    one-way-commands and `cr//${deviceId}/<command-request-id>` or `command_response//${deviceId}/<command-request-id>` for commands
    expecting a response. In the latter case, the *location-path* option contains exactly the URI-path that the device
    must use when sending its response to the command. Note that in both cases the `${deviceId}` path segment indicates
    the device that the command is targeted at. `command` and `command_response` will be used if the request also uses
    the fully spelled out endpoint name.
  * (optional) *experimental option 0xfde8 (time)*: This option will only be present if the request contained this
    option (with or without value) or the request contained the `hono-time` query parameter. The value of this option
    in a response will indicate the server timestamp (as milliseconds since UNIX epoch) at the time the response was
    sent.
* Response Body:
  * (optional) Arbitrary data serving as input to a command to be executed by the device, if status code is 2.05 (Content).
  * (optional) Error details, if status code is >= 4.00.
* Response Codes:
  * 2.04 (Changed): The data in the request body has been accepted for processing. The response may contain a command
    for a device to execute. Note that if the message type is `NON` (*at most once* semantics), this status code does
    **not** mean that the message has been delivered to any potential consumer (yet). However, if the message type is
    `CON` (*at least once* semantics), then the adapter waits for the message to be delivered and accepted by a
    downstream consumer before responding with this status code.
  * 4.00 (Bad Request): The request cannot be processed. Possible reasons include:
    * The *URI-query* option contains the *empty* parameter but the request body is not empty.
    * The request body is empty but the request has no *content-format* set nor does its
      *URI-query* option contain the *empty* parameter.
  * 4.03 (Forbidden): The request cannot be processed because the device's registration status cannot be asserted.
    Possible reasons for this include:
    * The tenant that the gateway belongs to is not allowed to use this protocol adapter.
    * The device belongs to another tenant than the gateway.
    * The gateway is not authorized to act *on behalf of* the device.
    * The gateway associated with the device is not registered or disabled.
  * 4.04 (Not Found): The request cannot be processed because the device is disabled or does not exist.
  * 4.13 (Request Entity Too Large): The request cannot be processed because the request body exceeds the maximum
    supported size.
  * 4.29 (Too Many Requests): The request cannot be processed because the tenant's message limit for the current period
    is exceeded.
  * 5.03 (Service Unavailable): The request cannot be processed. Possible reasons for this include:
    * There is no consumer of telemetry data for the given tenant connected to Hono, or the consumer has not indicated
      that it may receive further messages (not giving credits).
    * If the message type is `CON` (*at least once* semantics), the reason may be:
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

Publish some JSON data on behalf of device `4712` using default message type `CON` (*at least once*):

~~~sh
coap-client -u gw@DEFAULT_TENANT -k gw-secret -m PUT coaps://hono.eclipseprojects.io/t//4712 -t application/json -e '{"temp": 5}'
~~~

Publish some JSON data on behalf of device `4712` using message type `NON` (*at most once*):

~~~sh
coap-client -u gw@DEFAULT_TENANT -k gw-secret -N -m PUT coaps://hono.eclipseprojects.io/t//4712 -t application/json -e '{"temp": 5}'
~~~

Publish some JSON data on behalf of device `4712`, indicating that the gateway will wait for 10 seconds to receive the response:

~~~sh
coap-client -u gw@DEFAULT_TENANT -k gw-secret -m PUT coaps://hono.eclipseprojects.io/t//4712?hono-ttd=10 -t application/json -e '{"temp": 5}'

{
  "brightness": 87
}
~~~

{{% notice info %}}
The example above assumes that a gateway device has been registered with `psk` credentials with
*auth-id* `gw` and secret `gw-secret` which is authorized to publish data *on behalf of* device `4712`.
{{% /notice %}}

## Publish an Event (authenticated Device)

* URI:
  * `/e`
  * `/event`
* Method: `POST`
* Type:`CON`
* Request Options:
  * (optional) *content-format*: The type of payload contained in the request body. Required, if request body is empty
    but message is not an [empty notification]({{< relref "/api/event#empty-notification" >}}).
    The content type corresponding to the given content format, if not empty, will be used in the message being forwarded
    downstream. Otherwise, the content type of the downstream message will be set to `application/octet-stream`, if the payload
    is not empty and no default content type has been defined for the origin device or its tenant (see
    [Downstream Meta Data]({{< relref "#downstream-meta-data" >}})).
  * (optional) *experimental option 0xfde8 (time)*: Request that the response include the server time.
    If this option is present in the request, the server will add the same option to its response where the value will
    indicate the server time (as milliseconds since UNIX epoch). The request option does not need to have a value, and
    any value will, in fact, be ignored.
* Query Parameters:
  * (optional) *hono-ttd*: The number of seconds the device will wait for the response.
  * (optional) *hono-time*: Indicates that the client wants to receive the server time as a response option.
  * (optional) *empty*: Marks the request as an [empty notification]({{< relref "/api/event#empty-notification" >}}).
* Request Body:
  * (optional) Arbitrary payload matching the given content type.
* Response Options:
  * (optional) *content-format*: A media type describing the semantics and format of payload contained in the response body.
    This option will only be present if the response contains a command to be executed by the device which requires input
    data. Note that this option will be empty if the media type contained in the command (AMQP) message's *content-type*
    property cannot be mapped to one of the registered CoAP *content-format* codes.
  * (optional) *location-query*: The *hono-command* query parameter contains the name of the command to execute.
    This option will only be present if the response contains a command to be executed by the device.
  * (optional) *location-path*: This option will only be present if the response contains a command to be executed
    by the device. The location path is `c` or `command` for one-way-commands and `cr/<command-request-id>` or
    `command_response/<command-request-id>` for commands expecting a response.
    In the latter case, the *location-path* option contains exactly the URI-path that the device must use when sending
    its response to the command. `command` and `command_response` will be used if the request also uses the fully spelled
    out endpoint name.
  * (optional) *experimental option 0xfde8 (time)*: This option will only be present if the request contained this
    option (with or without value) or the request contained the `hono-time` query parameter. The value of this option
    in a response will indicate the server timestamp (as milliseconds since UNIX epoch) at the time the response was
    sent.
* Response Body:
  * (optional) Arbitrary data serving as input to a command to be executed by the device, if status code is 2.05 (Content).
  * (optional) Error details, if status code is >= 4.00.
* Response Codes:
  * 2.04 (Changed): The data in the request body has been accepted for processing. The response may contain a command
    for the device to execute. Note that if the message type is `NON` (*at most once* semantics), this status code does
    **not** mean that the message has been delivered to any potential consumer (yet). However, if the message type is
    `CON` (*at least once* semantics), then the adapter waits for the message to be delivered and accepted by a
    downstream consumer before responding with this status code.
  * 4.00 (Bad Request): The request cannot be processed. Possible reasons include:
    * The *URI-query* option contains the *empty* parameter but the request body is not empty.
    * The request body is empty but the request has no *content-format* set nor does its
      *URI-query* option contain the *empty* parameter.
  * 4.03 (Forbidden): The request cannot be processed because the device's registration status cannot be asserted.
    Possible reasons for this include:
    * The given tenant is not allowed to use this protocol adapter.
  * 4.04 (Not Found): The request cannot be processed because the device is disabled or does not exist.
  * 4.13 (Request Entity Too Large): The request cannot be processed because the request body exceeds the maximum
    supported size.
  * 4.29 (Too Many Requests): The request cannot be processed because the tenant's message limit for the current period
    is exceeded.
  * 5.03 (Service Unavailable): The request cannot be processed because there is no consumer of events for the given
    tenant connected to Hono, or the consumer didn't process the event.

This is the preferred way for devices to publish events. It is available only if the protocol adapter is configured to
require devices to authenticate (which is the default).

**Examples**

The examples provided below make use of the *coap-client* command line tool which is part of the
[libcoap project](https://libcoap.net/). Precompiled packages should be available for different Linux variants.

Publish some JSON data for device `4711` using default message type `CON` (*at least once*):

~~~sh
coap-client -u sensor1@DEFAULT_TENANT -k hono-secret -m POST coaps://hono.eclipseprojects.io/e -t application/json -e '{"temp": 5}'
~~~

{{% notice tip %}}
*coap-client* only reports error response-codes, so the expected 2.04 response code will not be printed to the terminal.
{{% /notice %}}

Publish some JSON data for device `4711`, indicating that the device will wait for 10 seconds to receive the response:

~~~sh
coap-client -u sensor1@DEFAULT_TENANT -k hono-secret -m POST coaps://hono.eclipseprojects.io/e?hono-ttd=10 -t application/json -e '{"temp": 5}'

{
  "brightness": 87
}
~~~

{{% notice info %}}
In the example above the response actually contains payload that should be used as input to a command to be executed
by the device. This is just for illustrative purposes. You will usually get an empty response because there is no
downstream application attached which could send any commands to the device.
{{% /notice %}}

## Publish an Event (unauthenticated Device)

* URI:
  * `/e/${tenantId}/${deviceId}`
  * `/event/${tenantId}/${deviceId}`
* Method: `PUT`
* Type:`CON`
* Request Options:
  * (optional) *content-format*: The type of payload contained in the request body. Required, if request body is empty
    but message is not an [empty notification]({{< relref "/api/event#empty-notification" >}}).
    The content type corresponding to the given content format, if not empty, will be used in the message being forwarded
    downstream. Otherwise, the content type of the downstream message will be set to `application/octet-stream`, if the payload
    is not empty and no default content type has been defined for the origin device or its tenant (see
    [Downstream Meta Data]({{< relref "#downstream-meta-data" >}})).
  * (optional) *experimental option 0xfde8 (time)*: Request that the response include the server time.
    If this option is present in the request, the server will add the same option to its response where the value will
    indicate the server time (as milliseconds since UNIX epoch). The request option does not need to have a value, and
    any value will, in fact, be ignored.
* Query Parameters:
  * (optional) *hono-ttd*: The number of seconds the device will wait for the response.
  * (optional) *hono-time*: Indicates that the client wants to receive the server time as a response option.
  * (optional) *empty*: Marks the request as an [empty notification]({{< relref "/api/event#empty-notification" >}}).
* Request Body:
  * (optional) Arbitrary payload matching the given content type.
* Response Options:
  * (optional) *content-format*: A media type describing the semantics and format of payload contained in the response body.
    This option will only be present if the response contains a command to be executed by the device which requires input
    data. Note that this option will be empty if the media type contained in the command (AMQP) message's *content-type*
    property cannot be mapped to one of the registered CoAP *content-format* codes.
  * (optional) *location-query*: The *hono-command* query parameter contains the name of the command to execute.
    This option will only be present if the response contains a command to be executed by the device.
  * (optional) *location-path*: This option will only be present if the response contains
    a command to be executed by the device. The location path is `c` or `command` for one-way-commands and
    `cr/${tenantId}/${deviceId}/<command-request-id>` or `command_response/${tenantId}/${deviceId}/<command-request-id>` for commands
    expecting  a response. In the latter case, the *location-path* option contains exactly the URI-path that the
    device must use when sending its response to the command. `command` and `command_response` will be used if the request
    also uses the fully spelled out endpoint name.
  * (optional) *experimental option 0xfde8 (time)*: This option will only be present if the request contained this
    option (with or without value) or the request contained the `hono-time` query parameter. The value of this option
    in a response will indicate the server timestamp (as milliseconds since UNIX epoch) at the time the response was
    sent.
* Response Body:
  * (optional) Arbitrary data serving as input to a command to be executed by the device, if status code is 2.05 (Content).
  * (optional) Error details, if status code is >= 4.00.
* Response Codes:
  * 2.04 (Changed): The data in the request body has been accepted for processing. The response may contain a command
    for the device to execute. Note that if the message type is `NON` (*at most once* semantics), this status code does
    **not** mean that the message has been delivered to any potential consumer (yet). However, if the message type is
    `CON` (*at least once* semantics), then the adapter waits for the message to be delivered and accepted by a
    downstream consumer before responding with this status code.
  * 4.00 (Bad Request): The request cannot be processed. Possible reasons include:
    * The *URI-query* option contains the *empty* parameter but the request body is not empty.
    * The request body is empty but the request has no *content-format* set nor does its
      *URI-query* option contain the *empty* parameter.
  * 4.03 (Forbidden): The request cannot be processed because the device's registration status cannot be asserted.
    Possible reasons for this include:
    * The given tenant is not allowed to use this protocol adapter.
    * The given device does not belong to the given tenant.
  * 4.04 (Not Found): The request cannot be processed because the device is disabled or does not exist.
  * 4.13 (Request Entity Too Large): The request cannot be processed because the request body exceeds the maximum
    supported size.
  * 4.29 (Too Many Requests): The request cannot be processed because the tenant's message limit for the current period
    is exceeded.
  * 5.03 (Service Unavailable): The request cannot be processed because there is no consumer of events for the given
    tenant connected to Hono, or the consumer didn't process the event.

This resource MUST be used by devices that have not authenticated to the protocol adapter.

{{% notice info %}}
In order to allow devices to use this resource, the protocol adapter's *HONO_COAP_AUTHENTICATIONREQUIRED*
configuration property needs to be set to `false` explicitly.
{{% /notice %}}

**Examples**

Publish some JSON data for device `4711` using default message type `CON` (*at least once*):

~~~sh
coap-client -m PUT coap://hono.eclipseprojects.io/e/DEFAULT_TENANT/4711 -t application/json -e '{"temp": 5}'
~~~

Publish some JSON data for device `4711`, indicating that the device will wait for 10 seconds to receive the response:

~~~sh
coap-client -m PUT coap://hono.eclipseprojects.io/e/DEFAULT_TENANT/4711?hono-ttd=10 -t application/json -e '{"temp": 5}'

{
  "brightness": 87
}
~~~

{{% notice info %}}
The example commands above actually do not work with the Hono Sandbox because it requires devices to always
authenticate. However, these commands can be used with a custom Hono installation that allows connections from
unauthenticated devices.
{{% /notice %}}

## Publish an Event (authenticated Gateway)

* URI:
  * `/e//${deviceId}`
  * `/event//${deviceId}`
  * `/e/${tenantId}/${deviceId}`
  * `/event/${tenantId}/${deviceId}`
* Method: `PUT`
* Type:`CON`
* Request Options:
  * (optional) *content-format*: The type of payload contained in the request body. Required, if request body is empty
    but message is not an [empty notification]({{< relref "/api/event#empty-notification" >}}).
    The content type corresponding to the given content format, if not empty, will be used in the message being forwarded
    downstream. Otherwise, the content type of the downstream message will be set to `application/octet-stream`, if the payload
    is not empty and no default content type has been defined for the origin device or its tenant (see
    [Downstream Meta Data]({{< relref "#downstream-meta-data" >}})).
  * (optional) *experimental option 0xfde8 (time)*: Request that the response include the server time.
    If this option is present in the request, the server will add the same option to its response where the value will
    indicate the server time (as milliseconds since UNIX epoch). The request option does not need to have a value, and
    any value will, in fact, be ignored.
* Query Parameters:
  * (optional) *hono-ttd*: The number of seconds the device will wait for the response.
  * (optional) *hono-time*: Indicates that the client wants to receive the server time as a response option.
  * (optional) *empty*: Marks the request as an [empty notification]({{< relref "/api/event#empty-notification" >}}).
* Request Body:
  * (optional) Arbitrary payload matching the given content type.
* Response Options:
  * (optional) *content-format*: A media type describing the semantics and format of payload contained in the response body.
    This option will only be present if the response contains a command to be executed by the device which requires input
    data. Note that this option will be empty if the media type contained in the command (AMQP) message's *content-type*
    property cannot be mapped to one of the registered CoAP *content-format* codes.
  * (optional) *location-query*: The *hono-command* query parameter contains the name of the command to execute.
    This option will only be present if the response contains a command to be executed by the device.
  * (optional) *location-path*: This option will only be present if the response
    contains a command to be executed by the device. The location path is `c//${deviceId}` or `command//${deviceId}` for
    one-way-commands and `cr//${deviceId}/<command-request-id>` or `command_response//${deviceId}/<command-request-id>` for commands
    expecting a response. In the latter case, the *location-path* option contains exactly the URI-path that the device
    must use when sending its response to the command. Note that in both cases the `${deviceId}` path segment indicates
    the device that the command is targeted at. `command` and `command_response` will be used if the request also uses
    the fully spelled out endpoint name.
  * (optional) *experimental option 0xfde8 (time)*: This option will only be present if the request contained this
    option (with or without value) or the request contained the `hono-time` query parameter. The value of this option
    in a response will indicate the server timestamp (as milliseconds since UNIX epoch) at the time the response was
    sent.
* Response Body:
  * (optional) Arbitrary data serving as input to a command to be executed by the device, if status code is 2.05 (Content).
  * (optional) Error details, if status code is >= 4.00.
* Response Codes:
  * 2.04 (Changed): The data in the request body has been accepted for processing. The response may contain a command
    for a device to execute. Note that if the message type is `NON` (*at most once* semantics), this status code does
    **not** mean that the message has been delivered to any potential consumer (yet). However, if the message type is
    `CON` (*at least once* semantics), then the adapter waits for the message to be delivered and accepted by a
    downstream consumer before responding with this status code.
  * 4.00 (Bad Request): The request cannot be processed. Possible reasons include:
    * The *URI-query* option contains the *empty* parameter but the request body is not empty.
    * The request body is empty but the request has no *content-format* set nor does its
      *URI-query* option contain the *empty* parameter.
  * 4.03 (Forbidden): The request cannot be processed because the device's registration status cannot be asserted.
    Possible reasons for this include:
    * The tenant that the gateway belongs to is not allowed to use this protocol adapter.
    * The device belongs to another tenant than the gateway.
    * The gateway is not authorized to act *on behalf of* the device.
    * The gateway associated with the device is not registered or disabled.
  * 4.04 (Not Found): The request cannot be processed because the device is disabled or does not exist.
  * 4.13 (Request Entity Too Large): The request cannot be processed because the request body exceeds the maximum
    supported size.
  * 4.29 (Too Many Requests): The request cannot be processed because the tenant's message limit for the current period
    is exceeded.
  * 5.03 (Service Unavailable): The request cannot be processed because there is no consumer of events for the given
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

Publish some JSON data on behalf of device `4712` using default message type `CON` (*at least once*):

~~~sh
coap-client -u gw@DEFAULT_TENANT -k gw-secret -m PUT coaps://hono.eclipseprojects.io/e//4712 -t application/json -e '{"temp": 5}'
~~~

Publish some JSON data on behalf of device `4712`, indicating that the gateway will wait for 10 seconds to receive the response:

~~~sh
coap-client -u gw@DEFAULT_TENANT -k gw-secret -m PUT coaps://hono.eclipseprojects.io/e//4712?hono-ttd=10 -t application/json -e '{"temp": 5}'

{
  "brightness": 87
}
~~~

{{% notice info %}}
The example above assumes that a gateway device has been registered with `psk` credentials with
*auth-id* `gw` and secret `gw-secret` which is authorized to publish data *on behalf of* device `4712`.
{{% /notice %}}

## Command & Control

The CoAP adapter enables devices to receive commands that have been sent by business applications. Commands are
delivered to the device by means of a response message. That means a device first has to send a request, indicating
how long it will wait for the response. That request can either be a telemetry or event message, with a *hono-ttd*
query parameter (*time till disconnect*) specifying the number of seconds that the device will wait for the response.
The business application can react on that message by sending a command message, targeted at the device. The CoAP
adapter will then send the command message as part of the response message to the device.

### Commands handled by gateways

Authenticated gateways will receive commands for devices which do not connect to a protocol adapter directly but
instead are connected to the gateway. Corresponding devices have to be configured so that they can be used with a
gateway.
See [Connecting via a Device Gateway]({{< relref "/concepts/connecting-devices#connecting-via-a-device-gateway" >}})
for details.

A gateway can send a request with the *hono-ttd* query parameter on the `/e` or `/t` URIs, indicating its readiness
to receive a command for *any* of the devices that it acts on behalf of. Note that in this case, the business
application will be notified with the gateway's identifier in the `device_id` property of the downstream message.

An authenticated gateway can also indicate its readiness to receive a command targeted at a *specific* device. For that,
the `/e//${deviceId}` or `/t//${deviceId}` URIs are to be used, containing the identifier of the device to
receive a command for. The business application will receive a notification with that device identifier.

If there are multiple concurrent requests with a *hono-ttd* query parameter, sent by the command target device and/or one
or more of its potential gateways, the CoAP adapter will choose the device or gateway to send the command to as follows:

* A request done by the command target device or by a gateway specifically done for that device, has precedence. If
  there are multiple concurrent such requests, the last one will get the command message (if received) in its response.
  Note that the other requests won't be answered with a command message in their response event if the business
  application sent multiple command messages. This means that commands for a single device can only be requested
  sequentially, but not in parallel.
* If the above doesn't apply, a single *hono-ttd* request on the `/e` or `/t` URIs, sent by a gateway that the
  command target device is configured for, will get the command message in its response.
* If there are multiple concurrent such requests by different gateways, all configured for the command target device,
  the request by the gateway will be chosen, through which the target device has last sent a telemetry or event message.
  If the target device hasn't sent a message yet and it is thereby unknown via which gateway the device communicates,
  then one of the requests will be chosen randomly to include the command in its response. 

### Sending a Response to a Command (authenticated Device)

* URI:
  * `/cr/${commandRequestId}`
  * `/command_response/${commandRequestId}`
* Method: `POST`
* Type: `CON`
* Request Options:
  * (optional) `content-type`: A media type describing the semantics and format of the payload contained in the request body.
    This option must be set if the result of processing the command on the device is non-empty. In this case the result
    data is contained in the request body.
* Query Parameters:
  * (required) `hono-cmd-status`: An HTTP status code indicating the outcome of processing the command.
* Request Body:
  * (optional) Arbitrary data representing the result of processing the command on the device.
* Response Codes:
  * 2.04 (Changed): The response has been successfully delivered to the application that has sent the command.
  * 4.00 (Bad Request): The request cannot be processed because the command status or command request ID are
    missing/malformed.
  * 4.03 (Forbidden): The request cannot be processed because the device's registration status cannot be asserted.
    Possible reasons for this include:
    * The given tenant is not allowed to use this protocol adapter.
  * 4.04 (Not Found): The request cannot be processed because the device is disabled or does not exist.
  * 4.13 (Request Entity Too Large): The request cannot be processed because the request body exceeds the maximum
    supported size.
  * 4.29 (Too Many Requests): The request cannot be processed because the tenant's message limit for the current period
    is exceeded.
  * 5.03 (Service Unavailable): The request cannot be processed. Possible reasons for this include:
    * There is no application listening for a reply to the given *commandRequestId*.
    * The application has already given up on waiting for a response.

This is the preferred way for devices to respond to commands. It is available only if the protocol adapter is configured
to require devices to authenticate (which is the default).

**Example**

Send a response to a previously received command with the command-request-id `req-id-uuid` for device `4711`:

~~~sh
coap-client -u sensor1@DEFAULT_TENANT -k hono-secret coaps://hono.eclipseprojects.io/cr/req-id-uuid?hono-cmd-status=200
~~~

### Sending a Response to a Command (unauthenticated Device)

* URI:
  * `/cr/${tenantId}/${deviceId}/${commandRequestId}`
  * `/command_response/${tenantId}/${deviceId}/${commandRequestId}`
* Method: `PUT`
* Type: `CON`
* Request Options:
  * (optional) `content-type`: A media type describing the semantics and format of the payload contained in the request body.
    This option must be set if the result of processing the command on the device is non-empty. In this case the result
    data is contained in the request body.
* Query Parameters:
  * (required) `hono-cmd-status`: An HTTP status code indicating the outcome of processing the command.
* Request Body:
  * (optional) Arbitrary data representing the result of processing the command on the device.
* Response Codes:
  * 2.04 (Changed): The response has been successfully delivered to the application that has sent the command.
  * 4.00 (Bad Request): The request cannot be processed because the command status or command request ID are
    missing/malformed.
  * 4.03 (Forbidden): The request cannot be processed because the device's registration status cannot be asserted.
    Possible reasons for this include:
    * The given tenant is not allowed to use this protocol adapter.
    * The given device does not belong to the given tenant.
  * 4.04 (Not Found): The request cannot be processed because the device is disabled or does not exist.
  * 4.13 (Request Entity Too Large): The request cannot be processed because the request body exceeds the maximum
    supported size.
  * 4.29 (Too Many Requests): The request cannot be processed because the tenant's message limit for the current period
    is exceeded.
  * 5.03 (Service Unavailable): The request cannot be processed. Possible reasons for this include:
    * There is no application listening for a reply to the given *commandRequestId*.
    * The application has already given up on waiting for a response.

This resource MUST be used by devices that have not authenticated to the protocol adapter.

{{% notice info %}}
In order to allow devices to use this resource, the protocol adapter's *HONO_COAP_AUTHENTICATIONREQUIRED*
configuration property needs to be set to `false` explicitly.
{{% /notice %}}

**Examples**

Send a response to a previously received command with the command-request-id `req-id-uuid` for the unauthenticated
device `4711`:

~~~sh
coap-client -u sensor1@DEFAULT_TENANT -k hono-secret coaps://hono.eclipseprojects.io/cr/DEFAULT_TENANT/4711/req-id-uuid?hono-cmd-status=200 -e '{"brightness-changed": true}'
~~~

{{% notice info %}}
The example command above actually does not work with the Hono Sandbox because it requires devices to always
authenticate. However, this command can be used with a custom Hono installation that allows connections from
unauthenticated devices.
{{% /notice %}}

### Sending a Response to a Command (authenticated Gateway)

* URI:
  * `/cr//${deviceId}/${commandRequestId}`
  * `/command_response//${deviceId}/${commandRequestId}`
  * `/cr/${tenantId}/${deviceId}/${commandRequestId}`
  * `/command_response/${tenantId}/${deviceId}/${commandRequestId}`
* Method: `PUT`
* Type: `CON`
* Request Options:
  * (optional) `content-type`: A media type describing the semantics and format of the payload contained in the request body.
    This option must be set if the result of processing the command on the device is non-empty. In this case the result
    data is contained in the request body.
* Query Parameters:
  * (required) `hono-cmd-status`: An HTTP status code indicating the outcome of processing the command.
* Request Body:
  * (optional) Arbitrary data representing the result of processing the command on the device.
* Response Codes:
  * 2.04 (Changed): The response has been successfully delivered to the application that has sent the command.
  * 4.00 (Bad Request): The request cannot be processed because the command status or command request ID are
    missing/malformed.
  * 4.03 (Forbidden): The request cannot be processed because the device's registration status cannot be asserted.
    Possible reasons for this include:
    * The given tenant is not allowed to use this protocol adapter.
    * The given device does not belong to the given tenant.
    * The gateway is not authorized to act *on behalf of* the device.
    * The gateway associated with the device is not registered or disabled.
  * 4.04 (Not Found): The request cannot be processed because the device is disabled or does not exist.
  * 4.13 (Request Entity Too Large): The request cannot be processed because the request body exceeds the maximum
    supported size.
  * 4.29 (Too Many Requests): The request cannot be processed because the tenant's message limit for the current period
    is exceeded.
  * 5.03 (Service Unavailable): The request cannot be processed. Possible reasons for this include:
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

Send a response to a previously received command with the command-request-id `req-id-uuid` on behalf of device `4712`:

~~~sh
coap-client -u gw@DEFAULT_TENANT -k gw-secret coaps://hono.eclipseprojects.io/cr//4712/req-id-uuid?hono-cmd-status=200 -e '{"brightness-changed": true}'
~~~

{{% notice info %}}
The example above assumes that a gateway device has been registered with `psk` credentials with
*auth-id* `gw` and secret `gw-secret` which is authorized to publish data *on behalf of* device `4712`.
{{% /notice %}}

## Downstream Meta Data

The adapter includes the following meta data in messages being sent downstream:

| Name               | Type      | Description                                                     |
| :----------------- | :-------- | :-------------------------------------------------------------- |
| *device_id*        | *string*  | The identifier of the device that the message originates from.  |
| *orig_adapter*     | *string*  | Contains the adapter's *type name* which can be used by downstream consumers to determine the protocol adapter that the message has been received over. The CoAP adapter's type name is `hono-coap`. |
| *orig_address*     | *string*  | Contains the (relative) URI that the device has originally posted the data to. |
| *ttd*              | *integer* | Contains the effective number of seconds that the device will wait for a response. This property is only set if the request contains the *hono-ttd* *URI-query* option. |

The adapter also considers *defaults* registered for the device at either the
[tenant]({{< relref "/api/tenant#tenant-information-format" >}}) or the
[device level]({{< relref "/api/device-registration#assert-device-registration" >}}).
The values of the default properties are determined as follows:

1. If the message already contains a non-empty property of the same name, its value remains unchanged.
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

The adapter uses the [Tenant API]({{< ref "/api/tenant#get-tenant-information" >}}) to retrieve *tenant specific configuration* for adapter type `hono-coap`.
The following properties are (currently) supported:

| Name               | Type       | Default Value | Description                                                     |
| :----------------- | :--------- | :------------ | :-------------------------------------------------------------- |
| *enabled*          | *boolean*  | `true`       | If set to `false` the adapter will reject all data from devices belonging to the tenant. |
| *max-ttd*          | *integer*  | `60`         | Defines a tenant specific upper limit for the *time until disconnect* property that devices may include in requests for uploading telemetry data or events. Please refer to the [Command & Control concept page]({{< relref "/concepts/command-and-control/index.md" >}}) for a discussion of this parameter's purpose and usage.<br>This property can be set for the `hono-coap` adapter type as an *extension* property in the adapter section of the tenant configuration.<br>If it is not set, then the default value of `60` seconds is used.|
| *timeoutToAck*     | *integer*  | -             | This property has the same semantics as the [corresponding property at the adapter level]({{< relref "admin-guide/coap-adapter-config" >}}). However, any (non-null) value configured for a tenant takes precedence over the adapter level value for all devices of the particular tenant. |
