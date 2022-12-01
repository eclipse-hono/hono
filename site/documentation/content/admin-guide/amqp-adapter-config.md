+++
title = "AMQP Adapter Configuration"
weight = 327
+++

The AMQP protocol adapter exposes AMQP based endpoints for Eclipse Hono&trade;'s south bound Telemetry, Event and
Command & Control APIs.
<!--more-->

The adapter is implemented as a Quarkus application. It can be run either directly from the command line or by
means of starting the corresponding [Docker image](https://hub.docker.com/r/eclipse/hono-adapter-amqp/)
created from it.

{{% notice info %}}
The AMQP adapter had originally been implemented as a Spring Boot application. That variant has been removed in Hono
2.0.0.
{{% /notice %}}

## Service Configuration

The following table provides an overview of the configuration variables and corresponding system properties for
configuring the AMQP adapter.

| OS Environment Variable<br>Java System Property | Mandatory | Default Value | Description  |
| :---------------------------------------------- | :-------: | :------------ | :------------|
| `HONO_AMQP_AUTHENTICATIONREQUIRED`<br>`hono.amqp.authenticationRequired` | no | `true` | If set to `true` the protocol adapter requires devices to authenticate when connecting to the adapter. The credentials provided by the device are verified using the configured [Credentials Service]({{< relref "/admin-guide/common-config#credentials-service-connection-configuration" >}}). Devices that have failed to authenticate are not allowed to publish any data. |
| `HONO_AMQP_BINDADDRESS`<br>`hono.amqp.bindAddress` | no | `127.0.0.1` | The IP address of the network interface that the secure port should be bound to.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_AMQP_CERTPATH`<br>`hono.amqp.certPath` | no | - | The absolute path to the PEM file containing the certificate that the protocol adapter should use for authenticating to clients. This option must be used in conjunction with `HONO_AMQP_KEYPATH`.<br>Alternatively, the `HONO_AMQP_KEYSTOREPATH` option can be used to configure a key store containing both the key as well as the certificate. |
| `HONO_AMQP_DEFAULTSENABLED`<br>`hono.amqp.defaultsEnabled` | no | `true` | If set to `true` the protocol adapter uses *default values* registered for a device and/or its tenant to augment messages published by the device with missing information like a content type. In particular, the protocol adapter adds such default values as Kafka record headers or AMQP 1.0 message (application) properties before the message is sent downstream. |
| `HONO_AMQP_GCHEAPPERCENTAGE`<br>`hono.amqp.gcHeapPercentage` | no | `25` | The share of heap memory that should not be used by the live-data set but should be left to be used by the garbage collector. This property is used for determining the maximum number of (device) connections that the adapter should support. The value may be adapted to better reflect the characteristics of the type of garbage collector being used by the JVM and the total amount of memory available to the JVM. |
| `HONO_AMQP_IDLETIMEOUT`<br>`hono.amqp.idleTimeout` | no | `60000` | The time interval (milliseconds) to wait for incoming traffic from a device before the connection should be considered stale and thus be closed. Setting this property to `0` prevents the adapter from detecting and closing stale connections. |
| `HONO_AMQP_SEND_MESSAGE_TO_DEVICE_TIMEOUT`<br>`hono.amqp.sendMessageToDeviceTimeout` | no | `1000` | The time interval (milliseconds) to wait for a device to acknowledge receiving a (command) message before the AMQP link used for sending the message will be closed. Setting this property to `0` means the adapter waits indefinitely for a device to acknowledge receiving the message. |
| `HONO_AMQP_INSECUREPORTBINDADDRESS`<br>`hono.amqp.insecurePortBindAddress` | no | `127.0.0.1` | The IP address of the network interface that the insecure port should be bound to.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_AMQP_INSECUREPORT`<br>`hono.amqp.insecurePort` | no | `5672` | The port number that the protocol adapter should listen on for insecure connections.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_AMQP_INSECUREPORTENABLED`<br>`hono.amqp.insecurePortEnabled` | no | `false` | If set to `true` the protocol adapter will open an insecure port (not secured by TLS) using either the port number set via `HONO_AMQP_INSECUREPORT` or the default AMQP port number (`1883`) if not set explicitly.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_AMQP_KEYPATH`<br>`hono.amqp.keyPath` | no | - | The absolute path to the (PKCS8) PEM file containing the private key that the protocol adapter should use for authenticating to clients. This option must be used in conjunction with `HONO_AMQP_CERTPATH`. Alternatively, the `HONO_AMQP_KEYSTOREPATH` option can be used to configure a key store containing both the key as well as the certificate. |
| `HONO_AMQP_KEYSTOREPASSWORD`<br>`hono.amqp.keyStorePassword` | no | - | The password required to read the contents of the key store. |
| `HONO_AMQP_KEYSTOREPATH`<br>`hono.amqp.keyStorePath` | no | - | The absolute path to the Java key store containing the private key and certificate that the protocol adapter should use for authenticating to clients. Either this option or the `HONO_AMQP_KEYPATH` and `HONO_AMQP_CERTPATH` options need to be set in order to enable TLS secured connections with clients. The key store format can be either `JKS` or `PKCS12` indicated by a `.jks` or `.p12` file suffix respectively. |
| `HONO_AMQP_SNI`<br>`hono.amqp.sni` | no | `false` | Set whether the server supports Server Name Indication. By default, the server will not support SNI and the option is `false`. However, if set to `true` then the key store format, `HONO_AMQP_KEYSTOREPATH`,  should be either `JKS` or `PKCS12` indicated by a `.jks` or `.p12` file suffix respectively. |
| `HONO_AMQP_MAXCONNECTIONS`<br>`hono.amqp.maxConnections` | no | `0` | The maximum number of concurrent connections that the protocol adapter should accept. If not set (or set to `0`), the protocol adapter determines a reasonable value based on the available resources like memory and CPU. |
| `HONO_AMQP_MAXFRAMESIZE`<br>`hono.amqp.maxFrameSize` | no | `16384` | The maximum size (in bytes) of a single AMQP frame that the adapter should accept from the device. When a device sends a bigger frame, the connection will be closed. |
| `HONO_AMQP_MAXPAYLOADSIZE`<br>`hono.amqp.maxPayloadSize` | no | `2048` | The maximum allowed size of an incoming AMQP message in bytes. When a client sends a message with a larger payload, the message is discarded and the link to the client is closed. |
| `HONO_AMQP_MAX_SESSION_FRAMES`<br>`hono.amqp.maxSessionFrames` | no | `30` | The maximum number of AMQP transfer frames for sessions created on this connection. This is the number of transfer frames that may simultaneously be in flight for all links in the session. |
| `HONO_AMQP_NATIVETLSREQUIRED`<br>`hono.amqp.nativeTlsRequired` | no | `false` | The server will probe for OpenSSL on startup if a secure port is configured. By default, the server will fall back to the JVM's default SSL engine if not available. However, if set to `true`, the server will fail to start at all in this case. |
| `HONO_AMQP_PORT`<br>`hono.amqp.port` | no | `5671` | The secure port that the protocol adapter should listen on.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_AMQP_SECUREPROTOCOLS`<br>`hono.amqp.secureProtocols` | no | `TLSv1.3,TLSv1.2` | A (comma separated) list of secure protocols (in order of preference) that are supported when negotiating TLS sessions. Please refer to the [vert.x documentation](https://vertx.io/docs/vertx-core/java/#ssl) for a list of supported protocol names. |
| `HONO_AMQP_SUPPORTEDCIPHERSUITES`<br>`hono.amqp.supportedCipherSuites` | no | - | A (comma separated) list of names of cipher suites (in order of preference) that the adapter may use in TLS sessions with devices. Please refer to [JSSE Cipher Suite Names](https://docs.oracle.com/en/java/javase/17/docs/specs/security/standard-names.html#jsse-cipher-suite-names) for a list of supported names. |
| `HONO_AMQP_TENANTIDLETIMEOUT`<br>`hono.amqp.tenantIdleTimeout` | no | `PT0S` | The duration after which the protocol adapter removes local state of the tenant (e.g. open AMQP links) with an amount and a unit, e.g. `2h` for 2 hours. See the `java.time.Duration` [documentation](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/Duration.html#parse(java.lang.CharSequence)) for an explanation of the format. The leading `PT` can be omitted if only specifying hours, minutes or seconds. The value `0s` (or `PT0S`) disables the timeout. |
| `HONO_APP_MAXINSTANCES`<br>`hono.app.maxInstances` | no | *#CPU cores* | The number of verticle instances to deploy. If not set, one verticle per processor core is deployed. |

The variables only need to be set if the default values do not match your environment.

In addition to the options described in the table above, this component supports the following standard configuration
options:

* [Common Java VM Options]({{< relref "common-config.md/#java-vm-options" >}})
* [Common vert.x Options]({{< relref "common-config.md/#vertx-options" >}})
* [Common Protocol Adapter Options]({{< relref "common-config.md/#protocol-adapter-options" >}})
* [Monitoring Options]({{< relref "monitoring-tracing-config.md" >}})

## Port Configuration

The AMQP protocol adapter can be configured to listen for connections on

* a secure port only (default) or
* an insecure port only or
* both a secure and an insecure port (dual port configuration)

The AMQP protocol adapter will fail to start if none of the ports is configured properly.

### Secure Port Only

The protocol adapter needs to be configured with a private key and certificate in order to open a TLS secured port.

There are two alternative ways for doing so:

1. either setting the `HONO_AMQP_KEYSTOREPATH` and the `HONO_AMQP_KEYSTOREPASSWORD` variables in order to load the key & certificate from a password protected key store, or
1. setting the `HONO_AMQP_KEYPATH` and `HONO_AMQP_CERTPATH` variables in order to load the key and certificate from two separate PEM files in PKCS8 format.

When starting up, the protocol adapter will bind a TLS secured socket to the default secure port 5671. The port number can also be set explicitly using the `HONO_AMQP_PORT` variable.

The `HONO_AMQP_BINDADDRESS` variable can be used to specify the network interface that the port should be exposed on. By default the port is bound to the *loopback device* only, i.e. the port will only be accessible from the local host. Setting this variable to `0.0.0.0` will let the port being bound to **all** network interfaces (be careful not to expose the port unintentionally to the outside world).

### Insecure Port Only

The secure port will mostly be required for production scenarios. However, it might be desirable to expose a non-TLS secured port instead, e.g. for testing purposes. In any case, the non-secure port needs to be explicitly enabled either by

- explicitly setting `HONO_AMQP_INSECUREPORT` to a valid port number, or by
- implicitly configuring the default adapter port (5672) by simply setting `HONO_AMQP_INSECUREPORTENABLED` to `true`.

The protocol adapter issues a warning on the console if `HONO_AMQP_INSECUREPORT` is set to the default secure port (5671) used by the adapter for secure connections.

The `HONO_AMQP_INSECUREPORTBINDADDRESS` variable can be used to specify the network interface that the port should be exposed on. By default the port is bound to the *loopback device* only, i.e. the port will only be accessible from the local host. This variable might be used to e.g. expose the non-TLS secured port on a local interface only, thus providing easy access from within the local network, while still requiring encrypted communication when accessed from the outside over public network infrastructure.

Setting this variable to `0.0.0.0` will let the port being bound to **all** network interfaces (be careful not to expose the port unintentionally to the outside world).

### Dual Port
 
The protocol adapter may be configured to open both a secure and a non-secure port at the same time simply by configuring both ports as described above. For this to work, both ports must be configured to use different port numbers, otherwise startup will fail.

### Ephemeral Ports

Both the secure as well as the insecure port numbers may be explicitly set to `0`. The protocol adapter will then use arbitrary (unused) port numbers determined by the operating system during startup.
