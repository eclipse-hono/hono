+++
title = "Common Configuration"
weight = 300
+++

Most Hono components support a common set of configuration options. This section
describes those options. <!--more-->

Each component which supports the following options explicitly states so. If
it doesn't, then these options are not supported by this component.

## Using epoll() on Linux 

Using `epoll()` on Linux may provide better performance for application which
have a high I/O throughput. Especially when the application supports an
asynchronous I/O model. This is true for most Hono applications.

Netty allows the use of `epoll()` on Linux x86_64 based systems. Hono provides
a build profile for enabling this support, and allows to configure Vert.x to
make use of this.

Only when the Maven profile was active during the build **and** the Vert.x
option is enabled during runtime, this support is being enabled.

The Maven profile is named `netty-native-linux-x86_64`. It can be enabled
using the command line argument `-Pnetty-native-linux-x86_64`, by default
it is **not** active.

| Environment Variable<br>Command Line Option | Mandatory | Default | Description                                                             |
| :------------------------------------------ | :-------: | :------ | :-----------------------------------------------------------------------|
| `HONO_VERTX_PREFER_NATIVE`<br>`--hono.vertx.preferNative`| no | `false` | Tries to enable epoll() support on Linux, when available   |
