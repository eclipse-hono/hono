+++
title = "AMQP Adapter"
weight = 215
+++

The AMQP protocol adapter allows clients (devices or gateway components) supporting the AMQP 1.0 protocol to publish telemetry messages and events to Eclipse Hono&trade;'s Telemetry and Event endpoints. 
<!--more-->

Clients can publish Telemetry messages using either `AT_MOST_ONCE` (presettled) or `AT_LEAST_ONCE` (unsettled) quality of service while Event messages can be published using only `AT_LEAST_ONCE` quality of service. 

`AT_MOST_ONCE` QoS means that the client does not wait for the message to be accepted and settled by the downstream consumer while with `AT_LEAST_ONCE`, the client sends the message and wait for the message to be delivered. If the message cannot be delivered due to a failure, the client will be notified.

The AMQP adapter distinguishes between two types of failures when a message is published using `AT_LEAST_ONCE` QoS:

* A failure caused by a client-side error (e.g invalid message address, content-type, adapter disabled for tenant, etc).
* A failure caused by a server-side error (e.g sender queue full, no credit available yet to send the message, etc).

For a client-side error, the adapter **rejects** the message and provides a reason why it was rejected. In the case of a server-side error, the adapter **releases** the message, indicating to the client that the message was OK but it cannot be delivered due to a failure beyond the control of the client. In this case, the client should attempt to redeliver the message again.

## Device Authentication

By default, all Hono protocol adapters require clients (devices or gateway components) to authenticate during connection establishment. This is the preferred way for devices to publish data via protocol adapters. The AMQP adapter supports both the [SASL PLAIN](https://tools.ietf.org/html/rfc4616) and [SASL EXTERNAL](https://tools.ietf.org/html/rfc4422) authentication mechanisms. The former uses a *username* and *password* to authenticate to the adapter while the latter uses a client certificate.

In this guide, we will give examples for publishing telemetry and events for *authenticated* (using SASL PLAIN) and *unauthenticated* clients. 

NB: The AMQP adapter can be configured to *disallow* authentication for devices by setting the value of `HONO_AMQP_AUTHENTICATION_REQUIRED` to `false`.

### SASL PLAIN Authentication

The AMQP adapter supports authenticating clients using a *username* and *password*. This means that clients need to provide a *username* and a *password* when connecting to the AMQP adapter. If the adapter is configured for multi-tenancy (i.e `HONO_AMQP_SINGLE_TENANT` is set to `false`), then the *username* must match the pattern [*auth-id@tenant*], e.g. `sensor1@DEFAULT_TENANT`. Otherwise the `DEFAULT_TENANT` is assumed and the tenant-id can be omitted from the username.

The adapter verifies the credentials provided by the client against the credentials that the [Credentials Service] ({{< relref "#credentials-service-connection-configuration" >}}) has on record for the device. If the credentials match, then authentication is successful and the client device can proceed to publish messages to Hono.

When running the Hono example installation, as described in the [Getting Started guide]({{< relref "getting-started.md" >}}), the demo Credentials service comes pre-configured with a `hashed-password` secret for devices `4711` and `gw-1` of tenant `DEFAULT_TENANT` as shown below. These credentials are used in the following examples to illustrate the usage of the adapter.

{{% note %}}
There is a subtle difference between the *device identifier* (*device-id*) and the *auth-id* a device uses for authentication. See [Device Identity]({{< relref "concepts/device-identity.md" >}}) for a discussion of the concepts.
{{% /note %}}

### SASL EXTERNAL Authentication
When a device uses a client certificate for authentication, the TLS handshake is initiated during TCP connection establishment. If no trust anchor is configured for the AMQP adapter, the TLS handshake will succeed only if the certificate has not yet expired. Once the TLS handshake completes and a secure connection is established, the certificate's signature is checked during the SASL handshake. To complete the SASL handshake and authenticate the client, the adapter performs the following steps:

* Adapter extracts the client certificate's Issuer DN and uses it to
* perform a Tenant API lookup to retrieve the tenant that the client belongs to. In order for the lookup to succeed, the tenant’s trust anchor needs to be configured by means of registering the [trusted certificate authority]({{< relref "/api/Tenant-API.md#trusted-ca-format" >}}).
* If the lookup succeeds, the Tenant API returns the tenant, thus implicitly establishing the tenant that the device belongs to.
* Adapter validates the device’s client certificate using the registered trust anchor for the tenant.
* Finally, adapter authenticates the client certificate using Hono's credentials API. In this step, the adapter uses the client certificate’s subject DN (as authentication identifier) and x509-cert (as credentials type) in order to determine the device ID.

NB: The AMQP adapter needs to be configured for TLS in order to support this mechanism.

## AMQP Command-line Client
For purposes of demonstrating the usage of the AMQP adapter, the **Hono CLI Module** contains an AMQP command-line client for interacting with the AMQP adapter.

The command-line client supports the following parameters (with default values):

* `--message.address`: The AMQP 1.0 message address (default: `telemetry/DEFAULT_TENANT/4711`)
* `--amqp.host`: The hostname that the AMQP adapter is running on (default: `localhost`)
* `--amqp.port`: The port that the adapter is listening for incoming connections (default: `4040`)
* `--username`: The username to authenticate to the adapter (default: `sensor1@DEFAULT_TENANT`)
* `--password`: The password to authenticate to the adapter (default: `hono-secret`)
* `--payload`: The message payload body (default: `'{"temp": 5}'`)
* `--spring.profiles.active=amqp-adapter-cli`: Tells Hono CLI to activate the AMQP command-line client.

To run the client using the above default values, open a terminal and execute the following:

    $ /hono/cli/target$ java -jar hono-cli-0.7-SNAPSHOT-exec.jar --spring.profiles.active=amqp-adapter-cli

To run the client with a different value for the message address, username, password and payload body, do the following:

    $ /hono/cli/target$ java -jar hono-cli-0.7-SNAPSHOT-exec.jar --spring.profiles.active=amqp-adapter-cli --username=sensor20@DEFAULT_TENANT --password=my-secret --message.address=event/DEFAULT_TENANT/4710 --payload='{"alarm": 1}'
    
    Accepted{}

After running the client, the delivery state is printed to standard output. The output shown above shows that the request to upload a telemetry message succeeded and `accepted` disposition frame is printed to standard output. 

{{% note %}}
There are two JAR files in the hono/cli/target directory. The JAR to use for the client is the `hono-cli-0.7-SNAPSHOT-exec.jar` and not the `hono-cli-0.7-SNAPSHOT.jar` file. Running the latter will not work and will output the message: `no main manifest attribute, in hono-cli-0.7-SNAPSHOT.jar`
{{% /note %}}

## Publish Telemetry Data (authenticated Device)

The AMQP adapter supports publishing of telemetry data to Hono's Telemetry API. Telemetry messages can be published using either `AT_LEAST_ONCE` or `AT_MOST_ONCE` QoS.

* Message Address: `telemetry` or `t`
  * This refers to the `to` property of the message.
* Settlement Mode: `presettled` (AT_MOST_ONCE) or `unsettled` (AT_LEAST_ONCE)
* Authentication: required
* Message Header(s):
  * (optional) Arbitrary headers (durable, ttl, priority, ...)
* Message Body:
  * (optional) Arbitrary payload
* Message properties:
  * (optional) Arbitrary properties (content-type, correlation-id, ...)
* Disposition Frames:
  * Accepted: Message successfully processed by the adapter.
  * Released: Message cannot be processed and should be redelivered.
  * Rejected: Adapter rejects the message due to one of the following:
        * (`hono:bad-request`): Request rejected due to a bad client request.
        * (`amqp:unauthorized-access`): Request rejected because the adapter is disabled for tenant.
        * (`amqp:precondition-failed`): Request does not fulfill certain requirements e.g adapter cannot assert device registration etc.

When a device publishes data to the `telemetry` address, the AMQP adapter automatically determines the device's identity and tenant during the authentication process.

**Example**

Publish some JSON data for device `4711`:

    $ /hono/cli/target$ java -jar hono-cli-0.7-SNAPSHOT-exec.jar --spring.profiles.active=amqp-adapter-cli --message.address=telemetry

Notice that we only supplied a new value for the message address, leaving the other default values.

## Publish Telemetry Data (unauthenticated Device)

* Message Address: `telemetry/${tenant-id}/${device-id}` or `t/${tenant-id}/${device-id}`
* Settlement Mode: `presettled` (AT_MOST_ONCE) or `unsettled` (AT_LEAST_ONCE)
* Authentication: none
* Message Header(s):
  * (optional) Arbitrary headers (durable, ttl, priority, ...)
* Message Body:
  * (optional) Arbitrary payload
* Message properties:
  * (optional) Arbitrary properties (content-type, correlation-id, ...)
* Disposition Frames:
  * Accepted: Message successfully processed by the adapter.
  * Released: Message cannot be processed and should be redelivered.
  * Rejected: Adapter rejects the message due to:
        * (`hono:bad-request`): A bad client request (e.g invalid content-type).
        * (`amqp:unauthorized-access`): The adapter is disabled for tenant.
        * (`amqp:precondition-failed`): Request not fulfilling certain requirements.

Note how verbose the address is for unauthenticated devices. This address can be used by devices that have not authenticated to the protocol adapter. This requires the `HONO_AMQP_AUTHENTICATION_REQUIRED` configuration property to be explicitly set to `false` before starting the protocol adapter.

**Examples**

Publish some JSON data for device `4711`:

    $ /hono/cli/target$ java -jar hono-cli-0.7-SNAPSHOT-exec.jar --spring.profiles.active=amqp-adapter-cli --username="" --password=""

We supply an empty username and password to connect anonymously to the adapter.

## Publish Telemetry Data (authenticated Gateway)

A device that publishes data on behalf of another device is called a gateway device. The message address is used by *gateway* components to publish data *on behalf of* other devices which do not connect to a protocol adapter directly but instead are connected to the gateway, e.g. using some low-bandwidth radio based technology like [SigFox](https://www.sigfox.com) or [LoRa](https://www.lora-alliance.org/). In this case the credentials provided by the gateway during connection establishment with the protocol adapter are used to authenticate the gateway whereas the message address is used to identify the device that the gateway publishes data for.

**Examples**

A Gateway connecting to the adapter using `gw@DEFAULT_TENANT` as username and `gw-secret` as password and then publishing some JSON data for device `4711`, as shown below:

    $ /hono/cli/target$ java -jar hono-cli-0.7-SNAPSHOT-exec.jar --spring.profiles.active=amqp-adapter-cli --username="gw@DEFAULT_TENANT" --password="gw-secret"

In this example, we are using the default message address: `telemetry/DEFAULT_TENANT/4711`, which contains the real device that the gateway is publishing the message for.

## Publishing Events

The AMQP adapter supports publishing of events to Hono's Event API. Events are published using only `AT_LEAST_ONCE` delivery semantics.

## Publish an Event (authenticated Device)

* Message Address: `event` or `e`
* Settlement Mode: `unsettled` (AT_LEAST_ONCE)
* Authentication: required
* Message Header(s):
  * (optional) Arbitrary headers (durable, ttl, priority, ...)
* Message Body:
  * (optional) Arbitrary payload
* Message properties:
  * (optional) Arbitrary properties (content-type, correlation-id, ...)
* Disposition Frames:
  * Accepted: Message successfully processed by the adapter.
  * Released: Message cannot be processed and should be redelivered.
  * Rejected: Adapter rejects the message due to:
        * (`hono:bad-request`): A bad client request (e.g invalid content-type).
        * (`amqp:unauthorized-access`): The adapter is disabled for tenant.
        * (`amqp:precondition-failed`): Request not fulfilling certain requirements.

This is the preferred way for devices to publish events. It is available only if the protocol adapter has been configured to require devices to authenticate (which is the default).

**Example**

Upload a JSON string for device `4711`:

    $ /hono/cli/target$ java -jar hono-cli-0.7-SNAPSHOT-exec.jar --spring.profiles.active=amqp-adapter-cli --message.address=event --payload='{"alarm": 1}'

## Publish an Event (unauthenticated Device)

* Message Address: `event/${tenant-id}/${device-id}` or `e/${tenant-id}/${device-id}`
* Settlement Mode: `unsettled` (AT_LEAST_ONCE)
* Authentication: none
* Message Header(s):
  * (optional) Arbitrary headers (durable, ttl, priority, ...)
* Message Body:
  * (optional) Arbitrary payload
* Message properties:
  * (optional) Arbitrary properties (content-type, correlation-id, ...)
* Disposition Frames:
  * Accepted: Message successfully processed by the adapter.
  * Released: Message cannot be processed and should be redelivered.
  * Rejected: Adapter rejects the message due to:
        * (`hono:bad-request`): A bad client request (e.g invalid content-type).
        * (`amqp:unauthorized-access`): The adapter is disabled for tenant.
        * (`amqp:precondition-failed`): Request not fulfilling certain requirements.

This address format is used by devices that have not authenticated to the protocol adapter. Note that this requires the
`HONO_AMQP_AUTHENTICATION_REQUIRED` configuration property to be explicitly set to `false`.

**Examples**

Publish some JSON data for device `4711`:

    $ /hono/cli/target$ java -jar hono-cli-0.7-SNAPSHOT-exec.jar --spring.profiles.active=amqp-adapter-cli --username="" --password="" --message.address=event/DEFAULT_TENANT/4711 --payload='{"alarm": 1}'

## Publish an Event (authenticated Gateway)
**Examples**

A Gateway connecting to the adapter using `gw@DEFAULT_TENANT` as username and `gw-secret` as password and then publishing some JSON data for device `4711`, as shown below:

    $ /hono/cli/target$ java -jar hono-cli-0.7-SNAPSHOT-exec.jar --spring.profiles.active=amqp-adapter-cli --username="gw@DEFAULT_TENANT" --password="gw-secret" --message.address="event/DEFAULT_TENANT/4711"

In this example, we are using the default message address: `telemetry/DEFAULT_TENANT/4711`, which contains the real device that the gateway is publishing the message for.

## Downstream Meta Data

Like the MQTT and HTTP adapters, the AMQP adapter also includes the following application properties in the AMQP 1.0 message being sent downstream:

| Name               | Location        | Type      | Description                                                     |
| :----------------- | :-------------- | :-------- | :-------------------------------------------------------------- |
| *orig_adapter*     | *application*   | *string*  | Contains the adapter's *type name* which can be used by downstream consumers to determine the protocol adapter that the message has been received over. The AMQP adapter's type name is `hono-amqp. |
| *orig_address*     | *application*   | *string*  | Contains the name of the MQTT topic that the device has originally published the data to. |
| *device_id*     | *application*   | *string*  | Contains the ID of the device that published the message. |
| *reg_assertion*     | *application*   | *string*  | If the downstream peer requires assertion information to be added to the message. |


The adapter also considers [*defaults* registered for the device]({{< relref "api/Device-Registration-API.md#payload-format" >}}). For each default value the adapter checks if a corresponding property is already set on the message and if not, sets the message's property to the registered default value or adds a corresponding application property.

Note that of the standard AMQP 1.0 message properties only the *content-type* can be set this way to a registered default value.

## Tenant specific Configuration

The adapter uses the [Tenant API]({{< relref "api/Tenant-API.md#get-tenant-information" >}}) to retrieve *tenant specific configuration* for adapter type `hono-amqp`.
The following properties are (currently) supported:

| Name               | Type       | Default Value | Description                                                     |
| :----------------- | :--------- | :------------ | :-------------------------------------------------------------- |
| *enabled*          | *boolean*  | `true`       | If set to `false` the adapter will reject all data from devices belonging to the tenant and respond with a `amqp:unauthorized-access` as the error condition value for rejecting the message. |
