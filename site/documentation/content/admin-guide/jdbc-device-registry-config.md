+++
title = "JDBC Based Device Registry Configuration"
weight = 315
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
* By default, supports H2, PostgreSQL and MS SQL Server
* Supports different JDBC connections for read-only and read-write operations, to support read-only replicas

**Note:** The provided container images contains only the H2, PostgreSQL and MS SQL Server configuration and JDBC driver. While it is
possible to use other JDBC compliant databases, database specific configuration and JDBC driver have to be provided
by the user.

## Service Configuration

The following table provides an overview of the configuration variables and corresponding command line options for
configuring the JDBC based Device Registry. In addition to the following options, this component also supports
the options described in [Common Configuration]({{< relref "common-config.md" >}}).

The configuration uses the section `ADAPTER` for configurations of the *protocol adapter facing* services, and the
section `MANAGEMENT` for *management facing* services. As adapters only require read-only operations, this can be
used to direct adapters to services instances, which are backed by read-only replicas. Which can improve performance
and availability. 

| Environment Variable<br>Command Line Option | Mandatory | Default | Description                                                             |
| :------------------------------------------ | :-------: | :------ | :-----------------------------------------------------------------------|
| `HONO_REGISTRY_JDBC_ADAPTER_URL`                 <br> `--hono.registry.jdbc.adapter.url`                  | yes | -    | The JDBC URL to the database. |
| `HONO_REGISTRY_JDBC_ADAPTER_DRIVERCLASS`         <br> `--hono.registry.jdbc.adapter.driverClass`          | no  | The default driver registered for the JDBC URL. | The class name of the JDBC driver.|
| `HONO_REGISTRY_JDBC_ADAPTER_USERNAME`            <br> `--hono.registry.jdbc.adapter.username`             | no  | -    | The username used to access the database. |
| `HONO_REGISTRY_JDBC_ADAPTER_PASSWORD`            <br> `--hono.registry.jdbc.adapter.password`             | no  | -    | The password used to access the database. |
| `HONO_REGISTRY_JDBC_ADAPTER_MAXIMUMPOOLSIZE`     <br> `--hono.registry.jdbc.adapter.maximumPoolSize`      | no  | Depends on the connection pool implementation. `15` for C3P0. | The maximum size of the connection pool. |
| `HONO_REGISTRY_JDBC_ADAPTER_TABLENAME`           <br> `--hono.registry.jdbc.adapter.tableName`            | no  | -    | The name of the table the datastore uses. If the datastore requires multiple tables, this is the prefix. |
| `HONO_REGISTRY_JDBC_MANAGEMENT_URL`              <br> `--hono.registry.jdbc.management.url`               | yes | -    | The JDBC URL to the database. |
| `HONO_REGISTRY_JDBC_MANAGEMENT_DRIVERCLASS`      <br> `--hono.registry.jdbc.management.driverClass`       | no  | The default driver registered for the JDBC URL. | The class name of the JDBC driver. |
| `HONO_REGISTRY_JDBC_MANAGEMENT_USERNAME`         <br> `--hono.registry.jdbc.management.username`          | no  | -    | The username used to access the database. |
| `HONO_REGISTRY_JDBC_MANAGEMENT_PASSWORD`         <br> `--hono.registry.jdbc.management.password`          | no  | -    | The password used to access the database. |
| `HONO_REGISTRY_JDBC_MANAGEMENT_MAXIMUMPOOLSIZE`  <br> `--hono.registry.jdbc.management.maximumPoolSize`   | no  | Depends on the connection pool implementation. `15` for C3P0. | The maximum size of the connection pool. |
| `HONO_REGISTRY_JDBC_MANAGEMENT_TABLENAME`        <br> `--hono.registry.jdbc.management`                   | no  | -    | The name of the table the datastore uses. If the datastore requires multiple tables, this is the prefix. |
| `HONO_REGISTRY_SVC_TASKEXECUTORQUEUESIZE`        <br> `--hono.registry.svc.taskExecutorQueueSize`         | no  | `1024` | The size of the executor queue for hashing passwords. |
| `HONO_REGISTRY_SVC_CREDENTIALSTTL`               <br> `--hono.registry.svc.credentialsTtl`                | no  | `1m` | The TTL for credentials responses. |
| `HONO_REGISTRY_SVC_REGISTRATIONTTL`              <br> `--hono.registry.svc.registrationTtl`               | no  | `1m` | The TTL for registrations responses. |
| `HONO_REGISTRY_SVC_MAXBCRYPTITERATIONS`          <br> `--hono.registry.svc.maxBcryptIterations`           | no  | `10` | The maximum number of allowed bcrypt iterations. |
| `HONO_TENANT_JDBC_ADAPTER_URL`                   <br> `--hono.tenant.jdbc.adapter.url`                    | yes | -    | The JDBC URL to the database. |
| `HONO_TENANT_JDBC_ADAPTER_DRIVERCLASS`           <br> `--hono.tenant.jdbc.adapter.driverClass`            | no  | The default driver registered for the JDBC URL. | The class name of the JDBC driver.|
| `HONO_TENANT_JDBC_ADAPTER_USERNAME`              <br> `--hono.tenant.jdbc.adapter.username`               | no  | -    | The username used to access the database. |
| `HONO_TENANT_JDBC_ADAPTER_PASSWORD`              <br> `--hono.tenant.jdbc.adapter.password`               | no  | -    | The password used to access the database. |
| `HONO_TENANT_JDBC_ADAPTER_MAXIMUMPOOLSIZE`       <br> `--hono.tenant.jdbc.adapter.maximumPoolSize`        | no  | Depends on the connection pool implementation. `15` for C3P0. | The maximum size of the connection pool. |
| `HONO_TENANT_JDBC_ADAPTER_TABLENAME`             <br> `--hono.tenant.jdbc.adapter.tableName`              | no  | -    | The name of the table the datastore uses. If the datastore requires multiple tables, this is the prefix. |
| `HONO_TENANT_JDBC_MANAGEMENT_URL`                <br> `--hono.tenant.jdbc.management.url`                 | yes | -    | The JDBC URL to the database. |
| `HONO_TENANT_JDBC_MANAGEMENT_DRIVERCLASS`        <br> `--hono.tenant.jdbc.management.driverClass`         | no  | The default driver registered for the JDBC URL. | The class name of the JDBC driver. |
| `HONO_TENANT_JDBC_MANAGEMENT_USERNAME`           <br> `--hono.tenant.jdbc.management.username`            | no  | -    | The username used to access the database. |
| `HONO_TENANT_JDBC_MANAGEMENT_PASSWORD`           <br> `--hono.tenant.jdbc.management.password`            | no  | -    | The password used to access the database. |
| `HONO_TENANT_JDBC_MANAGEMENT_MAXIMUMPOOLSIZE`    <br> `--hono.tenant.jdbc.management.maximumPoolSize`     | no  | Depends on the connection pool implementation. `15` for C3P0. | The maximum size of the connection pool. |
| `HONO_TENANT_JDBC_MANAGEMENT_TABLENAME`          <br> `--hono.tenant.jdbc.management.tableName`           | no  | -    | The name of the table the datastore uses. If the datastore requires multiple tables, this is the prefix. |
| `HONO_TENANT_SVC_TENANTTTL`                      <br> `--hono.tenant.service.tenantTtl`                   | no  | `1m` | The TTL for tenant responses. |
| `HONO_REGISTRY_AMQP_BIND_ADDRESS`<br>`--hono.registry.amqp.bindAddress` | no | `127.0.0.1` | The IP address of the network interface that the secure AMQP port should be bound to.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_REGISTRY_AMQP_CERT_PATH`<br>`--hono.registry.amqp.certPath` | no | - | The absolute path to the PEM file containing the certificate that the server should use for authenticating to clients. This option must be used in conjunction with `HONO_REGISTRY_AMQP_KEY_PATH`.<br>Alternatively, the `HONO_REGISTRY_AMQP_KEY_STORE_PATH` option can be used to configure a key store containing both the key as well as the certificate. |
| `HONO_REGISTRY_AMQP_INSECURE_PORT`<br>`--hono.registry.amqp.insecurePort` | no | - | The insecure port the server should listen on for AMQP 1.0 connections.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_REGISTRY_AMQP_INSECURE_PORT_BIND_ADDRESS`<br>`--hono.registry.amqp.insecurePortBindAddress` | no | `127.0.0.1` | The IP address of the network interface that the insecure AMQP port should be bound to.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_REGISTRY_AMQP_INSECURE_PORT_ENABLED`<br>`--hono.registry.amqp.insecurePortEnabled` | no | `false` | If set to `true` the server will open an insecure port (not secured by TLS) using either the port number set via `HONO_REGISTRY_AMQP_INSECURE_PORT` or the default AMQP port number (`5672`) if not set explicitly.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_REGISTRY_AMQP_KEY_PATH`<br>`--hono.registry.amqp.keyPath` | no | - | The absolute path to the (PKCS8) PEM file containing the private key that the server should use for authenticating to clients. This option must be used in conjunction with `HONO_REGISTRY_AMQP_CERT_PATH`. Alternatively, the `HONO_REGISTRY_AMQP_KEY_STORE_PATH` option can be used to configure a key store containing both the key as well as the certificate. |
| `HONO_REGISTRY_AMQP_KEY_STORE_PASSWORD`<br>`--hono.registry.amqp.keyStorePassword` | no | - | The password required to read the contents of the key store. |
| `HONO_REGISTRY_AMQP_KEY_STORE_PATH`<br>`--hono.registry.amqp.keyStorePath` | no | - | The absolute path to the Java key store containing the private key and certificate that the server should use for authenticating to clients. Either this option or the `HONO_REGISTRY_AMQP_KEY_PATH` and `HONO_REGISTRY_AMQP_CERT_PATH` options need to be set in order to enable TLS secured connections with clients. The key store format can be either `JKS` or `PKCS12` indicated by a `.jks` or `.p12` file suffix respectively. |
| `HONO_REGISTRY_AMQP_NATIVE_TLS_REQUIRED`<br>`--hono.registry.amqp.nativeTlsRequired` | no | `false` | The server will probe for OpenSLL on startup if a secure port is configured. By default, the server will fall back to the JVM's default SSL engine if not available. However, if set to `true`, the server will fail to start at all in this case. |
| `HONO_REGISTRY_AMQP_PORT`<br>`--hono.registry.amqp.port` | no | `5671` | The secure port that the server should listen on for AMQP 1.0 connections.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_REGISTRY_AMQP_SECURE_PROTOCOLS`<br>`--hono.registry.amqp.secureProtocols` | no | `TLSv1.2` | A (comma separated) list of secure protocols that are supported when negotiating TLS sessions. Please refer to the [vert.x documentation](https://vertx.io/docs/vertx-core/java/#ssl) for a list of supported protocol names. |
| `HONO_REGISTRY_HTTP_AUTHENTICATION_REQUIRED`<br>`--hono.registry.http.authenticationRequired` | no | `true` | If set to `true` the HTTP endpoint of the Device Registry requires clients to authenticate when connecting to the Device Registry. The JDBC based Device Registry currently supports basic authentication and the user credentials are to be stored in the database. <br>For more information on how to manage users please refer to [JDBC Auth Provider](https://vertx.io/docs/vertx-auth-jdbc/java/).|
| `HONO_REGISTRY_HTTP_BIND_ADDRESS`<br>`--hono.registry.http.bindAddress` | no | `127.0.0.1` | The IP address of the network interface that the secure HTTP port should be bound to.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_REGISTRY_HTTP_CERT_PATH`<br>`--hono.registry.http.certPath` | no | - | The absolute path to the PEM file containing the certificate that the server should use for authenticating to clients. This option must be used in conjunction with `HONO_REGISTRY_HTTP_KEY_PATH`.<br>Alternatively, the `HONO_REGISTRY_HTTP_KEY_STORE_PATH` option can be used to configure a key store containing both the key as well as the certificate. |
| `HONO_REGISTRY_HTTP_DEVICE_ID_REGEX`<br>`--hono.registry.http.deviceIdRegex` | no | `^[a-zA-Z0-9-_]+$` | The regular expression to use to validate device ID. Please refer to the [java pattern documentation](https://docs.oracle.com/javase/7/docs/api/java/util/regex/Pattern.html). |
| `HONO_REGISTRY_HTTP_INSECURE_PORT`<br>`--hono.registry.http.insecurePort` | no | - | The insecure port the server should listen on for HTTP requests.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_REGISTRY_HTTP_INSECURE_PORT_BIND_ADDRESS`<br>`--hono.registry.http.insecurePortBindAddress` | no | `127.0.0.1` | The IP address of the network interface that the insecure HTTP port should be bound to.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_REGISTRY_HTTP_INSECURE_PORT_ENABLED`<br>`--hono.registry.http.insecurePortEnabled` | no | `false` | If set to `true` the server will open an insecure port (not secured by TLS) using either the port number set via `HONO_REGISTRY_HTTP_INSECURE_PORT` or the default AMQP port number (`5672`) if not set explicitly.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_REGISTRY_HTTP_KEY_PATH`<br>`--hono.registry.http.keyPath` | no | - | The absolute path to the (PKCS8) PEM file containing the private key that the server should use for authenticating to clients. This option must be used in conjunction with `HONO_REGISTRY_HTTP_CERT_PATH`. Alternatively, the `HONO_REGISTRY_HTTP_KEY_STORE_PATH` option can be used to configure a key store containing both the key as well as the certificate. |
| `HONO_REGISTRY_HTTP_KEY_STORE_PASSWORD`<br>`--hono.registry.http.keyStorePassword` | no | - | The password required to read the contents of the key store. |
| `HONO_REGISTRY_HTTP_KEY_STORE_PATH`<br>`--hono.registry.http.keyStorePath` | no | - | The absolute path to the Java key store containing the private key and certificate that the server should use for authenticating to clients. Either this option or the `HONO_REGISTRY_HTTP_KEY_PATH` and `HONO_REGISTRY_HTTP_CERT_PATH` options need to be set in order to enable TLS secured connections with clients. The key store format can be either `JKS` or `PKCS12` indicated by a `.jks` or `.p12` file suffix respectively. |
| `HONO_REGISTRY_HTTP_PORT`<br>`--hono.registry.http.port` | no | `5671` | The secure port that the server should listen on for HTTP requests.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_REGISTRY_HTTP_TENANT_ID_REGEX`<br>`--hono.registry.http.tenantIdRegex` | no | `^[a-zA-Z0-9-_.]+$` | The regular expression to use to validate tenant ID. Please refer to the [java pattern documentation](https://docs.oracle.com/javase/7/docs/api/java/util/regex/Pattern.html). |

The variables only need to be set if the default value does not match your environment.

## Port Configuration

The Device Registry supports configuration of both, an AMQP based endpoint and an HTTP based endpoint proving RESTful
resources for managing registration information and credentials. Both endpoints can be configured to listen for
connections on:

* a secure port only (default) or
* an insecure port only or
* both a secure and an insecure port (dual port configuration)

See [Port Configuration]({{< relref "admin-guide/file-based-device-registry-config.md#port-configuration" >}}) for
more information. 

{{% note %}}
The environment variables to use for configuring the REST endpoint are the same as the ones for the AMQP endpoint,
substituting `_AMQP_` with `_HTTP_`.
{{% /note %}}

## Authentication Service Connection Configuration

See [Authentication Service Connection Configuration]({{< relref "admin-guide/file-based-device-registry-config.md#authentication-service-connection-configuration" >}})
for more information.

## Metrics Configuration

See [Monitoring & Tracing Admin Guide]({{< relref "monitoring-tracing-config.md" >}}) for details on how to configure
the reporting of metrics.
