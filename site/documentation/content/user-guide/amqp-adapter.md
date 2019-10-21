+++
title = "AMQP Adapter"
weight = 220
+++

The AMQP protocol adapter allows clients (devices or gateway components) supporting the AMQP 1.0 protocol to publish messages to Eclipse Hono&trade;'s Telemetry, Event and Command & Control endpoints.
<!--more-->

## Device Authentication

By default, all Hono protocol adapters require clients (devices or gateway components) to authenticate during connection establishment. This is the preferred way for devices to publish data via protocol adapters. The AMQP adapter supports both the [SASL PLAIN](https://tools.ietf.org/html/rfc4616) and [SASL EXTERNAL](https://tools.ietf.org/html/rfc4422) authentication mechanisms. The former uses a *username* and *password* to authenticate to the adapter while the latter uses a client certificate.

In this guide, we will give examples for publishing telemetry and events for *authenticated* (using SASL PLAIN) and *unauthenticated* clients. 

**NB** The AMQP adapter can be configured to *allow* unauthenticated devices to connect by setting configuration variable `HONO_AMQP_AUTHENTICATION_REQUIRED` to `false`.

### SASL PLAIN Authentication

The AMQP adapter supports authenticating clients using a *username* and *password*. This means that clients need to provide a *username* and a *password* when connecting to the AMQP adapter. If the adapter is configured for multi-tenancy (i.e `HONO_AMQP_SINGLE_TENANT` is set to `false`), then the *username* must match the pattern [*auth-id@tenant*], e.g. `sensor1@DEFAULT_TENANT`. Otherwise the `DEFAULT_TENANT` is assumed and the tenant-id can be omitted from the username.

The adapter verifies the credentials provided by the client against the credentials the [configured Credentials service]({{< relref "/admin-guide/amqp-adapter-config#credentials-service-connection-configuration" >}}) has on record for the client. If the credentials match, then authentication is successful and the client device can proceed to publish messages to Hono.

The examples below refer to devices `4711` and `gw-1` of tenant `DEFAULT_TENANT` using *auth-ids* `sensor1` and `gw1` and corresponding passwords. The example deployment as described in the [Deployment Guides]({{< relref "deployment" >}}) comes pre-configured with the corresponding entities in its device registry component.

**NB** There is a subtle difference between the *device identifier* (*device-id*) and the *auth-id* a device uses for authentication. See [Device Identity]({{< relref "/concepts/device-identity.md" >}}) for a discussion of the concepts.

### SASL EXTERNAL Authentication

When a device uses a client certificate for authentication, the TLS handshake is initiated during TCP connection establishment. If no trust anchor is configured for the AMQP adapter, the TLS handshake will succeed only if the certificate has not yet expired. Once the TLS handshake completes and a secure connection is established, the certificate's signature is checked during the SASL handshake. To complete the SASL handshake and authenticate the client, the adapter performs the following steps:

* Adapter extracts the client certificate's *Issuer DN* and uses it to
* us the Tenant service to look up the tenant that the client belongs to. In order for the lookup to succeed, the tenant’s trust anchor needs to be configured by means of registering the [trusted certificate authority]({{< relref "/api/tenant#trusted-ca-format" >}}).
* If the lookup succeeds, the Tenant service returns the tenant, thus implicitly establishing the tenant that the device belongs to.
* Adapter validates the device’s client certificate using the registered trust anchor for the tenant.
* Finally, adapter authenticates the client certificate using Hono's credentials API. In this step, the adapter uses the client certificate’s *Subject DN* (as authentication identifier) and `x509-cert` (for the credentials type) in order to determine the device ID.

**NB** The AMQP adapter needs to be configured for TLS in order to support this mechanism.

## Connection Limits

After verifying the credentials, the number of existing connections is checked against the configured [resource-limits] ({{< ref "/concepts/resource-limits.md" >}}) by the AMQP adapter.  If the limit is exceeded then the connection request is not accepted.

## Message Limits

Before accepting any telemetry or event or command messages, the AMQP adapter verifies that the configured [message limit] ({{< relref "/concepts/resource-limits.md" >}}) is not exceeded. The incoming message is discarded if the limit is exceeded. 

## Connection Event

The AMQP Adapter can send a [Connection Event]({{< relref "/api/event#connection-event" >}}) once the connection with a device has been successfully established or ended. Note that this requires the [`HONO_CONNECTION_EVENTS_PRODUCER`]({{< relref "/admin-guide/mqtt-adapter-config#service-configuration" >}}) configuration property to be explicitly set to `events`.

## Link Establishment

The AMQP adapter supports the [Anonymous Terminus for Message Routing](http://docs.oasis-open.org/amqp/anonterm/v1.0/anonterm-v1.0.html) specification
and requires clients to create a single sender link using the `null` target address for publishing all types of messages to the AMQP adapter.

Using *AT MOST ONCE* delivery semantics, the client will not wait for the message to be accepted and settled by the downstream consumer. However, with *AT LEAST ONCE*, the client sends the message and waits for the message to be delivered to and accepted by the downstream consumer. If the message cannot be delivered due to a failure, the client will be notified.

The client indicates its preferred message delivery mode by means of the *snd-settle-mode* and *rcv-settle-mode* fields of its *attach* frame during link establishment. Clients should use `mixed` as the *snd-settle-mode* and `first` as the *rcv-settle-mode* in order to be able to use the same link for sending all types of messages using different delivery semantics as described in the following sections.

## Error Handling

The AMQP adapter distinguishes between two types of errors when a message is published using *at least once*:

* An error caused by the client side, e.g invalid message address, content-type, adapter disabled for tenant etc.
* An error caused by the server side, e.g no downstream consumers registered, downstream connection loss etc.

For a client side error, the adapter settles the message transfer with the *rejected* outcome and provides an error description in the corresponding disposition frame. In the case of a server-side error, the adapter settles the message with the *released* outcome, indicating to the client that the message itself was OK but it cannot be delivered due to a failure beyond the control of the client. In the latter case, a client may attempt to re-send the message unaltered.


## AMQP Command-line Client

For purposes of demonstrating the usage of the AMQP adapter, the **Hono CLI Module** contains an AMQP command-line client for interacting with the AMQP adapter. The client can be used to send telemetry or events and to receive/respond to command request messages.

The command-line client supports the following parameters (with default values):

* `--spring.profiles.active=amqp-send`: Profile for sending telemetry data or events to Hono.
* `--spring.profiles.active=amqp-command`: Profile for receiving and responding to command request messages.
* `--message.address`: The AMQP 1.0 message address (default: `telemetry`)
* `--message.payload`: The message payload body (default: `'{"temp": 5}'`)
* `--hono.client.host`: The host name that the AMQP adapter is running on (default: `localhost`)
* `--hono.client.port`: The port that the adapter is listening for incoming connections (default: `5672`)

To run the client to send a telemetry message to Hono, open a terminal and execute the following:

~~~sh
# in directory: hono/cli/target/
java -jar hono-cli-*-exec.jar --spring.profiles.active=amqp-send --hono.client.username=sensor1@DEFAULT_TENANT --hono.client.password=hono-secret

Accepted{}
~~~

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

The AMQP adapter supports publishing of telemetry data to Hono's Telemetry API. Telemetry messages can be published using either *AT LEAST ONCE* or *AT MOST ONCE* delivery semantics.

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
        * (`amqp:resource-limit-exceeded`): Request rejected because the message limit for the given tenant is exceeded.

When a device publishes data to the `telemetry` address, the AMQP adapter automatically determines the device's identity and tenant during the authentication process.

**Example**

Publish some JSON data for device `4711`:

~~~sh
# in directory: hono/cli/target/
java -jar hono-cli-*-exec.jar --spring.profiles.active=amqp-send --hono.client.username=sensor1@DEFAULT_TENANT --hono.client.password=hono-secret
~~~

Notice that we only supplied a new value for the message address, leaving the other default values.

Publish some JSON data for device `4711` using a client certificate for authentication:

~~~sh
# in directory: hono/cli/target/
java -jar hono-cli-*-exec.jar --spring.profiles.active=amqp-send --hono.client.port=5671 --hono.client.certPath=config/hono-demo-certs-jar/device-4711-cert.pem --hono.client.keyPath=config/hono-demo-certs-jar/device-4711-key.pem --hono.client.trustStorePath=config/hono-demo-certs-jar/trusted-certs.pem --hono.client.hostnameVerificationRequired=false
~~~

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
        * (`amqp:resource-limit-exceeded`): Request rejected because the message limit for the given tenant is exceeded.

Note how verbose the address is for unauthenticated devices. This address can be used by devices that have not authenticated to the protocol adapter. This requires the `HONO_AMQP_AUTHENTICATION_REQUIRED` configuration property to be explicitly set to `false` before starting the protocol adapter.

**Examples**

Publish some JSON data for device `4711`:

~~~sh
# in directory: hono/cli/target/
java -jar hono-cli-*-exec.jar --spring.profiles.active=amqp-send --message.address=t/DEFAULT_TENANT/4711
~~~

## Publish Telemetry Data (authenticated Gateway)

A device that publishes data on behalf of another device is called a gateway device. The message address is used by *gateway* components to publish data *on behalf of* other devices which do not connect to a protocol adapter directly but instead are connected to the gateway, e.g. using some low-bandwidth radio based technology like [SigFox](https://www.sigfox.com) or [LoRa](https://lora-alliance.org/). In this case the credentials provided by the gateway during connection establishment with the protocol adapter are used to authenticate the gateway whereas the message address is used to identify the device that the gateway publishes data for.

**Examples**

A gateway connecting to the adapter using `gw@DEFAULT_TENANT` as username and `gw-secret` as password and then publishing some JSON data for device `4711`:

~~~sh
# in directory: hono/cli/target/
java -jar hono-cli-*-exec.jar --spring.profiles.active=amqp-send --hono.client.username=gw@DEFAULT_TENANT --hono.client.password=gw-secret --message.address=t/DEFAULT_TENANT/4711
~~~

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
        * (`amqp:resource-limit-exceeded`): Request rejected because the message limit for the given tenant is exceeded.

This is the preferred way for devices to publish events. It is available only if the protocol adapter has been configured to require devices to authenticate (which is the default).

**Example**

Upload a JSON string for device `4711`:

~~~sh
# in directory: hono/cli/target/
java -jar hono-cli-*-exec.jar --spring.profiles.active=amqp-send --hono.client.username=sensor1@DEFAULT_TENANT --hono.client.password=hono-secret --message.address=event --message.payload='{"alarm": 1}'
~~~

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
        * (`amqp:resource-limit-exceeded`): Request rejected because the message limit for the given tenant is exceeded.

This address format is used by devices that have not authenticated to the protocol adapter. Note that this requires the
`HONO_AMQP_AUTHENTICATION_REQUIRED` configuration property to be explicitly set to `false`.

**Examples**

Publish some JSON data for device `4711`:

~~~sh
# in directory: hono/cli/target/
java -jar hono-cli-*-exec.jar --spring.profiles.active=amqp-send --message.address=e/DEFAULT_TENANT/4711 --message.payload='{"alarm": 1}'
~~~

## Publish an Event (authenticated Gateway)

**Examples**

A gateway connecting to the adapter using `gw@DEFAULT_TENANT` as username and `gw-secret` as password and then publishing some JSON data for device `4711`:

~~~sh
# in directory: hono/cli/target/
java -jar hono-cli-*-exec.jar --spring.profiles.active=amqp-send --hono.client.username=gw@DEFAULT_TENANT --hono.client.password=gw-secret --message.address=e/DEFAULT_TENANT/4711
~~~

In this example, we are using message address `e/DEFAULT_TENANT/4711` which contains the device that the gateway is publishing the message for.

## Command & Control

The AMQP adapter supports devices to receive commands that have been sent by business applications by means of opening a receiver link using a device specific *source address* as described below. When a device no longer wants to receive commands anymore, it can simply close the link.

When a device has successfully opened a receiver link for commands, the adapter sends an [empty notification]({{< relref "/api/event#empty-notification" >}}) on behalf of the device to the downstream AMQP 1.0 Messaging Network with the *ttd* header set to `-1`, indicating that the device will be ready to receive commands until further notice. Analogously, the adapter sends an empty notification with the *ttd* header set to `0` when a device closes the link or disconnects.

Devices send their responses to commands by means of sending an AMQP message with properties specific to the command that has been executed. The AMQP adapter accepts responses being published using either *at most once* (QoS 0) or *at least once* (QoS 1) delivery semantics. The device must send the command response messages using the same (sender) link that it uses for sending telemetry data and events.

The AMQP adapter checks the configured [message limit] ({{< relref "/concepts/resource-limits.md" >}}) before accepting any command requests and responses. In case of incoming command requests from business applications or the command responses from devices, if the message limit is exceeded, the Adapter rejects the message with the reason `amqp:resource-limit-exceeded`. 

### Receiving Commands

A device MUST use the following source address in its *attach* frame to open a link for receiving commands:

* `command` (authenticated device)
* `command` (authenticated gateway receiving commands for all devices it acts on behalf of)
* `command/${tenant}/${device-id}` (unauthenticated device)
* `command/${tenant}/${device-id}` (authenticated gateway receiving commands for a specific device it acts on behalf of)

{{% note %}}
Previous versions of Hono used `control` instead of `command` as address prefix. Using the `control` prefix is still supported but deprecated. 
{{% /note %}}

The adapter supports *AT LEAST ONCE* delivery of command messages only. A client therefore MUST use `unsettled` for the *snd-settle-mode* and `first` for the *rcv-settle-mode* fields of its *attach* frame during link establishment. All other combinations are not supported and result in the termination of the link.

Once the link has been established, the adapter will send command messages having the following properties:

| Name              | Mandatory       | Location                 | Type        | Description |
| :--------------   | :-------------: | :----------------------- | :---------- | :---------- |
| *subject*         | yes             | *properties*             | *string*    | Contains the name of the command to be executed. |
| *reply-to*        | no              | *properties*             | *string*    | Contains the address to which the command response should be sent. This property will be empty for *one-way* commands. |
| *correlation-id*  | no              | *properties*             | *string*    | This property will be empty for *one-way* commands, otherwise it will contain the identifier used to correlate the response with the command request. |
| *device_id*       | no              | *application-properties* | *string*    | This property will only be set if an authenticated gateway has connected to the adapter. It will contain the id of the device (connected to the gateway) that the command is targeted at. |

Authenticated gateways will receive commands for devices which do not connect to a protocol adapter directly but instead are connected to the gateway. Corresponding devices have to be configured so that they can be used with a gateway. See [Configuring Gateway Devices]({{< relref "/admin-guide/device-registry-config.md#configuring-gateway-devices" >}}) for details.

If a device is configured in such a way that there can be *one* gateway, acting on behalf of the device, a command sent to this device will by default be directed to that gateway.

If a device is configured to be used with *multiple* gateways, the particular gateway that last acted on behalf of the device will be the target that commands for that device will be routed to. The mapping of device and gateway last used by the device is updated whenever a device sends a telemetry, event or command response message via the gateway. This means that for a device configured to be used via multiple gateways to receive commands, the device first has to send at least one telemetry or event message to establish which gateway to use for receiving commands for that device.

### Sending a Response to a Command

A device only needs to respond to commands that contain a *reply-to* address and a *correlation-id*. However, if the application expects a response, then devices must publish a response back to the application. Devices may use the same anonymous sender link for this purpose that they also use for sending telemetry data and events.

The adapter supports *AT LEAST ONCE* delivery of command response messages only. A client therefore MUST set the *settled* property to `true` and the *rcv-settle-mode* property to `first` in all *transfer* frame(s) it uses for uploading command responses. All other combinations are not supported by the adapter and result in the message being rejected.

The table below provides an overview of the properties that must be set on a command response message:

 Name              | Mandatory       | Location                 | Type         | Description |
| :--------------  | :-------------: | :----------------------- | :----------- | :---------- |
| *to*             | yes             | *properties*             | *string*     | MUST contain the value of the *reply-to* property of the command request message. |
| *correlation-id* | yes             | *properties*             | *string*     | MUST contain the value of the *correlation-id* property of the command request message. |
| *status*         | yes             | *application-properties* | *integer*    | MUST contain a status code indicating the outcome of processing the command at the device (see [Command & Control API]({{< relref "/api/command-and-control" >}}) for details). |

### Examples

The AMQP adapter client can be used to simulate a device which receives commands and sends responses back to the application. The command line client is used to simulate an application sending commands to devices and receiving command responses from devices.

Start the AMQP adapter client, as follows:

~~~sh
# in directory: hono/cli/target/
java -jar hono-cli-*-exec.jar --spring.profiles.active=amqp-command --hono.client.username=sensor1@DEFAULT_TENANT --hono.client.password=hono-secret
~~~

After successfully starting the client, a message indicating that the device is ready to receive commands will be printed to standard output. The device is now waiting to receive commands from applications. 

To send a command to the device, open a new terminal shell and start the command application, as shown below:

~~~sh
# in directory: hono/cli/
java -jar target/hono-cli-*-exec.jar --hono.client.host=localhost --hono.client.username=consumer@HONO --hono.client.password=verysecret --spring.profiles.active=command,ssl
~~~

{{% note %}}
Change into the `cli` directory before running the command above to start the command application. If you change into the target directory (i.e `cli/target`), then the client will not be able to locate to certificate needed to connect to the messaging network.
{{% /note %}}

Once the command application starts successfully, enter a command name, payload and content-type of the command to send to the device.

~~~plaintext
>>>>>>>>> Enter name of command for device [DEFAULT_TENANT:4711] (prefix with 'ow:' to send one-way command):
setBrightness
>>>>>>>>> Enter command payload:
some-payload
>>>>>>>>> Enter content type:
text/plain
~~~

After sending the command, the device (i.e. AMQP command client) will print out the command name and payload that it receives and automatically sends a command response to the application.

~~~plaintext
Received Command Message : [Command name: setBrightness, Command payload: some-payload]
Command response sent [outcome: Accepted{}]
~~~

## Downstream Meta Data

The adapter includes the following meta data in the application properties of messages being sent downstream:

| Name               | Type      | Description                                                     |
| :----------------- | :-------- | :-------------------------------------------------------------- |
| *device_id*        | *string*  | The identifier of the device that the message originates from.  |
| *orig_adapter*     | *string*  | Contains the adapter's *type name* which can be used by downstream consumers to determine the protocol adapter that the message has been received over. The AMQP adapter's type name is `hono-amqp`. |
| *orig_address*     | *string*  | Contains the AMQP *target address* that the device has used to send the data. |

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

The adapter uses the [Tenant API]({{< ref "/api/tenant#get-tenant-information" >}}) to retrieve *tenant specific configuration* for adapter type `hono-amqp`.
The following properties are (currently) supported:

| Name               | Type       | Default Value | Description                                                     |
| :----------------- | :--------- | :------------ | :-------------------------------------------------------------- |
| *enabled*          | *boolean*  | `true`       | If set to `false` the adapter will reject all data from devices belonging to the tenant and respond with a `amqp:unauthorized-access` as the error condition value for rejecting the message. |
