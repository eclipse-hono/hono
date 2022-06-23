+++
title = "HTTP Adapter Configuration"
weight = 320
+++

The HTTP protocol adapter exposes HTTP based endpoints for Eclipse Hono&trade;'s south bound Telemetry, Event and
Command & Control APIs.
<!--more-->

The adapter is implemented as a Quarkus application. It can be run either directly from the command line or by means
of starting the corresponding [Docker image](https://hub.docker.com/r/eclipse/hono-adapter-http-vertx-quarkus/)
created from it.

{{% notice info %}}
The HTTP adapter had originally been implemented as a Spring Boot application. That variant has been deprecated with Hono
1.11.0 and will be completely removed in Hono 2.0.0.
The [Spring Boot based Docker image](https://hub.docker.com/r/eclipse/hono-adapter-http-vertx/) will be available until
then.
{{% /notice %}}

## Service Configuration

The following table provides an overview of the configuration variables and corresponding system properties for
configuring the HTTP adapter.

| OS Environment Variable<br>Java System Property | Mandatory | Default | Description |
| :---------------------------------------------- | :-------: | :------ | :---------- |
| `HONO_APP_MAXINSTANCES`<br>`hono.app.maxInstances` | no | *#CPU cores* | The number of verticle instances to deploy. If not set, one verticle per processor core is deployed. |
| `HONO_HTTP_AUTHENTICATIONREQUIRED`<br>`hono.http.authenticationRequired` | no | `true` | If set to `true` the protocol adapter requires devices to authenticate when connecting to the adapter. The credentials provided by the device are verified using the configured [Credentials Service]({{< relref "/admin-guide/common-config#credentials-service-connection-configuration" >}}). Devices that have failed to authenticate are not allowed to publish any data. |
| `HONO_HTTP_BINDADDRESS`<br>`hono.http.bindAddress` | no | `127.0.0.1` | The IP address of the network interface that the secure port should be bound to.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_HTTP_CERTPATH`<br>`hono.http.certPath` | no | - | The absolute path to the PEM file containing the certificate that the protocol adapter should use for authenticating to clients. This option must be used in conjunction with `HONO_HTTP_KEYPATH`.<br>Alternatively, the `HONO_HTTP_KEYSTOREPATH` option can be used to configure a key store containing both the key as well as the certificate. |
| `HONO_HTTP_DEFAULTSENABLED`<br>`hono.http.defaultsEnabled` | no | `true` | If set to `true` the protocol adapter uses *default values* registered for a device to augment messages published by the device with missing information like a content type. In particular, the protocol adapter adds default values registered for the device as (application) properties with the same name to the AMQP 1.0 messages it sends downstream to the AMQP Messaging Network. |
| `HONO_HTTP_IDLETIMEOUT` <br>`hono.http.idleTimeout` | no | `60` | The idle timeout in seconds. A connection will timeout and be closed if no data is received or sent within the idle timeout period. A zero value means no timeout is used.|
| `HONO_HTTP_INSECUREPORT`<br>`hono.http.insecurePort` | no | - | The insecure port the protocol adapter should listen on.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_HTTP_INSECUREPORTBINDADDRESS`<br>`hono.http.insecurePortBindAddress` | no | `127.0.0.1` | The IP address of the network interface that the insecure port should be bound to.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_HTTP_INSECUREPORTENABLED`<br>`hono.http.insecurePortEnabled` | no | `false` | If set to `true` the protocol adapter will open an insecure port (not secured by TLS) using either the port number set via `HONO_HTTP_INSECUREPORT` or the default port number (`8080`) if not set explicitly.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_HTTP_KEYPATH`<br>`hono.http.keyPath` | no | - | The absolute path to the (PKCS8) PEM file containing the private key that the protocol adapter should use for authenticating to clients. This option must be used in conjunction with `HONO_HTTP_CERTPATH`. Alternatively, the `HONO_HTTP_KEYSTOREPATH` option can be used to configure a key store containing both the key as well as the certificate. |
| `HONO_HTTP_KEYSTOREPASSWORD`<br>`hono.http.keyStorePassword` | no | - | The password required to read the contents of the key store. |
| `HONO_HTTP_KEYSTOREPATH`<br>`hono.http.keyStorePath` | no | - | The absolute path to the Java key store containing the private key and certificate that the protocol adapter should use for authenticating to clients. Either this option or the `HONO_HTTP_KEYPATH` and `HONO_HTTP_CERTPATH` options need to be set in order to enable TLS secured connections with clients. The key store format can be either `JKS` or `PKCS12` indicated by a `.jks` or `.p12` file suffix respectively. |
| `HONO_HTTP_SNI`<br>`hono.http.sni` | no | `false` | Set whether the server supports Server Name Indication. By default, the server will not support SNI and the option is `false`. However, if set to `true` then the key store format , `HONO_HTTP_KEYSTOREPATH`,  should be either `JKS` or `PKCS12` indicated by a `.jks` or `.p12` file suffix respectively. |
| `HONO_HTTP_NATIVETLSREQUIRED`<br>`hono.http.nativeTlsRequired` | no | `false` | The server will probe for OpenSSL on startup if a secure port is configured. By default, the server will fall back to the JVM's default SSL engine if not available. However, if set to `true`, the server will fail to start at all in this case. |
| `HONO_HTTP_MAXPAYLOADSIZE`<br>`hono.http.maxPayloadSize` | no | `2048` | The maximum allowed size of an incoming HTTP request's body in bytes. Requests with a larger body size are rejected with a 413 `Request entity too large` response. |
| `HONO_HTTP_PORT`<br>`hono.http.port` | no | `8443` | The secure port that the protocol adapter should listen on.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_HTTP_REALM`<br>`hono.http.realm` | no | `Hono` | The name of the *realm* that unauthenticated devices are prompted to provide credentials for. The realm is used in the *WWW-Authenticate* header returned to devices in response to unauthenticated requests. |
| `HONO_HTTP_SECUREPROTOCOLS`<br>`hono.http.secureProtocols` | no | `TLSv1.3,TLSv1.2` | A (comma separated) list of secure protocols (in order of preference) that are supported when negotiating TLS sessions. Please refer to the [vert.x documentation](https://vertx.io/docs/vertx-core/java/#ssl) for a list of supported protocol names. |
| `HONO_AMQP_SUPPORTEDCIPHERSUITES`<br>`hono.amqp.supportedCipherSuites` | no | - | A (comma separated) list of names of cipher suites (in order of preference) that the adapter may use in TLS sessions with devices. Please refer to [JSSE Cipher Suite Names](https://docs.oracle.com/en/java/javase/11/docs/specs/security/standard-names.html#jsse-cipher-suite-names) for a list of supported names. |
| `HONO_HTTP_TENANTIDLETIMEOUT`<br>`hono.http.tenantIdleTimeout` | no | `PT0S` | The duration after which the protocol adapter removes local state of the tenant (e.g. open AMQP links) with an amount and a unit, e.g. `2h` for 2 hours. See the `java.time.Duration` [documentation](https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/time/Duration.html#parse(java.lang.CharSequence)) for an explanation of the format. The leading `PT` can be omitted if only specifying hours, minutes or seconds. The value `0s` (or `PT0S`) disables the timeout. |

The variables only need to be set if the default value does not match your environment.

In addition to the options described in the table above, this component supports the following standard configuration
options:

* [Common Java VM Options]({{< relref "common-config.md/#java-vm-options" >}})
* [Common vert.x Options]({{< relref "common-config.md/#vertx-options" >}})
* [Common Protocol Adapter Options]({{< relref "common-config.md/#protocol-adapter-options" >}})
* [Monitoring Options]({{< relref "monitoring-tracing-config.md" >}})

## Port Configuration

The HTTP protocol adapter can be configured to listen for connections on

* a secure port only (default) or
* an insecure port only or
* both a secure and an insecure port (dual port configuration)

The HTTP protocol adapter will fail to start if none of the ports is configured properly.

### Secure Port Only

The protocol adapter needs to be configured with a private key and certificate in order to open a TLS secured port.

There are two alternative ways for doing so:

1. Setting the `HONO_HTTP_KEYSTOREPATH` and the `HONO_HTTP_KEYSTOREPASSWORD` variables in order to load the key & certificate from a password protected key store, or
1. setting the `HONO_HTTP_KEYPATH` and `HONO_HTTP_CERTPATH` variables in order to load the key and certificate from two separate PEM files in PKCS8 format.

When starting up, the protocol adapter will bind a TLS secured socket to the default secure port 8443. The port number can also be set explicitly using the `HONO_HTTP_PORT` variable.

The `HONO_HTTP_BINDADDRESS` variable can be used to specify the network interface that the port should be exposed on. By default the port is bound to the *loopback device* only, i.e. the port will only be accessible from the local host. Setting this variable to `0.0.0.0` will let the port being bound to **all** network interfaces (be careful not to expose the port unintentionally to the outside world).

### Insecure Port Only

The secure port will mostly be required for production scenarios. However, it might be desirable to expose a non-TLS secured port instead, e.g. for testing purposes. In any case, the non-secure port needs to be explicitly enabled either by

- explicitly setting `HONO_HTTP_INSECUREPORT` to a valid port number, or by
- implicitly configuring the default port (8080) by simply setting `HONO_HTTP_INSECUREPORTENABLED` to `true`.

The protocol adapter issues a warning on the console if `HONO_HTTP_INSECUREPORT` is set to the default secure HTTP port (8443).

The `HONO_HTTP_INSECUREPORTBINDADDRESS` variable can be used to specify the network interface that the port should be exposed on. By default the port is bound to the *loopback device* only, i.e. the port will only be accessible from the local host. This variable might be used to e.g. expose the non-TLS secured port on a local interface only, thus providing easy access from within the local network, while still requiring encrypted communication when accessed from the outside over public network infrastructure.

Setting this variable to `0.0.0.0` will let the port being bound to **all** network interfaces (be careful not to expose the port unintentionally to the outside world).

### Dual Port
 
The protocol adapter may be configured to open both a secure and a non-secure port at the same time simply by configuring both ports as described above. For this to work, both ports must be configured to use different port numbers, otherwise startup will fail.

### Ephemeral Ports

Both the secure as well as the insecure port numbers may be explicitly set to `0`. The protocol adapter will then use arbitrary (unused) port numbers determined by the operating system during startup.
