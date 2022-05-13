+++
title = "Kura Adapter"
hidden = true # does not show up in the menu, but shows up in the version selector and can be accessed from links
weight = 0
+++

The Kura protocol adapter exposes an MQTT topic hierarchy allowing Eclipse Kura&trade; based gateways to publish
*control* and *data* messages to Eclipse Hono&trade;'s Telemetry and Event endpoints.
<!--more-->

{{% notice tip %}}
The Kura adapter is supposed to be used with gateways running Kura version 3.x. Gateways running Kura version 4 and
later should connect to the MQTT adapter instead.
{{% /notice %}}

{{% notice info %}}
The Kura adapter has been removed in Hono 2.0.0. Support for Kura version 4 and later is still available by means of
Hono's standard MQTT adapter.
{{% /notice %}}

## Authentication

The Kura adapter by default requires devices (gateways) to authenticate during connection establishment.
The adapter supports both the authentication based on the username/password provided in an MQTT CONNECT packet as well
as client certificate based authentication as part of a TLS handshake for that purpose.

The adapter tries to authenticate the device using these mechanisms in the following order

### Client Certificate

The MQTT adapter supports authenticating clients based on TLS cipher suites using a digital signature based key
exchange algorithm as described in [RFC 5246 (TLS 1.2)](https://datatracker.ietf.org/doc/html/rfc5246) and
[RFC 8446 (TLS 1.3)](https://datatracker.ietf.org/doc/html/rfc8446). This requires a client to provide an X.509
certificate containing a public key that can be used for digital signature. The adapter uses the information in the
client certificate to verify the device's identity as described in
[Client Certificate based Authentication]({{< relref "/concepts/device-identity#client-certificate-based-authentication" >}}).

{{% notice info %}}
The adapter needs to be [configured for TLS]({{< relref "/admin-guide/secure_communication#mqtt-adapter" >}})
in order to support this mechanism.
{{% /notice %}}

### Username/Password

The MQTT adapter supports authenticating clients based on credentials provided during MQTT connection establishment.
This means that clients need to provide a *user* and a *password* field in their MQTT CONNECT packet as defined in
[MQTT Version 3.1.1, Section 3.1](http://docs.oasis-open.org/mqtt/mqtt/v3.1.1/os/mqtt-v3.1.1-os.html#_Toc398718028)
when connecting to the MQTT adapter. The username provided in the *user* field must match the pattern
*auth-id@tenant*, e.g. `sensor1@DEFAULT_TENANT`.

The adapter extracts the *auth-id*, *tenant* and password from the CONNECT packet and verifies them using the
credentials that the
[configured Credentials service]({{< relref "/admin-guide/common-config#credentials-service-connection-configuration" >}})
has on record for the client as described in
[Username/Password based Authentication]({{< relref "/concepts/device-identity#usernamepassword-based-authentication" >}}).
If the credentials match, the client has been authenticated successfully and the connection is being established.

{{% notice info %}}
There is a subtle difference between the *device identifier* (*device-id*) and the *auth-id* a device uses
for authentication. See [Device Identity]({{< relref "/concepts/device-identity.md" >}}) for a discussion of the
concepts.
{{% /notice %}}

## Resource Limit Checks

The adapter performs additional checks regarding resource limits when a client tries to connect and/or send a message
to the adapter.

### Connection Limits

The adapter rejects a client's connection attempt with return code `0x05`, indicating `Connection Refused: not authorized`, if

* the maximum number of connections per protocol adapter instance is reached, or
* if the maximum number of simultaneously connected devices for the tenant is reached.

Please refer to [resource-limits]({{< ref "/concepts/resource-limits.md" >}}) for details.

### Connection Duration Limits

The adapter rejects a client's connection attempt with return code `0x05`, indicating `Connection Refused: not authorized`, if
the [connection duration limit]({{< relref "/concepts/resource-limits#connection-duration-limit" >}}) that has been
configured for the client's tenant is exceeded.

### Message Limits

The adapter

* discards any MQTT PUBLISH packet containing telemetry data or an event that is sent by a client and
* rejects any AMQP 1.0 message containing a command sent by a north bound application

if the [message limit]({{< relref "/concepts/resource-limits.md" >}}) that has been configured for the device's tenant
is exceeded.

## Connection Events

The adapter can emit [Connection Events]({{< relref "/api/event#connection-event" >}}) for client connections being
established and/or terminated. Please refer to the
[common configuration options]({{< relref "/admin-guide/common-config#connection-event-producer-configuration" >}})
for details regarding how to enable this behavior.

The adapter includes the *client identifier* from the client's MQTT CONNECT packet as the Connection Event's *remote-id*.

## Publishing Data

Once the gateway has established a connection to the Kura adapter, all *control* and *data* messages published by
applications running on the gateway are sent to the adapter and mapped to Hono's Telemetry and Event API endpoints as
follows:

1. The adapter treats all messages that are published to a topic starting with the configured `HONO_KURA_CONTROL_PREFIX` as
   control messages.
   All other messages are considered to be data messages.
1. *control* messages with QoS 0 are forwarded to Hono's telemetry endpoint whereas messages with QoS 1 are forwarded to
   the event endpoint. The corresponding messages that are sent downstream have a content type of
   `application/vnd.eclipse.kura-control`.
1. *data* messages with QoS 0 are forwarded to the telemetry endpoint whereas messages with QoS 1 are forwarded to the
   event endpoint. The corresponding messages that are sent downstream have a content type of
   `application/vnd.eclipse.kura-data`.

## Downstream Meta Data

The adapter includes the following meta data in messages being sent downstream:

| Name               | Location        | Type      | Description                                                     |
| :----------------- | :-------------- | :-------- | :-------------------------------------------------------------- |
| *device_id*        | *application*   | *string*  | The identifier of the device that the message originates from.  |
| *orig_adapter*     | *application*   | *string*  | Contains the adapter's *type name* which can be used by downstream consumers to determine the protocol adapter that the message has been received over. The Kura adapter's type name is `hono-kura-mqtt`. |
| *orig_address*     | *application*   | *string*  | Contains the name of the MQTT topic that the Kura gateway has originally published the data to. |

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
configured for a tenant/device as described in the
[Tenant API]({{< relref "/api/tenant#resource-limits-configuration-format" >}}).

## Tenant specific Configuration

The adapter uses the [Tenant API]({{< ref "/api/tenant#get-tenant-information" >}}) to retrieve *tenant specific
configuration* for adapter type `hono-kura-mqtt`. The following properties are (currently) supported:

| Name               | Type       | Default Value | Description                                                     |
| :----------------- | :--------- | :------------ | :-------------------------------------------------------------- |
| *enabled*          | *boolean*  | `true`         | If set to `false` the adapter will reject all data from devices belonging to the tenant. |
