+++
title = "Common Configuration"
weight = 300
+++

Most Hono components support a common set of configuration options. This section
describes those options. <!--more-->

Each component which supports the following options explicitly states so. If
it doesn't, then these options are not supported by this component.


## Java VM Options

The Java VM started in Hono's components can be configured with arbitrary command line options by means of setting the `_JAVA_OPTIONS` environment variable.

| Environment Variable | Mandatory | Default | Description |
| :------------------- | :-------: | :------ | :-----------|
| `_JAVA_OPTIONS`    | no        | -       | Any options that should be passed to the Java VM on the command line, e.g. `-Xmx128m` |

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
