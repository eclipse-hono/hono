+++
title = "AMQP Adapter Configuration"
weight = 325
+++

The AMQP protocol adapter allows clients (devices and gateway components) that speaks AMQP 1.0 to publish telemetry messages and events to Eclipse Hono&trade;'s Telemetry and Event endpoints.
<!--more-->

The adapter is implemented as a Spring Boot application. It can be run either directly from the command line or by means of starting the corresponding [Docker image](https://hub.docker.com/r/eclipse/hono-adapter-amqp-vertx/) created from it.

## Service Configuration

In addition to the following options, this component supports the options described in [Common Configuration]({{< relref "common-config.md" >}}).

The following table provides an overview of the configuration variables and corresponding command line options for configuring the AMQP adapter.

| Environment Variable<br>Command Line Option | Mandatory | Default Value | Description  |
| :------------------------------------------ | :-------: | :------------ | :------------|
| `HONO_AMQP_AUTHENTICATION_REQUIRED`<br>`--hono.amqp.authenticationRequired` | no | `true` | If set to `true` the protocol adapter requires devices to authenticate when connecting to the adapter. The credentials provided by the device are verified using the configured [Credentials Service]({{< relref "#credentials-service-connection-configuration" >}}). Devices that have failed to authenticate are not allowed to publish any data. |
| `HONO_AMQP_BIND_ADDRESS`<br>`--hono.amqp.bindAddress` | no | `127.0.0.1` | The IP address of the network interface that the secure port should be bound to.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_AMQP_CERT_PATH`<br>`--hono.amqp.certPath` | no | - | The absolute path to the PEM file containing the certificate that the protocol adapter should use for authenticating to clients. This option must be used in conjunction with `HONO_AMQP_KEY_PATH`.<br>Alternatively, the `HONO_AMQP_KEY_STORE_PATH` option can be used to configure a key store containing both the key as well as the certificate. |
| `HONO_AMQP_DEFAULTS_ENABLED`<br>`--hono.amqp.defaultsEnabled` | no | `true` | If set to `true` the protocol adapter uses *default values* registered for a device to augment messages published by the device with missing information like a content type. In particular, the protocol adapter adds default values registered for the device as (application) properties with the same name to the AMQP 1.0 messages it sends downstream to the AMQP Messaging Network. |
| `HONO_AMQP_INSECURE_PORT_BIND_ADDRESS`<br>`--hono.amqp.insecurePortBindAddress` | no | `127.0.0.1` | The IP address of the network interface that the insecure port should be bound to.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_AMQP_INSECURE_PORT`<br>`--hono.amqp.insecurePort` | no | `4040` | The port number that the protocol adapter should listen on for insecure connections.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_AMQP_INSECURE_PORT_ENABLED`<br>`--hono.amqp.insecurePortEnabled` | no | `false` | If set to `true` the protocol adapter will open an insecure port (not secured by TLS) using either the port number set via `HONO_AMQP_INSECURE_PORT` or the default AMQP port number (`1883`) if not set explicitly.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_AMQP_KEY_PATH`<br>`--hono.amqp.keyPath` | no | - | The absolute path to the (PKCS8) PEM file containing the private key that the protocol adapter should use for authenticating to clients. This option must be used in conjunction with `HONO_AMQP_CERT_PATH`. Alternatively, the `HONO_AMQP_KEY_STORE_PATH` option can be used to configure a key store containing both the key as well as the certificate. |
| `HONO_AMQP_KEY_STORE_PASSWORD`<br>`--hono.amqp.keyStorePassword` | no | - | The password required to read the contents of the key store. |
| `HONO_AMQP_KEY_STORE_PATH`<br>`--hono.amqp.keyStorePath` | no | - | The absolute path to the Java key store containing the private key and certificate that the protocol adapter should use for authenticating to clients. Either this option or the `HONO_AMQP_KEY_PATH` and `HONO_AMQP_CERT_PATH` options need to be set in order to enable TLS secured connections with clients. The key store format can be either `JKS` or `PKCS12` indicated by a `.jks` or `.p12` file suffix respectively. |
| `HONO_AMQP_MAX_CONNECTIONS`<br>`--hono.amqp.maxConnections` | no | `0` | The maximum number of concurrent connections that the protocol adapter should accept. If not set (or set to `0`), the protocol adapter determines a reasonable value based on the available resources like memory and CPU. |
| `HONO_AMQP_MAX_FRAME_SIZE`<br>`--hono.amqp.maxFrameSize` | no | `16384` | The maximum number of bytes that can be sent in an AMQP message delivery over the connection with a device. When a client sends an AMQP frame of larger size, the connection is closed. |
| `HONO_AMQP_MAX_PAYLOAD_SIZE`<br>`--hono.amqp.maxPayloadSize` | no | `2048` | The maximum allowed size of an incoming AMQP message in bytes. When a client sends a message with a larger payload, the message is discarded and the link to the client is closed. |
| `HONO_AMQP_MAX_SESSION_FRAMES`<br>`--hono.amqp.maxSessionFrames` | no | `30` | The maximum number of AMQP transfer frames for sessions created on this connection. This is the number of transfer frames that may simultaneously be in flight for all links in the session. |
| `HONO_AMQP_NATIVE_TLS_REQUIRED`<br>`--hono.amqp.nativeTlsRequired` | no | `false` | The server will probe for OpenSSL on startup if a secure port is configured. By default, the server will fall back to the JVM's default SSL engine if not available. However, if set to `true`, the server will fail to start at all in this case. |
| `HONO_AMQP_PORT`<br>`--hono.amqp.port` | no | `4041` | The secure port that the protocol adapter should listen on.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_AMQP_SECURE_PROTOCOLS`<br>`--hono.amqp.secureProtocols` | no | `TLSv1.2` | A (comma separated) list of secure protocols that are supported when negotiating TLS sessions. Please refer to the [vert.x documentation](https://vertx.io/docs/vertx-core/java/#ssl) for a list of supported protocol names. |
| `HONO_APP_MAX_INSTANCES`<br>`--hono.app.maxInstances` | no | *#CPU cores* | The number of verticle instances to deploy. If not set, one verticle per processor core is deployed. |

The variables only need to be set if the default values do not match your environment.

## Port Configuration

The AMQP protocol adapter can be configured to listen for connections on

* a secure port only (default) or
* an insecure port only or
* both a secure and an insecure port (dual port configuration)

The AMQP protocol adapter will fail to start if none of the ports is configured properly.

### Secure Port Only

The protocol adapter needs to be configured with a private key and certificate in order to open a TLS secured port.

There are two alternative ways for doing so:

1. either setting the `HONO_AMQP_KEY_STORE_PATH` and the `HONO_AMQP_KEY_STORE_PASSWORD` variables in order to load the key & certificate from a password protected key store, or
1. setting the `HONO_AMQP_KEY_PATH` and `HONO_AMQP_CERT_PATH` variables in order to load the key and certificate from two separate PEM files in PKCS8 format.

When starting up, the protocol adapter will bind a TLS secured socket to the default secure port 4041. The port number can also be set explicitly using the `HONO_AMQP_PORT` variable.

The `HONO_AMQP_BIND_ADDRESS` variable can be used to specify the network interface that the port should be exposed on. By default the port is bound to the *loopback device* only, i.e. the port will only be accessible from the local host. Setting this variable to `0.0.0.0` will let the port being bound to **all** network interfaces (be careful not to expose the port unintentionally to the outside world).

### Insecure Port Only

The secure port will mostly be required for production scenarios. However, it might be desirable to expose a non-TLS secured port instead, e.g. for testing purposes. In any case, the non-secure port needs to be explicitly enabled either by

- explicitly setting `HONO_AMQP_INSECURE_PORT` to a valid port number, or by
- implicitly configuring the default adapter port (4040) by simply setting `HONO_AMQP_INSECURE_PORT_ENABLED` to `true`.

The protocol adapter issues a warning on the console if `HONO_AMQP_INSECURE_PORT` is set to the default secure port (4041) used by the adapter for secure connections.

The `HONO_AMQP_INSECURE_PORT_BIND_ADDRESS` variable can be used to specify the network interface that the port should be exposed on. By default the port is bound to the *loopback device* only, i.e. the port will only be accessible from the local host. This variable might be used to e.g. expose the non-TLS secured port on a local interface only, thus providing easy access from within the local network, while still requiring encrypted communication when accessed from the outside over public network infrastructure.

Setting this variable to `0.0.0.0` will let the port being bound to **all** network interfaces (be careful not to expose the port unintentionally to the outside world).

### Dual Port
 
The protocol adapter may be configured to open both a secure and a non-secure port at the same time simply by configuring both ports as described above. For this to work, both ports must be configured to use different port numbers, otherwise startup will fail.

### Ephemeral Ports

Both the secure as well as the insecure port numbers may be explicitly set to `0`. The protocol adapter will then use arbitrary (unused) port numbers determined by the operating system during startup.

## AMQP 1.0 Messaging Network Connection Configuration

The adapter requires a connection to the *AMQP 1.0 Messaging Network* in order to forward telemetry data and events received from devices to downstream consumers.

The connection to the messaging network is configured according to [Hono Client Configuration]({{< relref "hono-client-configuration.md" >}})
with `HONO_MESSAGING` being used as `${PREFIX}`. Since there are no responses being received, the properties for configuring response caching can be ignored.

## Tenant Service Connection Configuration

The adapter requires a connection to an implementation of Hono's [Tenant API]({{< ref "Tenant-API.md" >}}) in order to retrieve information for a tenant.

The connection to the Tenant Service is configured according to [Hono Client Configuration]({{< relref "hono-client-configuration.md" >}})
where the `${PREFIX}` is set to `HONO_TENANT` and the additional values for response caching apply.

The adapter caches the responses from the service according to the *cache directive* included in the response.
If the response doesn't contain a *cache directive* no data will be cached.

## Device Registration Service Connection Configuration

The adapter requires a connection to an implementation of Hono's [Device Registration API]({{< ref "Device-Registration-API.md" >}}) in order to retrieve registration status assertions for connected devices.

The connection to the Device Registration Service is configured according to [Hono Client Configuration]({{< relref "hono-client-configuration.md" >}})
where the `${PREFIX}` is set to `HONO_REGISTRATION`.

The adapter caches the responses from the service according to the *cache directive* included in the response.
If the response doesn't contain a *cache directive* no data will be cached.

## Credentials Service Connection Configuration

The adapter requires a connection to an implementation of Hono's [Credentials API]({{< ref "Credentials-API.md" >}}) in order to retrieve credentials stored for devices that needs to be authenticated. During connection establishment, the adapter uses the Credentials API to retrieve the credentials on record for the device and matches that with the credentials provided by a device.

The connection to the Credentials Service is configured according to [Hono Client Configuration]({{< relref "hono-client-configuration.md" >}})
where the `${PREFIX}` is set to `HONO_CREDENTIALS`.

The adapter caches the responses from the service according to the *cache directive* included in the response.
If the response doesn't contain a *cache directive* no data will be cached.


## Device Connection Service Connection Configuration

The adapter requires a connection to an implementation of Hono's [Device Connection API]({{< relref "Device-Connection-API.md" >}}) in order to determine the gateway that a device is connected via to a protocol adapter. This information is required in order to forward commands issued by applications to the protocol adapter instance that the gateway is connected to.

The connection to the Device Connection service is configured according to [Hono Client Configuration]({{< relref "hono-client-configuration.md" >}})
where the `${PREFIX}` is set to `HONO_DEVICE_CONNECTION`.

Responses from the Device Connection service are never cached, so the properties for configuring the cache are ignored.

## Resource Limits Checker Configuration

The adapter can use metrics collected by a Prometheus server to enforce certain limits set at the tenant level like the overall number of connected devices allowed per tenant.

The following table provides an overview of the configuration variables and corresponding command line options for configuring the checker.

| Environment Variable<br>Command Line Option | Mandatory | Default Value | Description  |
| :------------------------------------------ | :-------: | :------------ | :------------|
| `HONO_PLAN_PROMETHEUS_BASED_HOST`<br>`--hono.plan.prometheusBased.host` | no | none | The host name or IP address of the Prometheus server to retrieve the metrics data from. This property needs to be set in order to enable the Prometheus based checks. |
| `HONO_PLAN_PROMETHEUS_BASED_PORT`<br>`--hono.plan.prometheusBased.port` | no | `9090` | The port of the Prometheus server to retrieve metrics data from. |
| `HONO_PLAN_PROMETHEUS_BASED_CACHE_MIN_SIZE`<br>`--hono.plan.prometheusBased.cacheMinSize` | no | `20` | The minimum size of the cache to store the metrics data retrieved from the Prometheus server. The cache is used for storing the current amount of data exchanged with devices of tenants. |
| `HONO_PLAN_PROMETHEUS_BASED_CACHE_MAX_SIZE`<br>`--hono.plan.prometheusBased.cacheMaxSize` | no | `1000` | The maximum size of the cache to store the metrics data retrieved from the Prometheus server. |
| `HONO_PLAN_PROMETHEUS_BASED_CACHE_TIMEOUT`<br>`--hono.plan.prometheusBased.cacheTimeout` | no | `600` | The number of seconds after which the cached metrics data should be considered invalid. |

## Metrics Configuration

See [Monitoring & Tracing Admin Guide]({{< relref "monitoring-tracing-config.md" >}}) for details on how to configure the reporting of metrics.
