+++
title = "Kura Adapter Configuration"
hidden = true # does not show up in the menu, but shows up in the version selector and can be accessed from links
weight = 0
+++

The Kura protocol adapter exposes an MQTT topic hierarchy allowing Eclipse Kura&trade; 3 based gateways to access
Eclipse Hono&trade;'s south bound Telemetry, Event and Command & Control APIs.
<!--more-->

The adapter is implemented as a Spring Boot application. It can be run either directly from the command line or by means
of starting the corresponding [Docker image](https://hub.docker.com/r/eclipse/hono-adapter-kura/) created from it.

{{% notice info %}}
The Kura adapter has been removed in Hono 2.0.0. Support for Kura version 4 and later is still available by means of
Hono's standard MQTT adapter.
{{% /notice %}}

## Service Configuration

The following table provides an overview of the configuration variables and corresponding system properties for
configuring the MQTT adapter.

| OS Environment Variable<br>Java System Property | Mandatory | Default Value | Description  |
| :---------------------------------------------- | :-------: | :------------ | :------------|
| `HONO_APP_MAXINSTANCES`<br>`hono.app.maxInstances` | no | *#CPU cores* | The number of verticle instances to deploy. If not set, one verticle per processor core is deployed. |
| `HONO_KURA_AUTHENTICATIONREQUIRED`<br>`hono.kura.authenticationRequired` | no | `true` | If set to `true` the protocol adapter requires devices to authenticate when connecting to the adapter. The credentials provided by the device are verified using the configured [Credentials Service]({{< relref "/admin-guide/common-config#credentials-service-connection-configuration" >}}). Devices that have failed to authenticate are not allowed to publish any data. |
| `HONO_KURA_BINDADDRESS`<br>`hono.kura.bindAddress` | no | `127.0.0.1` | The IP address of the network interface that the secure port should be bound to.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_KURA_CERTPATH`<br>`hono.kura.certPath` | no | - | The absolute path to the PEM file containing the certificate that the protocol adapter should use for authenticating to clients. This option must be used in conjunction with `HONO_KURA_KEYPATH`.<br>Alternatively, the `HONO_KURA_KEYSTOREPATH` option can be used to configure a key store containing both the key as well as the certificate. |
| `HONO_KURA_CONTROLPREFIX`<br>`hono.kura.controlPrefix` | no | `$EDC` | The *topic.control-prefix* to use for determining if a message published by a Kura gateway is a *control* message. All messages published to a topic that does not start with this prefix are considered *data* messages. |
| `HONO_KURA_CTRLMSGCONTENTTYPE`<br>`hono.kura.ctrlMsgContentType` | no | `application/vnd.eclipse.kura-control` | The content type to set on AMQP messages created from Kura *control* messages. |
| `HONO_KURA_DATAMSGCONTENTTYPE`<br>`hono.kura.dataMsgContentType` | no | `application/vnd.eclipse.kura-data` | The content type to set on AMQP messages created from Kura *data* messages. |
| `HONO_KURA_DEFAULTSENABLED`<br>`hono.kura.defaultsEnabled` | no | `true` | If set to `true` the protocol adapter uses *default values* registered for a device and/or its tenant to augment messages published by the device with missing information like a content type. In particular, the protocol adapter adds such default values as Kafka record headers or AMQP 1.0 message (application) properties before the message is sent downstream. |
| `HONO_KURA_INSECUREPORT`<br>`hono.kura.insecurePort` | no | - | The insecure port the protocol adapter should listen on.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_KURA_INSECUREPORTBINDADDRESS`<br>`hono.kura.insecurePortBindAddress` | no | `127.0.0.1` | The IP address of the network interface that the insecure port should be bound to.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_KURA_INSECUREPORTENABLED`<br>`hono.kura.insecurePortEnabled` | no | `false` | If set to `true` the protocol adapter will open an insecure port (not secured by TLS) using either the port number set via `HONO_KURA_INSECUREPORT` or the default MQTT port number (`1883`) if not set explicitly.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_KURA_KEYPATH`<br>`hono.kura.keyPath` | no | - | The absolute path to the (PKCS8) PEM file containing the private key that the protocol adapter should use for authenticating to clients. This option must be used in conjunction with `HONO_KURA_CERTPATH`. Alternatively, the `HONO_KURA_KEYSTOREPATH` option can be used to configure a key store containing both the key as well as the certificate. |
| `HONO_KURA_KEYSTOREPASSWORD`<br>`hono.kura.keyStorePassword` | no | - | The password required to read the contents of the key store. |
| `HONO_KURA_KEYSTOREPATH`<br>`hono.kura.keyStorePath` | no | - | The absolute path to the Java key store containing the private key and certificate that the protocol adapter should use for authenticating to clients. Either this option or the `HONO_KURA_KEYPATH` and `HONO_KURA_CERTPATH` options need to be set in order to enable TLS secured connections with clients. The key store format can be either `JKS` or `PKCS12` indicated by a `.jks` or `.p12` file suffix respectively. |
| `HONO_KURA_SNI`<br>`hono.kura.sni` | no | `false` | Set whether the server supports Server Name Indication. By default, the server will not support SNI and the option is `false`. However, if set to `true` then the key store format , `HONO_KURA_KEYSTOREPATH`,  should be either `JKS` or `PKCS12` indicated by a `.jks` or `.p12` file suffix respectively. |
| `HONO_MQTT_MAXCONNECTIONS`<br>`hono.mqtt.maxConnections` | no | `0` | The maximum number of concurrent connections that the protocol adapter should accept. If not set (or set to `0`), the protocol adapter determines a reasonable value based on the available resources like memory and CPU. |
| `HONO_KURA_MAXPAYLOADSIZE`<br>`hono.kura.maxPayloadSize` | no | `2048` | The maximum allowed size of an incoming MQTT message's payload in bytes. When a client sends a message with a larger payload, the message is discarded and the connection to the client gets closed. |
| `HONO_KURA_NATIVETLSREQUIRED`<br>`hono.kura.nativeTlsRequired` | no | `false` | The server will probe for OpenSSL on startup if a secure port is configured. By default, the server will fall back to the JVM's default SSL engine if not available. However, if set to `true`, the server will fail to start at all in this case. |
| `HONO_KURA_PORT`<br>`hono.kura.port` | no | `8883` | The secure port that the protocol adapter should listen on.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_KURA_SECUREPROTOCOLS`<br>`hono.kura.secureProtocols` | no | `TLSv1.3,TLSv1.2` | A (comma separated) list of secure protocols (in order of preference) that are supported when negotiating TLS sessions. Please refer to the [vert.x documentation](https://vertx.io/docs/vertx-core/java/#ssl) for a list of supported protocol names. |
| `HONO_AMQP_SUPPORTEDCIPHERSUITES`<br>`hono.amqp.supportedCipherSuites` | no | - | A (comma separated) list of names of cipher suites (in order of preference) that the adapter may use in TLS sessions with devices. Please refer to [JSSE Cipher Suite Names](https://docs.oracle.com/en/java/javase/17/docs/specs/security/standard-names.html#jsse-cipher-suite-names) for a list of supported names. |
| `HONO_KURA_TENANTIDLETIMEOUT`<br>`hono.kura.tenantIdleTimeout` | no | `PT0S` | The duration after which the protocol adapter removes local state of the tenant (e.g. open AMQP links) with an amount and a unit, e.g. `2h` for 2 hours. See the `java.time.Duration` [documentation](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/Duration.html#parse(java.lang.CharSequence)) for an explanation of the format. The leading `PT` can be omitted if only specifying hours, minutes or seconds. The value `0s` (or `PT0S`) disables the timeout. |
| `HONO_KURA_SENDMESSAGETODEVICETIMEOUT`<br>`hono.kura.sendMessageToDeviceTimeout` | no | `1000` | The amount of time (milliseconds) after which the sending of a command to a device using QoS 1 is considered to be failed. The value of this variable should be increased in cases where devices are connected over a network with high latency. |

The variables only need to be set if the default values do not match your environment.

In addition to the options described in the table above, this component supports the following standard configuration
options:

* [Common Java VM Options]({{< relref "common-config.md/#java-vm-options" >}})
* [Common vert.x Options]({{< relref "common-config.md/#vertx-options" >}})
* [Common Protocol Adapter Options]({{< relref "common-config.md/#protocol-adapter-options" >}})
* [Monitoring Options]({{< relref "monitoring-tracing-config.md" >}})

## Port Configuration

The Kura protocol adapter can be configured to listen for connections on

* a secure port only (default) or
* an insecure port only or
* both a secure and an insecure port (dual port configuration)

The Kura protocol adapter will fail to start if none of the ports is configured properly.

### Secure Port Only

The protocol adapter needs to be configured with a private key and certificate in order to open a TLS secured port.

There are two alternative ways for doing so:

1. either setting the `HONO_KURA_KEYSTOREPATH` and the `HONO_KURA_KEYSTOREPASSWORD` variables in order to load the key & certificate from a password protected key store, or
1. setting the `HONO_KURA_KEYPATH` and `HONO_KURA_CERTPATH` variables in order to load the key and certificate from two separate PEM files in PKCS8 format.

When starting up, the protocol adapter will bind a TLS secured socket to the default secure MQTT port 8883. The port number can also be set explicitly using the `HONO_KURA_PORT` variable.

The `HONO_KURA_BINDADDRESS` variable can be used to specify the network interface that the port should be exposed on. By default the port is bound to the *loopback device* only, i.e. the port will only be accessible from the local host. Setting this variable to `0.0.0.0` will let the port being bound to **all** network interfaces (be careful not to expose the port unintentionally to the outside world).

### Insecure Port Only

The secure port will mostly be required for production scenarios. However, it might be desirable to expose a non-TLS secured port instead, e.g. for testing purposes. In any case, the non-secure port needs to be explicitly enabled either by

- explicitly setting `HONO_KURA_INSECUREPORT` to a valid port number, or by
- implicitly configuring the default MQTT port (1883) by simply setting `HONO_KURA_INSECUREPORTENABLED` to `true`.

The protocol adapter issues a warning on the console if `HONO_KURA_INSECUREPORT` is set to the default secure MQTT port (8883).

The `HONO_KURA_INSECUREPORTBINDADDRESS` variable can be used to specify the network interface that the port should be exposed on. By default the port is bound to the *loopback device* only, i.e. the port will only be accessible from the local host. This variable might be used to e.g. expose the non-TLS secured port on a local interface only, thus providing easy access from within the local network, while still requiring encrypted communication when accessed from the outside over public network infrastructure.

Setting this variable to `0.0.0.0` will let the port being bound to **all** network interfaces (be careful not to expose the port unintentionally to the outside world).

### Dual Port
 
The protocol adapter may be configured to open both a secure and a non-secure port at the same time simply by configuring both ports as described above. For this to work, both ports must be configured to use different port numbers, otherwise startup will fail.

### Ephemeral Ports

Both the secure as well as the insecure port numbers may be explicitly set to `0`. The protocol adapter will then use arbitrary (unused) port numbers determined by the operating system during startup.
