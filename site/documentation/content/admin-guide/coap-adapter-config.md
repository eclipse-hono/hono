+++
title = "CoAP Adapter Configuration"
weight = 329
+++

The CoAP protocol adapter exposes CoAP based endpoints for Eclipse Hono&trade;'s south bound Telemetry, Event and Command & Control APIs.
<!--more-->

The adapter is implemented as a Spring Boot application using [Eclipse Californium&trade;](https://www.eclipse.org/californium/) for implementing
the CoAP protocol handling. It can be run either directly from the command line or by means of starting the corresponding
[Docker image](https://hub.docker.com/r/eclipse/hono-adapter-coap-vertx/) created from it.

The adapter supports the following standard configuration options:

* [Common Java VM Options]({{< relref "common-config.md/#java-vm-options" >}})
* [Common vert.x Options]({{< relref "common-config.md/#vert-x-options" >}})
* [Common Protocol Adapter Options]({{< relref "common-config.md/#protocol-adapter-options" >}})
* [Monitoring Options]({{< relref "monitoring-tracing-config.md" >}})

## Service Configuration

The following table provides an overview of the configuration variables and corresponding command line options for configuring the CoAP adapter.

| Environment Variable<br>Command Line Option | Mandatory | Default | Description |
| :------------------------------------------ | :-------: | :------ | :---------- |
| `HONO_APP_MAX_INSTANCES`<br>`--hono.app.maxInstances` | no | *#CPU cores* | The number of verticle instances to deploy. If not set, one verticle per processor core is deployed. |
| `HONO_COAP_AUTHENTICATION_REQUIRED`<br>`--hono.coap.authenticationRequired` | no | `true` | If set to `true` the protocol adapter requires devices to authenticate when connecting to the adapter. The credentials provided by the device are verified using the configured [Credentials Service]({{< relref "#credentials-service-connection-configuration" >}}). Devices that fail to authenticate are not allowed to connect to the adapter. |
| `HONO_COAP_BIND_ADDRESS`<br>`--hono.coap.bindAddress` | no | `127.0.0.1` | The IP address of the network interface that the secure port should be bound to.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_COAP_CERT_PATH`<br>`--hono.coap.certPath` | no | - | The absolute path to the PEM file containing the certificate that the protocol adapter should use for authenticating to clients. This option must be used in conjunction with `HONO_COAP_KEY_PATH`.<br>Alternatively, the `HONO_COAP_KEY_STORE_PATH` option can be used to configure a key store containing both the key as well as the certificate. |
| `HONO_COAP_COAP_THREADS`<br>`--hono.coap.coapThreads` | no | 2 | The number of threads to use for processing CoAP message exchanges at the protocol layer. |
| `HONO_COAP_CONNECTOR_THREADS`<br>`--hono.coap.connectorThreads` | no | 2 | The number of threads to use for receiving/sending UDP packets. The connector will start the given number of threads for each direction, outbound (sending) as well as inbound (receiving). |
| `HONO_COAP_DTLS_THREADS`<br>`--hono.coap.dtlsThreads` | no | 32 | The number of threads to use for processing DTLS message exchanges at the connection layer. |
| `HONO_COAP_DTLS_RETRANSMISSION_TIMEOUT`<br>`--hono.coap.dtlsRetransmissionTimeout` | no | 2000 | The timeout in milliseconds for DTLS retransmissions. |
| `HONO_COAP_DEFAULTS_ENABLED`<br>`--hono.coap.defaultsEnabled` | no | `true` | If set to `true` the protocol adapter uses *default values* registered for a device to augment messages published by the device with missing information like a content type. In particular, the protocol adapter adds default values registered for the device as (application) properties with the same name to the AMQP 1.0 messages it sends downstream to the AMQP Messaging Network. |
| `HONO_COAP_EXCHANGE_LIFETIME`<br>`--hono.coap.exchangeLifetime` | no | 247000 | The exchange lifetime in milliseconds. According RFC 7252, that value is 247s. Such a large time requires also a huge amount of heap. That time includes a processing time of 100s and retransmissions of CON messages. Therefore a practical value could be much smaller.|
| `HONO_COAP_INSECURE_NETWORK_CONFIG`<br>`--hono.coap.insecureNetworkConfig` | no | - | The absolute path to a Californium properties file containing network configuration properties that should be used for the insecure CoAP port. If not set, Californium's default properties will be used. |
| `HONO_COAP_INSECURE_PORT`<br>`--hono.coap.insecurePort` | no | - | The insecure port the protocol adapter should listen on.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_COAP_INSECURE_PORT_BIND_ADDRESS`<br>`--hono.coap.insecurePortBindAddress` | no | `127.0.0.1` | The IP address of the network interface that the insecure port should be bound to.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_COAP_INSECURE_PORT_ENABLED`<br>`--hono.coap.insecurePortEnabled` | no | `false` | If set to `true` the protocol adapter will open an insecure port (not secured by TLS) using either the port number set via `HONO_COAP_INSECURE_PORT` or the default port number (`5683`) if not set explicitly.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_COAP_KEY_PATH`<br>`--hono.coap.keyPath` | no | - | The absolute path to the (PKCS8) PEM file containing the private key that the protocol adapter should use for authenticating to clients. This option must be used in conjunction with `HONO_COAP_CERT_PATH`. Alternatively, the `HONO_COAP_KEY_STORE_PATH` option can be used to configure a key store containing both the key as well as the certificate. |
| `HONO_COAP_KEY_STORE_PASSWORD`<br>`--hono.coap.keyStorePassword` | no | - | The password required to read the contents of the key store. |
| `HONO_COAP_KEY_STORE_PATH`<br>`--hono.coap.keyStorePath` | no | - | The absolute path to the Java key store containing the private key and certificate that the protocol adapter should use for authenticating to clients. Either this option or the `HONO_COAP_KEY_PATH` and `HONO_COAP_CERT_PATH` options need to be set in order to enable TLS secured connections with clients. The key store format can be either `JKS` or `PKCS12` indicated by a `.jks` or `.p12` file suffix respectively. |
| `HONO_COAP_MAX_CONNECTIONS`<br>`--hono.coap.maxConnections` | no | `0` | The maximum number of concurrent DTLS connections that the protocol adapter should accept. If set to `0`, the protocol adapter determines a reasonable value based on the available resources like memory and CPU. |
| `HONO_COAP_MAX_PAYLOAD_SIZE`<br>`--hono.coap.maxPayloadSize` | no | `2048` | The maximum allowed size of an incoming CoAP request's body in bytes. Requests with a larger body size are rejected with a 4.13 `Request entity too large` response. |
| `HONO_COAP_MESSAGE_OFFLOADING_ENABLED`<br>`--hono.coap.messageOffloadingEnabled` | no | true | Enables to clear payload and serialized messages kept for deduplication in order to reduce the heap consumption. Experimental. |
| `HONO_COAP_NETWORK_CONFIG`<br>`--hono.coap.networkConfig` | no | - | The absolute path to a Californium properties file containing network configuration properties that should be used for the secure CoAP port. If not set, Californium's default properties will be used. |
| `HONO_COAP_PORT`<br>`--hono.coap.port` | no | - | The secure port that the protocol adapter should listen on.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_COAP_TENANT_IDLE_TIMEOUT`<br>`--hono.coap.tenantIdleTimeout` | no | `0ms` | The duration after which the protocol adapter removes local state of the tenant (e.g. open AMQP links) with an amount and a unit, e.g. `2h` for 2 hours. See the [Spring Boot documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/spring-boot-features.html#boot-features-external-config-conversion-duration) for an explanation of the format. The value `0ms` disables the timeout. |
| `HONO_COAP_TIMEOUT_TO_ACK`<br>`--hono.coap.timeoutToAck` | no | 500 | Timeout in milliseconds to send an ACK for a CoAP CON request. If the response is available before that timeout, a more efficient piggybacked response is used. If the timeout is reached without having received a response, an empty ACK is sent back to the client and the response is sent in a separate CON once it becomes available. Special values: `-1`  means to always piggyback the response in an ACK and never send a separate CON; `0` means to always send an ACK immediately and include the response in a separate CON. |

The variables only need to be set if the default value needs to be changed.


## Port Configuration

The CoAP protocol adapter can be configured to listen for connections on

* a secure port only (default) or
* an insecure port only or
* both a secure and an insecure port (dual port configuration)

The CoAP protocol adapter will fail to start if none of the ports is configured properly.

### Secure Port Only

The protocol adapter opens a DTLS secured port if any of the following criteria are met

* The `HONO_COAP_KEY_STORE_PATH` and `HONO_COAP_KEY_STORE_PASSWORD` environment variables are set in order to load a key and certificate from a password protected key store or
* the `HONO_COAP_KEY_PATH` and `HONO_COAP_CERT_PATH` environment variables are set in order to load a key and certificate from two separate PEM files in PKCS8 format or
* the `HONO_COAP_PORT` environment variable is set to a valid port number.

When starting up, the protocol adapter will bind a DTLS secured UDP socket to the configured port. If the port is not set explicitly, the default CoAP secure port 5684 is used.

The `HONO_COAP_BIND_ADDRESS` variable can be used to specify the network interface that the port should be exposed on. By default the port is bound to the *loopback device* only,
i.e. the port will only be accessible from the local host. Setting this variable to `0.0.0.0` will let the port being bound to **all** network interfaces (be careful not to expose
the port unintentionally to the outside world).

### Insecure Port Only

The secure port will mostly be required for production scenarios. However, it might be desirable to expose a non-DTLS secured port instead, e.g. for testing purposes.
In any case, the non-secure port needs to be explicitly enabled by

* explicitly setting `HONO_COAP_AUTHENTICATION_REQUIRED` to `false` and either
  * explicitly setting `HONO_COAP_INSECURE_PORT` to a valid port number or
  * implicitly configuring the default insecure CoAP port (5683) by setting `HONO_COAP_INSECURE_PORT_ENABLED` to `true`.

The protocol adapter issues a warning on the console if `HONO_COAP_INSECURE_PORT` is set to the default secure CoAP port (5684).

The `HONO_COAP_INSECURE_PORT_BIND_ADDRESS` variable can be used to specify the network interface that the port should be exposed on. By default the port is bound to the *loopback device* only,
i.e. the port will only be accessible from the local host. This variable might be used to e.g. expose the non-DTLS secured port on a local interface only, thus providing easy access from within
the local network, while still requiring encrypted communication when accessed from the outside over public network infrastructure.

Setting this variable to `0.0.0.0` will let the port being bound to **all** network interfaces (be careful not to expose the port unintentionally to the outside world).

{{% note %}}
The insecure port will only be bound if the `HONO_COAP_AUTHENTICATION_REQUIRED` variable is set to `false` because CoAP authenticates clients (devices) as
part of the DTLS handshake. Thus, requiring devices to authenticate effectively rules out setting up a non-DTLS secured port.
{{% /note %}}


### Dual Port
 
The protocol adapter may be configured to open both a secure and a non-secure port at the same time simply by configuring both ports as described above. For this to work,
both ports must be configured to use different port numbers, otherwise startup will fail.

### Ephemeral Ports

Both the secure as well as the insecure port numbers may be explicitly set to `0`. The protocol adapter will then use arbitrary (unused) port numbers determined by the operating system during startup.

## Authentication

The CoAP protocol is UDP based and as such uses the DTLS protocol to secure the communication between a client (device) and a server (adapter). The CoAP adapter also uses the DTLS handshake
to prove its identity to devices and to authenticate the devices themselves. The DTLS protocol allows for different *cipher suites* to be
used for doing so. These suites mainly differ from each other in the type of secret being used for proving the participants' identity to each other.
One class of suites is based on a secret that is shared between the client and the server, very much like in a username/password based authentication scheme. This class of suites
is called *pre-shared key* or PSK-based and is very popular for use cases where the devices are very constrained regarding CPU and memory. Another class of cipher suites is based
on certificates which use asymmetric encryption for proving possession of the secret (the private key). The CoAP adapter supports cipher suites from both classes but only supports
*elliptic curve cryptography* (ECC) based keys for the latter class of cipher suites.

When enabling the secure port without configuring an ECC based key and certificate, the adapter will only use PSK based cipher suites for authentication.
When configuring an ECC based key and certificate, the adapter will also offer certificate based cipher suites to the client to use for authentication.

In any case the device's credentials need to be registered with the device registry. Please refer to the [Standard Credential Types]({{< relref "/api/credentials#standard-credential-types" >}})
and the [Device Registry Management API]({{< relref "/api/management" >}}) for additional information.
