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
* discards *malformed* messages that e.g. are published to an unsupported topic or use an unsupported QoS value.
* does not support *retaining* messages. However, if an event or telemetry message's *retain* flag is set to `1` then the corresponding AMQP 1.0 message being sent downstream by the adapter will contain an *x-opt-retain* message annotation containing the boolean value `true`. A downstream consumer may then react according to the presence of this annotation.

## Authentication

The MQTT adapter by default requires clients (devices or gateway components) to authenticate during connection establishment. The adapter supports both the authentication based on the username/password provided in an MQTT CONNECT packet as well as client certificate based authentication as part of a TLS handshake for that purpose.

The adapter tries to authenticate the device using these mechanisms in the following order

### Client Certificate

When a device uses a client certificate for authentication during the TLS handshake, the adapter tries to determine the tenant that the device belongs to, based on the *issuer DN* contained in the certificate. In order for the lookup to succeed, the tenant's trust anchor needs to be configured by means of [registering the trusted certificate authority]({{< ref "/api/Tenant-API.md#request-payload" >}}). The device's client certificate will then be validated using the registered trust anchor, thus implicitly establishing the tenant that the device belongs to. In a second step, the adapter then uses the Credentials API's *get* operation with the client certificate's *subject DN* as the *auth-id* and `x509-cert` as the *type* of secret as query parameters.

NB: The adapter needs to be [configured for TLS]({{< ref "/admin-guide/secure_communication.md#mqtt-adapter" >}}) in order to support this mechanism.

### Username/Password

When a device wants to authenticate using this mechanism, it needs to provide a *username* and a *password* in the MQTT *CONNECT* packet it sends in order to initiate the connection. The *username* must have the form *auth-id@tenant*, e.g. `sensor1@DEFAULT_TENANT`. The adapter verifies the credentials provided by the client against the credentials the [configured Credentials service]({{< ref "/admin-guide/mqtt-adapter-config.md#credentials-service-connection-configuration" >}}) has on record for the client. The adapter uses the Credentials API's *get* operation to retrieve the credentials on record with the *tenant* and *auth-id* provided by the client in the *username* and `hashed-password` as the *type* of secret as query parameters.

When running the Hono example installation as described in the [Getting Started guide]({{< ref "/getting-started" >}}), the demo Credentials service comes pre-configured with a `hashed-password` secret for devices `4711` and `gw-1` of tenant `DEFAULT_TENANT` having *auth-ids* `sensor1` and `gw1` and (hashed) *passwords* `hono-secret` and `gw-secret` respectively. These credentials are used in the following examples illustrating the usage of the adapter. Please refer to the [Credentials API]({{< ref "/api/Credentials-API.md#standard-credential-types" >}}) for details regarding the different types of secrets.

{{% note %}}
There is a subtle difference between the *device identifier* (*device-id*) and the *auth-id* a device uses for authentication. See [Device Identity]({{< ref "/concepts/device-identity.md" >}}) for a discussion of the concepts.
{{% /note %}}

## Publishing Telemetry Data

The MQTT adapter supports the publishing of telemetry data by means of MQTT *PUBLISH* packets using either QoS 0 or QoS 1.
Using QoS 1 will result in the adapter sending an MQTT *PUBACK* packet to the client once the message has been settled with the *accepted* outcome by the AMQP 1.0 Messaging Network.

This requires that

* the AMQP 1.0 Messaging Network has capacity to process telemetry messages for the client's tenant and
* the messages published by the client comply with the format defined by the Telemetry API.

## Publish Telemetry Data (authenticated Device)

* Topic: `telemetry` or `t`
* Authentication: required
* Payload:
  * (required) Arbitrary payload

This is the preferred way for devices to publish telemetry data. It is available only if the protocol adapter is configured to require devices to authenticate (which is the default). When using this topic, the MQTT adapter determines the device's tenant and device identity as part of the authentication process.

**Example**

Publish some JSON data for device `4711`:

    $ mosquitto_pub -u 'sensor1@DEFAULT_TENANT' -P hono-secret -t telemetry -m '{"temp": 5}'

Publish some JSON data for device `4711` using a client certificate for authentication (in the directory that contains the certificates, e.g. `hono/demo-certs/certs/`):

    $ mosquitto_pub -p 8883 -t telemetry -m '{"temp": 5}' --cert device-4711-cert.pem --key device-4711-key.pem --cafile trusted-certs.pem

**NB**: The example above assumes that the MQTT adapter is [configured for TLS]({{< ref "/admin-guide/secure_communication.md#mqtt-adapter" >}}) and the secure port is used.

## Publish Telemetry Data (unauthenticated Device)

* Topic: `telemetry/${tenant-id}/${device-id}` or `t/${tenant-id}/${device-id}`
* Authentication: none
* Payload:
  * (required) Arbitrary payload

This topic can be used by devices that have not authenticated to the protocol adapter. Note that this requires the
`HONO_MQTT_AUTHENTICATION_REQUIRED` configuration property to be explicitly set to `false`.

**Examples**

Publish some JSON data for device `4711`:

    $ mosquitto_pub -t telemetry/DEFAULT_TENANT/4711 -m '{"temp": 5}'


## Publish Telemetry Data (authenticated Gateway)

* Topic: `telemetry/${tenant-id}/${device-id}` or `t/${tenant-id}/${device-id}`
* Authentication: required
* Payload:
  * (required) Arbitrary payload

This topic can be used by *gateway* components to publish data *on behalf of* other devices which do not connect to a protocol adapter directly but instead are connected to the gateway, e.g. using some low-bandwidth radio based technology like [SigFox](https://www.sigfox.com) or [LoRa](https://www.lora-alliance.org/). In this case the credentials provided by the gateway during connection establishment with the protocol adapter are used to authenticate the gateway whereas the parameters from the topic name are used to identify the device that the gateway publishes data for.

The protocol adapter checks the gateway's authority to publish data on behalf of the device implicitly by means of retrieving a *registration assertion* for the device from the [configured Device Registration service]({{< relref "#device-registration-service-connection-configuration" >}}).

**Examples**

Publish some JSON data for device `4712` via gateway `gw-1`:

    $ mosquitto_pub -u 'gw@DEFAULT_TENANT' -P gw-secret -t telemetry/DEFAULT_TENANT/4712 -m '{"temp": 5}'

**NB**: The example above assumes that a gateway device with ID `gw-1` has been registered with `hashed-password` credentials with *auth-id* `gw` and password `gw-secret`.

## Publishing Events

The MQTT adapter supports the publishing of events by means of MQTT *PUBLISH* packets using QoS 1 only.
The adapter will send an MQTT *PUBACK* packet to the client once the event has been settled with the *accepted* outcome by the AMQP 1.0 Messaging Network.

This requires that

* the AMQP 1.0 Messaging Network has capacity to process events for the client's tenant and
* the events published by the client comply with the format defined by the Event API.

## Publish an Event (authenticated Device)

* Topic: `event` or `e`
* Authentication: required
* Payload:
  * (required) Arbitrary payload

This is the preferred way for devices to publish events. It is available only if the protocol adapter has been configured to require devices to authenticate (which is the default).

**Example**

Upload a JSON string for device `4711`:

    $ mosquitto_pub -u 'sensor1@DEFAULT_TENANT' -P hono-secret -t event -q 1 -m '{"alarm": 1}'

## Publish an Event (unauthenticated Device)

* Topic: `event/${tenant-id}/${device-id}` or `e/${tenant-id}/${device-id}`
* Authentication: none
* Payload:
  * (required) Arbitrary payload

This topic can be used by devices that have not authenticated to the protocol adapter. Note that this requires the
`HONO_MQTT_AUTHENTICATION_REQUIRED` configuration property to be explicitly set to `false`.

**Examples**

Publish some JSON data for device `4711`:

    $ mosquitto_pub -t event/DEFAULT_TENANT/4711 -q 1 -m '{"alarm": 1}'

## Publish an Event (authenticated Gateway)

* Topic: `event/${tenant-id}/${device-id}` or `e/${tenant-id}/${device-id}`
* Authentication: required
* Payload:
  * (required) Arbitrary payload

This topic can be used by *gateway* components to publish data *on behalf of* other devices which do not connect to a protocol adapter directly but instead are connected to the gateway, e.g. using some low-bandwidth radio based technology like [SigFox](https://www.sigfox.com) or [LoRa](https://www.lora-alliance.org/). In this case the credentials provided by the gateway during connection establishment with the protocol adapter are used to authenticate the gateway whereas the parameters from the topic name are used to identify the device that the gateway publishes data for.

The protocol adapter checks the gateway's authority to publish data on behalf of the device implicitly by means of retrieving a *registration assertion* for the device from the [configured Device Registration service]({{< relref "#device-registration-service-connection-configuration" >}}).

**Examples**

Publish some JSON data for device `4712` via gateway `gw-1`:

    $ mosquitto_pub -u 'gw@DEFAULT_TENANT' -P gw-secret -t event/DEFAULT_TENANT/4712 -q 1 -m '{"temp": 5}'

**NB**: The example above assumes that a gateway device with ID `gw-1` has been registered with `hashed-password` credentials with *auth-id* `gw` and password `gw-secret`.

## Command & Control

The MQTT adapter supports devices to receive commands that have been sent by business applications by means of sending an MQTT *SUBSCRIBE* packet containing a device specific *topic filter* as described below. Devices can subscribe with QoS 1 or QoS 0. The adapter indicates the outcome of the subscription request by sending back a corresponding *SUBACK* packet. The SUBACK packet will contain *Success - QoS 0* (`0x00`) or *Success - QoS 1* (`0x01`) for a command topic filter indicating QoS 0 or 1 and will contain the *Failure* (`0x80`) value for all other filters. When a device no longer wants to receive commands anymore, it can send an MQTT *UNSUBSCRIBE* packet to the adapter, including the same topic filter that has been used to subscribe.

When a device has successfully subscribed, the adapter sends an [empty notification]({{< relref "/api/Event-API.md#empty-notification" >}}) on behalf of the device to the downstream AMQP 1.0 Messaging Network with the *ttd* header set to `-1`, indicating that the device will be ready to receive commands until further notice. Analogously, the adapter sends an empty notification with the *ttd* header set to `0` when a device unsubcribes from commands.

Commands can be sent following a *request/response* pattern or being *one-way*. 

For *Request/Response* commands, devices send their responses to commands by means of sending an MQTT *PUBLISH* message to a topic that is specific to the command that has been executed. The MQTT adapter accepts responses being published using either QoS 0 or QoS 1.

The following sections define the topic filters/names to use for subscribing to and responding to commands. The following *shorthand* versions of topic path segments are supported:

* `c` instead of `control`
* `q` instead of `req`
* `s` instead of `res`

The following variables are used:

* `${command}` : is an arbitrary string that indicates the command to execute, e.g. `setBrightness`. The command is provided by the application that sends the command.
* `${req-id}` (only for *Request/Response* commands) : denotes the unique identifier of the command execution request and is passed to the device as part of the name of the topic that the command is published to. The device needs to publish its response to the command to a topic which includes this identifier, thus allowing the adapter to correlate the response with the request.
* `${status}` : is the HTTP status code indicating the outcome of executing the command. This status code is passed on to the application in the AMQP message's *status* header.

The `property-bag` is an optional collection of properties intended for the receiver of the message. A property bag is only allowed at the very end of a topic. It always starts with a `?` character, followed by pairs of URL encoded property names and values that are separated by `&`. The following example shows a property bag that contains two properties *seqNo* and *importance*:

    /topic/name/?seqNo=10034&importance="high"

The MQTT adapter currently does not require nor use any properties.

### Receiving Commands (authenticated Device)

An authenticated device MUST use the following topic filter to subscribe to commands:

* `control/+/+/req/#`

**Example**

    $ mosquitto_sub -v -u 'sensor1@DEFAULT_TENANT' -P hono-secret -t control/+/+/req/#

The adapter will then publish commands for the device to topic:

* for *Request/Response* commands: `control///req/${req-id}/${command}[/*][/property-bag]`
* for *one-way* commands: `control///req//${command}[/*][/property-bag]`


**Example**

For example, if the [HonoExampleApplication]({{< relref "dev-guide/java_client_consumer.md" >}}) was started, after the `ttd` event requested by the subscription of mosquitto_sub, it layers a command that arrives as follows:  

    $ control///q/1010f8ab0b53-bd96-4d99-9d9c-56b868474a6a/setBrightness {
       "brightness" : 79
    }

If the command is a *one-way* command, it will arrive as follows:

    $ control///q//setBrightness {
       "brightness" : 79
    }


### Receiving Commands (unauthenticated Device)

An unauthenticated device MUST use the following topic filter to subscribe to commands:

* `control/${tenant-id}/${device-id}/req/#`

**Example**

    $ mosquitto_sub -v -t control/DEFAULT_TENANT/4711/req/#

The adapter will then publish *Request/Response* commands for the device to topic:

* `control/${tenant-id}/${device-id}/req/${req-id}/${command}[/*][/property-bag]`

and *one-way* commands to the topic:

* `control/${tenant-id}/${device-id}/req//${command}[/*][/property-bag]`

(For an example of the incoming command see above at authenticated device)

### Sending a Response to a Command (authenticated Device)

An authenticated device MUST send the response to a previously received command to the following topic:

* `control///res/${req-id}/${status}`

**Example**

After a command has arrived as in the above example, you send a response using the arrived `${req-id}`:

    $ mosquitto_pub -u 'sensor1@DEFAULT_TENANT' -P hono-secret -t control///res/1010f8ab0b53-bd96-4d99-9d9c-56b868474a6a/200 -m '{"lumen": 200}'

### Sending a Response to a Command (unauthenticated Device)

An unauthenticated device MUST send the response to a previously received command to the following topic:

* `control/${tenant-id}/${device-id}/res/${req-id}/${status}`

**Example**

After a command has arrived as in the above example, you send a response using the arrived `${req-id}`:

    $ mosquitto_pub -t control/DEFAULT_TENANT/4711/res/1010f8ab0b53-bd96-4d99-9d9c-56b868474a6a/200 -m '{"lumen": 200}'

## Downstream Meta Data

The adapter includes the following meta data in messages being sent downstream:

| Name               | Location                | Type      | Description                                                     |
| :----------------- | :---------------------- | :-------- | :-------------------------------------------------------------- |
| *orig_adapter*     | *application*           | *string*  | Contains the adapter's *type name* which can be used by downstream consumers to determine the protocol adapter that the message has been received over. The MQTT adapter's type name is `hono-mqtt`. |
| *orig_address*     | *application*           | *string*  | Contains the name of the MQTT topic that the device has originally published the data to. |
| *x-opt-retain*     | * *message-annotations* | *boolean* | Contains `true` if the device has published an event or telemetry message with its *retain* flag set to `1` |

The adapter also considers [*defaults* registered for the device]({{< relref "api/Device-Registration-API.md#payload-format" >}}). For each default value the adapter checks if a corresponding property is already set on the message and if not, sets the message's property to the registered default value or adds a corresponding application property.

Note that of the standard AMQP 1.0 message properties only the *content-type* can be set this way to a registered default value.

## Tenant specific Configuration

The adapter uses the [Tenant API]({{< relref "api/Tenant-API.md#get-tenant-information" >}}) to retrieve *tenant specific configuration* for adapter type `hono-mqtt`.
The following properties are (currently) supported:

| Name               | Type       | Default Value | Description                                                     |
| :----------------- | :--------- | :------------ | :-------------------------------------------------------------- |
| *enabled*          | *boolean*  | `true`       | If set to `false` the adapter will reject all data from devices belonging to the tenant. |

