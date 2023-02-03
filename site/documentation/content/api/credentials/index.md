---
title: "Credentials API Specification"
linkTitle: "Credentials API"
weight: 430
resources:
  - src: preconditions.svg
  - src: get-credentials-success.svg
  - src: mqtt_adapter_device_authentication.svg
---

The *Credentials API* is used by *Protocol Adapters* to retrieve credentials used to authenticate *Devices* connecting to the adapter. In particular, the API supports to look up *shared secrets* which are often used by IoT devices by means of *username/password* based authentication schemes.
<!--more-->

Credentials are of a certain *type* which indicates which authentication mechanism the credentials can be used with. Each set of credentials also contains an *authentication identity* which is the identity claimed by the device during authentication. This authentication identity is usually different from the *device-id* the device has been registered under. A device may have multiple sets of credentials, using arbitrary *authentication identities*.

The Credentials API is defined by means of AMQP 1.0 message exchanges, i.e. a client needs to connect to Hono using an AMQP 1.0 client in order to invoke operations of the API as described in the following sections.

<a name="preconditions"></a>
## Preconditions for invoking the Credentials API

1. Client has established an AMQP connection with the Credentials service.
2. Client has established an AMQP link in role *sender* on the connection using target address `credentials/${tenant_id}`. This link is used by the client to send commands to the Credentials service.
3. Client has established an AMQP link in role *receiver* on the connection using source address `credentials/${tenant_id}/${reply-to}` where *reply-to* may be any arbitrary string chosen by the client. This link is used by the client to receive responses to the requests it has sent to the Credentials service. This link's source address is also referred to as the *reply-to* address for the request messages.

{{< figure src="preconditions.svg" alt="A client establishes an AMQP connection and the links required to invoke operations of the Credentials service" title="Client connecting to Credentials service" >}}

## Get Credentials

Protocol adapters use this command to *look up* credentials of a particular type for a device identity.

**Message Flow**

{{< figure src="get-credentials-success.svg" title="Client looking up credentials for a device" alt="A client sends a request message for looking up device credentials and receives a response containing the credentials" >}}


**Request Message Format**

The following table provides an overview of the properties a client needs to set on an *get credentials* message.

| Name             | Mandatory | Location                 | AMQP Type    | Description                   |
| :--------------- | :-------: | :----------------------- | :----------- | :---------------------------- |
| *correlation-id* | no        | *properties*             | *message-id* | MAY contain an ID used to correlate a response message to the original request. If set, it is used as the *correlation-id* property in the response, otherwise the value of the *message-id* property is used. Either this or the *message-id* property MUST be set. |
| *message-id*     | no        | *properties*             | *string*     | MAY contain an identifier that uniquely identifies the message at the sender side. Either this or the *correlation-id* property MUST be set. |
| *reply-to*       | yes       | *properties*             | *string*     | MUST contain the source address that the client wants to receive response messages from. This address MUST be the same as the source address used for establishing the client's receive link (see [Preconditions]({{< relref "#preconditions-for-invoking-the-credentials-api" >}})). |
| *subject*        | yes       | *properties*             | *string*     | MUST contain the value `get`. |

The body of the request MUST consist of a single *Data* section containing a UTF-8 encoded string representation of a single JSON object having the following members:

| Name             | Mandatory | JSON Type  | Description |
| :--------------- | :-------: | :--------- | :---------- |
| *type*           | *yes*     | *string*   | The type of credentials to look up. Potential values include (but are not limited to) `psk`, `x509-cert`, `hashed-password` etc. |
| *auth-id*        | *yes*     | *string*   | The authentication identifier to look up credentials for. |
| *client-certificate* | *no*  | *string*   | The client certificate the device authenticated with. If present, it MUST be the DER encoding of the (validated) X.509 client certificate as a Base64 encoded byte array and its subject DN MUST match the *auth-id*. |

The *client-certificate* property MAY be used by the service implementation for auto-provisioning of devices. 
To do so, the device registry needs to create credentials (and registration data) for the device if they do not already exist. 

Additionally, the body MAY contain arbitrary properties that service implementations can use to determine a device's identity.

The following request payload may be used to look up the hashed password for a device with the authentication identifier `sensor1`:

~~~json
{
  "type": "hashed-password",
  "auth-id": "sensor1"
}
~~~

The following request payload may be used to look up *or create* `x509-cert` credentials for a device with the authentication identifier `CN=device-1,O=ACME Corporation`:

~~~json
{
  "type": "x509-cert",
  "auth-id": "CN=device-1,O=ACME Corporation",
  "client-certificate": "DeviceCert=="
}
~~~

**Response Message Format**

A response to a *get credentials* request contains the following properties:

| Name             | Mandatory | Location                 | AMQP Type    | Description |
| :--------------- | :-------: | :----------------------- | :----------- | :---------- |
| *correlation-id* | yes       | *properties*             | *message-id* | Contains the *message-id* (or the *correlation-id*, if specified) of the request message that this message is the response to. |
| *content-type*   | no        | *properties*             | *string*     | MUST be set to `application/json` if the invocation of the operation was successful and the body of the response message contains payload as described below. |
| *status*         | yes       | *application-properties* | *int*        | Contains the status code indicating the outcome of the operation. Concrete values and their semantics are defined below. |
| *cache_control*  | no        | *application-properties* | *string*     | Contains an [RFC 2616](https://tools.ietf.org/html/rfc2616#section-14.9) compliant <em>cache directive</em>. The directive contained in the property MUST be obeyed by clients that are caching responses. |


The response message payload MUST contain credential information as defined in [Credentials Format]({{< relref "#credentials-format" >}}) if the *status* is `200` or `201`.

The response message's *status* property may contain the following codes:

| Code  | Description |
| :---- | :---------- |
| *200* | OK, the payload contains the credentials for the authentication identifier. |
| *201* | Created, the payload contains the newly created credentials for the authentication identifier. |
| *400* | Bad Request, the request message did not contain all mandatory properties or the subject DN of the certificate does not match the authentication identifier. |
| *404* | Not Found, there are no credentials registered matching the criteria. |

For status codes indicating an error (codes in the `400 - 499` range) the message body MAY contain a detailed description of the error that occurred.
In this case, the response message's *content-type* property SHOULD be set accordingly.

## Delivery States

The Credentials service uses the following AMQP message delivery states when receiving request messages from clients:

| Delivery State | Description |
| :------------- | :---------- |
| *ACCEPTED*     | Indicates that the request message has been received and accepted for processing. |
| *REJECTED*     | Indicates that the request message has been received but cannot be processed. The disposition frame's *error* field contains information regarding the reason why. Clients should not try to re-send the request using the same message properties in this case. |

## Credentials Format

Credential data is carried in the body of an AMQP message as part of a single *Data* section. The message's *content-type* property must be set to `application/json`.

The credential data is contained in the Data section as a UTF-8 encoded string representation of a single JSON object. It is an error to include payload that is not of this type.

The table below provides an overview of the standard members defined for the JSON object:

| Name             | Mandatory | JSON Type  | Default Value | Description |
| :--------------- | :-------: | :--------- | :------------ | :---------- |
| *device-id*      | *yes*     | *string*   |               | The ID of the device to which the credentials belong. |
| *type*           | *yes*     | *string*   |               | The credential type name. The value may be arbitrarily chosen by clients but SHOULD reflect the particular type of authentication mechanism the credentials are to be used with. Possible values include (but are not limited to) `psk`, `x509-cert`, `hashed-password` etc. |
| *auth-id*        | *yes*     | *string*   |               | The identity that the device should be authenticated as. |
| *enabled*        | *no*      | *boolean*  | *true*        | If set to *false* the credentials are not supposed to be used to authenticate devices any longer. This may e.g. be used to disable a particular mechanism for authenticating the device. **NB** It is the responsibility of the protocol adapter to make use of this information. |
| *secrets*        | *yes*     | *array*    |               | A list of secrets scoped to a particular time period. See [Secrets Format]({{< relref "#secrets-format" >}}) for details. **NB** This array must contain at least one element - an empty array is considered an error. |

For each set of credentials the combination of *auth-id* and *type* MUST be unique within a tenant.

The device registry may choose to not return information which is not suitable for authentication a device. This includes for example the `enabled` property. If set to `false`, then the device registry may choose to treat this request as if no credentials would be found. For secrets for example, this could mean that the device registry does not return secrets which are not valid at the current point in time.

{{% notice info %}}
Care needs to be taken that the value for the *authentication identifier* is compliant with the authentication mechanism(s) it is supposed to be used with. For example, when using standard HTTP Basic authentication, the *username* part of the Basic Authorization header value (which corresponds to the *auth-id*) MUST not contain any *colon* (`:`) characters, because the colon character is used as the separator between username and password. Similar constraints may exist for other authentication mechanisms, so the *authentication identifier* needs to be chosen with the anticipated mechanism(s) being used in mind. Otherwise, devices may fail to authenticate with protocol adapters, even if the credentials provided by the device match the credentials registered for the device. In general, using only characters from the `[a-zA-Z0-9_-]` range for the authentication identifier should be compatible with most mechanisms.
{{% /notice %}}

### Secrets Format

Each set of credentials may contain arbitrary *secrets* scoped to a particular *validity period* during which the secrets may be used for authenticating a device. The validity periods MAY overlap in order to support the process of changing a secret on a device that itself doesn't support the definition of multiple secrets for gapless authentication across adjacent validity periods.

The table below contains the properties used to define the validity period of a single secret:

| Name             | Mandatory | JSON Type  | Default Value | Description |
| :--------------- | :-------: | :--------- | :------------ | :---------- |
| *not-before*     | *no*      | *string*   | `null`        | The point in time from which on the secret may be used to authenticate devices. If not *null*, the value MUST be an [ISO 8601 compliant *combined date and time representation in extended format*](https://en.wikipedia.org/wiki/ISO_8601#Combined_date_and_time_representations). **NB** It is up to the discretion of the protocol adapter to make use of this information. |
| *not-after*      | *no*      | *string*   | `null`        | The point in time until which the secret may be used to authenticate devices. If not *null*, the value MUST be an [ISO 8601 compliant *combined date and time representation in extended format*](https://en.wikipedia.org/wiki/ISO_8601#Combined_date_and_time_representations). **NB** It is up to the discretion of the protocol adapter to make use of this information. |

### Examples

Below is an example for a payload containing [a hashed password]({{< relref "#hashed-password" >}}) for device `4711` with auth-id `sensor1` using SHA512 as the hashing function with a 4 byte salt (Base64 encoding of `0x32AEF017`). Note that the payload does not contain a `not-before` property, thus it may be used immediately up until X-mas eve 2017.

~~~json
{
  "device-id": "4711",
  "type": "hashed-password",
  "auth-id": "sensor1",
  "enabled": true,
  "secrets": [{
    "not-after": "2017-12-24T19:00:00+0100",
    "pwd-hash": "AQIDBAUGBwg=",
    "salt": "Mq7wFw==",
    "hash-function": "sha-512"
  }]
}
~~~

The next example contains two [pre-shared keys]({{< relref "#pre-shared-key" >}}) with overlapping validity periods for device `myDevice` with PSK identity `little-sensor2`.

~~~json
{
  "device-id": "myDevice",
  "type": "psk",
  "auth-id": "little-sensor2",
  "enabled": true,
  "secrets": [{
    "not-after": "2017-07-01T00:00:00+0100",
    "key": "cGFzc3dvcmRfb2xk"
  },{
    "not-before": "2017-06-29T00:00:00+0100",
    "key": "cGFzc3dvcmRfbmV3"
  }]
}
~~~

## Credential Verification

Protocol Adapters are responsible for authenticating devices when they connect. The Credentials API provides the [Get Credentials]({{< relref "#get-credentials" >}}) operation to support Protocol Adapters in doing so as illustrated below:

The following sequence diagram illustrates the flow of messages involved in a *Protocol Adapter* authenticating a device.
This is shown for the *MQTT Protocol Adapter* as example how a device authenticates with a username and a hashed-password.
The mechanism can be transferred to other protocols in a similar manner.

{{< figure src="mqtt_adapter_device_authentication.svg" title="MQTT Adapter authenticates device using the Credentials service" alt="The MQTT Adapter sends a request message for looking up credentials presented by a device and receives a response containing the credentials for verification" >}}

Protocol adapters MUST comply with the following rules when verifying credentials presented by a device:

* Credentials that have their *enabled* property set to `false` MUST NOT be used for authentication.
* Adapters MUST only consider secrets for authentication which

  * have their *not-before* property set to either `null` or the current or a past point in time **and**
  * have their *not-after* property set to either `null` or the current or a future point in time.

## Standard Credential Types

The following sections define some standard credential types and their properties. Applications are encouraged to make use of these types. However, the types are not enforced anywhere in Hono and clients may of course add application specific properties to the credential types.

### Common Properties

All credential types used with Hono MUST contain `device-id`, `type`, `auth-id`, `enabled` and `secrets` properties as defined in [Credentials Format]({{< relref "#credentials-format" >}}).

### Hashed Password

A credential type for storing a (hashed) password for a device.

Example:

~~~json
{
  "device-id": "4711",
  "type": "hashed-password",
  "auth-id": "sensor1",
  "secrets": [{
    "pwd-hash": "AQIDBAUGBwg=",
    "salt": "Mq7wFw==",
    "hash-function": "sha-512"
  }]
}
~~~

| Name             | Mandatory | JSON Type  | Default   | Description |
| :--------------- | :-------: | :--------- | :-------- | :---------- |
| *type*           | *yes*     | *string*   |           | The credential type name, always `hashed-password`. |
| *auth-id*        | *yes*     | *string*   |           | The identity that the device should be authenticated as. |
| *pwd-hash*       | *yes*     | *string*   |           | The password hash (see table below for details). |
| *salt*           | *no*      | *string*   |           | The Base64 encoding of the *salt* used in the password hash (see table below for details). |
| <nobr>*hash-function*<nobr>  | *no*      | *string*   | `sha-256` | The name of the hash function used to create the password hash. The hash functions supported by Hono are described in the table below. |

{{% notice tip %}}
It is strongly recommended to use salted password hashes only. Furthermore, the salt should be unique per user and password, so no lookup table or rainbow table attacks can be used to crack the salt-hashed password.
Whenever a password is updated for a user, the salt should change as well.
{{% /notice %}}

{{% notice note %}}
The example above does not contain any of the `not-before`, `not-after` and `enabled` properties, thus the credentials can be used at any time according to the rules defined in [Credential Verification]({{< relref "#credential-verification" >}}).
{{% /notice %}}

The table below describes the hash functions supported by Hono and how they map to the *secret* structure.

| Name         | Salt Usage   | Salt Location    | Password Hash Format         |
| :----------- | :----------: | :--------------- | :--------------------------- |
| *sha-256*    | optional     | *salt* field     | The Base64 encoding of the bytes resulting from applying the *sha-256* hash function to the byte array consisting of the salt bytes (if a salt is used) and the UTF-8 encoding of the clear text password. |
| *sha-512*    | optional     | *salt* field     | The Base64 encoding of the bytes resulting from applying the *sha-512* hash function to the byte array consisting of the salt bytes (if a salt is used) and the UTF-8 encoding of the clear text password. |
| *bcrypt*     | mandatory    | *pwd-hash* value | The output of applying the *Bcrypt* hash function to the clear text password. The salt is contained in the password hash.<br>**NB** Hono (currently) uses [Spring Security](https://docs.spring.io/spring-security/site/docs/4.2.7.RELEASE/reference/htmlsingle/#core-services-password-encoding) for matching clear text passwords against Bcrypt hashes. However, this library only supports hashes containing the `$2a$` prefix (see https://github.com/fpirsch/twin-bcrypt#about-prefixes) so Hono will fail to verify any passwords for which the corresponding Bcrypt hashes returned by the Credentials service contain e.g. the `$2y$` prefix. ||

### Pre-Shared Key

A credential type for storing a *Pre-shared Key* as used in (D)TLS handshakes.

Example:

~~~json
{
  "device-id": "4711",
  "type": "psk",
  "auth-id": "little-sensor2",
  "secrets": [{
    "key": "AQIDBAUGBwg="
  }]
}
~~~

| Name             | Mandatory | JSON Type  | Description |
| :--------------- | :-------: | :--------- | :---------- |
| *type*           | *yes*     | *string*   | The credential type name, always `psk`. |
| *auth-id*        | *yes*     | *string*   | The PSK identity. |
| *key*            | *yes*     | *string*   | The Base64 encoded bytes representing the shared (secret) key. |

{{% notice note %}}
The example above does not contain any of the `not-before`, `not-after` and `enabled` properties, thus the credentials can be used at any time according to the rules defined in [Credential Verification]({{< relref "#credential-verification" >}}).
{{% /notice %}}


### X.509 Certificate

A credential type for storing the [RFC 2253](https://www.ietf.org/rfc/rfc2253.txt) formatted *subject DN* of a client certificate that is used to authenticate the device as part of a (D)TLS handshake.

Example:

~~~json
{
  "device-id": "4711",
  "type": "x509-cert",
  "auth-id": "CN=device-1,O=ACME Corporation",
  "secrets": [{}]
}
~~~

| Name             | Mandatory | JSON Type  | Description |
| :--------------- | :-------: | :--------- | :---------- |
| *type*           | *yes*     | *string*   | The credential type name, always `x509-cert`. |
| *auth-id*        | *yes*     | *string*   | The subject DN of the client certificate in the format defined by [RFC 2253](https://www.ietf.org/rfc/rfc2253.txt). |

{{% notice note %}}
The example above does not contain any of the `not-before`, `not-after` and `enabled` properties. The `not-before`
and `not-after` properties should be omitted if the validity period is the same as the period indicated by the client
certificate's corresponding properties. It is still necessary to provide a (empty) JSON object in the *secrets* array,
though.
{{% /notice %}}

### Raw Public Key

A credentials type for storing a public key as used in the [JSON Web Token based Authentication]({{< relref "concepts/device-identity.md#json-web-token-based-authentication" >}}) to authenticate a device.

Example with public key:

~~~json
{
  "device-id": "4711",
  "type": "rpk",
  "auth-id": "sensor1",
  "secrets": [
    {
      "key": "MIIBIjANBgkqhki...yn7qGrzgQIDAQAB"
    }
  ]
}
~~~

Example with X.509 certificate:

~~~json
{
  "device-id": "4711",
  "type": "rpk",
  "auth-id": "sensor1",
  "secrets": [
    {
      "cert": "MIIDezCCAmOgAwI...GCfMrYD6dnpbg=="
    }
  ]
}
~~~

| Name            | Mandatory | JSON Type | Description                                                                                                                                                                                               |
|:----------------|:---------:|:----------|:----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| *type*          |   *yes*   | *string*  | The credential type name, always `rpk`.                                                                                                                                                                   |
| *auth-id*       |   *yes*   | *string*  | The identity that the device should be authenticated as.                                                                                                                                                  |
| *key* or *cert* |   *yes*   | *string*  | The Base64 encoded binary DER encoding of the public key (*key*) or X.509 certificate (*cert*). In case a certificate is provided the public key will be extracted from it and the certificate discarded. |

{{% notice note %}}
The example above does not contain any of the `not-before`, `not-after` and `enabled` properties, thus the credentials can be used at any time according to the rules defined in [Credential Verification]({{< relref "#credential-verification" >}}).
{{% /notice %}}