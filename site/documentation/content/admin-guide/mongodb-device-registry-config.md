+++
title = "MongoDB Based Device Registry Configuration"
weight = 316
+++

The MongoDB based Device Registry component provides an implementation of Eclipse Hono&trade;'s [Device Registration]({{< relref "/api/device-registration" >}}),
[Credentials]({{< relref "/api/credentials" >}}) and [Tenant]({{< relref "/api/tenant" >}}) APIs. Protocol adapters use these APIs to determine a device's registration status, e.g. if it is enabled and if it is registered with a particular tenant, and to authenticate a device before accepting any data for processing from it. In addition to the above, this Device Registry also provides an implementation of [Device Registry Management APIs]({{< relref "/api/management" >}}) for managing tenants, registration information and credentials of devices.

The Device Registry is implemented as a Spring Boot application, and the data is persisted in a MongoDB database. It can be run either directly from the command line or by means of starting the corresponding [Docker image](https://hub.docker.com/r/eclipse/hono-service-device-registry-mongodb/) created from it.

## Service Configuration

The following table provides an overview of the configuration variables and corresponding command line options for configuring the MongoDB based Device Registry. In addition to the following options, this component also supports the options described in [Common Configuration]({{< relref "common-config.md" >}}).

| Environment Variable<br>Command Line Option | Mandatory | Default | Description                                                             |
| :------------------------------------------ | :-------: | :------ | :-----------------------------------------------------------------------|
| `HONO_CREDENTIALS_SVC_CACHE_MAX_AGE`<br>`--hono.credentials.svc.cacheMaxAge` | no | `180` | The maximum period of time (seconds) that information returned by the service's operations may be cached for. |
| `HONO_CREDENTIALS_SVC_COLLECTION_NAME`<br>`--hono.credentials.svc.collectionName` | no | `credentials` | The name of the MongoDB collection where the server stores credentials of devices.|
| `HONO_CREDENTIALS_SVC_HASH_ALGORITHMS_WHITELIST`<br>`--hono.credentials.svc.hashAlgorithmsWhitelist` | no | `empty` | An array of supported hashing algorithms to be used with the `hashed-password` type of credentials. When not set, all values will be accepted. |
| `HONO_CREDENTIALS_SVC_MAX_BCRYPT_ITERATIONS`<br>`--hono.credentials.svc.maxBcryptIterations` | no | `10` | The maximum number of iterations that are supported in password hashes using the BCrypt hash function. This limit is enforced by the device registry when adding or updating corresponding credentials. Increasing this number allows for potentially more secure password hashes to be used. However, the time required to compute the hash increases exponentially with the number of iterations. |
| `HONO_CREDENTIALS_SVC_MODIFICATION_ENABLED`<br>`--hono.credentials.svc.modificationEnabled` | no | `true` | When set to `false` the credentials contained in the registry cannot be updated nor removed. |
| `HONO_CREDENTIALS_SVC_RECEIVER_LINK_CREDIT`<br>`--hono.credentials.svc.receiverLinkCredit` | no | `100` | The number of credits to flow to a client connecting to the Credentials endpoint. |
| `HONO_MONGODB_CONNECTION_STRING`<br>`--hono.mongodb.connectionString` | no | - | The connection string used by the Device Registry application to connect to the MongoDB database. If `HONO_MONGODB_CONNECTION_STRING` is set, it overrides the other MongoDB connection settings.<br> See [Connection String URI Format](https://docs.mongodb.com/manual/reference/connection-string/) for more information.|
| `HONO_MONGODB_CONNECTION_TIMEOUT_IN_MS`<br>`--hono.mongodb.connectionTimeoutInMs` | no | `10000` | The time in milliseconds to attempt a connection before timing out.|
| `HONO_MONGODB_DB_NAME`<br>`--hono.mongodb.dbName` | no | - | The name of the MongoDB database that should be used by the Device Registry application. |
| `HONO_MONGODB_HOST`<br>`--hono.mongodb.host` | no | `localhost` | The host name or IP address of the MongoDB instance.|
| `HONO_MONGODB_PORT`<br>`--hono.mongodb.port` | no | `27017` | The port that the MongoDB instance is listening on.|
| `HONO_MONGODB_PASSWORD`<br>`--hono.mongodb.password` | no | - | The password to use for authenticating to the MongoDB instance.|
| `HONO_MONGODB_SERVER_SELECTION_TIMEOUT_IN_MS`<br>`--hono.mongodb.serverSelectionTimeoutInMs` | no | `1000` | The time in milliseconds that the mongo driver will wait to select a server for an operation before raising an error.|
| `HONO_MONGODB_USERNAME`<br>`--hono.mongodb.username` | no | - | The user name to use for authenticating to the MongoDB instance.|
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
| `HONO_REGISTRY_HTTP_AUTHENTICATION_REQUIRED`<br>`--hono.registry.http.authenticationRequired` | no | `true` | If set to `true` the HTTP endpoint of the Device Registry requires clients to authenticate when connecting to the Device Registry. The MongoDB based Device Registry currently supports basic authentication and the user credentials are to be stored in a MongoDB collection with name `user`. <br>For more information on how to manage users please refer to [Mongo Auth Provider](https://vertx.io/docs/vertx-auth-mongo/java/).|
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
| `HONO_REGISTRY_SVC_CACHE_MAX_AGE`<br>`--hono.registry.svc.cacheMaxAge` | no | `180` | The maximum period of time (seconds) that information returned by the service's operations may be cached for. |
| `HONO_REGISTRY_SVC_COLLECTION_NAME`<br>`--hono.registry.svc.collectionName` | no | `devices` | The name of the MongoDB collection where the server stores registered device information.|
| `HONO_REGISTRY_SVC_MAX_DEVICES_PER_TENANT`<br>`--hono.registry.svc.maxDevicesPerTenant` | no | `-1` | The number of devices that can be registered for each tenant. It is an error to set this property to a value < -1. The value `-1` indicates that no limit is set.|
| `HONO_REGISTRY_SVC_MODIFICATION_ENABLED`<br>`--hono.registry.svc.modificationEnabled` | no | `true` | When set to `false` the device information contained in the registry cannot be updated nor removed from the registry. |
| `HONO_REGISTRY_SVC_RECEIVER_LINK_CREDIT`<br>`--hono.registry.svc.receiverLinkCredit` | no | `100` | The number of credits to flow to a client connecting to the Device Registration endpoint. |
| `HONO_TENANT_SVC_CACHE_MAX_AGE`<br>`--hono.tenant.svc.cacheMaxAge` | no | `180` | The maximum period of time (seconds) that information returned by the service's operations may be cached for. |
| `HONO_TENANT_SVC_COLLECTION_NAME`<br>`--hono.tenant.svc.collectionName` | no | `tenants` | The name of the MongoDB collection where the server stores tenants information.|
| `HONO_TENANT_SVC_MODIFICATION_ENABLED`<br>`--hono.tenant.svc.modificationEnabled` | no | `true` | When set to `false` the tenants contained in the registry cannot be updated nor removed. |
| `HONO_TENANT_SVC_RECEIVER_LINK_CREDIT`<br>`--hono.tenant.svc.receiverLinkCredit` | no | `100` | The number of credits to flow to a client connecting to the Tenant endpoint. |

The variables only need to be set if the default value does not match your environment.

## Port Configuration

The Device Registry supports configuration of both, an AMQP based endpoint as well as an HTTP based endpoint proving RESTful resources for managing registration information and credentials. Both endpoints can be configured to listen for connections on

* a secure port only (default) or
* an insecure port only or
* both a secure and an insecure port (dual port configuration)

See [Port Configuration]({{< relref "admin-guide/file-based-device-registry-config.md#port-configuration" >}}) for more information. 

{{% note %}}
The environment variables to use for configuring the REST endpoint are the same as the ones for the AMQP endpoint, substituting `_AMQP_` with `_HTTP_`.
{{% /note %}}

## Authentication Service Connection Configuration

See [Authentication Service Connection Configuration]({{< relref "admin-guide/file-based-device-registry-config.md#authentication-service-connection-configuration" >}}) for more information.

## Metrics Configuration

See [Monitoring & Tracing Admin Guide]({{< relref "monitoring-tracing-config.md" >}}) for details on how to configure the reporting of metrics.