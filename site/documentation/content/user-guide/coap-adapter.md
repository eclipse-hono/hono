+++
title = "CoAP Adapter"
weight = 225
+++

The CoAP protocol adapter exposes [CoAP](https://tools.ietf.org/html/rfc7252) based endpoints for Eclipse Hono&trade;'s south bound Telemetry, Event and Command & Control APIs.
<!--more-->

## Device Authentication

The CoAP adapter by default requires clients (devices or gateway components) to authenticate during connection establishment.
The adapter (currently) only supports [PSK](https://tools.ietf.org/html/rfc4279) as part of a DTLS handshake for that purpose.
Additional variants mentioned in [Securing CoAP](https://tools.ietf.org/html/rfc7252#section-9) might be added in the future.

The adapter tries to authenticate the device using these mechanisms in the following order

### PSK

The *identity* provided in the ClientKeyExchange must have the form *auth-id@tenant*, e.g. `sensor1@DEFAULT_TENANT`. The adapter performs the handshake using the credentials the `configured Credentials service - (documentation pending)` has one record for the client. The adapter uses the Credentials API's *get* operation to retrieve the credentials on record with the *tenant* and *auth-id* provided by the device in the *identity* and `psk` as the *type* of secret as query parameters.

The examples below refer to devices `4711` and `gw-1` of tenant `DEFAULT_TENANT` using *auth-ids* `sensor1` and `gw1` and corresponding passwords. The example deployment as described in the [Deployment Guides]({{< relref "deployment" >}}) comes pre-configured with the corresponding entities in its device registry component.
Please refer to the [Credentials API]({{< relref "/api/credentials#standard-credential-types" >}}) for details regarding the different types of secrets.

**NB** There is a subtle difference between the *device identifier* (*device-id*) and the *auth-id* a device uses for authentication. See [Device Identity]({{< relref "/concepts/device-identity.md" >}}) for a discussion of the concepts.

## Message Limits

Before accepting any telemetry or event or command messages, the CoAP adapter verifies that the configured [message limit]({{< relref "/concepts/resource-limits#messages-limit" >}}) is not exceeded.
For CoAP currently only the messages limit is verified, the connections limit is not available. If the limit is exceeded then the incoming message is discarded with the status code `429 Too Many Requests`. 

## CoAP Content Format Codes

CoAP doesn't use a textual identifier for content types. Instead numbers are used, which are maintained by the [IANA](https://www.iana.org/).
The [IANA - CoAP Content Formats](https://www.iana.org/assignments/core-parameters/core-parameters.xhtml#content-formats) page lists all
(currently) registered codes and the corresponding media types.

## Publish Telemetry Data (authenticated Device)

The device is authenticated using PSK.

* URI: `/telemetry`
* Method: `POST`
* Type:
  * `CON`: *at least once* (`1`) QoS levels
  * `NON`: *at most once* (`0`) QoS levels
* Request Options:
  * (optional) `content-format`: The type of payload contained in the request body. Required, if request contains payload.
  * (optional) `URI-query: hono-ttd`: The number of seconds the device will wait for the response.
  * (optional) `URI-query: empty`: Marks the request as an [empty notification]({{< relref "/api/event#empty-notification" >}}).
* Request Body:
  * (optional) Arbitrary payload encoded according to the given content type. Maybe empty, if `URI-query: empty` is provided.
* Response Options:
  * (optional) `content-format`: A media type describing the semantics and format of payload contained in the response body.
    This option will only be present if the response contains a command to be executed by the device which requires input data.
    Note that this option will be empty if the media type contained in the command (AMQP) message's *content-type* property cannot
    be mapped to one of the registered CoAP *content-format* codes.
  * (optional) `location-query: hono-command`: The name of the command to execute.
    This option will only be present if the response contains a command to be executed by the device.
  * (optional) `location-path`: The location path contains `command` for one-way-commands,
    or `command_response/<command-request-id>` for commands expecting  a response.
    In the latter case, the *location-path* option contains exactly the URI-path that the device must use when sending its
    response to the command.
* Response Body:
  * (optional) Arbitrary data serving as input to a command to be executed by the device, if status code is 2.05 (Content).
  * (optional) Error details, if status code is >= 4.00.
* Response Codes:
  * 2.04 (Changed): The data in the request body has been accepted for processing.
    Note that if the message type is `NON` (*at most once* semantics), this status code does **not** mean that the message has been
    delivered to any potential consumer. However, if the message type is `CON` (*at least once* semantics), then the adapter waits
    for the message to be delivered and accepted by a downstream consumer before responding with this status code.
  * 2.05 (Content): The data in the request body has been accepted for processing. The response contains a command for the device to execute.
    Note that if the message type is `NON` (*at most once* semantics), this status code does **not** mean that the message has been
    delivered to any potential consumer. However, if the message type is `CON` (*at least once* semantics), then the adapter waits
    for the message to be delivered and accepted by a downstream consumer before responding with this status code.
  * 4.00 (Bad Request): The request cannot be processed. Possible reasons include:
         * the request body is empty and the *URI-query* option doesn't contain the `empty` parameter
  * 4.03 (Forbidden): The request cannot be processed because the device's registration status cannot be asserted.
    Possible reasons for this include:
        * The given tenant is not allowed to use this protocol adapter.
  * 4.04 (Not Found): The request cannot be processed because the device is disabled or does not exist.
  * 4.29 (Too Many Requests): The request cannot be processed because the tenant's message limit for the current period is exceeded.
  * 5.03 (Service Unavailable): The request cannot be processed because there is no consumer of telemetry data for the given tenant
    connected to Hono.

This is the preferred way for devices to publish telemetry data. It is available only if the protocol adapter is configured to require
devices to authenticate (which is the default).

If the `hono-ttd` *URI-query* option is set in order to receive a command and if the authenticated device is actually a gateway,
the returned command will be the first command that the north bound application has sent to either the gateway itself or to *any*
device that has last sent a telemetry or event message via this gateway.

**Examples**

The examples provided below make use of the *coap-client* command line tool which is part of the [libcoap project](https://libcoap.net/).
Precompiled packages should be available for different Linux variants.

Publish some JSON data for device `4711` using default message type `CON` (*at least once*):

~~~sh
./coap-client -u sensor1@DEFAULT_TENANT -k hono-secret -m POST coaps://hono.eclipseprojects.io/telemetry -t application/json -e '{"temp": 5}'
~~~

{{% note %}}
*coap-client* only reports error response-codes, so the expected 2.04 response code will not be printed to the terminal.
{{% /note %}}

Publish some JSON data for device `4711` using message type `NON` (*at most once*):

~~~sh
./coap-client -u sensor1@DEFAULT_TENANT -k hono-secret -N -m POST coaps://hono.eclipseprojects.io/telemetry -t application/json -e '{"temp": 5}'
~~~

Publish some JSON data for device `4711`, indicating that the device will wait for 10 seconds to receive the response:

~~~sh
./coap-client -u sensor1@DEFAULT_TENANT -k hono-secret -m POST coaps://hono.eclipseprojects.io/telemetry?hono-ttd=10 -t application/json -e '{"temp": 5}'

{
  "brightness": 87
}
~~~

{{% note %}}
In the example above the response actually contains payload that should be used as input to a command to be executed by the device.
This is just for illustrative purposes. You will usually get an empty response because there is no downstream application attached which could
send any commands to the device.
{{% /note %}}

## Publish Telemetry Data (unauthenticated Device)

* URI: `/telemetry/${tenantId}/${deviceId}`
* Method: `PUT`
* Type:
  * `CON`: *at least once* (`1`) QoS levels
  * `NON`: *at most once* (`0`) QoS levels
* Request Options:
  * (optional) `content-format`: The type of payload contained in the request body. Required, if request contains payload.
  * (optional) `URI-query: hono-ttd`: The number of seconds the device will wait for the response.
  * (optional) `URI-query: empty`: Marks the request as an [empty notification]({{< relref "/api/event#empty-notification" >}}).
* Request Body:
  * (optional) Arbitrary payload encoded according to the given content type. Maybe empty, if `URI-query: empty` is provided.
* Response Options:
  * (optional) `content-format`: A media type describing the semantics and format of payload contained in the response body.
    This option will only be present if the response contains a command to be executed by the device which requires input data.
    Note that this option will be empty if the media type contained in the command (AMQP) message's *content-type* property cannot
    be mapped to one of the registered CoAP *content-format* codes.
  * (optional) `location-query: hono-command`: The name of the command to execute.
    This option will only be present if the response contains a command to be executed by the device.
  * (optional) `location-path`: The location path contains `command` for one-way-commands,
    or `command_response/<command-request-id>` for commands expecting  a response.
    In the latter case, the *location-path* option contains exactly the URI-path that the device must use when sending its
    response to the command.
* Response Body:
  * (optional) Arbitrary data serving as input to a command to be executed by the device, if status code is 2.05 (Content).
  * (optional) Error details, if status code is >= 4.00.
* Response Codes:
  * 2.04 (Changed): The data in the request body has been accepted for processing.
    Note that if the message type is `NON` (*at most once* semantics), this status code does **not** mean that the message has been
    delivered to any potential consumer. However, if the message type is `CON` (*at least once* semantics), then the adapter waits
    for the message to be delivered and accepted by a downstream consumer before responding with this status code.
  * 2.05 (Content): The data in the request body has been accepted for processing. The response contains a command for the device to execute.
    Note that if the message type is `NON` (*at most once* semantics), this status code does **not** mean that the message has been
    delivered to any potential consumer. However, if the message type is `CON` (*at least once* semantics), then the adapter waits
    for the message to be delivered and accepted by a downstream consumer before responding with this status code.
  * 4.00 (Bad Request): The request cannot be processed. Possible reasons include:
         * the request body is empty and the *URI-query* option doesn't contain the `empty` parameter
  * 4.03 (Forbidden): The request cannot be processed because the device's registration status cannot be asserted.
    Possible reasons for this include:
        * The given tenant is not allowed to use this protocol adapter.
        * The given device does not belong to the given tenant.
  * 4.04 (Not Found): The request cannot be processed because the device is disabled or does not exist.
  * 4.29 (Too Many Requests): The request cannot be processed because the tenant's message limit for the current period is exceeded.
  * 5.03 (Service Unavailable): The request cannot be processed because there is no consumer of telemetry data for the given tenant
    connected to Hono.

This resource MUST be used by devices that have not authenticated to the protocol adapter. Note that this requires the
`HONO_COAP_AUTHENTICATION_REQUIRED` configuration property to be explicitly set to `false`.

**Examples**

Publish some JSON data for device `4711` using default message type `CON` (*at least once*):

~~~sh
./coap-client -m PUT coap://hono.eclipseprojects.io/telemetry/DEFAULT_TENANT/4711 -t application/json -e '{"temp": 5}'
~~~

Publish some JSON data for device `4711` using message type `NON` (*at most once*):

~~~sh
./coap-client -N -m PUT coap://hono.eclipseprojects.io/telemetry/DEFAULT_TENANT/4711 -t application/json -e '{"temp": 5}'
~~~

Publish some JSON data for device `4711`, indicating that the device will wait for 10 seconds to receive the response:

~~~sh
./coap-client -m PUT coap://hono.eclipseprojects.io/telemetry/DEFAULT_TENANT/4711?hono-ttd=10 -t application/json -e '{"temp": 5}'

{
  "brightness": 87
}
~~~

## Publish Telemetry Data (authenticated Gateway)

* URI: `/telemetry/${tenantId}/${deviceId}`
* Method: `PUT`
* Type:
  * `CON`: *at least once* (`1`) QoS levels
  * `NON`: *at most once* (`0`) QoS levels
* Request Options:
  * (optional) `content-format`: The type of payload contained in the request body. Required, if request contains payload.
  * (optional) `URI-query: hono-ttd`: The number of seconds the device will wait for the response.
  * (optional) `URI-query: empty`: Marks the request as an [empty notification]({{< relref "/api/event#empty-notification" >}}).
* Request Body:
  * (optional) Arbitrary payload encoded according to the given content type. Maybe empty, if `URI-query: empty` is provided.
* Response Options:
  * (optional) `content-format`: A media type describing the semantics and format of payload contained in the response body.
    This option will only be present if the response contains a command to be executed by the device which requires input data.
    Note that this option will be empty if the media type contained in the command (AMQP) message's *content-type* property cannot
    be mapped to one of the registered CoAP *content-format* codes.
  * (optional) `location-query: hono-command`: The name of the command to execute.
    This option will only be present if the response contains a command to be executed by the device.
  * (optional) `location-path`: The location path contains `command/${tenantId}/${deviceId}` for one-way-commands,
    or `command_response/${tenantId}/${deviceId}/<command-request-id>` for commands expecting  a response.
    In the latter case, the *location-path* option contains exactly the URI-path that the device must use when sending its
    response to the command. Note that in both cases the `${tenantId}/${deviceId}` path segments indicate the device that
    the command is targeted at.
* Response Body:
  * (optional) Arbitrary data serving as input to a command to be executed by the device, if status code is 2.05 (Content).
  * (optional) Error details, if status code is >= 4.00.
* Response Codes:
  * 2.04 (Changed): The data in the request body has been accepted for processing.
    Note that if the message type is `NON` (*at most once* semantics), this status code does **not** mean that the message has been
    delivered to any potential consumer. However, if the message type is `CON` (*at least once* semantics), then the adapter waits
    for the message to be delivered and accepted by a downstream consumer before responding with this status code.
  * 2.05 (Content): The data in the request body has been accepted for processing. The response contains a command for the device to execute.
    Note that if the message type is `NON` (*at most once* semantics), this status code does **not** mean that the message has been
    delivered to any potential consumer. However, if the message type is `CON` (*at least once* semantics), then the adapter waits
    for the message to be delivered and accepted by a downstream consumer before responding with this status code.
  * 4.00 (Bad Request): The request cannot be processed. Possible reasons include:
         * the request body is empty and the *URI-query* option doesn't contain the `empty` parameter
  * 4.03 (Forbidden): The request cannot be processed because the device's registration status cannot be asserted.
    Possible reasons for this include:
        * The tenant that the gateway belongs to is not allowed to use this protocol adapter.
        * The device belongs to another tenant than the gateway.
        * The gateway is not authorized to act *on behalf of* the device.
        * The gateway associated with the device is not registered or disabled.
  * 4.04 (Not Found): The request cannot be processed because the device is disabled or does not exist.
  * 4.29 (Too Many Requests): The request cannot be processed because the tenant's message limit for the current period is exceeded.
  * 5.03 (Service Unavailable): The request cannot be processed because there is no consumer of telemetry data for the given tenant
    connected to Hono.

This resource can be used by *gateway* components to publish data *on behalf of* other devices which do not connect to a protocol adapter
directly but instead are connected to the gateway, e.g. using some low-bandwidth radio based technology like [SigFox](https://www.sigfox.com)
or [LoRa](https://lora-alliance.org/). In this case the credentials provided by the gateway during connection establishment with the
protocol adapter are used to authenticate the gateway whereas the parameters from the URI are used to identify the device that the gateway
publishes data for.

The protocol adapter checks the gateway's authority to publish data on behalf of the device implicitly by means of retrieving a *registration assertion*
for the device from the [configured Device Registration service]({{< relref "/admin-guide/http-adapter-config#device-registration-service-connection-configuration" >}}).

{{% note %}}
When sending requests with the *hono-ttd* query parameter in order to receive a command for a specific device connected to the authenticated gateway,
it has to be noted that multiple concurrent such requests for the same gateway but different devices may lead to some commands not getting
forwarded to the gateway.
To resolve such potential issues, the corresponding tenant can be configured with the *support-concurrent-gateway-device-command-requests*
option set to `true` in the *ext* field of an *adapters* entry of type `hono-coap`. Note that with this option set, it is not supported for
the authenticated gateway to send a single request with a *hono-ttd* query parameter but no device id in order to receive commands for
*any* device that has last sent a telemetry or event message via the authenticated gateway.
{{% /note %}}

**Examples**

Publish some JSON data for device `4712` using default message type `CON` (*at least once*):

~~~sh
./coap-client -u gw@DEFAULT_TENANT -k gw-secret -m PUT coaps://hono.eclipseprojects.io/telemetry/DEFAULT_TENANT/4712 -t application/json -e '{"temp": 5}'
~~~

Publish some JSON data for device `4712` using message type `NON` (*at most once*):

~~~sh
./coap-client -u gw@DEFAULT_TENANT -k gw-secret -N -m PUT coaps://hono.eclipseprojects.io/telemetry/DEFAULT_TENANT/4712 -t application/json -e '{"temp": 5}'
~~~

Publish some JSON data for device `4712`, indicating that the gateway will wait for 10 seconds to receive the response:

~~~sh
./coap-client -u gw@DEFAULT_TENANT -k gw-secret -m PUT coaps://hono.eclipseprojects.io/telemetry/DEFAULT_TENANT/4712?hono-ttd=10 -t application/json -e '{"temp": 5}'

{
  "brightness": 87
}
~~~

**NB** The example above assumes that a gateway device has been registered with `hashed-password` credentials with *auth-id* `gw` and password `gw-secret`
which is authorized to publish data *on behalf of* device `4712`.

