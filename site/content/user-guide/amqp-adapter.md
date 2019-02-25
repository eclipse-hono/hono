+++
title = "AMQP Adapter"
weight = 220
+++

The AMQP protocol adapter allows clients (devices or gateway components) supporting the AMQP 1.0 protocol to publish messages to Eclipse Hono&trade;'s Telemetry, Event and Command & Control endpoints.
<!--more-->

## Device Authentication

By default, all Hono protocol adapters require clients (devices or gateway components) to authenticate during connection establishment. This is the preferred way for devices to publish data via protocol adapters. The AMQP adapter supports both the [SASL PLAIN](https://tools.ietf.org/html/rfc4616) and [SASL EXTERNAL](https://tools.ietf.org/html/rfc4422) authentication mechanisms. The former uses a *username* and *password* to authenticate to the adapter while the latter uses a client certificate.

In this guide, we will give examples for publishing telemetry and events for *authenticated* (using SASL PLAIN) and *unauthenticated* clients. 

NB: The AMQP adapter can be configured to *allow* unauthenticated devices to connect by setting configuration variable `HONO_AMQP_AUTHENTICATION_REQUIRED` to `false`.

### SASL PLAIN Authentication

The AMQP adapter supports authenticating clients using a *username* and *password*. This means that clients need to provide a *username* and a *password* when connecting to the AMQP adapter. If the adapter is configured for multi-tenancy (i.e `HONO_AMQP_SINGLE_TENANT` is set to `false`), then the *username* must match the pattern [*auth-id@tenant*], e.g. `sensor1@DEFAULT_TENANT`. Otherwise the `DEFAULT_TENANT` is assumed and the tenant-id can be omitted from the username.

The adapter verifies the credentials provided by the client against the credentials that the [Credentials Service] ({{< relref "#credentials-service-connection-configuration" >}}) has on record for the device. If the credentials match, then authentication is successful and the client device can proceed to publish messages to Hono.

When running the Hono example installation, as described in the [Getting Started guide]({{< relref "getting-started.md" >}}), the demo Credentials service comes pre-configured with a `hashed-password` secret for devices `4711` and `gw-1` of tenant `DEFAULT_TENANT` as shown below. These credentials are used in the following examples to illustrate the usage of the adapter.

{{% note %}}
There is a subtle difference between the *device identifier* (*device-id*) and the *auth-id* a device uses for authentication. See [Device Identity]({{< relref "concepts/device-identity.md" >}}) for a discussion of the concepts.
{{% /note %}}

### SASL EXTERNAL Authentication

When a device uses a client certificate for authentication, the TLS handshake is initiated during TCP connection establishment. If no trust anchor is configured for the AMQP adapter, the TLS handshake will succeed only if the certificate has not yet expired. Once the TLS handshake completes and a secure connection is established, the certificate's signature is checked during the SASL handshake. To complete the SASL handshake and authenticate the client, the adapter performs the following steps:

* Adapter extracts the client certificate's *Issuer DN* and uses it to
* us the Tenant service to look up the tenant that the client belongs to. In order for the lookup to succeed, the tenant’s trust anchor needs to be configured by means of registering the [trusted certificate authority]({{< relref "/api/Tenant-API.md#trusted-ca-format" >}}).
* If the lookup succeeds, the Tenant service returns the tenant, thus implicitly establishing the tenant that the device belongs to.
* Adapter validates the device’s client certificate using the registered trust anchor for the tenant.
* Finally, adapter authenticates the client certificate using Hono's credentials API. In this step, the adapter uses the client certificate’s *Subject DN* (as authentication identifier) and `x509-cert` (for the credentials type) in order to determine the device ID.

NB: The AMQP adapter needs to be configured for TLS in order to support this mechanism.

## Link Establishment

Clients can publish all types of messages to the AMQP adapter via a single *anonymous* sender link. Using *AT MOST ONCE* delivery semantics, the client will not wait for the message to be accepted and settled by the downstream consumer. However, with *AT LEAST ONCE*, the client sends the message and waits for the message to be delivered to and accepted by the downstream consumer. If the message cannot be delivered due to a failure, the client will be notified.

The client indicates its preferred message delivery mode by means of the *snd-settle-mode* and *rcv-settle-mode* fields of its *attach* frame during link establishment. Clients should use `mixed` as the *snd-settle-mode* and `first` as the *rcv-settle-mode* in order to be able to use the same link for sending all types of messages using different delivery semantics as described in the following sections.

## Error Handling

The AMQP adapter distinguishes between two types of errors when a message is published using *at least once*:

* An error caused by the client side, e.g invalid message address, content-type, adapter disabled for tenant etc.
* An error caused by the server side, e.g no downstream consumers registered, downstream connection loss etc.

For a client side error, the adapter settles the message transfer with the *rejected* outcome and provides an error description in the corresponding disposition frame. In the case of a server-side error, the adapter settles the message with the *released* outcome, indicating to the client that the message itself was OK but it cannot be delivered due to a failure beyond the control of the client. In the latter case, a client may attempt to re-send the message unaltered.


## AMQP Command-line Client

For purposes of demonstrating the usage of the AMQP adapter, the **Hono CLI Module** contains an AMQP command-line client for interacting with the AMQP adapter.

The command-line client supports the following parameters (with default values):

* `--spring.profiles.active=amqp-adapter-cli`: Tells Hono CLI to activate the AMQP command-line client.
* `--message.address`: The AMQP 1.0 message address (default: `telemetry`)
* `--message.payload`: The message payload body (default: `'{"temp": 5}'`)
* `--hono.client.host`: The host name that the AMQP adapter is running on (default: `localhost`)
* `--hono.client.port`: The port that the adapter is listening for incoming connections (default: `5672`)

To run the client using the above default values, open a terminal and execute the following:

    $ /hono/cli/target$ java -jar hono-cli-*-exec.jar --spring.profiles.active=amqp-adapter-cli --hono.client.username=sensor1@DEFAULT_TENANT --hono.client.password=hono-secret
    
    Accepted{}

The client prints the outcome of the operation to standard out. The outcome above (`Accepted`) indicates that the request to upload the data has succeeded.

**NB**
There are two JAR files in the hono/cli/target directory. The JAR to use for the client is the `hono-cli-$VERSION-exec.jar` and not the `hono-cli-$VERSION.jar` file. Running the latter will not work and will output the message: `no main manifest attribute, in hono-cli-$VERSION.jar`

## Publishing Telemetry Data

The client indicates the delivery mode to use when uploading telemetry messages by means of the *settled* and *rcv-settle-mode* properties of the AMQP *transfer* frame(s) it uses for uploading the message. The AMQP adapter will accept messages using a delivery mode according to the following table:

| settled                | rcv-settle-mode        | Delivery semantics |
| :--------------------- | :--------------------- | :----------------- |
| `false`               | `first`               | The adapter will forward the message to the downstream AMQP 1.0 Messaging Network and will forward any AMQP *disposition* frame received from the AMQP 1.0 Messaging Network to the client *as is*. It is up to the client's discretion if and how it processes the disposition frame. The adapter will accept any re-delivered message. Sending *unsettled* messages allows for clients to implement either *AT LEAST ONCE* or *AT MOST ONCE* delivery semantics, depending on whether a client actually waits for and considers the disposition frames it receives from the adapter or not. This is the recommended mode for uploading telemetry data. |
| `true`                | `first`                | The adapter will acknowledge and settle any received message spontaneously before forwarding it to the downstream AMQP 1.0 Messaging Network. The adapter will ignore any AMQP *disposition* frames it receives from the AMQP 1.0 Messaging Network. Sending *pre-settled* messages allows for clients to implement *AT MOST ONCE* delivery semantics only. This is the fastest mode of delivery but has the drawback of less reliable end-to-end flow control and potential loss of messages without notice. |

All other combinations are not supported by the adapter and will result in the message being ignored (pre-settled) or rejected (unsettled).

## Publish Telemetry Data (authenticated Device)

The AMQP adapter supports publishing of telemetry data to Hono's Telemetry API. Telemetry messages can be published using either *AT LEAS ONCE* or *AT MOST ONCE delivery semantics.

* Message Address: `telemetry` or `t`
  * This refers to the `to` property of the message.
* Settlement Mode: `presettled` (*AT MOST ONCE*) or `unsettled` (*AT LEAST ONCE*)
* Authentication: SASL PLAIN or SASL EXTERNAL
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

    $ /hono/cli/target$ java -jar hono-cli-*-exec.jar --spring.profiles.active=amqp-adapter-cli --hono.client.username=sensor1@DEFAULT_TENANT --hono.client.password=hono-secret

Notice that we only supplied a new value for the message address, leaving the other default values.

## Publish Telemetry Data (unauthenticated Device)

* Message Address: `telemetry/${tenant-id}/${device-id}` or `t/${tenant-id}/${device-id}`
* Settlement Mode: `presettled` (AT MOST ONCE) or `unsettled` (AT LEAST ONCE)
* Authentication: none
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

    $ /hono/cli/target$ java -jar hono-cli-*-exec.jar --spring.profiles.active=amqp-adapter-cli --message.address=t/DEFAULT_TENANT/4711

## Publish Telemetry Data (authenticated Gateway)

A device that publishes data on behalf of another device is called a gateway device. The message address is used by *gateway* components to publish data *on behalf of* other devices which do not connect to a protocol adapter directly but instead are connected to the gateway, e.g. using some low-bandwidth radio based technology like [SigFox](https://www.sigfox.com) or [LoRa](https://www.lora-alliance.org/). In this case the credentials provided by the gateway during connection establishment with the protocol adapter are used to authenticate the gateway whereas the message address is used to identify the device that the gateway publishes data for.

**Examples**

A Gateway connecting to the adapter using `gw@DEFAULT_TENANT` as username and `gw-secret` as password and then publishing some JSON data for device `4711`:

    $ /hono/cli/target$ java -jar hono-cli-*-exec.jar --spring.profiles.active=amqp-adapter-cli --hono.client.username=gw@DEFAULT_TENANT --hono.client.password=gw-secret --message.address=t/DEFAULT_TENANT/4711

In this example, we are using message address `t/DEFAULT_TENANT/4711` which contains the device that the gateway is publishing the message for.

## Publishing Events

The adapter supports *AT LEAST ONCE* delivery of *Event* messages only. A client therefore MUST set the *settled* property to `false` and the *rcv-settle-mode* property to `first` in all *transfer* frame(s) it uses for uploading events. All other combinations are not supported by the adapter and result in the message being rejected.

## Publish an Event (authenticated Device)

* Message Address: `event` or `e`
* Settlement Mode: `unsettled` (AT LEAST ONCE)
* Authentication: SASL PLAIN or SASL EXTERNAL
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

    $ /hono/cli/target$ java -jar hono-cli-*-exec.jar --spring.profiles.active=amqp-adapter-cli --hono.client.username=sensor1@DEFAULT_TENANT --hono.client.password=hono-secret --message.address=event --message.payload='{"alarm": 1}'

## Publish an Event (unauthenticated Device)

* Message Address: `event/${tenant-id}/${device-id}` or `e/${tenant-id}/${device-id}`
* Settlement Mode: `unsettled` (AT LEAST ONCE)
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

    $ /hono/cli/target$ java -jar hono-cli-*-exec.jar --spring.profiles.active=amqp-adapter-cli --message.address=e/DEFAULT_TENANT/4711 --message.payload='{"alarm": 1}'

## Publish an Event (authenticated Gateway)

**Examples**

A Gateway connecting to the adapter using `gw@DEFAULT_TENANT` as username and `gw-secret` as password and then publishing some JSON data for device `4711`:

    $ /hono/cli/target$ java -jar hono-cli-*-exec.jar --spring.profiles.active=amqp-adapter-cli --hono.client.username=gw@DEFAULT_TENANT --hono.client.password=gw-secret --message.address=e/DEFAULT_TENANT/4711

In this example, we are using message address `e/DEFAULT_TENANT/4711` which contains the device that the gateway is publishing the message for.

## Command & Control

The AMQP adapter supports devices to receive commands that have been sent by business applications by means of opening a receiver link using a device specific *source address* as described below. When a device no longer wants to receive commands anymore, it can simply close the link.

When a device has successfully opened a receiver link for commands, the adapter sends an [empty notification]({{< relref "/api/Event-API.md#empty-notification" >}}) on behalf of the device to the downstream AMQP 1.0 Messaging Network with the *ttd* header set to `-1`, indicating that the device will be ready to receive commands until further notice. Analogously, the adapter sends an empty notification with the *ttd* header set to `0` when a device closes the link or disconnects.

Devices send their responses to commands by means of sending an AMQP message with properties specific to the command that has been executed. The AMQP adapter accepts responses being published using either *at most once* (QoS 0) or *at least once* (QoS 1) delivery semantics. The device must send the command response messages using the same (sender) link that it uses for sending telemetry data and events.

### Receiving Commands

A device MUST use the following source address in its *attach* frame to open a link for receiving commands:

* `control` (authenticated device)
* `control/${tenant}/${device-id}` (unauthenticated device)

The adapter supports *AT LEAST ONCE* delivery of command messages only. A client therefore MUST use `unsettled` for the *snd-settle-mode* and `first` for the *rcv-settle-mode* fields of its *attach* frame during link establishment. All other combinations are not supported and result in the termination of the link.

Once the link has been established, the adapter will send command messages having the following properties:

| Name              | Mandatory       | Location                 | Type        | Description |
| :--------------   | :-------------: | :----------------------- | :---------- | :---------- |
| *subject*         | yes             | *properties*             | *string*    | Contains the name of the command to be executed. |
| *reply-to*        | no              | *properties*             | *string*    | Contains the address to which the command response should be sent. This property will be empty for *one-way* commands. |
| *correlation-id*  | no              | *properties*             | *string*    | This property will be empty for *one-way* commands, otherwise it will contain the identifier used to correlate the response with the command request. |

### Sending a Response to a Command

A device only needs to respond to commands that contain a *reply-to* address and a *correlation-id*. However, if the application expects a response, then devices must publish a response back to the application. Devices may use the same anonymous sender link for this purpose that they also use for sending telemetry data and events.

The adapter supports *AT LEAST ONCE* delivery of command response messages only. A client therefore MUST set the *settled* property to `true` and the *rcv-settle-mode* property to `first` in all *transfer* frame(s) it uses for uploading command responses. All other combinations are not supported by the adapter and result in the message being rejected.

The table below provides an overview of the properties that must be set on a command response message:

 Name              | Mandatory       | Location                 | Type         | Description |
| :--------------  | :-------------: | :----------------------- | :----------- | :---------- |
| *to*             | yes             | *properties*             | *string*     | MUST contain the value of the *reply-to* property of the command request message. |
| *correlation-id* | yes             | *properties*             | *string*     | MUST contain the value of the *correlation-id* property of the command request message. |
| *status*         | yes             | *application-properties* | *integer*    | MUST contain a status code indicating the outcome of processing the command at the device (see [Command & Control API]({{< ref "/api/Command-And-Control-API.md" >}}) for details). |

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
