+++
title = "JDBC Based Device Registry Configuration"
weight = 313
+++

The JDBC based Device Registry component provides an implementation of Eclipse Hono™'s
[Device Registration]({{< relref "/api/device-registration" >}}), [Credentials]({{< relref "/api/credentials" >}})
and [Tenant]({{< relref "/api/tenant" >}}) APIs. Protocol adapters use these APIs to determine a device's registration
status, e.g. if it is enabled and if it is registered with a particular tenant, and to authenticate a device before
accepting any data for processing from it. In addition to the above, this Device Registry also provides an
implementation of [Device Registry Management APIs]({{< relref "/api/management" >}}) for managing tenants,
registration information and credentials of devices.

The registry is implemented as a Quarkus application, and it uses a JDBC compliant database to persist data. It
provides the following features:

* By default, supports H2 and PostgreSQL
* Supports different JDBC connections for read-only and read-write operations, to support read-only replicas

**Note:** The provided container image contains only the H2 and PostgreSQL configuration and JDBC driver. While it is
possible to use other JDBC compliant databases, database specific configuration and JDBC driver have to be provided
by the user.

## Service Configuration

The following table provides an overview of the configuration variables and corresponding system properties for
configuring the JDBC based Device Registry.

The configuration uses the section `ADAPTER` for configurations of the *protocol adapter facing* services, and the
section `MANAGEMENT` for *management facing* services. As adapters only require read-only operations, this can be
used to direct adapters to services instances, which are backed by read-only replicas. Which can improve performance
and availability.

| OS Environment Variable<br>Java System Property | Mandatory | Default | Description                                 |
| :---------------------------------------------- | :-------: | :------ | :------------------------------------------ |
| `HONO_REGISTRY_AMQP_BINDADDRESS`                <br> `hono.registry.amqp.bindAddress`                 | no | `127.0.0.1` | The IP address of the network interface that the secure AMQP port should be bound to.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_REGISTRY_AMQP_CERTPATH`                   <br> `hono.registry.amqp.certPath`                    | no | - | The absolute path to the PEM file containing the certificate that the server should use for authenticating to clients. This option must be used in conjunction with `HONO_REGISTRY_AMQP_KEYPATH`.<br>Alternatively, the `HONO_REGISTRY_AMQP_KEYSTOREPATH` option can be used to configure a key store containing both the key as well as the certificate. |
| `HONO_REGISTRY_AMQP_INSECUREPORT`                <br> `hono.registry.amqp.insecurePort`                | no | - | The insecure port the server should listen on for AMQP 1.0 connections.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_REGISTRY_AMQP_INSECUREPORTBINDADDRESS`      <br> `hono.registry.amqp.insecurePortBindAddress`      | no | `127.0.0.1` | The IP address of the network interface that the insecure AMQP port should be bound to.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_REGISTRY_AMQP_INSECUREPORTENABLED`         <br> `hono.registry.amqp.insecurePortEnabled`           | no | `false` | If set to `true` the server will open an insecure port (not secured by TLS) using either the port number set via `HONO_REGISTRY_AMQP_INSECUREPORT` or the default AMQP port number (`5672`) if not set explicitly.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_REGISTRY_AMQP_KEYPATH`                    <br> `hono.registry.amqp.keyPath`                      | no | - | The absolute path to the (PKCS8) PEM file containing the private key that the server should use for authenticating to clients. This option must be used in conjunction with `HONO_REGISTRY_AMQP_CERTPATH`. Alternatively, the `HONO_REGISTRY_AMQP_KEYSTOREPATH` option can be used to configure a key store containing both the key as well as the certificate. |
| `HONO_REGISTRY_AMQP_KEYSTOREPASSWORD`            <br> `hono.registry.amqp.keyStorePassword`              | no | - | The password required to read the contents of the key store. |
| `HONO_REGISTRY_AMQP_KEYSTOREPATH`               <br> `hono.registry.amqp.keyStorePath`                  | no | - | The absolute path to the Java key store containing the private key and certificate that the server should use for authenticating to clients. Either this option or the `HONO_REGISTRY_AMQP_KEYPATH` and `HONO_REGISTRY_AMQP_CERTPATH` options need to be set in order to enable TLS secured connections with clients. The key store format can be either `JKS` or `PKCS12` indicated by a `.jks` or `.p12` file suffix respectively. |
| `HONO_REGISTRY_AMQP_NATIVETLSREQUIRED`           <br> `hono.registry.amqp.nativeTlsRequired`             | no | `false` | The server will probe for OpenSSL on startup if a secure port is configured. By default, the server will fall back to the JVM's default SSL engine if not available. However, if set to `true`, the server will fail to start at all in this case. |
| `HONO_REGISTRY_AMQP_PORT`                       <br> `hono.registry.amqp.port`                         | no | `5671` | The secure port that the server should listen on for AMQP 1.0 connections.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_REGISTRY_AMQP_SECUREPROTOCOLS`             <br> `hono.registry.amqp.secureProtocols`               | no | `TLSv1.3,TLSv1.2` | A (comma separated) list of secure protocols (in order of preference) that are supported when negotiating TLS sessions. Please refer to the [vert.x documentation](https://vertx.io/docs/vertx-core/java/#ssl) for a list of supported protocol names. |
| `HONO_REGISTRY_AMQP_SUPPORTEDCIPHERSUITES`       <br> `hono.registry.amqp.supportedCipherSuites`          | no | - | A (comma separated) list of names of cipher suites (in order of preference) that are supported when negotiating TLS sessions. Please refer to [JSSE Cipher Suite Names](https://docs.oracle.com/en/java/javase/11/docs/specs/security/standard-names.html#jsse-cipher-suite-names) for a list of supported names. |
| `HONO_REGISTRY_HTTP_AUTHENTICATIONREQUIRED`      <br> `hono.registry.http.authenticationRequired`        | no | `true` | If set to `true` the HTTP endpoint of the Device Registry requires clients to authenticate when connecting to the Device Registry. The JDBC based Device Registry currently supports basic authentication and the user credentials are to be stored in the database. <br>For more information on how to manage users please refer to [JDBC Auth Provider](https://vertx.io/docs/vertx-auth-jdbc/java/).|
| `HONO_REGISTRY_HTTP_BINDADDRESS`                <br> `hono.registry.http.bindAddress`                  | no | `127.0.0.1` | The IP address of the network interface that the secure HTTP port should be bound to.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_REGISTRY_HTTP_CERTPATH`                   <br> `hono.registry.http.certPath`                    | no | - | The absolute path to the PEM file containing the certificate that the server should use for authenticating to clients. This option must be used in conjunction with `HONO_REGISTRY_HTTP_KEYPATH`.<br>Alternatively, the `HONO_REGISTRY_HTTP_KEYSTOREPATH` option can be used to configure a key store containing both the key as well as the certificate. |
| `HONO_REGISTRY_HTTP_DEVICEIDREGEX`              <br> `hono.registry.http.deviceIdRegex`                | no | `^[a-zA-Z0-9-_]+$` | The regular expression to use to validate device ID. Please refer to the [java pattern documentation](https://docs.oracle.com/javase/7/docs/api/java/util/regex/Pattern.html). |
| `HONO_REGISTRY_HTTP_INSECUREPORT`               <br> `hono.registry.http.insecurePort`                 | no | - | The insecure port the server should listen on for HTTP requests.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_REGISTRY_HTTP_INSECUREPORTBINDADDRESS`     <br> `hono.registry.http.insecurePortBindAddress`       | no | `127.0.0.1` | The IP address of the network interface that the insecure HTTP port should be bound to.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_REGISTRY_HTTP_INSECUREPORTENABLED`         <br> `hono.registry.http.insecurePortEnabled`          | no | `false` | If set to `true` the server will open an insecure port (not secured by TLS) using either the port number set via `HONO_REGISTRY_HTTP_INSECUREPORT` or the default AMQP port number (`5672`) if not set explicitly.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_REGISTRY_HTTP_KEYPATH`                    <br> `hono.registry.http.keyPath`                     | no | - | The absolute path to the (PKCS8) PEM file containing the private key that the server should use for authenticating to clients. This option must be used in conjunction with `HONO_REGISTRY_HTTP_CERTPATH`. Alternatively, the `HONO_REGISTRY_HTTP_KEYSTOREPATH` option can be used to configure a key store containing both the key as well as the certificate. |
| `HONO_REGISTRY_HTTP_KEYSTOREPASSWORD`            <br> `hono.registry.http.keyStorePassword`             | no | - | The password required to read the contents of the key store. |
| `HONO_REGISTRY_HTTP_KEYSTOREPATH`                <br>`hono.registry.http.keyStorePath`                 | no | - | The absolute path to the Java key store containing the private key and certificate that the server should use for authenticating to clients. Either this option or the `HONO_REGISTRY_HTTP_KEYPATH` and `HONO_REGISTRY_HTTP_CERTPATH` options need to be set in order to enable TLS secured connections with clients. The key store format can be either `JKS` or `PKCS12` indicated by a `.jks` or `.p12` file suffix respectively. |
| `HONO_REGISTRY_HTTP_MAXPAYLOADSIZE`              <br> `hono.registry.http.maxPayloadSize`               | no | `16000` | The maximum size of an HTTP request body in bytes that is accepted by the registry. |
| `HONO_REGISTRY_HTTP_PORT`                       <br> `hono.registry.http.port`                        | no | `5671` | The secure port that the server should listen on for HTTP requests.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_REGISTRY_HTTP_SECUREPROTOCOLS`            <br> `hono.registry.http.secureProtocols`                | no | `TLSv1.3,TLSv1.2` | A (comma separated) list of secure protocols (in order of preference) that are supported when negotiating TLS sessions. Please refer to the [vert.x documentation](https://vertx.io/docs/vertx-core/java/#ssl) for a list of supported protocol names. |
| `HONO_REGISTRY_HTTP_SUPPORTEDCIPHERSUITES`       <br> `hono.registry.http.supportedCipherSuites`          | no | - | A (comma separated) list of names of cipher suites (in order of preference) that are supported when negotiating TLS sessions. Please refer to [JSSE Cipher Suite Names](https://docs.oracle.com/en/java/javase/11/docs/specs/security/standard-names.html#jsse-cipher-suite-names) for a list of supported names. |
| `HONO_REGISTRY_HTTP_TENANTIDREGEX`              <br> `hono.registry.http.tenantIdRegex`                  | no | `^[a-zA-Z0-9-_.]+$` | The regular expression to use to validate tenant ID. Please refer to the [java pattern documentation](https://docs.oracle.com/javase/7/docs/api/java/util/regex/Pattern.html). |
| `HONO_REGISTRY_JDBC_ADAPTER_URL`                 <br> `hono.registry.jdbc.adapter.url`                  | yes | -    | The JDBC URL to the database. |
| `HONO_REGISTRY_JDBC_ADAPTER_DRIVERCLASS`         <br> `hono.registry.jdbc.adapter.driverClass`          | no  | The default driver registered for the JDBC URL. | The class name of the JDBC driver.|
| `HONO_REGISTRY_JDBC_ADAPTER_USERNAME`            <br> `hono.registry.jdbc.adapter.username`             | no  | -    | The username used to access the database. |
| `HONO_REGISTRY_JDBC_ADAPTER_PASSWORD`            <br> `hono.registry.jdbc.adapter.password`             | no  | -    | The password used to access the database. |
| `HONO_REGISTRY_JDBC_ADAPTER_MAXIMUMPOOLSIZE`     <br> `hono.registry.jdbc.adapter.maximumPoolSize`      | no  | Depends on the connection pool implementation. `15` for C3P0. | The maximum size of the connection pool. |
| `HONO_REGISTRY_JDBC_ADAPTER_TABLENAME`           <br> `hono.registry.jdbc.adapter.tableName`            | no  | -    | The name of the table the datastore uses. If the datastore requires multiple tables, this is the prefix. |
| `HONO_REGISTRY_JDBC_MANAGEMENT_URL`              <br> `hono.registry.jdbc.management.url`               | yes | -    | The JDBC URL to the database. |
| `HONO_REGISTRY_JDBC_MANAGEMENT_DRIVERCLASS`      <br> `hono.registry.jdbc.management.driverClass`       | no  | The default driver registered for the JDBC URL. | The class name of the JDBC driver. |
| `HONO_REGISTRY_JDBC_MANAGEMENT_USERNAME`         <br> `hono.registry.jdbc.management.username`          | no  | -    | The username used to access the database. |
| `HONO_REGISTRY_JDBC_MANAGEMENT_PASSWORD`         <br> `hono.registry.jdbc.management.password`          | no  | -    | The password used to access the database. |
| `HONO_REGISTRY_JDBC_MANAGEMENT_MAXIMUMPOOLSIZE`  <br> `hono.registry.jdbc.management.maximumPoolSize`   | no  | Depends on the connection pool implementation. `15` for C3P0. | The maximum size of the connection pool. |
| `HONO_REGISTRY_JDBC_MANAGEMENT_TABLENAME`        <br> `hono.registry.jdbc.management`                   | no  | -    | The name of the table the datastore uses. If the datastore requires multiple tables, this is the prefix. |
| `HONO_REGISTRY_SVC_CREDENTIALSTTL`               <br> `hono.registry.svc.credentialsTtl`                | no  | `1m` | The TTL for credentials responses. |
| `HONO_REGISTRY_SVC_HASHALGORITHMSWHITELIST`       <br> `hono.registry.svc.hashAlgorithmsWhitelist`         | no | `empty` | An array of supported hashing algorithms to be used with the `hashed-password` type of credentials. When not set, all values will be accepted. |
| `HONO_REGISTRY_SVC_MAXBCRYPTCOSTFACTOR`          <br> `hono.registry.svc.maxBcryptCostFactor`            | no  | `10` | The maximum cost factor that is supported in password hashes using the BCrypt hash function. This limit is enforced by the device registry when adding or updating corresponding credentials. Increasing this number allows for potentially more secure password hashes to be used. However, the time required to compute the hash increases exponentially with the cost factor. |
| `HONO_REGISTRY_SVC_MAXDEVICESPERTENANT`          <br> `hono.registry.svc.maxDevicesPerTenant`             | no | `-1` | The number of devices that can be registered for each tenant. It is an error to set this property to a value < -1. The value `-1` indicates that no limit is set.|
| `HONO_REGISTRY_SVC_REGISTRATIONTTL`              <br> `hono.registry.svc.registrationTtl`               | no  | `1m` | The TTL for registrations responses. |
| `HONO_REGISTRY_SVC_USERNAMEPATTERN`              <br> `hono.registry.svc.usernamePattern`               | no | `^[a-zA-Z0-9-_=\\.]+$` | The regular expression to use for validating authentication identifiers (user names) of hashed-password credentials. |
| `HONO_TENANT_JDBC_ADAPTER_URL`                   <br> `hono.tenant.jdbc.adapter.url`                    | yes | -    | The JDBC URL to the database. |
| `HONO_TENANT_JDBC_ADAPTER_DRIVERCLASS`           <br> `hono.tenant.jdbc.adapter.driverClass`            | no  | The default driver registered for the JDBC URL. | The class name of the JDBC driver.|
| `HONO_TENANT_JDBC_ADAPTER_USERNAME`              <br> `hono.tenant.jdbc.adapter.username`               | no  | -    | The username used to access the database. |
| `HONO_TENANT_JDBC_ADAPTER_PASSWORD`              <br> `hono.tenant.jdbc.adapter.password`               | no  | -    | The password used to access the database. |
| `HONO_TENANT_JDBC_ADAPTER_MAXIMUMPOOLSIZE`       <br> `hono.tenant.jdbc.adapter.maximumPoolSize`        | no  | Depends on the connection pool implementation. `15` for C3P0. | The maximum size of the connection pool. |
| `HONO_TENANT_JDBC_ADAPTER_TABLENAME`             <br> `hono.tenant.jdbc.adapter.tableName`              | no  | -    | The name of the table the datastore uses. If the datastore requires multiple tables, this is the prefix. |
| `HONO_TENANT_JDBC_MANAGEMENT_URL`                <br> `hono.tenant.jdbc.management.url`                 | yes | -    | The JDBC URL to the database. |
| `HONO_TENANT_JDBC_MANAGEMENT_DRIVERCLASS`        <br> `hono.tenant.jdbc.management.driverClass`         | no  | The default driver registered for the JDBC URL. | The class name of the JDBC driver. |
| `HONO_TENANT_JDBC_MANAGEMENT_USERNAME`           <br> `hono.tenant.jdbc.management.username`            | no  | -    | The username used to access the database. |
| `HONO_TENANT_JDBC_MANAGEMENT_PASSWORD`           <br> `hono.tenant.jdbc.management.password`            | no  | -    | The password used to access the database. |
| `HONO_TENANT_JDBC_MANAGEMENT_MAXIMUMPOOLSIZE`    <br> `hono.tenant.jdbc.management.maximumPoolSize`     | no  | Depends on the connection pool implementation. `15` for C3P0. | The maximum size of the connection pool. |
| `HONO_TENANT_JDBC_MANAGEMENT_TABLENAME`          <br> `hono.tenant.jdbc.management.tableName`           | no  | -    | The name of the table the datastore uses. If the datastore requires multiple tables, this is the prefix. |
| `HONO_TENANT_SVC_TENANTTTL`                     <br> `hono.tenant.service.tenantTtl`                   | no  | `1m` | The TTL for tenant responses. |

The variables only need to be set if the default value does not match your environment.

In addition to the options described in the table above, this component supports the following standard configuration
options:

* [Common Java VM Options]({{< relref "common-config.md/#java-vm-options" >}})
* [Common vert.x Options]({{< relref "common-config.md/#vertx-options" >}})
* [Monitoring Options]({{< relref "monitoring-tracing-config.md" >}})

## Port Configuration

The device registry supports configuration of both an AMQP based endpoint exposing the Tenant, Device
Registration and Credentials APIs as well as an HTTP based endpoint providing resources for managing tenants,
registration information and credentials as defined by the [Registry Management API]({{< relref "api/management" >}}).
Both endpoints can be configured to listen for connections on

* a secure port only (default) or
* an insecure port only or
* both a secure and an insecure port (dual port configuration)

The registry will fail to start if none of the ports is configured properly.

The following sections apply to configuring both the AMQP and the HTTP endpoint. The environment variables to use for
configuring the HTTP endpoint are the same as the ones for the AMQP endpoint, substituting `_AMQP_` with `_HTTP_`,
e.g. `HONO_REGISTRY_HTTP_KEYPATH` instead of `HONO_REGISTRY_AMQP_KEYPATH`.

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
By default, the port is bound to the *loopback device* only, i.e. the port will only be accessible from the local host.
Setting this variable to `0.0.0.0` will let the port being bound to **all** network interfaces (be careful not to expose
the port unintentionally to the outside world).

### Insecure Port Only

The secure port will mostly be required for production scenarios. However, it might be desirable to expose a non-TLS
secured port instead, e.g. for testing purposes. In any case, the non-secure port needs to be explicitly enabled either
by

* explicitly setting `HONO_REGISTRY_AMQP_INSECUREPORT` to a valid port number, or
* implicitly configuring the default port (`5672` for AMQP and `8080` for HTTP) to be used by setting
  `HONO_REGISTRY_AMQP_INSECUREPORTENABLED` to `true`.

The server issues a warning on the console if one of the insecure ports is set to the corresponding default secure port.

The `HONO_REGISTRY_AMQP_INSECUREPORTBINDADDRESS` variable can be used to specify the network interface that the port should be
exposed on. By default, the port is bound to the *loopback device* only, i.e. the port will only be accessible from the
local host. This variable might be used to e.g. expose the non-TLS secured port on a local interface only, thus
providing easy access from within the local network, while still requiring encrypted communication when accessed from
the outside over public network infrastructure.

Setting this variable to `0.0.0.0` will let the port being bound to **all** network interfaces (be careful not to expose
the port unintentionally to the outside world).

### Dual Port
 
In test setups and some production scenarios Hono server may be configured to open one secure **and** one insecure port
at the same time.
 
This is achieved by configuring both ports correctly (see above). The server will fail to start if both ports are
configured to use the same port number.

Since the secure port may need different visibility in the network setup compared to the secure port, it has its own
binding address `HONO_REGISTRY_AMQP_INSECUREPORTBINDADDRESS`. This can be used to narrow the visibility of the insecure port
to a local network e.g., while the secure port may be visible worldwide. 

### Ephemeral Ports

Both the secure as well as the insecure port numbers may be explicitly set to `0`. The registry will then use
arbitrary (unused) port numbers determined by the operating system during startup.

## Authentication Service Connection Configuration

The Device Registry requires a connection to an implementation of Hono's Authentication API in order to authenticate
and authorize client requests.

The connection is configured according to the [Hono Client Configuration]({{< relref "hono-client-configuration.md" >}})
where the `${PREFIX}` is set to `HONO_AUTH`. Since Hono's Authentication Service does not allow caching of the responses,
the cache properties can be ignored.

In addition to the standard client configuration properties, the following properties may be set for the connection:

| OS Environment Variable<br>Java System Property                 | Mandatory | Default        | Description          |
| :-------------------------------------------------------------- | :-------: | :------------- | :--------------------|
| `HONO_AUTH_VALIDATION_CERTPATH`<br>`hono.auth.validation.certPath`        | no        | -              | The absolute path to the PEM file containing the an X.509 certificate that the service should use for validating tokens issued by the Authentication service. Alternatively, a symmetric key can be used for validating tokens by setting the `HONO_AUTH_VALIDATION_SHAREDSECRET` variable. If none of these variables is set, startup of the service fails. |
| `HONO_AUTH_VALIDATION_SHAREDSECRET`<br>`hono.auth.validation.sharedSecret` | no        | -              | A string to derive a symmetric key from which is used for validating tokens issued by the Authentication service. The key is derived from the string by using the bytes of the String's UTF8 encoding. When setting the validation key using this variable, the Authentication service **must** be configured with the same key. Alternatively, an X.509 certificate can be used for validating tokens by setting the `HONO_AUTH_VALIDATION_CERTPATH` variable. If none of these variables is set, startup of the service fails. |
| `HONO_AUTH_SUPPORTEDSASLMECHANISMS`<br>`hono.auth.supportedSaslMechanisms` | no        | `EXTERNAL, PLAIN` | A (comma separated) list of the SASL mechanisms that the device registry should offer to clients for authentication. This option may be set to specify only one of `EXTERNAL` or `PLAIN`, or to use a different order. |

## Metrics Configuration

See [Monitoring & Tracing Admin Guide]({{< relref "monitoring-tracing-config.md" >}}) for details on how to configure
the reporting of metrics.

## Messaging Configuration

The Device Registry uses a connection to an *AMQP 1.0 Messaging Network* and/or an *Apache Kafka cluster* to
* send [Device Provisioning Notification]({{< relref "/api/event#device-provisioning-notification" >}}) event messages
  to convey provisioning related changes regarding a device, to be received by downstream applications,
* send notification messages about changes to tenant/device/credentials data, to be processed by other Hono components.

For the event messages a connection to an *AMQP 1.0 Messaging Network* is used by default, if configured.
If both kinds of messaging are configured, the decision which one to use is done according to the
[Tenant Configuration]({{< relref "admin-guide/hono-kafka-client-configuration#configuring-tenants-to-use-kafka-based-messaging" >}}).

For notification messages, the Kafka connection is used by default, if configured. Otherwise the *AMQP messaging network*
is used.

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

| OS Environment Variable Prefix<br>Java System Property Prefix             | Description                             |
|:--------------------------------------------------------------------------|:----------------------------------------|
| `HONO_KAFKA_EVENT_PRODUCERCONFIG_`<br>`hono.kafka.event.producerConfig.`             | Configures the Kafka producer that publishes event messages. |
| `HONO_KAFKA_NOTIFICATION_PRODUCERCONFIG_`<br>`hono.kafka.notification.producerConfig.` | Configures the Kafka producer that publishes notification messages about changes to tenant/device/credentials data. |
