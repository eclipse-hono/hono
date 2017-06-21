+++
title = "Credentials API"
weight = 435
+++

The *Credentials API* is used by *Protocol Adapters* to retrieve credentials used to authenticate *Devices* connecting to the adapter. In particular, the API supports the storage, look up and deletion of *shared secrets* which are often used by IoT devices by means of *username/password* based authentication schemes.
<!--more-->

Credentials are of a certain *type* which indicates which authentication mechanism the credentials can be used with. Each set of credentials also contains an *authentication identity* which is the identity claimed by the device during authentication. This authentication identity is usually different from the *logical* ID the device has been registered with using Hono's [Device Registration API]({{< relref "api/Device-Registration-API.md" >}}). Multiple sets of credentials (including arbitrary *authentication identities*) can be registered for each *logical* device ID.

Note, however, that in real world applications the device credentials will probably be kept and managed by an existing *system of record*, using e.g. a database for persisting the credentials. The Credential API accounts for this fact by means of defining only the [Get Credentials]({{< relref "#get-credentials" >}}) operation as *mandatory*, meaning that this operation is strictly required by a Hono instance for it to work properly. The remaining operations are defined as *optional* from Hono's perspective.

The Credentials API is defined by means of AMQP 1.0 message exchanges, i.e. a client needs to connect to Hono using an AMQP 1.0 client in order to invoke operations of the API as described in the following sections.

{{% note %}}
This API is not yet implemented in Hono.
{{% /note %}}

## Preconditions

The preconditions for invoking any of the Credential API's operations are as follows:

1. Client has established an AMQP connection to Hono.
2. Client has established an AMQP link in role *sender* with Hono using target address `credentials/${tenant_id}`. This link is used by the client to send commands to Hono.
3. Client has established an AMQP link in role *receiver* with Hono using source address `credentials/${tenant_id}/${reply-to}` where *reply-to* may be any arbitrary string chosen by the client. This link is used by the client to receive responses to the requests it has sent to Hono. This link's source address is also referred to as the *reply-to* address for the request messages.

## Operations

The operations described in the following sections can be used by clients to manage credentials for authenticating devices connected to protocol adapters.

All operations are scoped to the *tenant* specified by the `${tenant_id}` parameter during link establishment. It is therefore not possible to e.g. retrieve credentials for devices of `TENANT_B` if the link has been established for target address `credentials/TENANT_A`.

### Add Credentials

Clients may use this command to initially *add* credentials for a device that has already been registered with Hono. The credentials to be added may be of arbitrary type. However, [Standard Credential Types]({{< relref "#standard-credential-types" >}}) contains an overview of some common types that are used by Hono's protocol adapters and which may be useful to others as well.

This operation is *optional*, implementors of this API may provide other means for adding credential information, e.g. a RESTful API or a configuration file.

**Message Flow**

*TODO* add sequence diagram

**Request Message Format**

The following table provides an overview of the properties a client needs to set on an *add credentials* message in addition to the [Standard Request Properties]({{< relref "#standard-request-properties" >}}).

| Name             | Mandatory | Location                 | Type            | Description                         |
| :--------------- | :-------: | :----------------------- | :-------------- | :---------------------------------- |
| *subject*        | yes       | *properties*             | UTF-8 *string*  | MUST contain the value `add`.      |

The request message payload MUST contain credential information as defined in [Credentials Format]({{< relref "#credentials-format" >}}).

**Response Message Format**

A response to an *add credentials* request contains the [Standard Response Properties]({{< relref "#standard-response-properties" >}}).

The response message's body is empty. 

The response message's *status* property may contain the following codes:

| Code  | Description |
| :---- | :---------- |
| *201* | Created, the credentials have been added successfully. |
| *409* | Conflict, there already exist credentials with the *type* and *auth-id* for the *device-id* from the payload. |
| *412* | Precondition Failed, there is no device registered with the given *device-id* within the tenant. |

For status codes indicating an error (codes in the `400 - 499` range) the message body MAY contain a detailed description of the error that occurred.

### Get Credentials

Protocol adapters use this command to *look up* credentials of a particular type for a device identity.

This operation is *mandatory* to implement.

**Message Flow**

*TODO* add sequence diagram

**Request Message Format**

The following table provides an overview of the properties a client needs to set on an *get credentials* message in addition to the [Standard Request Properties]({{< relref "#standard-request-properties" >}}).

| Name             | Mandatory | Location                 | Type            | Description                   |
| :--------------- | :-------: | :----------------------- | :-------------- | :---------------------------- |
| *subject*        | yes       | *properties*             | UTF-8 *string*  | MUST contain the value `get`. |

The body of the request MUST consist of a single *AMQP Value* section containing a UTF-8 encoded string representation of a single JSON object having the following members:

| Name             | Mandatory | Type       | Description |
| :--------------- | :-------: | :--------- | :---------- |
| *type*           | *yes*     | *string*   | The type of credentials to look up. Potential values include (but are not limited to) `psk`, `RawPublicKey`, `hashed-password` etc. |
| *auth-id*        | *yes*     | *string*   | The authentication identifier to look up credentials for. |

The following request payload may be used to look up the hashed password for user `billie`:

~~~json
{
  "type": "hashed-password",
  "auth-id": "billie"
}
~~~

**Response Message Format**

A response to a *get credentials* request contains the [Standard Response Properties]({{< relref "#standard-response-properties" >}}).

The response message payload MUST contain credential information as defined in [Credentials Format]({{< relref "#credentials-format" >}}) if the *status* is `200`.

The response message's *status* property may contain the following codes:

| Code  | Description |
| :---- | :---------- |
| *200* | OK, the payload contains the credentials for the authentication identifier. |
| *404* | Not Found, there are no credentials registered matching the criteria. |

For status codes indicating an error (codes in the `400 - 499` range) the message body MAY contain a detailed description of the error that occurred.

### Update Credentials

Clients use this command to *update* existing credentials registered for a device. All of the information that has been previously registered for the device gets *replaced* with the information contained in the request message.

This operation is *optional*, implementors of this API may provide other means for updating credential information, e.g. a RESTful API or a configuration file.

**Message Flow**

*TODO* add sequence diagram

**Request Message Format**

The following table provides an overview of the properties a client needs to set on an *update credentials* message in addition to the [Standard Request Properties]({{< relref "#standard-request-properties" >}}).

| Name             | Mandatory | Location                 | Type           | Description |
| :--------------- | :-------: | :----------------------- | :------------- | :---------- |
| *subject*        | yes       | *properties*             | UTF-8 *string* | MUST contain the value `update`. |

The request message payload MUST contain credential information as defined in [Credentials Format]({{< relref "#credentials-format" >}}).

**Response Message Format**

A response to an *update credentials* request contains the [Standard Response Properties]({{< relref "#standard-response-properties" >}}).

The response message's body is empty. 

The response message's *status* property may contain the following codes:

| Code  | Description |
| :---- | :---------- |
| *204* | No Content, the credentials have been updated successfully. |
| *404* | Not Found, there are no credentials registered matching the criteria from the payload. |

For status codes indicating an error (codes in the `400 - 499` range) the message body MAY contain a detailed description of the error that occurred.

### Remove Credentials

Clients use this command to *remove* credentials registered for a device. Once the credentials are removed, the device may no longer be able to authenticate with protocol adapters using the authentication mechanism that the removed credentials corresponded to.

This operation is *optional*, implementors of this API may provide other means for removing credential information, e.g. a RESTful API or a configuration file.

**Message Flow**

*TODO* add sequence diagram

**Request Message Format**

The following table provides an overview of the properties a client needs to set on a *remove credentials* message in addition to the [Standard Request Properties]({{< relref "#standard-request-properties" >}}).

| Name             | Mandatory | Location                 | Type           | Description |
| :--------------- | :-------: | :----------------------- | :------------- | :---------- |
| *subject*        | yes       | *properties*             | UTF-8 *string* | MUST contain the value `remove`. |

The body of the message MUST consist of a single *AMQP Value* section containing a UTF-8 encoded string representation of a single JSON object having the following properties:

| Name             | Mandatory | Type       | Description |
| :--------------- | :-------: | :--------- | :---------- |
| *device-id*      | *yes*     | *string*   | The ID of the device from which the credentials should be removed. |
| *type*           | *yes*     | *string*   | The type of credentials to remove. If set to `*` then all credentials of the device are removed (*auth-id* is ignored), otherwise only the credentials matching the *type* and *auth-id* are removed. |
| *auth-id*        | *no*      | *string*   | The authentication identifier of the credentials to remove. If omitted, then all credentials of the specified *type* are removed. |

The following JSON can be used to remove all `hashed-password` credentials of device `4711`:

~~~json
{
  "device-id": "4711",
  "type": "hashed-password"
}
~~~

**Response Message Format**

A response to a *remove credentials* request contains the [Standard Response Properties]({{< relref "#standard-response-properties" >}}).

The response message's body is empty.

The response message's *status* property may contain the following codes:

| Code  | Description |
| :---- | :---------- |
| *204* | No Content, the credentials have been removed successfully. |
| *404* | Not Found, there are no credentials registered matching the criteria given in the request payload. |

For status codes indicating an error (codes in the `400 - 499` range) the message body MAY contain a detailed description of the error that occurred.

## Standard Message Properties

Due to the nature of the request/response message pattern of the operations of the Credentials API, there are some standard properties shared by all of the request and response messages exchanged as part of the operations.

### Standard Request Properties

The following table provides an overview of the properties shared by all request messages regardless of the particular operation being invoked.

| Name             | Mandatory | Location                 | Type           | Description |
| :--------------- | :-------: | :----------------------- | :------------- | :---------- |
| *subject*        | yes       | *properties*             | UTF-8 *string* | MUST be set to the value defined by the particular operation being invoked. |
| *correlation-id* | no        | *properties*             | *message-id*   | MAY contain an ID used to correlate a response message to the original request. If set, it is used as the *correlation-id* property in the response, otherwise the value of the *message-id* property is used. |
| *message-id*     | yes       | *properties*             | UTF-8 *string* | MUST contain an identifier that uniquely identifies the message at the sender side. |
| *reply-to*       | yes       | *properties*             | UTF-8 *string*  | MUST contain the source address that the client wants to received response messages from. This address MUST be the same as the source address used for establishing the client's receive link (see [Preconditions]({{< relref "#preconditions" >}})). |

### Standard Response Properties

The following table provides an overview of the properties shared by all response messages regardless of the particular operation being invoked.

| Name             | Mandatory | Location                 | Type            | Description |
| :--------------- | :-------: | :----------------------- | :-------------- | :---------- |
| *correlation-id* | yes       | *properties*             | *message-id*    | Contains the *message-id* (or the *correlation-id*, if specified) of the request message that this message is the response to. |
| *device_id*      | yes       | *application-properties* | UTF-8 *string*  | Contains the ID of the device. |
| *tenant_id*      | yes       | *application-properties* | UTF-8 *string*  | Contains the ID of the tenant to which the device belongs. |
| *status*         | yes       | *application-properties* | *int*           | Contains the status code indicating the outcome of the operation. Concrete values and their semantics are defined for each particular operation. |

## Delivery States

Hono uses the following AMQP message delivery states when receiving request messages from clients:

| Delivery State | Description |
| :------------- | :---------- |
| *ACCEPTED*     | Indicates that Hono has successfully received and accepted the request for processing. |
| *REJECTED*     | Indicates that Hono has received the request but was not able to process it. The *error* field contains information regarding the reason why. Clients should not try to re-send the request using the same message properties in this case. |

## Credentials Format

Most of the operations of the Credentials API allow or require the inclusion of credential data in the payload of the
request or response messages of the operation. Such payload is carried in the body of the corresponding AMQP 
messages as part of a single *AMQP Value* section.

The credential data is carried in the payload as a UTF-8 encoded string representation of a single JSON object. It is an error to include payload that is not of this type.

The table below provides an overview of the standard members defined for the JSON object:

| Name             | Mandatory | Type       | Default Value | Description |
| :--------------- | :-------: | :--------- | :------------ | :---------- |
| *device-id*      | *yes*     | *string*   |               | The ID of the device to which the credentials belong. |
| *type*           | *yes*     | *string*   |               | The credential type name. The value may be arbitrarily chosen by clients but SHOULD reflect the particular type of authentication mechanism the credentials are to be used with. Possible values include (but are not limited to) `psk`, `RawPublicKey`, `hashed-password` etc. |
| *auth-id*        | *yes*     | *string*   |               | The identity that the device should be authenticated as. |
| *enabled*        | *no*      | *boolean*  | *true*        | If set to *false* the credentials are not supposed to be used to authenticate devices any longer. This may e.g. be used to disable a particular mechanism for authenticating the device. **NB** It is up to the discretion of the protocol adapter to make use of this information. |
| *secrets*        | *yes*     | *array*    |               | A list of secrets scoped to a particular time period. See [Secrets Format]({{< relref "#secrets-format" >}}) for details. |

For each set of credentials the combination of *auth-id* and *type* MUST be unique within a tenant.

### Secrets Format

Each set of credentials may contain arbitrary *secrets* scoped to a particular *validity period* during which the secrets may be used for authenticating a device. The validity periods MAY overlap in order to support the process of changing a secret on a device that itself doesn't support the definition of multiple secrets for gapless authentication across adjacent validity periods.

The table below contains the properties used to define the validity period of a single secret:

| Name             | Mandatory | Type       | Default Value | Description |
| :--------------- | :-------: | :--------- | :------------ | :---------- |
| *not-before*     | *no*      | *string*   | `null`        | The point in time from which on the secret may be used to authenticate devices. If not *null*, the value MUST be an [ISO 8601 compliant *combined date and time representation*](https://en.wikipedia.org/wiki/ISO_8601#Combined_date_and_time_representations). **NB** It is up to the discretion of the protocol adapter to make use of this information. |
| *not-after*      | *no*      | *string*   | `null`        | The point in time until which the secret may be used to authenticate devices. If not *null*, the value MUST be an [ISO 8601 compliant *combined date and time representation*](https://en.wikipedia.org/wiki/ISO_8601#Combined_date_and_time_representations). **NB** It is up to the discretion of the protocol adapter to make use of this information. |

### Examples

Below is an example for a payload containing [a hashed password]({{< relref "#hashed-password" >}}) for device `4711` with username `billie` using SHA512 as the hashing function with a 4 byte salt (Base64 encoding of `0x32AEF017`). Note that the payload does not contain a `not-before` property, thus it may be used immediately up until X-mas eve 2017.

~~~json
{
  "device-id": "4711",
  "type": "hashed-password",
  "auth-id": "billie",
  "enabled": true,
  "secrets": [{
    "not-after": "20171224T1900Z+0100",
    "pwd-hash": "AQIDBAUGBwg=",
    "salt": "Mq7wFw==",
    "hash-function": "sha512"
  }]
}
~~~

The next example contains two [pre-shared secrets]({{< relref "#pre-shared-key" >}}) with overlapping validity periods for device `myDevice` with PSK identity `jane`.

~~~json
{
  "device-id": "myDevice",
  "type": "psk",
  "auth-id": "jane",
  "enabled": true,
  "secrets": [{
    "not-after": "20170701T0000Z+0100",
    "key": "cGFzc3dvcmRfb2xk"
  },{
    "not-before": "20170629T0000Z+0100",
    "key": "cGFzc3dvcmRfbmV3"
  }]
}
~~~

## Credential Verification

Protocol Adapters are responsible for authenticating devices when they connect. The Credentials API provides the [Get Credentials]({{< relref "#get-credentials" >}}) operation to support Protocol Adapters in doing so as illustrated below:

**TBD sequence diagram**

Credentials that have their `enabled` property set to `false` MUST NOT be used for authenticating a device.
A Protocol Adapter MUST only use `secrets` for authentication which have

* their `not-before` property set to either `null` or the current or a past point in time **and**
* their `not-after` property set to either `null` or the current or a future point in time.

## Standard Credential Types

The following sections define some standard credential types and their properties. Applications are encouraged to make use of these types. However, the types are not enforced anywhere in Hono and clients may of course add application specific properties to the credential types.

### Common Properties

All credential types used with Hono MUST contain `device-id`, `type`, `auth-id`, `enabled` and `secrets` properties as defined in [Credentials Format]({{< relref "#credentials-format" >}}).

### Hashed Password

A credential type for storing a (hashed) password for a user.

Example:

~~~json
{
  "device-id": "4711",
  "type": "hashed-password",
  "auth-id": "billie",
  "secrets": [{
    "pwd-hash": "AQIDBAUGBwg=",
    "salt": "Mq7wFw==",
    "hash-function": "sha512"
  }]
}
~~~

| Name             | Mandatory | Type       | Default   | Description |
| :--------------- | :-------: | :--------- | :-------- | :---------- |
| *type*           | *yes*     | *string*   |           | The credential type name, always `hashed-password`. |
| *auth-id*        | *yes*     | *string*   |           | The *username* |
| *pwd-hash*       | *yes*     | *string*   |           | The Base64 encoded bytes representing the hashed password. |
| *salt*           | *no*      | *string*   |           | The Base64 encoded bytes used as *salt* for the password hash. If not set then the password hash has been created without salt. |
| *hash-function*  | *no*      | *string*   | `sha256`  | The name of the hash function used to create the password hash. Examples include `sha256`, `sha512` etc. |

**NB** The example above does not contain any of the `not-before`, `not-after` and `enabled` properties, thus the credentials can be used at any time according to the rules defined in [Credential Verification]({{< relref "#credential-verification" >}}).

### Pre-Shared Key

A credential type for storing a *Pre-shared Key* as used in TLS handshakes.

Example:

~~~json
{
  "device-id": "4711",
  "type": "psk",
  "auth-id": "little-sensor",
  "secrets": [{
    "key": "AQIDBAUGBwg="
  }]
}
~~~

| Name             | Mandatory | Type       | Description |
| :--------------- | :-------: | :--------- | :---------- |
| *type*           | *yes*     | *string*   | The credential type name, always `psk`. |
| *auth-id*        | *yes*     | *string*   | The PSK identity. |
| *key*            | *yes*     | *string*   | The Base64 encoded bytes representing the shared (secret) key. |

**NB** The example above does not contain any of the `not-before`, `not-after` and `enabled` properties, thus the credentials can be used at any time according to the rules defined in [Credential Verification]({{< relref "#credential-verification" >}}).
