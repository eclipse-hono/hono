+++
title = "Kura Adapter Configuration"
weight = 330
+++

The Kura protocol adapter exposes an MQTT topic hierarchy allowing Eclipse Kura&trade; based gateways to access Eclipse Hono&trade;'s
south bound Telemetry, Event and Command & Control APIs.
<!--more-->

The adapter is implemented as a Spring Boot application. It can be run either directly from the command line or by means of starting the
corresponding [Docker image](https://hub.docker.com/r/eclipse/hono-adapter-kura/) created from it.

The adapter supports the following standard configuration options:

* [Common Java VM Options]({{< relref "common-config.md/#java-vm-options" >}})
* [Common vert.x Options]({{< relref "common-config.md/#vert-x-options" >}})
* [Common Protocol Adapter Options]({{< relref "common-config.md/#protocol-adapter-options" >}})
* [Monitoring Options]({{< relref "monitoring-tracing-config.md" >}})

## Service Configuration

The following table provides an overview of the configuration variables and corresponding command line options for configuring the MQTT adapter.

| Environment Variable<br>Command Line Option | Mandatory | Default Value | Description  |
| :------------------------------------------ | :-------: | :------------ | :------------|
| `HONO_APP_MAX_INSTANCES`<br>`--hono.app.maxInstances` | no | *#CPU cores* | The number of verticle instances to deploy. If not set, one verticle per processor core is deployed. |
| `HONO_KURA_AUTHENTICATION_REQUIRED`<br>`--hono.kura.authenticationRequired` | no | `true` | If set to `true` the protocol adapter requires devices to authenticate when connecting to the adapter. The credentials provided by the device are verified using the configured [Credentials Service]({{< relref "#credentials-service-connection-configuration" >}}). Devices that have failed to authenticate are not allowed to publish any data. |
| `HONO_KURA_BIND_ADDRESS`<br>`--hono.kura.bindAddress` | no | `127.0.0.1` | The IP address of the network interface that the secure port should be bound to.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_KURA_CERT_PATH`<br>`--hono.kura.certPath` | no | - | The absolute path to the PEM file containing the certificate that the protocol adapter should use for authenticating to clients. This option must be used in conjunction with `HONO_KURA_KEY_PATH`.<br>Alternatively, the `HONO_KURA_KEY_STORE_PATH` option can be used to configure a key store containing both the key as well as the certificate. |
| `HONO_KURA_CONTROL_PREFIX`<br>`--hono.kura.controlPrefix` | no | `$EDC` | The *topic.control-prefix* to use for determining if a message published by a Kura gateway is a *control* message. All messages published to a topic that does not start with this prefix are considered *data* messages. |
| `HONO_KURA_CTRL_MSG_CONTENT_TYPE`<br>`--hono.kura.ctrlMsgContentType` | no | `application/vnd.eclipse.kura-control` | The content type to set on AMQP messages created from Kura *control* messages. |
| `HONO_KURA_DATA_MSG_CONTENT_TYPE`<br>`--hono.kura.dataMsgContentType` | no | `application/vnd.eclipse.kura-data` | The content type to set on AMQP messages created from Kura *data* messages. |
| `HONO_KURA_DEFAULTS_ENABLED`<br>`--hono.kura.defaultsEnabled` | no | `true` | If set to `true` the protocol adapter uses *default values* registered for a device to augment messages published by the device with missing information like a content type. In particular, the protocol adapter adds default values registered for the device as (application) properties with the same name to the AMQP 1.0 messages it sends downstream to the AMQP Messaging Network. |
| `HONO_KURA_INSECURE_PORT`<br>`--hono.kura.insecurePort` | no | - | The insecure port the protocol adapter should listen on.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_KURA_INSECURE_PORT_BIND_ADDRESS`<br>`--hono.kura.insecurePortBindAddress` | no | `127.0.0.1` | The IP address of the network interface that the insecure port should be bound to.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_KURA_INSECURE_PORT_ENABLED`<br>`--hono.kura.insecurePortEnabled` | no | `false` | If set to `true` the protocol adapter will open an insecure port (not secured by TLS) using either the port number set via `HONO_KURA_INSECURE_PORT` or the default MQTT port number (`1883`) if not set explicitly.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_KURA_KEY_PATH`<br>`--hono.kura.keyPath` | no | - | The absolute path to the (PKCS8) PEM file containing the private key that the protocol adapter should use for authenticating to clients. This option must be used in conjunction with `HONO_KURA_CERT_PATH`. Alternatively, the `HONO_KURA_KEY_STORE_PATH` option can be used to configure a key store containing both the key as well as the certificate. |
| `HONO_KURA_KEY_STORE_PASSWORD`<br>`--hono.kura.keyStorePassword` | no | - | The password required to read the contents of the key store. |
| `HONO_KURA_KEY_STORE_PATH`<br>`--hono.kura.keyStorePath` | no | - | The absolute path to the Java key store containing the private key and certificate that the protocol adapter should use for authenticating to clients. Either this option or the `HONO_KURA_KEY_PATH` and `HONO_KURA_CERT_PATH` options need to be set in order to enable TLS secured connections with clients. The key store format can be either `JKS` or `PKCS12` indicated by a `.jks` or `.p12` file suffix respectively. |
| `HONO_KURA_SNI`<br>`--hono.kura.sni` | no | `false` | Set whether the server supports Server Name Indication. By default, the server will not support SNI and the option is `false`. However, if set to `true` then the key store format , `HONO_KURA_KEY_STORE_PATH`,  should be either `JKS` or `PKCS12` indicated by a `.jks` or `.p12` file suffix respectively. |
| `HONO_MQTT_MAX_CONNECTIONS`<br>`--hono.mqtt.maxConnections` | no | `0` | The maximum number of concurrent connections that the protocol adapter should accept. If not set (or set to `0`), the protocol adapter determines a reasonable value based on the available resources like memory and CPU. |
| `HONO_KURA_MAX_PAYLOAD_SIZE`<br>`--hono.kura.maxPayloadSize` | no | `2048` | The maximum allowed size of an incoming MQTT message's payload in bytes. When a client sends a message with a larger payload, the message is discarded and the connection to the client gets closed. |
| `HONO_KURA_NATIVE_TLS_REQUIRED`<br>`--hono.kura.nativeTlsRequired` | no | `false` | The server will probe for OpenSLL on startup if a secure port is configured. By default, the server will fall back to the JVM's default SSL engine if not available. However, if set to `true`, the server will fail to start at all in this case. |
| `HONO_KURA_PORT`<br>`--hono.kura.port` | no | `8883` | The secure port that the protocol adapter should listen on.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_KURA_SECURE_PROTOCOLS`<br>`--hono.kura.secureProtocols` | no | `TLSv1.3,TLSv1.2` | A (comma separated) list of secure protocols (in order of preference) that are supported when negotiating TLS sessions. Please refer to the [vert.x documentation](https://vertx.io/docs/vertx-core/java/#ssl) for a list of supported protocol names. |
| `HONO_KURA_TENANT_IDLE_TIMEOUT`<br>`--hono.kura.tenantIdleTimeout` | no | `0ms` | The duration after which the protocol adapter removes local state of the tenant (e.g. open AMQP links) with an amount and a unit, e.g. `2h` for 2 hours. See the [Spring Boot documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/spring-boot-features.html#boot-features-external-config-conversion-duration) for an explanation of the format. The value `0ms` disables the timeout. |
| `HONO_KURA_SEND_MESSAGE_TO_DEVICE_TIMEOUT`<br>`--hono.kura.sendMessageToDeviceTimeout` | no | `1000` | The amount of time (milliseconds) after which the sending of a command to a device using QoS 1 is considered to be failed. The value of this variable should be increased in cases where devices are connected over a network with high latency. |

The variables only need to be set if the default values do not match your environment.


## Port Configuration

The Kura protocol adapter can be configured to listen for connections on

* a secure port only (default) or
* an insecure port only or
* both a secure and an insecure port (dual port configuration)

The Kura protocol adapter will fail to start if none of the ports is configured properly.

### Secure Port Only

The protocol adapter needs to be configured with a private key and certificate in order to open a TLS secured port.

There are two alternative ways for doing so:

1. either setting the `HONO_KURA_KEY_STORE_PATH` and the `HONO_KURA_KEY_STORE_PASSWORD` variables in order to load the key & certificate from a password protected key store, or
1. setting the `HONO_KURA_KEY_PATH` and `HONO_KURA_CERT_PATH` variables in order to load the key and certificate from two separate PEM files in PKCS8 format.

When starting up, the protocol adapter will bind a TLS secured socket to the default secure MQTT port 8883. The port number can also be set explicitly using the `HONO_KURA_PORT` variable.

The `HONO_KURA_BIND_ADDRESS` variable can be used to specify the network interface that the port should be exposed on. By default the port is bound to the *loopback device* only, i.e. the port will only be accessible from the local host. Setting this variable to `0.0.0.0` will let the port being bound to **all** network interfaces (be careful not to expose the port unintentionally to the outside world).

### Insecure Port Only

The secure port will mostly be required for production scenarios. However, it might be desirable to expose a non-TLS secured port instead, e.g. for testing purposes. In any case, the non-secure port needs to be explicitly enabled either by

- explicitly setting `HONO_KURA_INSECURE_PORT` to a valid port number, or by
- implicitly configuring the default MQTT port (1883) by simply setting `HONO_KURA_INSECURE_PORT_ENABLED` to `true`.

The protocol adapter issues a warning on the console if `HONO_KURA_INSECURE_PORT` is set to the default secure MQTT port (8883).

The `HONO_KURA_INSECURE_PORT_BIND_ADDRESS` variable can be used to specify the network interface that the port should be exposed on. By default the port is bound to the *loopback device* only, i.e. the port will only be accessible from the local host. This variable might be used to e.g. expose the non-TLS secured port on a local interface only, thus providing easy access from within the local network, while still requiring encrypted communication when accessed from the outside over public network infrastructure.

Setting this variable to `0.0.0.0` will let the port being bound to **all** network interfaces (be careful not to expose the port unintentionally to the outside world).

### Dual Port
 
The protocol adapter may be configured to open both a secure and a non-secure port at the same time simply by configuring both ports as described above. For this to work, both ports must be configured to use different port numbers, otherwise startup will fail.

### Ephemeral Ports

Both the secure as well as the insecure port numbers may be explicitly set to `0`. The protocol adapter will then use arbitrary (unused) port numbers determined by the operating system during startup.
