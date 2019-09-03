+++
title = "Kura Adapter"
weight = 250
+++

The Kura protocol adapter exposes an MQTT topic hierarchy allowing Eclipse Kura&trade; based gateways to publish *control* and *data* messages to Eclipse Hono&trade;'s Telemetry and Event endpoints.
<!--more-->

{{% note %}}
The Kura adapter is supposed to be used with gateways running Kura version 3.x. Gateways running Kura version 4 and later should connect to the MQTT adapter instead.
{{% /note %}}

The Kura adapter by default requires devices (gateways) to authenticate during connection establishment. In order to do so, gateways need to provide a *username* and a *password* in the MQTT *CONNECT* packet. The *username* must have the form *auth-id@tenant*, e.g. `sensor1@DEFAULT_TENANT`. The adapter verifies the credentials provided by the gateway against the credentials the [configured Credentials service]({{< relref "/admin-guide/kura-adapter-config.md#credentials-service-connection-configuration" >}}) has on record for the gateway. The adapter uses the Credentials API's *get* operation to retrieve the credentials-on-record with the *tenant* and *auth-id* provided by the device in the *username* and `hashed-password` as the *type* of secret as query parameters.

Please refer to the [Eclipse Kura documentation](http://eclipse.github.io/kura/config/cloud-services.html) on how to configure the gateway's cloud service connection accordingly. It is important to set the gateway's *topic.context.account-name* to the ID of the Hono tenant that the gateway has been registered with whereas the gateway's *client-id* needs to be set to the corresponding Hono device ID. The *auth-id* used as part of the gateway's *username* property needs to match the authentication identifier of a set of credentials registered for the device ID in Hono's Credentials service. In other words, the credentials configured on the gateway need to belong to the corresponding device ID.

Once the gateway has established a connection to the Kura adapter, all *control* and *data* messages published by applications running on the gateway are sent to the adapter and mapped to Hono's Telemetry and Event API endpoints as follows:

1. The adapter treats all messages that are published to a topic starting with the configured `HONO_KURA_CONTROL_PREFIX` as control messages. All other messages are considered to be data messages.
1. *control* messages with QoS 0 are forwarded to Hono's telemetry endpoint whereas messages with QoS 1 are forwarded to the event endpoint. The corresponding AMQP 1.0 messages that are sent downstream have a content type of `application/vnd.eclipse.kura-control`.
1. *data* messages with QoS 0 are forwarded to the telemetry endpoint whereas messages with QoS 1 are forwarded to the event endpoint. The corresponding AMQP 1.0 messages that are sent downstream have a content type of `application/vnd.eclipse.kura-data`.

## Downstream Meta Data

The adapter includes the following meta data in messages being sent downstream:

| Name               | Location        | Type      | Description                                                     |
| :----------------- | :-------------- | :-------- | :-------------------------------------------------------------- |
| *device_id*        | *application*   | *string*  | The identifier of the device that the message originates from.  |
| *orig_adapter*     | *application*   | *string*  | Contains the adapter's *type name* which can be used by downstream consumers to determine the protocol adapter that the message has been received over. The Kura adapter's type name is `hono-kura-mqtt`. |
| *orig_address*     | *application*   | *string*  | Contains the name of the MQTT topic that the Kura gateway has originally published the data to. |

The adapter also considers *defaults* registered for the device at either the [tenant]({{< relref "/api/tenant#payload-format" >}}) or the [device level]({{< relref "/api/device-registration#payload-format" >}}). The values of the default properties are determined as follows:

1. If the message already contains a non-empty property of the same name, the value if unchanged.
2. Otherwise, if a default property of the same name is defined in the device's registration information, that value is used.
3. Otherwise, if a default property of the same name is defined for the tenant that the device belongs to, that value is used.

Note that of the standard AMQP 1.0 message properties only the *content-type* and *ttl* can be set this way to a default value.

### Event Message Time-to-live

Events published by devices will usually be persisted by the AMQP Messaging Network in order to support deferred delivery to downstream consumers.
In most cases the AMQP Messaging Network can be configured with a maximum *time-to-live* to apply to the events so that the events will be removed
from the persistent store if no consumer has attached to receive the event before the message expires.

In order to support environments where the AMQP Messaging Network cannot be configured accordingly, the protocol adapter supports setting a
downstream event message's *ttl* property based on the default *ttl* and *max-ttl* values configured for a tenant/device as described in the [Tenant API]
({{< relref "/api/tenant#resource-limits-configuration-format" >}}).


## Tenant specific Configuration

The adapter uses the [Tenant API]({{< ref "/api/tenant#get-tenant-information" >}}) to retrieve *tenant specific configuration* for adapter type `hono-kura-mqtt`.
The following properties are (currently) supported:

| Name               | Type       | Default Value | Description                                                     |
| :----------------- | :--------- | :------------ | :-------------------------------------------------------------- |
| *enabled*          | *boolean*  | `true`       | If set to `false` the adapter will reject all data from devices belonging to the tenant. |
