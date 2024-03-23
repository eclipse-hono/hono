+++
title = "Device Identity"
weight = 182
+++

This page describes how devices are represented and identified throughout Hono and its APIs.
<!--more-->

The main purpose of Hono is to provide a uniform API for applications to interact with devices,
regardless of the particular communication protocol the devices natively use.
In order to do so, Hono uses a unique *logical* identifier to refer to each device individually.

Hono does not make any assumptions about the format of a device identifier (or *device-id* for short).
It basically is a string which is defined at the time a device is being
[provisioned]({{< relref "/concepts/device-provisioning" >}}). Once created, the device can be referred
to by this identifier when using Hono's APIs until the device is being removed from the system.

## Tenant

Hono supports the logical partitioning of devices into groups called [*tenants*]({{< relref "/concepts/tenancy" >}}).
Each tenant has a unique identifier, a string called the *tenant-id*, and can be used to provide a logical grouping of
devices belonging e.g. to the same application scope or organizational unit. Each device can thus be uniquely
identified by the tuple (*tenant-id*, *device-id*). This tuple is broadly used throughout Hono's APIs when addressing
a particular device.

## Device Registration

Hono components use the [Device Registration API]({{< relref "/api/device-registration" >}}) to access device
registration information. The API defines the *assert Registration* operation for verifying a device's registration
status.

In many real world scenarios there will already be a component in place which keeps track of devices and which supports
the particular *provisioning process* being used to bring devices into life. In such cases it makes sense to simply
implement the Device Registration API as a *facade* on top of the existing component.

In addition to that, Hono defines a [Device Registry Management API]({{< relref "/api/management" >}}), which can be
implemented to take advantage of standardized operations for managing devices and credentials. This API is optional
because Hono components do not require it during runtime.

Hono comes with a [MongoDB]({{< relref "/admin-guide/mongodb-device-registry-config.md" >}}) and a
[JDBC]({{< relref "/admin-guide/jdbc-device-registry-config.md" >}}) based implementation of both APIs.
The JDBC based implementation supports PostgreSQL and H2 out of the box. It can also be configured with an H2 based
in-memory database which can be used for demonstration or testing purposes.

## Device Authentication

Devices connect to protocol adapters in order to publish telemetry data or events. Downstream applications consuming
this data often take particular actions based on the content of the messages. Such actions may include simply
updating some statistics, e.g. tracking the average room temperature, but may also trigger more serious activities
like shutting down a power plant. It is therefore important that applications can rely on the fact that the messages
they process have in fact been produced by the device indicated by a message's source address.

Hono relies on protocol adapters to establish a device's identity before it is allowed to publish downstream data
or receive commands. Conceptually, Hono distinguishes between two identities

1. an identity associated with the authentication credentials (termed the *authentication identity* or *auth-id*), and
1. an identity to act as (the *device identity* or *device-id*).

A device therefore presents an *auth-id* as part of its credentials during the authentication process which is then
resolved to a *device identity* by the protocol adapter on successful verification of the credentials.

In order to support the protocol adapters in the process of verifying credentials presented by a device, the
[Credentials API]({{< relref "/api/credentials" >}}) provides means to look up *secrets* on record for the device and
use this information to verify the credentials.

The Credentials API supports registration of multiple sets of credentials for each device. A set of credentials
consists of an *auth-id* and some sort of *secret* information. The particular *type* of secret determines the kind of
information kept. Please refer to the [Standard Credential Types]({{< relref "/api/credentials#standard-credential-types" >}})
defined in the Credentials API for details. Based on this approach, a device may be authenticated using different
types of secrets, e.g. a *hashed password* or a *client certificate*, depending on the capabilities of the device and/or
protocol adapter.

Once the protocol adapter has resolved the *device-id* for a device, it uses this identity when referring to the
device in all subsequent API invocations, e.g. when forwarding telemetry messages downstream.

Every device connecting to Hono needs to be registered in the scope of a single tenant as described above already.
The Device Registration and Credentials APIs therefore require a tenant identifier to be passed in to their operations.
Consequently, the first step a protocol adapter needs to take when authenticating a device is determining the tenant
that the device belongs to.

The means used by a device to indicate the tenant that it belongs to vary according to the type of credentials
and authentication mechanism being used.

### Username/Password based Authentication

The MQTT, HTTP and AMQP protocol adapters support authentication of devices with a username/password based
mechanism. In this case, a protocol adapter verifies that the password presented by the device during connection
establishment matches the password that is on record for the device in the device registry.

During connection establishment the device presents a username and a password to the protocol adapter.
For example, a device that belongs to tenant `example-tenant` and for which
[*hashed-password* credentials]({{< relref "/api/credentials#hashed-password" >}}) with an *auth-id* of `device-1`
have been registered, would present a username of `device-1@example-tenant` when authenticating to a protocol adapter.

The protocol adapter then extracts the tenant identifier from the username and invokes the Credentials API's
[*get Credentials*]({{< relref "/api/credentials#get-credentials" >}}) operation in order to retrieve the
*hashed-password* type credentials that are on record for the device.

### Pre-Shared Key based Authentication

The CoAP protocol adapter supports authentication of devices using a *pre-shared key* (PSK) as part of a DTLS handshake.
In this case, the protocol adapter verifies that the PSK used by the device to generate the DTLS session's pre-master
secret is the same as the key contained in the credentials that are on record for the device in the device registry.

During the DTLS handshake the device provides a *psk_identity* to the protocol adapter. For example, a device that
belongs to tenant `example-tenant` and for which [*psk* credentials]({{< relref "/api/credentials#pre-shared-key" >}})
with an *auth-id* of `device-1` have been registered, would present a PSK identity of `device-1@example-tenant` during
the DTLS handshake.

The protocol adapter then extracts the tenant identifier from the PSK identity and invokes the Credentials API's
[*get Credentials*]({{< relref "/api/credentials#get-credentials" >}}) operation in order to retrieve the *psk*
type credentials that are on record for the device. The *psk* contained in the credentials are then used by the
protocol adapter to generate the pre-master secret of the DTLS session being negotiated with the client. If both,
the device and the protocol adapter use the same key for doing so, the DTLS handshake will succeed and the device
has been authenticated successfully.

### Client Certificate based Authentication

Devices can also use an X.509 (client) certificate to authenticate to protocol adapters. In this case, the protocol
adapter tries to verify that a valid chain of certificates can be established starting with the client certificate
presented by the device up to one of the trusted root certificates configured for the device's tenant.

During connection establishment with the device, the protocol adapter tries to determine the tenant to which the
device belongs and retrieve the tenant's configuration information using the Tenant API. The adapter then uses the
trust anchors that have been configured for the tenant to verify the client certificate.

The protocol adapter tries to look up the device's tenant configuration using the Tenant API's
[*get Tenant Information*]({{< relref "/api/tenant#get-tenant-information" >}}) operation.
The adapter first invokes the operation using the *issuer DN* from the device's client certificate.
If that fails and if the device has included the
[Server Name Indication](https://datatracker.ietf.org/doc/html/rfc6066#section-3) extension during the TLS handshake,
the adapter extracts the first label of the first host name conveyed in the SNI extension and invokes the *get Tenant
Information* operation with the extracted value as the tenant identifier. For example, a host name of
`my-tenant.hono.eclipseprojects.io` would result in a tenant identifier of `my-tenant` being used in the look-up.

{{% notice info %}}
Labels in host names may only consist of letters, digits and hyphens. In order to be able to refer to tenants which
have an identifier that consists of other characters as well, the Device Registry Management API supports registering
an *alias* for a tenant which can be used as an alternate identifier when looking up tenant configuration information.

Based on that, a tenant with identifier `unsupported_id` that has been registered using *alias* `my-tenant`,
can be referred to by a device by means of including a host name like `my-tenant.hono.eclipseprojects.io` in the SNI
extension.
{{% /notice %}}

After having verified the client certificate using the trust anchor(s), the protocol adapter extracts
the client certificate's *subject DN* and invokes the Credentials API's
[*get Credentials*]({{< relref "/api/credentials#get-credentials" >}}) operation in order to retrieve the
[*x509-cert* type credentials]({{< relref "/api/credentials#x509-certificate" >}}) that are on record for the device.

#### Certificate Revocation Checking (experimental)

Hono supports [OCSP](https://www.ietf.org/rfc/rfc2560.txt) based certificate revocation checking for client certificates
presented by devices using client certificate based authentication. If this feature is enabled, the protocol adapter
tries to verify the client certificate's revocation status. The device with revoked or unknown certificate status is not
allowed to connect to the protocol adapter. The adapter uses the OCSP responder URL that has been configured for the
tenant or *Authority Information Access* (AIA) extension in the client certificate to retrieve the OCSP response. The
response is then used to verify the client certificate's revocation status. Revocation check can be configured using the 
[Tenant](/hono/docs/api/management/#/tenants) resource of Device Registry Management API.

OCSP revocation check is supported for all protocol adapters.

{{% notice info %}}
This feature is experimental and may be subject to change in future releases without further notice. Current
implementation checks the revocation of end entity certificates only (with CA:FALSE in basic constraints extension). If
OCSP revocation check is enabled and revocation status cannot be determined, the protocol adapter will reject the
connection. Hono currently does not cache OCSP responses so frequent connections may cause high load on OCSP responders.
{{% /notice %}}

### JSON Web Token based Authentication

The HTTP and MQTT protocol adapters support authentication of devices with a signed
[JSON Web Token](https://www.rfc-editor.org/rfc/rfc7519) (JWT) based mechanism.
In this case, the protocol adapter tries to validate the token presented by the device using a public key on record.

During connection establishment the device is expected to present the tenant identifier, authentication identifier and
a valid and signed JWT to the protocol adapter. The information about the tenant and the authentication identifier can
be presented to the protocol adapter in one of two ways:

1. Either as claims inside the [JSON Web Signature](https://www.rfc-editor.org/rfc/rfc7515) (JWS) payload, in which case
   the *tenant-id* and *auth-id* must be provided in the `tid` and `sub` (*subject*) claims respectively,
   and the `aud` (*audience*) claim must contain `hono-adapter`
2. or via an adapter specific mechanism. For more information see
   [HTTP]({{< relref "/user-guide/http-adapter#http-bearer-auth" >}}) or
   [MQTT]({{< relref "/user-guide/mqtt-adapter#json-web-token" >}})

The JWT's JOSE header MUST contain the *typ* parameter with value `JWT` and MUST contain the *alg* parameter indicating
the algorithm that has been used to create the JWS signature.
`RS256`, `PS256`, `ES256` and their respective stronger variants are supported. The algorithm specified in the header
must be compatible with at least one of the
[*Raw Public Key* type credentials]({{< relref "/api/credentials#raw-public-key" >}}) registered for the device.

The JWS payload MUST contain the claims *iat* (issued at) and *exp* (expiration time) with values provided
in [Unix time](https://en.wikipedia.org/wiki/Unix_time). The *iat* claim marks the instant at which the token has
been created and also marks the start of its validity period. It must not be too far in the past or the future
(allowing 10 minutes for skew). The *nbf* (not before) claim is therefore not required and will be ignored.
The *exp* claim marks the instant after which the token MUST be considered invalid. The lifetime of the token must
be at most 24 hours plus skew.

The protocol adapter then invokes the Credentials API's [*get Credentials*]({{< relref "/api/credentials#get-credentials" >}})
operation in order to retrieve the *rpk* (raw public key) type credentials that are on record for the device.
The key contained in the credentials is then used by the protocol adapter to verify the JWS signature.
