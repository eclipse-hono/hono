+++
title = "JDBC Based Device Registry Configuration"
weight = 313
+++

The JDBC based Device Registry components provides an implementation of Eclipse Hono™'s
[Device Registration]({{< relref "/api/device-registration" >}}), [Credentials]({{< relref "/api/credentials" >}})
and [Tenant]({{< relref "/api/tenant" >}}) APIs. Protocol adapters use these APIs to determine a device's registration
status, e.g. if it is enabled and if it is registered with a particular tenant, and to authenticate a device before
accepting any data for processing from it. In addition to the above, this Device Registry also provides an
implementation of [Device Registry Management APIs]({{< relref "/api/management" >}}) for managing tenants,
registration information and credentials of devices.

The application is implemented as a Spring Boot application, and it uses a JDBC compliant database to persist data. In
provides the following features:

* Run only the registration and credentials service, or run including the tenant service.
* By default, supports H2 and PostgreSQL
* Supports different JDBC connections for read-only and read-write operations, to support read-only replicas

**Note:** The provided container images contains only the H2 and PostgreSQL configuration and JDBC driver. While it is
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
| :------------------------------------------ | :-------: | :------ | :---------------------------------------------- |
| `HONO_REGISTRY_AMQP_BINDADDRESS`                <br> `hono.registry.amqp.bindAddress`                 | no | `127.0.0.1` | The IP address of the network interface that the secure AMQP port should be bound to.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_REGISTRY_AMQP_CERTPATH`                   <br> `hono.registry.amqp.certPath`                    | no | - | The absolute path to the PEM file containing the certificate that the server should use for authenticating to clients. This option must be used in conjunction with `HONO_REGISTRY_AMQP_KEYPATH`.<br>Alternatively, the `HONO_REGISTRY_AMQP_KEYSTOREPATH` option can be used to configure a key store containing both the key as well as the certificate. |
| `HONO_REGISTRY_AMQP_INSECUREPORT`                <br> `hono.registry.amqp.insecurePort`                | no | - | The insecure port the server should listen on for AMQP 1.0 connections.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_REGISTRY_AMQP_INSECUREPORTBINDADDRESS`      <br> `hono.registry.amqp.insecurePortBindAddress`      | no | `127.0.0.1` | The IP address of the network interface that the insecure AMQP port should be bound to.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_REGISTRY_AMQP_INSECUREPORTENABLED`         <br> `hono.registry.amqp.insecurePortEnabled`           | no | `false` | If set to `true` the server will open an insecure port (not secured by TLS) using either the port number set via `HONO_REGISTRY_AMQP_INSECUREPORT` or the default AMQP port number (`5672`) if not set explicitly.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_REGISTRY_AMQP_KEYPATH`                    <br> `hono.registry.amqp.keyPath`                      | no | - | The absolute path to the (PKCS8) PEM file containing the private key that the server should use for authenticating to clients. This option must be used in conjunction with `HONO_REGISTRY_AMQP_CERTPATH`. Alternatively, the `HONO_REGISTRY_AMQP_KEYSTOREPATH` option can be used to configure a key store containing both the key as well as the certificate. |
| `HONO_REGISTRY_AMQP_KEYSTOREPASSWORD`            <br> `hono.registry.amqp.keyStorePassword`              | no | - | The password required to read the contents of the key store. |
| `HONO_REGISTRY_AMQP_KEYSTOREPATH`               <br> `hono.registry.amqp.keyStorePath`                  | no | - | The absolute path to the Java key store containing the private key and certificate that the server should use for authenticating to clients. Either this option or the `HONO_REGISTRY_AMQP_KEYPATH` and `HONO_REGISTRY_AMQP_CERTPATH` options need to be set in order to enable TLS secured connections with clients. The key store format can be either `JKS` or `PKCS12` indicated by a `.jks` or `.p12` file suffix respectively. |
| `HONO_REGISTRY_AMQP_NATIVETLSREQUIRED`           <br> `hono.registry.amqp.nativeTlsRequired`             | no | `false` | The server will probe for OpenSLL on startup if a secure port is configured. By default, the server will fall back to the JVM's default SSL engine if not available. However, if set to `true`, the server will fail to start at all in this case. |
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

The Device Registry supports configuration of both, an AMQP based endpoint and an HTTP based endpoint proving RESTful
resources for managing registration information and credentials. Both endpoints can be configured to listen for
connections on:

* a secure port only (default) or
* an insecure port only or
* both a secure and an insecure port (dual port configuration)

See [Port Configuration]({{< relref "file-based-device-registry-config#port-configuration" >}}) for
more information.

{{% note %}}
The environment variables to use for configuring the REST endpoint are the same as the ones for the AMQP endpoint,
substituting `_AMQP_` with `_HTTP_`.
{{% /note %}}

## Authentication Service Connection Configuration

See [Authentication Service Connection Configuration]({{< relref "file-based-device-registry-config#authentication-service-connection-configuration" >}})
for more information.

## Metrics Configuration

See [Monitoring & Tracing Admin Guide]({{< relref "monitoring-tracing-config.md" >}}) for details on how to configure
the reporting of metrics.
