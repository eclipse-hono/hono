+++
title = "MQTT Adapter Configuration"
weight = 325
+++

The MQTT protocol adapter exposes an MQTT topic hierarchy for Eclipse Hono&trade;'s south bound Telemetry, Event
and Command & Control APIs.
<!--more-->

The adapter is implemented as a Quarkus application. It can be run either directly from the command line or by means
of starting the corresponding [Docker image](https://hub.docker.com/r/eclipse/hono-adapter-mqtt/)
created from it.

{{% notice info %}}
The MQTT adapter had originally been implemented as a Spring Boot application. That variant has been removed in Hono
2.0.0.
{{% /notice %}}

## Service Configuration

The following table provides an overview of the configuration variables and corresponding system properties for
configuring the MQTT adapter.

| OS Environment Variable<br>Java System Property | Mandatory | Default Value | Description  |
| :---------------------------------------------- | :-------: | :------------ | :------------|
| `HONO_APP_MAXINSTANCES`<br>`hono.app.maxInstances` | no | *#CPU cores* | The number of verticle instances to deploy. If not set, one verticle per processor core is deployed. |
| `HONO_CONNECTION_EVENTS_PRODUCER`<br>`hono.connectionEvents.producer` | no | `logging` | The implementation of *connection events* producer which is to be used. This may be `logging` or `events`.<br>See [Connection Events]({{< relref "/concepts/connection-events.md">}})|
| `HONO_MQTT_AUTHENTICATIONREQUIRED`<br>`hono.mqtt.authenticationRequired` | no | `true` | If set to `true` the protocol adapter requires devices to authenticate when connecting to the adapter. The credentials provided by the device are verified using the configured [Credentials Service]({{< relref "/admin-guide/common-config#credentials-service-connection-configuration" >}}). Devices that have failed to authenticate are not allowed to publish any data. |
| `HONO_MQTT_BINDADDRESS`<br>`hono.mqtt.bindAddress` | no | `127.0.0.1` | The IP address of the network interface that the secure port should be bound to.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_MQTT_CERTPATH`<br>`hono.mqtt.certPath` | no | - | The absolute path to the PEM file containing the certificate that the protocol adapter should use for authenticating to clients. This option must be used in conjunction with `HONO_MQTT_KEYPATH`.<br>Alternatively, the `HONO_MQTT_KEYSTOREPATH` option can be used to configure a key store containing both the key as well as the certificate. |
| `HONO_MQTT_SENDMESSAGETODEVICETIMEOUT`<br>`hono.mqtt.sendMessageToDeviceTimeout` | no | `1000` | The amount of time (milliseconds) after which the sending of a command or an error message to a device using QoS 1 is considered to be failed. The value of this variable should be increased in cases where devices are connected over a network with high latency. |
| `HONO_MQTT_DEFAULTSENABLED`<br>`hono.mqtt.defaultsEnabled` | no | `true` | If set to `true` the protocol adapter uses *default values* registered for a device and/or its tenant to augment messages published by the device with missing information like a content type. In particular, the protocol adapter adds such default values as Kafka record headers or AMQP 1.0 message (application) properties before the message is sent downstream. |
| `HONO_MQTT_GCHEAPPERCENTAGE`<br>`hono.mqtt.gcHeapPercentage` | no | `25` | The share of heap memory that should not be used by the live-data set but should be left to be used by the garbage collector. This property is used for determining the maximum number of (device) connections that the adapter should support. The value may be adapted to better reflect the characteristics of the type of garbage collector being used by the JVM and the total amount of memory available to the JVM. |
| `HONO_MQTT_INSECUREPORTBINDADDRESS`<br>`hono.mqtt.insecurePortBindAddress` | no | `127.0.0.1` | The IP address of the network interface that the insecure port should be bound to.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_MQTT_INSECUREPORTENABLED`<br>`hono.mqtt.insecurePortEnabled` | no | `false` | If set to `true` the protocol adapter will open an insecure port (not secured by TLS) using either the port number set via `HONO_MQTT_INSECUREPORT` or the default MQTT port number (`1883`) if not set explicitly.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_MQTT_KEYPATH`<br>`hono.mqtt.keyPath` | no | - | The absolute path to the (PKCS8) PEM file containing the private key that the protocol adapter should use for authenticating to clients. This option must be used in conjunction with `HONO_MQTT_CERTPATH`. Alternatively, the `HONO_MQTT_KEYSTOREPATH` option can be used to configure a key store containing both the key as well as the certificate. |
| `HONO_MQTT_KEYSTOREPASSWORD`<br>`hono.mqtt.keyStorePassword` | no | - | The password required to read the contents of the key store. |
| `HONO_MQTT_KEYSTOREPATH`<br>`hono.mqtt.keyStorePath` | no | - | The absolute path to the Java key store containing the private key and certificate that the protocol adapter should use for authenticating to clients. Either this option or the `HONO_MQTT_KEYPATH` and `HONO_MQTT_CERTPATH` options need to be set in order to enable TLS secured connections with clients. The key store format can be either `JKS` or `PKCS12` indicated by a `.jks` or `.p12` file suffix respectively. |
| `HONO_MQTT_SNI`<br>`hono.mqtt.sni` | no | `false` | Set whether the server supports Server Name Indication. By default, the server will not support SNI and the option is `false`. However, if set to `true` then the key store format , `HONO_MQTT_KEYSTOREPATH`,  should be either `JKS` or `PKCS12` indicated by a `.jks` or `.p12` file suffix respectively. |
| `HONO_MQTT_MAXCONNECTIONS`<br>`hono.mqtt.maxConnections` | no | `0` | The maximum number of concurrent connections that the protocol adapter should accept. If not set (or set to `0`), the protocol adapter determines a reasonable value based on the available resources like memory and CPU. |
| `HONO_MQTT_MAXPAYLOADSIZE`<br>`hono.mqtt.maxPayloadSize` | no | `2048` | The maximum allowed size of an incoming MQTT message's payload in bytes. When a client sends a message with a larger payload, the message is discarded and the connection to the client gets closed. |
| `HONO_MQTT_NATIVETLSREQUIRED`<br>`hono.mqtt.nativeTlsRequired` | no | `false` | The server will probe for OpenSSL on startup if a secure port is configured. By default, the server will fall back to the JVM's default SSL engine if not available. However, if set to `true`, the server will fail to start at all in this case. |
| `HONO_MQTT_PORT`<br>`hono.mqtt.port` | no | `8883` | The secure port that the protocol adapter should listen on.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_MQTT_SECUREPROTOCOLS`<br>`hono.mqtt.secureProtocols` | no | `TLSv1.3,TLSv1.2` | A (comma separated) list of secure protocols (in order of preference) that are supported when negotiating TLS sessions. Please refer to the [vert.x documentation](https://vertx.io/docs/vertx-core/java/#ssl) for a list of supported protocol names. |
| `HONO_MQTT_SUPPORTEDCIPHERSUITES`<br>`hono.mqtt.supportedCipherSuites` | no | - | A (comma separated) list of names of cipher suites (in order of preference) that the adapter may use in TLS sessions with devices. Please refer to [JSSE Cipher Suite Names](https://docs.oracle.com/en/java/javase/17/docs/specs/security/standard-names.html#jsse-cipher-suite-names) for a list of supported names. |
| `HONO_MQTT_TENANTIDLETIMEOUT`<br>`hono.mqtt.tenantIdleTimeout` | no | `PT0S` | The duration after which the protocol adapter removes local state of the tenant (e.g. open AMQP links) with an amount and a unit, e.g. `2h` for 2 hours. See the `java.time.Duration` [documentation](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/Duration.html#parse(java.lang.CharSequence)) for an explanation of the format. The leading `PT` can be omitted if only specifying hours, minutes or seconds. The value `0s` (or `PT0S`) disables the timeout. |

The variables only need to be set if the default values do not match your environment.

In addition to the options described in the table above, this component supports the following standard configuration
options:

* [Common Java VM Options]({{< relref "common-config.md/#java-vm-options" >}})
* [Common vert.x Options]({{< relref "common-config.md/#vertx-options" >}})
* [Common Protocol Adapter Options]({{< relref "common-config.md/#protocol-adapter-options" >}})
* [Monitoring Options]({{< relref "monitoring-tracing-config.md" >}})

## Port Configuration

The MQTT protocol adapter can be configured to listen for connections on

* a secure port only (default) or
* an insecure port only or
* both a secure and an insecure port (dual port configuration)

The MQTT protocol adapter will fail to start if none of the ports is configured properly.

### Secure Port Only

The protocol adapter needs to be configured with a private key and certificate in order to open a TLS secured port.

There are two alternative ways for doing so:

1. either setting the `HONO_MQTT_KEYSTOREPATH` and the `HONO_MQTT_KEYSTOREPASSWORD` variables in order to load the key & certificate from a password protected key store, or
1. setting the `HONO_MQTT_KEYPATH` and `HONO_MQTT_CERTPATH` variables in order to load the key and certificate from two separate PEM files in PKCS8 format.

When starting up, the protocol adapter will bind a TLS secured socket to the default secure MQTT port 8883. The port number can also be set explicitly using the `HONO_MQTT_PORT` variable.

The `HONO_MQTT_BINDADDRESS` variable can be used to specify the network interface that the port should be exposed on. By default the port is bound to the *loopback device* only, i.e. the port will only be accessible from the local host. Setting this variable to `0.0.0.0` will let the port being bound to **all** network interfaces (be careful not to expose the port unintentionally to the outside world).

### Insecure Port Only

The secure port will mostly be required for production scenarios. However, it might be desirable to expose a non-TLS secured port instead, e.g. for testing purposes. In any case, the non-secure port needs to be explicitly enabled either by

- explicitly setting `HONO_MQTT_INSECUREPORT` to a valid port number, or by
- implicitly configuring the default MQTT port (1883) by simply setting `HONO_MQTT_INSECUREPORTENABLED` to `true`.

The protocol adapter issues a warning on the console if `HONO_MQTT_INSECUREPORT` is set to the default secure MQTT port (8883).

The `HONO_MQTT_INSECUREPORTBINDADDRESS` variable can be used to specify the network interface that the port should be exposed on. By default the port is bound to the *loopback device* only, i.e. the port will only be accessible from the local host. This variable might be used to e.g. expose the non-TLS secured port on a local interface only, thus providing easy access from within the local network, while still requiring encrypted communication when accessed from the outside over public network infrastructure.

Setting this variable to `0.0.0.0` will let the port being bound to **all** network interfaces (be careful not to expose the port unintentionally to the outside world).

### Dual Port
 
The protocol adapter may be configured to open both a secure and a non-secure port at the same time simply by configuring both ports as described above. For this to work, both ports must be configured to use different port numbers, otherwise startup will fail.

### Ephemeral Ports

Both the secure as well as the insecure port numbers may be explicitly set to `0`. The protocol adapter will then use arbitrary (unused) port numbers determined by the operating system during startup.

## Custom Message Mapping

This protocol adapter supports transformation of messages that have been uploaded by devices before forwarding them
 to downstream consumers. This message mapping can be used to overwrite the deviceID, add additional properties and
  change the payload.

{{% notice info %}}
This is an experimental feature. The names of the configuration properties, potential values and the overall
functionality are therefore subject to change without prior notice.
{{% /notice %}}

The following table provides an overview of the configuration variables and corresponding system properties for
configuring the external service endpoint(s) for transforming messages:

| OS Environment Variable<br>Java System Property | Mandatory | Default Value | Description  |
| :---------------------------------------------- | :-------: | :------------ | :------------|
| `HONO_MQTT_MAPPERENDPOINTS_<mapperName>_HOST`<br>`hono.mqtt.mapperEndpoints.<mapperName>.host` | no | - | The host name or IP address of the service to invoke for transforming uploaded messages. The `<mapperName>` needs to contain the service name as set in the *mapper* property of the device's registration information. |
| `HONO_MQTT_MAPPERENDPOINTS_<mapperName>_PORT`<br>`hono.mqtt.mapperEndpoints.<mapperName>.port` | no | - | The port of the service to invoke for transforming uploaded messages. The `<mapperName>` needs to contain the service name as set in the *mapper* property of the device's registration information. |
| `HONO_MQTT_MAPPERENDPOINTS_<mapperName>_URI`<br>`hono.mqtt.mapperEndpoints.<mapperName>.uri` | no | - | The URI of the service to invoke for transforming uploaded messages. The `<mapperName>` needs to contain the service name as set in the *mapper* property of the device's registration information. |

### Implementation

An implementation of the mapper needs to be provided. Following data will be provided to the mapper:
- HTTP headers:
  - orig_address
  - content-type
  - tenant_id
  - all strings configured during registration
- Body
  - The payload of the message is provided in the body of the mapping request

When the mapper responds successfully(=200), the adapter will map the returning values as follows:
- The header with key `device_id` will overwrite the current deviceID.
- The remaining HTTP headers will be added to the downstream message as additional properties.
- The returned body will be used to replace the payload.
