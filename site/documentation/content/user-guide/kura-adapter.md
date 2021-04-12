+++
title = "Kura Adapter"
weight = 250
+++

The Kura protocol adapter exposes an MQTT topic hierarchy allowing Eclipse Kura&trade; based gateways to publish *control* and *data* messages to Eclipse Hono&trade;'s Telemetry and Event endpoints.
<!--more-->

{{% note %}}
The Kura adapter is supposed to be used with gateways running Kura version 3.x. Gateways running Kura version 4 and later should connect to the MQTT adapter instead.
{{% /note %}}

## Authentication

The Kura adapter by default requires devices (gateways) to authenticate during connection establishment.
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
[configured Credentials service]({{< relref "/admin-guide/common-config#credentials-service-connection-configuration" >}}) has on record for the client.
The adapter uses the Credentials API's *get* operation to retrieve the credentials on record, including the *tenant* and *auth-id* provided
by the client in the *username*, `hashed-password` as the *type* of secret and the MQTT client identifier as *client-id* in the request payload.

Please refer to the [Eclipse Kura documentation](http://eclipse.github.io/kura/config/cloud-services.html) on how to configure the
gateway's cloud service connection accordingly. It is important to set the gateway's *topic.context.account-name* to the ID of the
Hono tenant that the gateway has been registered with whereas the gateway's *client-id* needs to be set to the corresponding Hono
device ID. The *auth-id* used as part of the gateway's *username* property needs to match the authentication identifier of a set of
credentials registered for the device ID in Hono's Credentials service. In other words, the credentials configured on the gateway
need to belong to the corresponding device ID.

**NB** There is a subtle difference between the *device identifier* (*device-id*) and the *auth-id* a device uses for authentication.
See [Device Identity]({{< relref "/concepts/device-identity.md" >}}) for a discussion of the concepts.

## Resource Limit Checks

The adapter performs additional checks regarding resource limits when a client tries to connect and/or send a message to the adapter.

### Connection Limits

The adapter rejects a client's connection attempt with return code `0x05`, indicating `Connection Refused: not authorized`, if

* the maximum number of connections per protocol adapter instance is reached, or
* if the maximum number of simultaneously connected devices for the tenant is reached.

Please refer to [resource-limits]({{< ref "/concepts/resource-limits.md" >}}) for details.

### Connection Duration Limits

The adapter rejects a client's connection attempt with return code `0x05`, indicating `Connection Refused: not authorized`, if the
[connection duration limit]({{< relref "/concepts/resource-limits.md#connection-duration-limit" >}}) that has been configured for
the client's tenant is exceeded.

### Message Limits

The adapter

* discards any MQTT PUBLISH packet containing telemetry data or an event that is sent by a client and
* rejects any AMQP 1.0 message containing a command sent by a north bound application

if the [message limit]({{< relref "/concepts/resource-limits.md" >}}) that has been configured for the device's tenant is exceeded.

## Connection Events

The adapter can emit [Connection Events]({{< relref "/api/event#connection-event" >}}) for client connections being established and/or terminated.
Please refer to the [common configuration options]({{< relref "/admin-guide/common-config.md#connection-event-producer-configuration" >}})
for details regarding how to enable this behavior.

The adapter includes the *client identifier* from the client's MQTT CONNECT packet as the Connection Event's *remote-id*.

## Publishing Data

Once the gateway has established a connection to the Kura adapter, all *control* and *data* messages published by applications running on
the gateway are sent to the adapter and mapped to Hono's Telemetry and Event API endpoints as follows:

1. The adapter treats all messages that are published to a topic starting with the configured `HONO_KURA_CONTROL_PREFIX` as control messages.
   All other messages are considered to be data messages.
1. *control* messages with QoS 0 are forwarded to Hono's telemetry endpoint whereas messages with QoS 1 are forwarded to the event endpoint.
   The corresponding AMQP 1.0 messages that are sent downstream have a content type of `application/vnd.eclipse.kura-control`.
1. *data* messages with QoS 0 are forwarded to the telemetry endpoint whereas messages with QoS 1 are forwarded to the event endpoint.
   The corresponding AMQP 1.0 messages that are sent downstream have a content type of `application/vnd.eclipse.kura-data`.

## Downstream Meta Data

The adapter includes the following meta data in messages being sent downstream:

| Name               | Location        | Type      | Description                                                     |
| :----------------- | :-------------- | :-------- | :-------------------------------------------------------------- |
| *device_id*        | *application*   | *string*  | The identifier of the device that the message originates from.  |
| *orig_adapter*     | *application*   | *string*  | Contains the adapter's *type name* which can be used by downstream consumers to determine the protocol adapter that the message has been received over. The Kura adapter's type name is `hono-kura-mqtt`. |
| *orig_address*     | *application*   | *string*  | Contains the name of the MQTT topic that the Kura gateway has originally published the data to. |

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
downstream event message's *ttl* property based on the default *ttl* and *max-ttl* values configured for a tenant/device as described in the [Tenant API]
({{< relref "/api/tenant#resource-limits-configuration-format" >}}).


## Tenant specific Configuration

The adapter uses the [Tenant API]({{< ref "/api/tenant#get-tenant-information" >}}) to retrieve *tenant specific configuration* for adapter type `hono-kura-mqtt`.
The following properties are (currently) supported:

| Name               | Type       | Default Value | Description                                                     |
| :----------------- | :--------- | :------------ | :-------------------------------------------------------------- |
| *enabled*          | *boolean*  | `true`       | If set to `false` the adapter will reject all data from devices belonging to the tenant. |
