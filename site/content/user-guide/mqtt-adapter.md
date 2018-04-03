+++
title = "MQTT Adapter"
weight = 215
+++

The MQTT protocol adapter exposes an MQTT topic hierarchy for publishing messages and events to Eclipse Hono&trade;'s Telemetry and Event endpoints.
<!--more-->

The MQTT adapter by default requires clients (devices or gateway components) to authenticate during connection establishment. In order to do so, clients need to provide a *username* and a *password* in the MQTT *CONNECT* packet. The *username* must have the form *auth-id@tenant*, e.g. `sensor1@DEFAULT_TENANT`. The adapter verifies the credentials provided by the client against the credentials the [configured Credentials service]({{< relref "#credentials-service-connection-configuration" >}}) has on record for the client. The adapter uses the Credentials API's *get* operation to retrieve the credentials on record with the *tenant* and *auth-id* provided by the client in the *username* and `hashed-password` as the *type* of secret as query parameters.

When running the Hono example installation as described in the [Getting Started guide]({{< relref "getting-started.md" >}}), the demo Credentials service comes pre-configured with a `hashed-password` secret for devices `4711` and `gw-1` of tenant `DEFAULT_TENANT` having *auth-ids* `sensor1` and `gw1` and (hashed) *passwords* `hono-secret` and `gw-secret` respectively. These credentials are used in the following examples illustrating the usage of the adapter. Please refer to the [Credentials API]({{< relref "api/Credentials-API.md#standard-credential-types" >}}) for details regarding the different types of secrets.

{{% note %}}
There is a subtle difference between the *device identifier* (*device-id*) and the *auth-id* a device uses for authentication. See [Device Identity]({{< relref "concepts/device-identity.md" >}}) for a discussion of the concepts.
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

## Downstream Meta Data

The adapter includes the following meta data in messages being sent downstream:

| Name               | Location        | Type      | Description                                                     |
| :----------------- | :-------------- | :-------- | :-------------------------------------------------------------- |
| *orig_adapter*     | *application*   | *string*  | Contains the adapter's *type name* which can be used by downstream consumers to determine the protocol adapter that the message has been received over. The MQTT adapter's type name is `hono-mqtt`. |
| *orig_address*     | *application*   | *string*  | Contains the name of the MQTT topic that the device has originally published the data to. |

The adapter also considers [*defaults* registered for the device]({{< relref "api/Device-Registration-API.md#payload-format" >}}). For each default value the adapter checks if a corresponding property is already set on the message and if not, sets the message's property to the registered default value or adds a corresponding application property.

Note that of the standard AMQP 1.0 message properties only the *content-type* can be set this way to a registered default value.

## Tenant specific Configuration

The adapter uses the [Tenant API]({{< relref "api/Tenant-API.md#get-tenant-information" >}}) to retrieve *tenant specific configuration* for adapter type `hono-mqtt`.
The following properties are (currently) supported:

| Name               | Type       | Default Value | Description                                                     |
| :----------------- | :--------- | :------------ | :-------------------------------------------------------------- |
| *enabled*          | *boolean*  | `true`       | If set to `false` the adapter will reject all data from devices belonging to the tenant. |
