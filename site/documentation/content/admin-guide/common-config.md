+++
title = "Common Configuration"
weight = 300
+++

Many Hono components support a common set of configuration options. This section
describes those options. <!--more-->

Each component which supports the following options explicitly states so. If
it doesn't, then these options are not supported by this component.


## Java VM Options

The Java VM started in Hono's components can be configured with arbitrary command line options by means of setting the `JDK_JAVA_OPTIONS` environment variable.

| Environment Variable | Mandatory | Default | Description |
| :------------------- | :-------: | :------ | :-----------|
| `JDK_JAVA_OPTIONS` | no        | -       | Any options that should be passed to the Java VM on the command line, e.g. `-Xmx128m` |

## Vert.x Options

The vert.x framework instance used to run Hono's components on can be configured using the following environment variables or corresponding command line options:

| Environment Variable<br>Command Line Option | Mandatory | Default | Description                                                             |
| :------------------------------------------ | :-------: | :------ | :-----------------------------------------------------------------------|
| `HONO_VERTX_DNS_QUERY_TIMEOUT`<br>`--hono.vertx.dnsQueryTimeout` | no | `5000` | The amount of time (in milliseconds) after which a DNS query is considered to be failed. Setting this variable to a smaller value may help to reduce the time required to establish connections to the services this adapter depends on. However, setting it to a value that is too small for any DNS query to succeed will effectively prevent any connections to be established at all. |
| `HONO_VERTX_MAX_EVENT_LOOP_EXECUTE_TIME_MILLIS`<br>`--hono.vertx.maxEventLoopExecuteTimeMillis` | no | `2000` | The maximum number of milliseconds that a task on the event loop may run without being considered to block the event loop. |
| `HONO_VERTX_PREFER_NATIVE`<br>`--hono.vertx.preferNative`| no | `false` | Tries to enable *epoll()* support on Linux (if available). See the [notes below](./#epoll) for an explanation of the benefits of enabling *epoll*. |

<a name="epoll"></a>
### Using epoll() on Linux 

Using `epoll()` on Linux may provide better performance for applications which
have a high I/O throughput. Especially when the application supports an
asynchronous I/O model. This is true for most Hono components and applications using Hono.

The *Netty* framework supports using `epoll()` on Linux x86_64 based systems.
Hono provides the a Maven build profile for enabling
support for *epoll* during the build process.

In order to use *epoll*

* Hono needs to be built with the `netty-native-linux-x86_64` Maven profile enabled and
* the `HONO_VERTX_PREFER_NATIVE` environment variable needs to be set to `true` on startup.

## Protocol Adapter Options

### AMQP 1.0 Messaging Network Connection Configuration

Protocol adapters require a connection to the *AMQP 1.0 Messaging Network* in order to forward telemetry data and events received from devices to downstream consumers.

The connection to the messaging network is configured according to [Hono Client Configuration]({{< relref "hono-client-configuration.md" >}})
with `HONO_MESSAGING` being used as `${PREFIX}`. Since there are no responses being received, the properties for configuring response caching can be ignored.

### Command & Control Connection Configuration

Protocol adapters require an additional connection to the *AMQP 1.0 Messaging Network* in order to receive
commands from downstream applications and send responses to commands back to applications.

The connection is configured according to [Hono Client Configuration]({{< relref "hono-client-configuration.md" >}})
with `HONO_COMMAND` being used as `${PREFIX}`. The properties for configuring response caching can be ignored.

### Tenant Service Connection Configuration

Protocol adapters require a connection to an implementation of Hono's [Tenant API]({{< ref "/api/tenant" >}}) in order to retrieve information for a tenant.

The connection to the Tenant Service is configured according to [Hono Client Configuration]({{< relref "hono-client-configuration.md" >}})
where the `${PREFIX}` is set to `HONO_TENANT` and the additional values for response caching apply.

The adapter caches the responses from the service according to the *cache directive* included in the response.
If the response doesn't contain a *cache directive* no data will be cached.

### Device Registration Service Connection Configuration

Protocol adapters require a connection to an implementation of Hono's [Device Registration API]({{< relref "/api/device-registration" >}}) in order to retrieve registration status assertions for connected devices.

The connection to the Device Registration Service is configured according to [Hono Client Configuration]({{< relref "hono-client-configuration.md" >}})
where the `${PREFIX}` is set to `HONO_REGISTRATION`.

The adapter caches the responses from the service according to the *cache directive* included in the response.
If the response doesn't contain a *cache directive* no data will be cached.

### Credentials Service Connection Configuration

Protocol adapters require a connection to an implementation of Hono's [Credentials API]({{< relref "/api/credentials" >}}) in order to retrieve credentials stored for devices that needs to be authenticated. During connection establishment, the adapter uses the Credentials API to retrieve the credentials on record for the device and matches that with the credentials provided by a device.

The connection to the Credentials Service is configured according to [Hono Client Configuration]({{< relref "hono-client-configuration.md" >}})
where the `${PREFIX}` is set to `HONO_CREDENTIALS`.

The adapter caches the responses from the service according to the *cache directive* included in the response.
If the response doesn't contain a *cache directive* no data will be cached.


### Device Connection Service Connection Configuration

Protocol adapters require a connection to an implementation of Hono's [Device Connection API]({{< relref "/api/device-connection" >}})
in order to determine the gateway that a device is connected via to a protocol adapter. This information is required in order to
forward commands issued by applications to the protocol adapter instance that the gateway is connected to.

The connection to the Device Connection service is configured according to [Hono Client Configuration]({{< relref "hono-client-configuration.md" >}})
where the `${PREFIX}` is set to `HONO_DEVICECONNECTION`.

Responses from the Device Connection service are never cached, so the properties for configuring the cache are ignored.

### Resource Limits Checker Configuration

The adapter can use metrics collected by a Prometheus server to enforce certain limits set at the tenant level like the overall number of connected devices allowed per tenant.

The following table provides an overview of the configuration variables and corresponding command line options for configuring the checker.

| Environment Variable<br>Command Line Option | Mandatory | Default Value | Description  |
| :------------------------------------------ | :-------: | :------------ | :------------|
| `HONO_RESOURCELIMITS_PROMETHEUSBASED_HOST`<br>`--hono.resourceLimits.prometheusBased.host` | no | none | The host name or IP address of the Prometheus server to retrieve the metrics data from. This property needs to be set in order to enable the Prometheus based checks. |
| `HONO_RESOURCELIMITS_PROMETHEUSBASED_PORT`<br>`--hono.resourceLimits.prometheusBased.port` | no | `9090` | The port of the Prometheus server to retrieve metrics data from. |
| `HONO_RESOURCELIMITS_PROMETHEUSBASED_CACHE_MIN_SIZE`<br>`--hono.resourceLimits.prometheusBased.cacheMinSize` | no | `20` | The minimum size of the cache to store the metrics data retrieved from the Prometheus server. The cache is used for storing the current amount of data exchanged with devices of tenants. |
| `HONO_RESOURCELIMITS_PROMETHEUSBASED_CACHE_MAX_SIZE`<br>`--hono.resourceLimits.prometheusBased.cacheMaxSize` | no | `1000` | The maximum size of the cache to store the metrics data retrieved from the Prometheus server. |
| `HONO_RESOURCELIMITS_PROMETHEUSBASED_CACHE_TIMEOUT`<br>`--hono.resourceLimits.prometheusBased.cacheTimeout` | no | `600` | The number of seconds after which the cached metrics data should be considered invalid. |
