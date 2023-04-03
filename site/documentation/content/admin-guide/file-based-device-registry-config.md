+++
title = "File Based Device Registry Configuration"
hidden = true # does not show up in the menu, but shows up in the version selector and can be accessed from links
weight = 0
+++

The File based Device Registry component provides an exemplary implementation of Eclipse Hono&trade;'s
[Device Registration]({{< relref "/api/device-registration" >}}), [Credentials]({{< relref "/api/credentials" >}}),
[Tenant]({{< relref "/api/tenant" >}}) and [Device Connection]({{< relref "/api/device-connection" >}}) APIs.

Protocol adapters use these APIs to determine a device's registration status, e.g. if it is enabled and if it is
registered with a particular tenant, and to authenticate a device before accepting any data for processing from it.

<!--more-->

There is no particular technical reason to implement these three APIs in one component, so for production scenarios
there might be up to three different components each implementing one of the APIs.

The Device Registry component also exposes [HTTP based resources]({{< relref "/api/management" >}}) for managing tenants
and the registration information and credentials of devices.

The Device Registry is implemented as a Spring Boot application. It can be run either directly from the command line or
by means of starting the corresponding [Docker image](https://hub.docker.com/r/eclipse/hono-service-device-registry-file/)
created from it.

{{% notice info %}}
The file based device registry has been removed in Hono 2.0.0.
Please use the [Mongo DB]({{< relref "mongodb-device-registry-config" >}}) or
[JDBC based registry]({{< relref "jdbc-device-registry-config" >}}) implementations instead. The JDBC based registry
can be configured to use an H2 database in either *embedded* or *in-memory* mode. The former can be used to persist
data to the local file system while the latter keeps all data in memory only.
Please refer to the [H2 documentation](http://www.h2database.com/html/features.html#database_url) for details.
{{% /notice %}}

## Service Configuration

The following table provides an overview of the configuration variables and corresponding system properties for
configuring the Device Registry.

| OS Environment Variable<br>Java System Property | Mandatory | Default | Description                                  |
| :---------------------------------------------- | :-------: | :------ | :------------------------------------------- |
| `HONO_APP_TYPE`<br>`hono.app.type` | no | `file` | The device registry implementation to use. This may be either `file` or `dummy`. In the case of `dummy` a dummy implementation will be used which will consider all devices queried for as valid devices, having the access credentials `hono-secret`. Of course this shouldn't be used for productive use. |
| `HONO_CREDENTIALS_SVC_CACHEMAXAGE`<br>`hono.credentials.svc.cacheMaxAge` | no | `180` | The maximum period of time (seconds) that information returned by the service's operations may be cached for. |
| `HONO_CREDENTIALS_SVC_FILENAME`<br>`hono.credentials.svc.filename` | no | `/var/lib/hono/device-registry/`<br>`credentials.json` | The path to the file where the server stores credentials of devices. Hono tries to read credentials from this file during start-up and writes out all identities to this file periodically if property `HONO_CREDENTIALS_SVC_SAVETOFILE` is set to `true`.<br>Please refer to [Credentials File Format]({{< relref "#credentials-file-format" >}}) for details regarding the file's format. |
| `HONO_CREDENTIALS_SVC_HASHALGORITHMSWHITELIST`<br>`hono.credentials.svc.hashAlgorithmsWhitelist` | no | `empty` | An array of supported hashing algorithms to be used with the `hashed-password` type of credentials. When not set, all values will be accepted. |
| `HONO_CREDENTIALS_SVC_MAXBCRYPTCOSTFACTOR`<br>`hono.credentials.svc.maxBcryptCostFactor` | no | `10` | The maximum cost factor that is supported in password hashes using the BCrypt hash function. This limit is enforced by the device registry when adding or updating corresponding credentials. Increasing this number allows for potentially more secure password hashes to be used. However, the time required to compute the hash increases exponentially with the cost factor. |
| `HONO_CREDENTIALS_SVC_MAXBCRYPTITERATIONS`<br>`hono.credentials.svc.maxBcryptIterations` | no | `10` | DEPRECATED Please use `HONO_CREDENTIALS_SVC_MAXBCRYPTCOSTFACTOR` instead.<br>The maximum cost factor that is supported in password hashes using the BCrypt hash function. This limit is enforced by the device registry when adding or updating corresponding credentials. Increasing this number allows for potentially more secure password hashes to be used. However, the time required to compute the hash increases exponentially with the cost factor. |
| `HONO_CREDENTIALS_SVC_MODIFICATIONENABLED`<br>`hono.credentials.svc.modificationEnabled` | no | `true` | When set to `false` the credentials contained in the registry cannot be updated nor removed. |
| `HONO_CREDENTIALS_SVC_RECEIVERLINKCREDIT`<br>`hono.credentials.svc.receiverLinkCredit` | no | `100` | The number of credits to flow to a client connecting to the Credentials endpoint. |
| `HONO_CREDENTIALS_SVC_SAVETOFILE`<br>`hono.credentials.svc.saveToFile` | no | `false` | When set to `true` the server will periodically write out the registered credentials to the file specified by the `HONO_CREDENTIALS_SVC_FILENAME` property. |
| `HONO_CREDENTIALS_SVC_STARTEMPTY`<br>`hono.credentials.svc.startEmpty` | no | `false` | When set to `true` the server will not try to load credentials from the file specified by the `HONO_CREDENTIALS_SVC_FILENAME` property during startup. |
| `HONO_DEVICE_CONNECTION_SVC_MAXDEVICESPERTENANT`<br>`hono.deviceConnection.svc.maxDevicesPerTenant` | no | `100` | The number of devices per tenant for which connection related data is stored. It is an error to set this property to a value <= 0. |
| `HONO_REGISTRY_AMQP_BINDADDRESS`<br>`hono.registry.amqp.bindAddress` | no | `127.0.0.1` | The IP address of the network interface that the secure AMQP port should be bound to.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_REGISTRY_AMQP_CERTPATH`<br>`hono.registry.amqp.certPath` | no | - | The absolute path to the PEM file containing the certificate that the server should use for authenticating to clients. This option must be used in conjunction with `HONO_REGISTRY_AMQP_KEYPATH`.<br>Alternatively, the `HONO_REGISTRY_AMQP_KEYSTOREPATH` option can be used to configure a key store containing both the key as well as the certificate. |
| `HONO_REGISTRY_AMQP_INSECUREPORT`<br>`hono.registry.amqp.insecurePort` | no | - | The insecure port the server should listen on for AMQP 1.0 connections.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_REGISTRY_AMQP_INSECUREPORTBINDADDRESS`<br>`hono.registry.amqp.insecurePortBindAddress` | no | `127.0.0.1` | The IP address of the network interface that the insecure AMQP port should be bound to.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_REGISTRY_AMQP_INSECUREPORTENABLED`<br>`hono.registry.amqp.insecurePortEnabled` | no | `false` | If set to `true` the server will open an insecure port (not secured by TLS) using either the port number set via `HONO_REGISTRY_AMQP_INSECUREPORT` or the default AMQP port number (`5672`) if not set explicitly.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_REGISTRY_AMQP_KEYPATH`<br>`hono.registry.amqp.keyPath` | no | - | The absolute path to the (PKCS8) PEM file containing the private key that the server should use for authenticating to clients. This option must be used in conjunction with `HONO_REGISTRY_AMQP_CERTPATH`. Alternatively, the `HONO_REGISTRY_AMQP_KEYSTOREPATH` option can be used to configure a key store containing both the key as well as the certificate. |
| `HONO_REGISTRY_AMQP_KEYSTOREPASSWORD`<br>`hono.registry.amqp.keyStorePassword` | no | - | The password required to read the contents of the key store. |
| `HONO_REGISTRY_AMQP_KEYSTOREPATH`<br>`hono.registry.amqp.keyStorePath` | no | - | The absolute path to the Java key store containing the private key and certificate that the server should use for authenticating to clients. Either this option or the `HONO_REGISTRY_AMQP_KEYPATH` and `HONO_REGISTRY_AMQP_CERTPATH` options need to be set in order to enable TLS secured connections with clients. The key store format can be either `JKS` or `PKCS12` indicated by a `.jks` or `.p12` file suffix respectively. |
| `HONO_REGISTRY_AMQP_NATIVETLSREQUIRED`<br>`hono.registry.amqp.nativeTlsRequired` | no | `false` | The server will probe for OpenSSL on startup if a secure port is configured. By default, the server will fall back to the JVM's default SSL engine if not available. However, if set to `true`, the server will fail to start at all in this case. |
| `HONO_REGISTRY_AMQP_PORT`<br>`hono.registry.amqp.port` | no | `5671` | The secure port that the server should listen on for AMQP 1.0 connections.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_REGISTRY_AMQP_SECUREPROTOCOLS`<br>`hono.registry.amqp.secureProtocols` | no | `TLSv1.3,TLSv1.2` | A (comma separated) list of secure protocols (in order of preference) that are supported when negotiating TLS sessions. Please refer to the [vert.x documentation](https://vertx.io/docs/vertx-core/java/#ssl) for a list of supported protocol names. |
| `HONO_REGISTRY_AMQP_SUPPORTEDCIPHERSUITES`<br>`hono.registry.amqp.supportedCipherSuites` | no | - | A (comma separated) list of names of cipher suites (in order of preference) that are supported when negotiating TLS sessions. Please refer to [JSSE Cipher Suite Names](https://docs.oracle.com/en/java/javase/17/docs/specs/security/standard-names.html#jsse-cipher-suite-names) for a list of supported names. |
| `HONO_REGISTRY_HTTP_TENANTIDPATTERN`<br>`hono.registry.http.tenantIdPattern` | no | `^[a-zA-Z0-9-_\.]+$` | The regular expression to use to validate tenant ID. Please refer to the [java pattern documentation](https://docs.oracle.com/javase/7/docs/api/java/util/regex/Pattern.html). |
| `HONO_REGISTRY_HTTP_DEVICEIDPATTERN`<br>`hono.registry.http.deviceIdPattern` | no | `^[a-zA-Z0-9-_\.:]+$` | The regular expression to use to validate device ID. Please refer to the [java pattern documentation](https://docs.oracle.com/javase/7/docs/api/java/util/regex/Pattern.html). |
| `HONO_REGISTRY_HTTP_BINDADDRESS`<br>`hono.registry.http.bindAddress` | no | `127.0.0.1` | The IP address of the network interface that the secure HTTP port should be bound to.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_REGISTRY_HTTP_CERTPATH`<br>`hono.registry.http.certPath` | no | - | The absolute path to the PEM file containing the certificate that the server should use for authenticating to clients. This option must be used in conjunction with `HONO_REGISTRY_HTTP_KEYPATH`.<br>Alternatively, the `HONO_REGISTRY_HTTP_KEYSTOREPATH` option can be used to configure a key store containing both the key as well as the certificate. |
| `HONO_REGISTRY_HTTP_INSECUREPORT`<br>`hono.registry.http.insecurePort` | no | - | The insecure port the server should listen on for HTTP requests.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_REGISTRY_HTTP_INSECUREPORTBINDADDRESS`<br>`hono.registry.http.insecurePortBindAddress` | no | `127.0.0.1` | The IP address of the network interface that the insecure HTTP port should be bound to.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_REGISTRY_HTTP_INSECUREPORTENABLED`<br>`hono.registry.http.insecurePortEnabled` | no | `false` | If set to `true` the server will open an insecure port (not secured by TLS) using either the port number set via `HONO_REGISTRY_HTTP_INSECUREPORT` or the default HTTP port number (`8080`) if not set explicitly.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_REGISTRY_HTTP_KEYPATH`<br>`hono.registry.http.keyPath` | no | - | The absolute path to the (PKCS8) PEM file containing the private key that the server should use for authenticating to clients. This option must be used in conjunction with `HONO_REGISTRY_HTTP_CERTPATH`. Alternatively, the `HONO_REGISTRY_HTTP_KEYSTOREPATH` option can be used to configure a key store containing both the key as well as the certificate. |
| `HONO_REGISTRY_HTTP_KEYSTOREPASSWORD`<br>`hono.registry.http.keyStorePassword` | no | - | The password required to read the contents of the key store. |
| `HONO_REGISTRY_HTTP_KEYSTOREPATH`<br>`hono.registry.http.keyStorePath` | no | - | The absolute path to the Java key store containing the private key and certificate that the server should use for authenticating to clients. Either this option or the `HONO_REGISTRY_HTTP_KEYPATH` and `HONO_REGISTRY_HTTP_CERTPATH` options need to be set in order to enable TLS secured connections with clients. The key store format can be either `JKS` or `PKCS12` indicated by a `.jks` or `.p12` file suffix respectively. |
| `HONO_REGISTRY_HTTP_MAXPAYLOADSIZE`<br>`hono.registry.http.maxPayloadSize` | no | `16000` | The maximum size of an HTTP request body in bytes that is accepted by the registry. |
| `HONO_REGISTRY_HTTP_PORT`<br>`hono.registry.http.port` | no | `8443` | The secure port that the server should listen on for HTTP requests.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_REGISTRY_HTTP_SECUREPROTOCOLS`<br>`hono.registry.http.secureProtocols` | no | `TLSv1.3,TLSv1.2` | A (comma separated) list of secure protocols (in order of preference) that are supported when negotiating TLS sessions. Please refer to the [vert.x documentation](https://vertx.io/docs/vertx-core/java/#ssl) for a list of supported protocol names. |
| `HONO_REGISTRY_HTTP_SUPPORTEDCIPHERSUITES`<br>`hono.registry.http.supportedCipherSuites` | no | - | A (comma separated) list of names of cipher suites (in order of preference) that are supported when negotiating TLS sessions. Please refer to [JSSE Cipher Suite Names](https://docs.oracle.com/en/java/javase/17/docs/specs/security/standard-names.html#jsse-cipher-suite-names) for a list of supported names. |
| `HONO_REGISTRY_REST_TENANTIDPATTERN`<br>`hono.registry.rest.tenantIdPattern` | no | `^[a-zA-Z0-9-_\.]+$` | The regular expression to use to validate tenant ID. Please refer to the [java pattern documentation](https://docs.oracle.com/javase/7/docs/api/java/util/regex/Pattern.html).<br>**Deprecated** Use `HONO_REGISTRY_HTTP_TENANTIDPATTERN` instead. |
| `HONO_REGISTRY_REST_DEVICEIDPATTERN`<br>`hono.registry.rest.deviceIdPattern` | no | `^[a-zA-Z0-9-_\.:]+$` | The regular expression to use to validate device ID. Please refer to the [java pattern documentation](https://docs.oracle.com/javase/7/docs/api/java/util/regex/Pattern.html).<br>**Deprecated** Use `HONO_REGISTRY_HTTP_DEVICEIDPATTERN` instead. |
| `HONO_REGISTRY_REST_BINDADDRESS`<br>`hono.registry.rest.bindAddress` | no | `127.0.0.1` | The IP address of the network interface that the secure HTTP port should be bound to.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details.<br>**Deprecated** Use `HONO_REGISTRY_HTTP_BINDADDRESS` instead. |
| `HONO_REGISTRY_REST_CERTPATH`<br>`hono.registry.rest.certPath` | no | - | The absolute path to the PEM file containing the certificate that the server should use for authenticating to clients. This option must be used in conjunction with `HONO_REGISTRY_REST_KEYPATH`.<br>Alternatively, the `HONO_REGISTRY_REST_KEYSTOREPATH` option can be used to configure a key store containing both the key as well as the certificate.<br>**Deprecated** Use `HONO_REGISTRY_HTTP_CERTPATH` instead. |
| `HONO_REGISTRY_REST_INSECUREPORT`<br>`hono.registry.rest.insecurePort` | no | - | The insecure port the server should listen on for HTTP requests.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details.<br>**Deprecated** Use `HONO_REGISTRY_HTTP_INSECUREPORT` instead. |
| `HONO_REGISTRY_REST_INSECUREPORTBINDADDRESS`<br>`hono.registry.rest.insecurePortBindAddress` | no | `127.0.0.1` | The IP address of the network interface that the insecure HTTP port should be bound to.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details.<br>**Deprecated** Use `HONO_REGISTRY_HTTP_INSECUREPORTBINDADDRESS` instead. |
| `HONO_REGISTRY_REST_INSECUREPORTENABLED`<br>`hono.registry.rest.insecurePortEnabled` | no | `false` | If set to `true` the server will open an insecure port (not secured by TLS) using either the port number set via `HONO_REGISTRY_REST_INSECUREPORT` or the default HTTP port number (`8080`) if not set explicitly.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details.<br>**Deprecated** Use `HONO_REGISTRY_HTTP_INSECUREPORTENABLED` instead. |
| `HONO_REGISTRY_REST_KEYPATH`<br>`hono.registry.rest.keyPath` | no | - | The absolute path to the (PKCS8) PEM file containing the private key that the server should use for authenticating to clients. This option must be used in conjunction with `HONO_REGISTRY_REST_CERTPATH`. Alternatively, the `HONO_REGISTRY_REST_KEYSTOREPATH` option can be used to configure a key store containing both the key as well as the certificate.<br>**Deprecated** Use `HONO_REGISTRY_HTTP_KEYPATH` instead. |
| `HONO_REGISTRY_REST_KEYSTOREPASSWORD`<br>`hono.registry.rest.keyStorePassword` | no | - | The password required to read the contents of the key store.<br>**Deprecated** Use `HONO_REGISTRY_HTTP_KEYSTOREPASSWORD` instead. |
| `HONO_REGISTRY_REST_KEYSTOREPATH`<br>`hono.registry.rest.keyStorePath` | no | - | The absolute path to the Java key store containing the private key and certificate that the server should use for authenticating to clients. Either this option or the `HONO_REGISTRY_REST_KEYPATH` and `HONO_REGISTRY_REST_CERTPATH` options need to be set in order to enable TLS secured connections with clients. The key store format can be either `JKS` or `PKCS12` indicated by a `.jks` or `.p12` file suffix respectively.<br>**Deprecated** Use `HONO_REGISTRY_HTTP_KEYSTOREPATH` instead. |
| `HONO_REGISTRY_REST_PORT`<br>`hono.registry.rest.port` | no | `8443` | The secure port that the server should listen on for HTTP requests.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details.<br>**Deprecated** Use `HONO_REGISTRY_HTTP_PORT` instead. |
| `HONO_REGISTRY_SVC_CACHEMAXAGE`<br>`hono.registry.svc.cacheMaxAge` | no | `180` | The maximum period of time (seconds) that information returned by the service's operations may be cached for. |
| `HONO_REGISTRY_SVC_FILENAME`<br>`hono.registry.svc.filename` | no | `/var/lib/hono/device-registry/`<br>`device-identities.json` | The path to the file where the server stores identities of registered devices. Hono tries to read device identities from this file during start-up and writes out all identities to this file periodically if property `HONO_REGISTRY_SVC_SAVETOFILE` is set to `true`.<br>Please refer to [Device Identities File Format]({{< relref "#device-identities-file-format" >}}) for details regarding the file's format. |
| `HONO_REGISTRY_SVC_MAXDEVICESPERTENANT`<br>`hono.registry.svc.maxDevicesPerTenant` | no | `100` | The number of devices that can be registered for each tenant. It is an error to set this property to a value <= 0. |
| `HONO_REGISTRY_SVC_MODIFICATIONENABLED`<br>`hono.registry.svc.modificationEnabled` | no | `true` | When set to `false` the device information contained in the registry cannot be updated nor removed from the registry. |
| `HONO_REGISTRY_SVC_RECEIVERLINKCREDIT`<br>`hono.registry.svc.receiverLinkCredit` | no | `100` | The number of credits to flow to a client connecting to the Device Registration endpoint. |
| `HONO_REGISTRY_SVC_SAVETOFILE`<br>`hono.registry.svc.saveToFile` | no | `false` | When set to `true` the server will periodically write out the registered device information to the file specified by the `HONO_REGISTRY_SVC_FILENAME` property. |
| `HONO_REGISTRY_SVC_SIGNING_KEYPATH`<br>`hono.registry.svc.signing.keyPath` | no  | - | The absolute path to the (PKCS8) PEM file containing the private key that the server should use for signing tokens asserting a device's registration status. When using this variable, other services that need to validate the tokens issued by this service need to be configured with the corresponding certificate/public key. Alternatively, a symmetric key can be used for signing (and validating) by setting the `HONO_REGISTRY_SVC_SIGNING_SHAREDSECRET` variable. If none of these variables is set, the server falls back to the key indicated by the `HONO_REGISTRY_AMP_KEYPATH` variable. If that variable is also not set, startup of the server fails. |
| `HONO_REGISTRY_SVC_SIGNING_SHAREDSECRET`<br>`hono.registry.svc.signing.sharedSecret` | no  | - | A string to derive a symmetric key from that is used for signing tokens asserting a device's registration status. The key is derived from the string by using the bytes of the String's UTF8 encoding. When setting the signing key using this variable, other services that need to validate the tokens issued by this service need to be configured with the same key. Alternatively, an asymmetric key pair can be used for signing (and validating) by setting the `HONO_REGISTRY_SVC_SIGNING_KEYPATH` variable. If none of these variables is set, startup of the server fails. |
| `HONO_REGISTRY_SVC_SIGNING_TOKENEXPIRATION`<br>`hono.registry.svc.signing.tokenExpiration` | no | `10` | The expiration period to use for the tokens asserting the registration status of devices. |
| `HONO_REGISTRY_SVC_STARTEMPTY`<br>`hono.registry.svc.startEmpty` | no | `false` | When set to `true` the server will not try to load device identities from the file specified by the `HONO_REGISTRY_SVC_FILENAME` property during startup. |
| `HONO_TENANT_SVC_CACHEMAXAGE`<br>`hono.tenant.svc.cacheMaxAge` | no | `180` | The maximum period of time (seconds) that information returned by the service's operations may be cached for. |
| `HONO_TENANT_SVC_FILENAME`<br>`hono.tenant.svc.filename` | no | `/var/lib/hono/device-registry/`<br>`tenants.json` | The path to the file where the server stores tenants. Hono tries to read tenants from this file during start-up and writes out all identities to this file periodically if property `HONO_TENANT_SVC_SAVETOFILE` is set to `true`.<br>Please refer to [Tenants File Format]({{< relref "#tenants-file-format" >}}) for details regarding the file's format. |
| `HONO_TENANT_SVC_MODIFICATIONENABLED`<br>`hono.tenant.svc.modificationEnabled` | no | `true` | When set to `false` the tenants contained in the registry cannot be updated nor removed. |
| `HONO_TENANT_SVC_RECEIVERLINKCREDIT`<br>`hono.tenant.svc.receiverLinkCredit` | no | `100` | The number of credits to flow to a client connecting to the Tenant endpoint. |
| `HONO_TENANT_SVC_SAVETOFILE`<br>`hono.tenant.svc.saveToFile` | no | `false` | When set to `true` the server will periodically write out the registered tenants to the file specified by the `HONO_TENANTS_SVC_TENANT_FILENAME` property. |
| `HONO_TENANT_SVC_STARTEMPTY`<br>`hono.tenant.svc.startEmpty` | no | `false` | When set to `true` the server will not try to load tenants from the file specified by the `HONO_TENANT_SVC_FILENAME` property during startup. |

The variables only need to be set if the default value does not match your environment.

In addition to the options described in the table above, this component supports the following standard configuration
options:

* [Common Java VM Options]({{< relref "common-config.md/#java-vm-options" >}})
* [Common vert.x Options]({{< relref "common-config.md/#vertx-options" >}})
* [Monitoring Options]({{< relref "monitoring-tracing-config.md" >}})

## Port Configuration

The file based Device Registry supports configuration of both an AMQP based endpoint exposing the Tenant, Device Registration and Credentials
APIs as well as an HTTP based endpoint providing resources for managing tenants, registration information and credentials as defined by the
[Registry Management API]({{< relref "api/management" >}}). Both endpoints can be configured to listen for connections on

* a secure port only (default) or
* an insecure port only or
* both a secure and an insecure port (dual port configuration)

The registry will fail to start if none of the ports is configured properly.

The following sections apply to configuring both the AMQP and the HTTP endpoint. The environment variables to use for configuring
the HTTP endpoint are the same as the ones for the AMQP endpoint, substituting `_AMQP_` with `_HTTP_`, e.g. `HONO_REGISTRY_HTTP_KEYPATH`
instead of `HONO_REGISTRY_AMQP_KEYPATH`.

### Secure Port Only

The server needs to be configured with a private key and certificate in order to open a TLS secured port.

There are two alternative ways for doing so:

1. Setting the `HONO_REGISTRY_AMQP_KEYSTOREPATH` and the `HONO_REGISTRY_AMQP_KEYSTOREPASSWORD` variables in order to load the
key & certificate from a password protected key store, or
1. setting the `HONO_REGISTRY_AMQP_KEYPATH` and `HONO_REGISTRY_AMQP_CERTPATH` variables in order to load the key and certificate
   from two separate PEM files in PKCS8 format.

When starting up, the server will bind a TLS secured socket to the default secure port (`5671` for AMQP and `8443` for HTTP).
The port number can also be set explicitly using the `HONO_REGISTRY_AMQP_PORT` variable.

The `HONO_REGISTRY_AMQP_BINDADDRESS` variable can be used to specify the network interface that the port should be exposed on.
By default, the port is bound to the *loopback device* only, i.e. the port will only be accessible from the local host. Setting this
variable to `0.0.0.0` will let the port being bound to **all** network interfaces (be careful not to expose the port unintentionally
to the outside world).

### Insecure Port Only

The secure port will mostly be required for production scenarios. However, it might be desirable to expose a non-TLS secured port instead,
e.g. for testing purposes. In any case, the non-secure port needs to be explicitly enabled either by

- explicitly setting `HONO_REGISTRY_AMQP_INSECUREPORT` to a valid port number, or by
- implicitly configuring the default port (`5672` for AMQP and `8080` for HTTP) to be used by setting `HONO_REGISTRY_AMQP_INSECUREPORTENABLED`
  to `true`.

The server issues a warning on the console if one of the insecure ports is set to the corresponding default secure port.

The `HONO_REGISTRY_AMQP_INSECUREPORTBINDADDRESS` variable can be used to specify the network interface that the port should be exposed on.
By default, the port is bound to the *loopback device* only, i.e. the port will only be accessible from the local host. This variable might be used
to e.g. expose the non-TLS secured port on a local interface only, thus providing easy access from within the local network, while still requiring
encrypted communication when accessed from the outside over public network infrastructure.

Setting this variable to `0.0.0.0` will let the port being bound to **all** network interfaces (be careful not to expose the port unintentionally
to the outside world).

### Dual Port
 
In test setups and some production scenarios Hono server may be configured to open one secure **and** one insecure port at the same time.
 
This is achieved by configuring both ports correctly (see above). The server will fail to start if both ports are configured to use the same port number.

Since the secure port may need different visibility in the network setup compared to the secure port, it has its own binding address
`HONO_REGISTRY_AMQP_INSECUREPORTBINDADDRESS`.
This can be used to narrow the visibility of the insecure port to a local network e.g., while the secure port may be visible worldwide. 

### Ephemeral Ports

Both the secure as well as the insecure port numbers may be explicitly set to `0`. The Device Registry will then use
arbitrary (unused) port numbers determined by the operating system during startup.

## Authentication Service Connection Configuration

The Device Registry requires a connection to an implementation of Hono's Authentication API in order to authenticate and authorize client requests.

The connection is configured according to the [Hono Client Configuration]({{< relref "hono-client-configuration.md" >}})
where the `${PREFIX}` is set to `HONO_AUTH`. Since Hono's Authentication Service does not allow caching of the responses, the cache properties
can be ignored.

In addition to the standard client configuration properties, following properties may be set for the connection:

| OS Environment Variable<br>Java System Property | Mandatory | Default | Description                                 |
| :---------------------------------------------- | :-------: | :------ | :------------------------------------------ |
| `HONO_AUTH_VALIDATION_CERTPATH`<br>`hono.auth.validation.certPath` | no  | - | The absolute path to the PEM file containing the an X.509 certificate that the service should use for validating tokens issued by the Authentication service. Alternatively, a symmetric key can be used for validating tokens by setting the `HONO_AUTH_VALIDATION_SHAREDSECRET` variable. If none of these variables is set, startup of the service fails. |
| `HONO_AUTH_VALIDATION_SHAREDSECRET`<br>`hono.auth.validation.sharedSecret` | no  | - | A string to derive a symmetric key from which is used for validating tokens issued by the Authentication service. The key is derived from the string by using the bytes of the String's UTF8 encoding. When setting the validation key using this variable, the Authentication service **must** be configured with the same key. Alternatively, an X.509 certificate can be used for validating tokens by setting the `HONO_AUTH_VALIDATION_CERTPATH` variable. If none of these variables is set, startup of the service fails. |
| `HONO_AUTH_SUPPORTEDSASLMECHANISMS`<br>`hono.auth.supportedSaslMechanisms` | no  | `EXTERNAL, PLAIN` | A (comma separated) list of the SASL mechanisms that the device registry should offer to clients for authentication. This option may be set to specify only one of `EXTERNAL` or `PLAIN`, or to use a different order. |

## Metrics Configuration

See [Monitoring & Tracing Admin Guide]({{< relref "monitoring-tracing-config.md" >}}) for details on how to configure the reporting of metrics.


## Device Identities File Format

The Device Registry supports persisting the device identities and their registration information to a JSON file in the
local file system.
The source repository contains an example configuration which illustrates the file format used. The configuration
file's location is `$HONO/services/device-registry-file/src/test/resources/device-identities.json`.

## Credentials File Format

The Device Registry supports persisting the devices' credentials to a JSON file in the local file system.
The source repository contains an example configuration which illustrates the file format used. The configuration
file's location is `$HONO/services/device-registry-file/src/test/resources/credentials.json`.

## Tenants File Format

The Device Registry supports persisting tenants to a JSON file in the local file system.
The source repository contains an example configuration which illustrates the file format used. The configuration
file's location is `$HONO/services/device-registry-file/src/test/resources/tenants.json`.

## Configuring Gateway Devices

The Device Registry supports devices to *act on behalf of* other devices. This is particularly useful for cases where
a device does not connect directly to a Hono protocol adapter but is connected to a *gateway* component that is usually
specific to the device's communication protocol. It is the gateway component which then connects to a Hono protocol
adapter and publishes data on behalf of the device(s). Examples of such a set up include devices using
[SigFox](https://www.sigfox.com) or [LoRa](https://lora-alliance.org/) for communication.

In these cases the protocol adapter will authenticate the gateway component instead of the device for which it wants to
publish data. In order to verify that the gateway is *authorized* to publish data on behalf of the particular device,
the protocol adapter should include the gateway's device identifier (as determined during the authentication process)
in its invocation of the Device Registration API's *assert Device Registration* operation.

The Device Registry will then do the following:
1. Verify that the device exists and is enabled.
2. Verify that the gateway exists and is enabled.
3. Verify that the device's registration information contains a property called `via` and that its value is either the
   gateway's device identifier or a JSON array which contains the gateway's device identifier as one of its values. 

Only if all conditions are met, the Device Registry returns an assertion of the device's registration status.
The protocol adapter can then forward the published data to the AMQP Messaging Network in the same way as for any
device that connects directly to the adapter.

The example configuration file (located at `$HONO/services/device-registry-file/src/test/resources/device-identities.json`)
includes a device and a corresponding gateway configured in this way.

## Messaging Configuration

The Device Registry uses a connection to an *AMQP 1.0 Messaging Network*, an *Apache Kafka cluster* and/or *Google Pub/Sub* to
* send [Device Provisioning Notification]({{< relref "/api/event#device-provisioning-notification" >}}) event messages
  to convey provisioning related changes regarding a device, to be received by downstream applications,
* send notification messages about changes to tenant/device/credentials data, to be processed by other Hono components.

For the event messages a connection to a *Apache Kafka cluster* is used by default, if configured.
If more than one kind of messaging is configured, the decision which one to use is done according to the
[Tenant Configuration]({{< relref "admin-guide/hono-kafka-client-configuration#configuring-tenants-to-use-kafka-based-messaging" >}}).

For notification messages, the Kafka connection is used by default, if configured. Otherwise the *AMQP messaging network*
or *Google Pub/Sub* is used.

### AMQP 1.0 Messaging Network Connection Configuration

The connection to the *AMQP 1.0 Messaging Network* is configured according to the
[Hono Client Configuration]({{< relref "hono-client-configuration.md" >}}) with `HONO_MESSAGING` being used as `${PREFIX}`. 
Since there are no responses being received, the properties for configuring response caching can be ignored.

### Kafka based Messaging Configuration

The connection to an *Apache Kafka cluster* can be configured according to the
[Hono Kafka Client Configuration]({{< relref "hono-kafka-client-configuration.md" >}}).

The following table shows the prefixes to be used to individually configure the Kafka clients used by the Device Registry.
The individual client configuration is optional, a minimal configuration may only contain a common client
configuration consisting of properties prefixed with `HONO_KAFKA_COMMONCLIENTCONFIG_` and `hono.kafka.commonClientConfig.`
respectively.

| OS Environment Variable Prefix<br>Java System Property Prefix                          | Description                                                                                                         |
|:---------------------------------------------------------------------------------------|:--------------------------------------------------------------------------------------------------------------------|
| `HONO_KAFKA_EVENT_PRODUCERCONFIG_`<br>`hono.kafka.event.producerConfig.`               | Configures the Kafka producer that publishes event messages.                                                        |
| `HONO_KAFKA_NOTIFICATION_PRODUCERCONFIG_`<br>`hono.kafka.notification.producerConfig.` | Configures the Kafka producer that publishes notification messages about changes to tenant/device/credentials data. |

### Google Pub/Sub Messaging Configuration

The connection to *Google Pub/Sub* is configured according to the
[Google Pub/Sub Messaging Configuration]({{< relref "pubsub-config.md" >}}).