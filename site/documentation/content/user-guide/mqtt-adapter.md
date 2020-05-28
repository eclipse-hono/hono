+++
title = "MQTT Adapter"
weight = 215
+++

The MQTT protocol adapter exposes an MQTT topic hierarchy for publishing telemetry data and events to downstream consumers and for receiving commands from applications and sending back responses.
<!--more-->

The MQTT adapter is **not** a general purpose MQTT broker. In particular the adapter

* supports MQTT 3.1.1 only.
* does not maintain session state for clients and thus always sets the *session present* flag in its CONNACK packet to `0`, regardless of the value  of the *clean session* flag provided in a client's CONNECT packet.
* ignores any *Will* included in a client's CONNECT packet.
* only supports topic names/filters for devices to publish and subscribe to that are specific to Hono's functionality as described in the following sections.
* does not support *retaining* messages. However, if an event or telemetry message's *retain* flag is set to `1` then the corresponding AMQP 1.0 message being sent downstream by the adapter will contain an *x-opt-retain* message annotation containing the boolean value `true`. A downstream consumer may then react according to the presence of this annotation.

## Authentication

The MQTT adapter by default requires clients (devices or gateway components) to authenticate during connection establishment.
The adapter supports both the authentication based on the username/password provided in an MQTT CONNECT packet as well as client
certificate based authentication as part of a TLS handshake for that purpose.

The adapter tries to authenticate the device using these mechanisms in the following order

### Client Certificate

When a device uses a client certificate for authentication during the TLS handshake, the adapter tries to determine the tenant
that the device belongs to based on the *issuer DN* contained in the certificate.
In order for the lookup to succeed, the tenant's trust anchor needs to be configured by means of registering the
[trusted certificate authority]({{< relref "/api/tenant#tenant-information-format" >}}). The device's client certificate will then be
validated using the registered trust anchor, thus implicitly establishing the tenant that the device belongs to.
In a second step, the adapter uses the Credentials API's *get* operation to retrieve the credentials on record, including the client
certificate's *subject DN* as the *auth-id*, `x509-cert` as the *type* of secret and the MQTT client identifier as *client-id* in the
request payload.

**NB** The adapter needs to be [configured for TLS]({{< relref "/admin-guide/secure_communication.md#mqtt-adapter" >}}) in order to support this mechanism.

### Username/Password

When a device wants to authenticate using this mechanism, it needs to provide a *username* and a *password* in the MQTT *CONNECT* packet
it sends in order to initiate the connection. The *username* must have the form *auth-id@tenant*, e.g. `sensor1@DEFAULT_TENANT`.
The adapter verifies the credentials provided by the client against the credentials that the
[configured Credentials service]({{< relref "/admin-guide/common-config.md#credentials-service-connection-configuration" >}}) has on record for the client.
The adapter uses the Credentials API's *get* operation to retrieve the credentials on record, including the *tenant* and *auth-id* provided
by the client in the *username*, `hashed-password` as the *type* of secret and the MQTT client identifier as *client-id* in the request payload.

The examples below refer to devices `4711` and `gw-1` of tenant `DEFAULT_TENANT` using *auth-ids* `sensor1` and `gw1` and corresponding passwords.
The example deployment as described in the [Deployment Guides]({{< relref "deployment" >}}) comes pre-configured with the corresponding entities in its device registry component.

**NB** There is a subtle difference between the *device identifier* (*device-id*) and the *auth-id* a device uses for authentication.
See [Device Identity]({{< relref "/concepts/device-identity.md" >}}) for a discussion of the concepts.

## Resource Limit Checks

The adapter performs additional checks regarding [resource limits]({{< ref "/concepts/resource-limits.md" >}}) when a client tries to connect and/or
send a message to the adapter.

### Connection Limits

The adapter rejects a client’s connection attempt with return code

* `0x03` (*Connection Refused: server unavailable*), if the maximum number of connections per protocol adapter instance is reached
* `0x05` (*Connection Refused: not authorized*), if the maximum number of simultaneously connected devices for the tenant is reached.

### Connection Duration Limits

The adapter rejects a client’s connection attempt with return code `0x05` (*Connection Refused: not authorized*), if the
[connection duration limit]({{< relref "/concepts/resource-limits.md#connection-duration-limit" >}}) that has been configured for the client’s tenant is exceeded.

### Message Limits

The adapter

* rejects a client's connection attempt with return code `0x05` (*Connection Refused: not authorized*),
* discards any MQTT PUBLISH packet containing telemetry data or an event that is sent by a client and
* rejects any AMQP 1.0 message containing a command sent by a north bound application

if the [message limit]({{< relref "/concepts/resource-limits.md" >}}) that has been configured for the device’s tenant is exceeded.

## Connection Events

The adapter can emit [Connection Events]({{< relref "/api/event#connection-event" >}}) for client connections being established and/or terminated.
Please refer to the [common configuration options]({{< relref "/admin-guide/common-config.md#connection-event-producer-configuration" >}})
for details regarding how to enable this behavior.

The adapter includes the *client identifier* from the client's MQTT CONNECT packet as the Connection Event's *remote-id*.

## Publishing Telemetry Data

The MQTT adapter supports the publishing of telemetry data by means of MQTT *PUBLISH* packets using either QoS 0 or QoS 1.
Using QoS 1 will result in the adapter sending an MQTT *PUBACK* packet to the client once the message has been settled with the *accepted* outcome by the AMQP 1.0 Messaging Network.

This requires that

* the AMQP 1.0 Messaging Network has capacity to process telemetry messages for the client's tenant and
* the messages published by the client comply with the format defined by the Telemetry API.

The protocol adapter checks the configured [message limit] ({{< relref "/concepts/resource-limits.md" >}}) before accepting any telemetry messages. If the message limit is exceeded or the incoming telemetry message cannot be processed, the connection to the client is closed.

## Publish Telemetry Data (authenticated Device)

* Topic: `telemetry` or `t`
* Authentication: required
* Payload:
  * (required) Arbitrary payload

This is the preferred way for devices to publish telemetry data. It is available only if the protocol adapter is configured to require devices to authenticate (which is the default). When using this topic, the MQTT adapter determines the device's tenant and device identity as part of the authentication process.

**Example**

Publish some JSON data for device `4711`:

```sh
mosquitto_pub -u 'sensor1@DEFAULT_TENANT' -P hono-secret -t telemetry -m '{"temp": 5}'
```

Publish some JSON data for device `4711` using a client certificate for authentication:

```sh
# in base directory of Hono repository:
mosquitto_pub -p 8883 -t telemetry -m '{"temp": 5}' --cert demo-certs/certs/device-4711-cert.pem --key demo-certs/certs/device-4711-key.pem --cafile demo-certs/certs/trusted-certs.pem
```

**NB** The example above assumes that the MQTT adapter is [configured for TLS]({{< ref "/admin-guide/secure_communication.md#mqtt-adapter" >}}) and the secure port is used.

## Publish Telemetry Data (unauthenticated Device)

* Topic: `telemetry/${tenant-id}/${device-id}` or `t/${tenant-id}/${device-id}`
* Authentication: none
* Payload:
  * (required) Arbitrary payload

This topic can be used by devices that have not authenticated to the protocol adapter. Note that this requires the
`HONO_MQTT_AUTHENTICATION_REQUIRED` configuration property to be explicitly set to `false`.

**Examples**

Publish some JSON data for device `4711`:

```sh
mosquitto_pub -t telemetry/DEFAULT_TENANT/4711 -m '{"temp": 5}'
```

## Publish Telemetry Data (authenticated Gateway)

* Topic: `telemetry/${tenant-id}/${device-id}` or `t/${tenant-id}/${device-id}`
* Authentication: required
* Payload:
  * (required) Arbitrary payload

This topic can be used by *gateway* components to publish data *on behalf of* other devices which do not connect to a protocol adapter directly but instead are connected to the gateway, e.g. using some low-bandwidth radio based technology like [SigFox](https://www.sigfox.com) or [LoRa](https://lora-alliance.org/). In this case the credentials provided by the gateway during connection establishment with the protocol adapter are used to authenticate the gateway whereas the parameters from the topic name are used to identify the device that the gateway publishes data for.

The protocol adapter checks the gateway's authority to publish data on behalf of the device implicitly by means of retrieving a *registration assertion* for the device from the [configured Device Registration service]({{< relref "/admin-guide/common-config#device-registration-service-connection-configuration" >}}).

**Examples**

Publish some JSON data for device `4712` via gateway `gw-1`:

```sh
mosquitto_pub -u 'gw@DEFAULT_TENANT' -P gw-secret -t telemetry/DEFAULT_TENANT/4712 -m '{"temp": 5}'
```

**NB** The example above assumes that a gateway device with ID `gw-1` has been registered with `hashed-password` credentials with *auth-id* `gw` and password `gw-secret`.

## Publishing Events

The MQTT adapter supports the publishing of events by means of MQTT *PUBLISH* packets using QoS 1 only.
The adapter will send an MQTT *PUBACK* packet to the client once the event has been settled with the *accepted* outcome by the AMQP 1.0 Messaging Network.

This requires that

* the AMQP 1.0 Messaging Network has capacity to process events for the client's tenant and
* the events published by the client comply with the format defined by the Event API.

The protocol adapter checks the configured [message limit] ({{< relref "/concepts/resource-limits.md" >}}) before accepting any event messages. If the message limit is exceeded or the incoming event message cannot be processed, the connection to the client is closed.

The devices can optionally indicate a *time-to-live* duration for event messages by setting the *hono-ttl* property explicitly in the `property-bag`. The `property-bag` is an optional collection of properties intended for the receiver of the message. A property bag is only allowed at the very end of a topic. It always starts with a `/?` character, followed by pairs of URL encoded property names and values that are separated by `&`. For example, a property bag containing two properties *seqNo* and *importance* looks like this: `/topic/name/?seqNo=10034&importance=high`.

The MQTT adapter currently does not use any properties except *hono-ttl*.

## Publish an Event (authenticated Device)

* Topic: `event` or `e`
* Authentication: required
* Payload:
  * (required) Arbitrary payload

This is the preferred way for devices to publish events. It is available only if the protocol adapter has been configured to require devices to authenticate (which is the default).

**Example**

Upload a JSON string for device `4711`:

```sh
mosquitto_pub -u 'sensor1@DEFAULT_TENANT' -P hono-secret -t event -q 1 -m '{"alarm": 1}'
```

Upload a JSON string for device `4711` with `time-to-live` as 10 seconds:

```sh
mosquitto_pub -u 'sensor1@DEFAULT_TENANT' -P hono-secret -t event/?hono-ttl=10 -q 1 -m '{"alarm": 1}'
```

## Publish an Event (unauthenticated Device)

* Topic: `event/${tenant-id}/${device-id}` or `e/${tenant-id}/${device-id}`
* Authentication: none
* Payload:
  * (required) Arbitrary payload

This topic can be used by devices that have not authenticated to the protocol adapter. Note that this requires the
`HONO_MQTT_AUTHENTICATION_REQUIRED` configuration property to be explicitly set to `false`.

**Examples**

Publish some JSON data for device `4711`:

```sh
mosquitto_pub -t event/DEFAULT_TENANT/4711 -q 1 -m '{"alarm": 1}'
```

Publish some JSON data for device `4711` with `time-to-live` as 15 seconds:

```sh
mosquitto_pub -t event/DEFAULT_TENANT/4711/?hono-ttl=15 -q 1 -m '{"alarm": 1}'
```

## Publish an Event (authenticated Gateway)

* Topic: `event/${tenant-id}/${device-id}` or `e/${tenant-id}/${device-id}`
* Authentication: required
* Payload:
  * (required) Arbitrary payload

This topic can be used by *gateway* components to publish data *on behalf of* other devices which do not connect to a protocol adapter directly but instead are connected to the gateway, e.g. using some low-bandwidth radio based technology like [SigFox](https://www.sigfox.com) or [LoRa](https://lora-alliance.org/). In this case the credentials provided by the gateway during connection establishment with the protocol adapter are used to authenticate the gateway whereas the parameters from the topic name are used to identify the device that the gateway publishes data for.

The protocol adapter checks the gateway's authority to publish data on behalf of the device implicitly by means of retrieving a *registration assertion* for the device from the [configured Device Registration service]({{< relref "/admin-guide/common-config#device-registration-service-connection-configuration" >}}).

**Examples**

Publish some JSON data for device `4712` via gateway `gw-1`:

```sh
mosquitto_pub -u 'gw@DEFAULT_TENANT' -P gw-secret -t event/DEFAULT_TENANT/4712 -q 1 -m '{"temp": 5}'
```

**NB** The example above assumes that a gateway device with ID `gw-1` has been registered with `hashed-password` credentials with *auth-id* `gw` and password `gw-secret`.

## Command & Control

The MQTT adapter enables devices to receive commands that have been sent by business applications by means of sending an MQTT *SUBSCRIBE* packet containing a device specific *topic filter* as described below. Devices can subscribe with QoS 1 or QoS 0. The adapter indicates the outcome of the subscription request by sending back a corresponding *SUBACK* packet. The SUBACK packet will contain *Success - QoS 0* (`0x00`) or *Success - QoS 1* (`0x01`) for a command topic filter indicating QoS 0 or 1 and will contain the *Failure* (`0x80`) value for all other filters. When a device no longer wants to receive commands anymore, it can send an MQTT *UNSUBSCRIBE* packet to the adapter, including the same topic filter that has been used to subscribe.

When a device has successfully subscribed, the adapter sends an [empty notification]({{< relref "/api/event#empty-notification" >}}) on behalf of the device to the downstream AMQP 1.0 Messaging Network with the *ttd* header set to `-1`, indicating that the device will be ready to receive commands until further notice. Analogously, the adapter sends an empty notification with the *ttd* header set to `0` when a device unsubscribes from commands.

Commands can be sent following a *request/response* pattern or being *one-way*. 

For *Request/Response* commands, devices send their responses to commands by means of sending an MQTT *PUBLISH* message to a topic that is specific to the command that has been executed. The MQTT adapter accepts responses being published using either QoS 0 or QoS 1.

The MQTT adapter checks the configured [message limit] ({{< relref "/concepts/resource-limits.md" >}}) before accepting any command requests and responses. In case of incoming command requests from business applications, if the message limit is exceeded, the Adapter rejects the message with the reason `amqp:resource-limit-exceeded`. And for the incoming command responses from devices, the Adapter rejects the message and closes the connection to the client. 

The following sections define the topic filters/names to use for subscribing to and responding to commands. The following *shorthand* versions of topic path segments are supported:

* `c` instead of `command`
* `q` instead of `req`
* `s` instead of `res`

The following variables are used:

* `${command}` : An arbitrary string that indicates the command to execute, e.g. `setBrightness`. The command is provided by the application that sends the command.
* `${req-id}` (only for *Request/Response* commands) : The unique identifier of the command execution request. The identifier is passed to the device as part of the name of the topic that the command is published to. The device needs to publish its response to the command to a topic which includes this identifier, thus allowing the adapter to correlate the response with the request.
* `${status}` : The HTTP status code indicating the outcome of executing the command. This status code is passed on to the application in the AMQP message's *status* application property.

### Receiving Commands (authenticated Device)

An authenticated device MUST use the topic filter `command///req/#` to subscribe to commands.

{{% note title="Deprecation" %}}
Previous versions of Hono required authenticated devices to use `command/+/+/req/#` for subscribing to commands.
This old topic filter is deprecated. Devices MAY still use it until support for it will be removed in a future Hono version.
{{% /note %}}

**Example**

```sh
mosquitto_sub -v -u 'sensor1@DEFAULT_TENANT' -P hono-secret -t command///req/#
```

The adapter will then publish *Request/Response* commands for the device to topic `command///req/${req-id}/${command}` and *one-way* commands to topic `command///req//${command}`.

For example, a request/response command with name `setBrightness` from an application might look like this:

```plaintext
command///q/1010f8ab0b53-bd96-4d99-9d9c-56b868474a6a/setBrightness
{
  "brightness": 79
}
```

A corresponding *one-way* command might look like this:

```plaintext
command///q//setBrightness
{
  "brightness": 79
}
```

Note that the topic in the latter case doesn't contain a request identifier.

### Receiving Commands (unauthenticated Device)

An unauthenticated device MUST use the topic filter `command/${tenant-id}/${device-id}/req/#` to subscribe to commands.

**Example**

```sh
mosquitto_sub -v -t command/DEFAULT_TENANT/4711/req/#
```

The adapter will then publish *Request/Response* commands for the device to topic `command/${tenant-id}/${device-id}/req/${req-id}/${command}` and *one-way* commands to topic `command/${tenant-id}/${device-id}/req//${command}`.

For example, a request/response command with name `setBrightness` from an application might look like this:

```plaintext
command/DEFAULT_TENANT/4711/q/1010f8ab0b53-bd96-4d99-9d9c-56b868474a6a/setBrightness
{
  "brightness": 79
}
```

A corresponding *one-way* command might look like this:

```plaintext
command/DEFAULT_TENANT/4711/q//setBrightness
{
  "brightness": 79
}
```

Note that the topic in the latter case doesn't contain a request identifier.


### Receiving Commands (authenticated Gateway)

*Gateway* components can receive commands for devices which do not connect to a protocol adapter directly but instead are connected to the gateway, e.g. using some low-bandwidth radio based technology like [SigFox](https://www.sigfox.com) or [LoRa](https://lora-alliance.org/). Corresponding devices have to be configured so that they can be used with a gateway. See [Configuring Gateway Devices]({{< relref "/admin-guide/device-registry-config.md#configuring-gateway-devices" >}}) for details.

An authenticated gateway MUST use the topic filter `command//+/req/#` to subscribe to commands for all devices in whose behalf it acts.

To subscribe only to commands for a specific device, an authenticated gateway MUST use the topic filter `command//${device-id}/req/#`.

{{% note title="Deprecation" %}}
Previous versions of Hono required authenticated gateways to use `command/+/+/req/#` for subscribing to commands.
This old topic filter is deprecated. Gateways MAY still use it until support for it will be removed in a future Hono version.
{{% /note %}}

When processing an incoming command message, the protocol adapter will give precedence to a device-specific command subscription matching the command target device, whether the subscription comes from a gateway or the device itself. If there are multiple such subscriptions from multiple gateways and/or from the device itself, the subscription initiated last will get the command messages.

If no device-specific command subscription exists for a command target device, but *one* gateway, that may act on behalf of the device, has subscribed to commands for all its devices, then the command message is sent to that gateway. 

If *multiple* gateways have initiated such generic subscriptions, the protocol adapter may have to decide to which gateway a particular command message will be sent to.
In case the command target device has already sent a telemetry, event or command response message via a gateway and if that gateway has created such a command subscription, that gateway will be chosen. Otherwise one gateway that may act on behalf of the command target device and that has an open subscription will be chosen randomly to receive the command message.

**Example**

A subscription to commands for all devices that a gateway acts on behalf of looks like this:
```sh
mosquitto_sub -v -u 'gw@DEFAULT_TENANT' -P gw-secret -t command//+/req/#
```
A subscription to commands for a specific device can be done like this:
```sh
mosquitto_sub -v -u 'gw@DEFAULT_TENANT' -P gw-secret -t command//4711/req/#
```

The adapter will then publish *Request/Response* commands for devices, that the gateway has acted on behalf of, to topic `command//${device-id}/req/${req-id}/${command}` and *one-way* commands to topic `command//${device-id}/req//${command}`.

For example, a request/response command for device `4711` with name `setBrightness` from an application might look like this:

```plaintext
command//4711/q/1010f8ab0b53-bd96-4d99-9d9c-56b868474a6a/setBrightness
{
  "brightness": 79
}
```

A corresponding *one-way* command might look like this:

```plaintext
command//4711/q//setBrightness
{
  "brightness": 79
}
```

Note that the topic in the latter case doesn't contain a request identifier.


### Sending a Response to a Command (authenticated Device)

An authenticated device MUST send the response to a previously received command to the topic `command///res/${req-id}/${status}`.

**Example**

After a command has arrived as in the above example, you send a response using the arrived `${req-id}`:

```sh
mosquitto_pub -u 'sensor1@DEFAULT_TENANT' -P hono-secret -t command///res/1010f8ab0b53-bd96-4d99-9d9c-56b868474a6a/200 -m '{"lumen": 200}'
```

### Sending a Response to a Command (unauthenticated Device)

An unauthenticated device MUST send the response to a previously received command to the topic `command/${tenant-id}/${device-id}/res/${req-id}/${status}`.

**Example**

After a command has arrived as in the above example, you send a response using the arrived `${req-id}`:

```sh
mosquitto_pub -t command/DEFAULT_TENANT/4711/res/1010f8ab0b53-bd96-4d99-9d9c-56b868474a6a/200 -m '{"lumen": 200}'
```

### Sending a Response to a Command (authenticated Gateway)

An authenticated gateway MUST send a device's response to a command it has received on behalf of the device to the topic `command//${device-id}/res/${req-id}/${status}`.

**Example**

After a command has arrived as in the above example, the response is sent using the `${req-id}` from the topic that the command had been published to:

```sh
mosquitto_pub -u 'gw@DEFAULT_TENANT' -P gw-secret -t command//4711/res/1010f8ab0b53-bd96-4d99-9d9c-56b868474a6a/200 -m '{"lumen": 200}'
```

## Custom Message Mapping

This protocol adapter supports transformation of messages that have been uploaded by devices before they get forwarded to downstream consumers.

{{% note title="Experimental" %}}
This is an experimental feature. The names of the configuration properties, potential values and the overall functionality are therefore
subject to change without prior notice.
{{% /note %}}

This feature is useful in scenarios where devices are connected to the adapter via a gateway but the gateway is not able to include
the device ID in the topic that the gateway publishes data to. The gateway will use the plain `telemetry` or `event` topics in this case.
The message payload will usually contain the identifier of the device that the data originates from.

The same functionality can also be used to transform the payload of messages uploaded by a device. This can be used for example to
transform binary encoded data into a JSON document which can be consumed more easily by downstream consumers.

The mechanism works as follows:
1. A client uploads a message to the MQTT adapter.
1. The adapter invokes the Device Registration service's *assert Registration* operation using either
   the authenticated device's identifier, if the topic does not contain a device ID, or the device ID from the
   topic.
1. If the assertion succeeds, the adapter creates the downstream message using the original message's payload and
   the asserted device ID as the origin device.
1. If the response payload contains a value for the *mapper* property, the adapter tries to find a *mapper endpoint* configuration
   for the given value. If a mapper endpoint with a matching name has been configured for the adapter,
   1. the adapter sends an HTTP request to the endpoint which contains the original message's payload in the request body.
   1. If the response body is not empty, it is used as the downstream message's payload.
   1. If the response contains a *device_id* header and its value is different from the original device ID, then the adapter
      invokes the *assert Registration* operation again, this time using the mapped device ID instead of the original device ID.
      If the assertion succeeds, the adapter uses the asserted (mapped) device ID for the downstream message.
1. The adapter forwards the downstream message.

Please refer to the [Device Registry Management API]({{< relref "/api/management#/devices/createDeviceRegistration" >}})
for how to register a *mapper* for a device.
Please refer to the [MQTT Adapter Admin Guide]({{< relref "/admin-guide/mqtt-adapter-config#custom-message-mapping" >}})
for how to configure custom mapper endpoints.

## Downstream Meta Data

The adapter includes the following meta data in messages being sent downstream:

| Name               | Location                | Type      | Description                                                     |
| :----------------- | :---------------------- | :-------- | :-------------------------------------------------------------- |
| *device_id*        | *application*           | *string*  | The identifier of the device that the message originates from.  |
| *orig_adapter*     | *application*           | *string*  | Contains the adapter's *type name* which can be used by downstream consumers to determine the protocol adapter that the message has been received over. The MQTT adapter's type name is `hono-mqtt`. |
| *orig_address*     | *application*           | *string*  | Contains the name of the MQTT topic that the device has originally published the data to. |
| *x-opt-retain*     | *message-annotations*   | *boolean* | Contains `true` if the device has published an event or telemetry message with its *retain* flag set to `1` |

The adapter also considers *defaults* registered for the device at either the [tenant]({{< relref "/api/tenant#tenant-information-format" >}})
or the [device level]({{< relref "/api/device-registration#assert-device-registration" >}}).
The values of the default properties are determined as follows:

1. If the message already contains a non-empty property of the same name, the value if unchanged.
2. Otherwise, if a default property of the same name is defined in the device's registration information, that value is used.
3. Otherwise, if a default property of the same name is defined for the tenant that the device belongs to, that value is used.

Note that of the standard AMQP 1.0 message properties only the *content-type* and *ttl* can be set this way to a default value.

### Event Message Time-to-live

Events published by devices will usually be persisted by the AMQP Messaging Network in order to support deferred delivery to downstream consumers.
In most cases the AMQP Messaging Network can be configured with a maximum *time-to-live* to apply to the events so that the events will be removed
from the persistent store if no consumer has attached to receive the event before the message expires.

In order to support environments where the AMQP Messaging Network cannot be configured accordingly, the MQTT protocol adapter supports setting a
downstream event message's *ttl* property based on the *hono-ttl* property set as *property-bag* at the end of the event topic.
Also the default *ttl* and *max-ttl* values can be configured for a tenant/device as described in the [Tenant API]
({{< relref "/api/tenant#resource-limits-configuration-format" >}}).


## Tenant specific Configuration

The adapter uses the [Tenant API]({{< ref "/api/tenant#get-tenant-information" >}}) to retrieve *tenant specific configuration* for adapter type `hono-mqtt`.
The following properties are (currently) supported:

| Name               | Type       | Default Value | Description                                                     |
| :----------------- | :--------- | :------------ | :-------------------------------------------------------------- |
| *enabled*          | *boolean*  | `true`       | If set to `false` the adapter will reject all data from devices belonging to the tenant. |

