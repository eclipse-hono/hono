+++
title = "CoAP Adapter Configuration"
weight = 329
+++

The CoAP protocol adapter exposes CoAP based endpoints for Eclipse Hono&trade;'s south bound Telemetry, Event and
Command & Control APIs.
<!--more-->

The adapter is implemented as a Quarkus application using [Eclipse Californium&trade;](https://www.eclipse.org/californium/)
for implementing the CoAP protocol handling. It can be run either directly from the command line or by means of starting
the corresponding [Docker image](https://hub.docker.com/r/eclipse/hono-adapter-coap/) created from it.

{{% notice info %}}
The CoAP adapter had originally been implemented as a Spring Boot application. That variant has been removed in Hono
2.0.0.
{{% /notice %}}

## Service Configuration

The following table provides an overview of the configuration variables and corresponding system properties for
configuring the CoAP adapter.

| OS Environment Variable<br>Java System Property | Mandatory | Default | Description |
| :---------------------------------------------- | :-------: | :------ | :---------- |
| `HONO_APP_MAXINSTANCES`<br>`hono.app.maxInstances` | no | *#CPU cores* | The number of verticle instances to deploy. If not set, one verticle per processor core is deployed. |
| `HONO_COAP_AUTHENTICATIONREQUIRED`<br>`hono.coap.authenticationRequired` | no | `true` | If set to `true` the protocol adapter requires devices to authenticate when connecting to the adapter. The credentials provided by the device are verified using the configured [Credentials Service]({{< relref "/admin-guide/common-config#credentials-service-connection-configuration" >}}). Devices that fail to authenticate are not allowed to connect to the adapter. |
| `HONO_COAP_BINDADDRESS`<br>`hono.coap.bindAddress` | no | `127.0.0.1` | The IP address of the network interface that the secure port should be bound to.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_COAP_BLOCKWISESTATUSLIFETIME`<br>`hono.coap.blockwiseStatusLifetime` | no | 300000 | The blockwise status lifetime in milliseconds. If no new blockwise request is received within that lifetime, blockwise status will be removed and the related resources are freed. |
| `HONO_COAP_CERTPATH`<br>`hono.coap.certPath` | no | - | The absolute path to the PEM file containing the certificate that the protocol adapter should use for authenticating to clients. This option must be used in conjunction with `HONO_COAP_KEYPATH`.<br>Alternatively, the `HONO_COAP_KEYSTOREPATH` option can be used to configure a key store containing both the key as well as the certificate. Note that the CoAP adapter supports ECDSA based keys only. |
| `HONO_COAP_COAPTHREADS`<br>`hono.coap.coapThreads` | no | 2 | The number of threads to use for processing CoAP message exchanges at the protocol layer. |
| `HONO_COAP_CONNECTORTHREADS`<br>`hono.coap.connectorThreads` | no | 2 | The number of threads to use for receiving/sending UDP packets. The connector will start the given number of threads for each direction, outbound (sending) as well as inbound (receiving). |
| `HONO_COAP_DTLSTHREADS`<br>`hono.coap.dtlsThreads` | no | 32 | The number of threads to use for processing DTLS message exchanges at the connection layer. |
| `HONO_COAP_DTLSRETRANSMISSIONTIMEOUT`<br>`hono.coap.dtlsRetransmissionTimeout` | no | 2000 | The timeout in milliseconds for DTLS retransmissions. |
| `HONO_COAP_DEFAULTSENABLED`<br>`hono.coap.defaultsEnabled` | no | `true` | If set to `true` the protocol adapter uses *default values* registered for a device and/or its tenant to augment messages published by the device with missing information like a content type. In particular, the protocol adapter adds such default values as Kafka record headers or AMQP 1.0 message (application) properties before the message is sent downstream. |
| `HONO_COAP_EXCHANGELIFETIME`<br>`hono.coap.exchangeLifetime` | no | 247000 | The exchange lifetime in milliseconds. According RFC 7252, that value is 247s. Such a large time requires also a huge amount of heap. That time includes a processing time of 100s and retransmissions of CON messages. Therefore a practical value could be much smaller. |
| `HONO_COAP_GCHEAPPERCENTAGE`<br>`hono.coap.gcHeapPercentage` | no | `25` | The share of heap memory that should not be used by the live-data set but should be left to be used by the garbage collector. This property is used for determining the maximum number of (device) connections that the adapter should support. The value may be adapted to better reflect the characteristics of the type of garbage collector being used by the JVM and the total amount of memory available to the JVM. |
| `HONO_COAP_INSECURENETWORKCONFIG`<br>`hono.coap.insecureNetworkConfig` | no | - | The absolute path to a Californium properties file containing network configuration properties that should be used for the insecure CoAP port. If not set, Californium's default properties will be used. If the file is not available, not readable or malformed, the adapter will fail to start. |
| `HONO_COAP_INSECUREPORT`<br>`hono.coap.insecurePort` | no | - | The insecure port the protocol adapter should listen on.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_COAP_INSECUREPORTBINDADDRESS`<br>`hono.coap.insecurePortBindAddress` | no | `127.0.0.1` | The IP address of the network interface that the insecure port should be bound to.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_COAP_INSECUREPORTENABLED`<br>`hono.coap.insecurePortEnabled` | no | `false` | If set to `true` the protocol adapter will open an insecure port (not secured by TLS) using either the port number set via `HONO_COAP_INSECUREPORT` or the default port number (`5683`) if not set explicitly.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_COAP_KEYPATH`<br>`hono.coap.keyPath` | no | - | The absolute path to the (PKCS8) PEM file containing the private key that the protocol adapter should use for authenticating to clients. This option must be used in conjunction with `HONO_COAP_CERTPATH`. Alternatively, the `HONO_COAP_KEYSTOREPATH` option can be used to configure a key store containing both the key as well as the certificate. Note that the CoAP adapter supports ECDSA based keys only. |
| `HONO_COAP_KEYSTOREPASSWORD`<br>`hono.coap.keyStorePassword` | no | - | The password required to read the contents of the key store. |
| `HONO_COAP_KEYSTOREPATH`<br>`hono.coap.keyStorePath` | no | - | The absolute path to the Java key store containing the private key and certificate that the protocol adapter should use for authenticating to clients. Either this option or the `HONO_COAP_KEYPATH` and `HONO_COAP_CERTPATH` options need to be set in order to enable TLS secured connections with clients. The key store format can be either `JKS` or `PKCS12` indicated by a `.jks` or `.p12` file suffix respectively. Note that the CoAP adapter supports ECDSA based keys only. |
| `HONO_COAP_MAXCONNECTIONS`<br>`hono.coap.maxConnections` | no | `0` | The maximum number of concurrent DTLS connections that the protocol adapter should accept. If set to `0`, the protocol adapter determines a reasonable value based on the available resources like memory and CPU. |
| `HONO_COAP_MAXPAYLOADSIZE`<br>`hono.coap.maxPayloadSize` | no | `2048` | The maximum allowed size of an incoming CoAP request's body in bytes. Requests with a larger body size are rejected with a 4.13 `Request entity too large` response. |
| `HONO_COAP_MESSAGEOFFLOADINGENABLED`<br>`hono.coap.messageOffloadingEnabled` | no | true | Enables to clear payload and serialized messages kept for deduplication in order to reduce the heap consumption. Experimental. |
| `HONO_COAP_NETWORKCONFIG`<br>`hono.coap.networkConfig` | no | - | The absolute path to a Californium properties file containing network configuration properties that should be used for the insecure and secure CoAP port. If not set, Californium's default properties will be used. Values may be overwritten using the specific `HONO_COAP_INSECURENETWORKCONFIG` or `HONO_COAP_SECURENETWORKCONFIG`. If the file is not available, not readable or malformed, the adapter will fail to start. |
| `HONO_COAP_PORT`<br>`hono.coap.port` | no | - | The secure port that the protocol adapter should listen on.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_COAP_SECURENETWORKCONFIG`<br>`hono.coap.secureNetworkConfig` | no | - | The absolute path to a Californium properties file containing network configuration properties that should be used for the secure CoAP port. If not set, Californium's default properties will be used. If the file is not available, not readable or malformed, the adapter will fail to start. |
| `HONO_COAP_TENANTIDLETIMEOUT`<br>`hono.coap.tenantIdleTimeout` | no | `PT0S` | The duration after which the protocol adapter removes local state of the tenant (e.g. open AMQP links) with an amount and a unit, e.g. `2h` for 2 hours. See the `java.time.Duration` [documentation](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/Duration.html#parse(java.lang.CharSequence)) for an explanation of the format. The leading `PT` can be omitted if only specifying hours, minutes or seconds. The value `0s` (or `PT0S`) disables the timeout. |
| `HONO_COAP_TIMEOUTTOACK`<br>`hono.coap.timeoutToAck` | no | 500 | Timeout in milliseconds to send an ACK for a CoAP CON request. If the response is available before that timeout, a more efficient piggybacked response is used. If the timeout is reached without having received a response, an empty ACK is sent back to the client and the response is sent in a separate CON once it becomes available. Special values: `-1`  means to always piggyback the response in an ACK and never send a separate CON; `0` means to always send an ACK immediately and include the response in a separate CON. |

The variables only need to be set if the default value needs to be changed.

In addition to the options described in the table above, this component supports the following standard configuration
options:

* [Common Java VM Options]({{< relref "common-config.md/#java-vm-options" >}})
* [Common vert.x Options]({{< relref "common-config.md/#vertx-options" >}})
* [Common Protocol Adapter Options]({{< relref "common-config.md/#protocol-adapter-options" >}})
* [Monitoring Options]({{< relref "monitoring-tracing-config.md" >}})

## Port Configuration

The CoAP protocol adapter can be configured to listen for connections on

* a secure port only (default) or
* an insecure port only or
* both a secure and an insecure port (dual port configuration)

The CoAP protocol adapter will fail to start if none of the ports is configured properly.

### Secure Port Only

The protocol adapter opens a DTLS secured port if any of the following criteria are met

* The `HONO_COAP_KEYSTOREPATH` and `HONO_COAP_KEYSTOREPASSWORD` environment variables are set in order to load a key and certificate from a password protected key store or
* the `HONO_COAP_KEYPATH` and `HONO_COAP_CERTPATH` environment variables are set in order to load a key and certificate from two separate PEM files in PKCS8 format or
* the `HONO_COAP_PORT` environment variable is set to a valid port number.

When starting up, the protocol adapter will bind a DTLS secured UDP socket to the configured port. If the port is not set explicitly, the default CoAP secure port 5684 is used.

The `HONO_COAP_BINDADDRESS` variable can be used to specify the network interface that the port should be exposed on. By default the port is bound to the *loopback device* only,
i.e. the port will only be accessible from the local host. Setting this variable to `0.0.0.0` will let the port being bound to **all** network interfaces (be careful not to expose
the port unintentionally to the outside world).

### Insecure Port Only

The secure port will mostly be required for production scenarios. However, it might be desirable to expose a non-DTLS secured port instead, e.g. for testing purposes.
In any case, the non-secure port needs to be explicitly enabled by

* explicitly setting `HONO_COAP_AUTHENTICATIONREQUIRED` to `false` and either
  * explicitly setting `HONO_COAP_INSECUREPORT` to a valid port number or
  * implicitly configuring the default insecure CoAP port (5683) by setting `HONO_COAP_INSECUREPORTENABLED` to `true`.

The protocol adapter issues a warning on the console if `HONO_COAP_INSECUREPORT` is set to the default secure CoAP port (5684).

The `HONO_COAP_INSECUREPORTBINDADDRESS` variable can be used to specify the network interface that the port should be exposed on. By default the port is bound to the *loopback device* only,
i.e. the port will only be accessible from the local host. This variable might be used to e.g. expose the non-DTLS secured port on a local interface only, thus providing easy access from within
the local network, while still requiring encrypted communication when accessed from the outside over public network infrastructure.

Setting this variable to `0.0.0.0` will let the port being bound to **all** network interfaces (be careful not to expose the port unintentionally to the outside world).

{{% notice tip %}}
The insecure port will only be bound if the `HONO_COAP_AUTHENTICATIONREQUIRED` variable is set to `false` because the CoAP
adapter authenticates clients (devices) as part of the DTLS handshake. Thus, requiring devices to authenticate
effectively rules out setting up a non-DTLS secured port.
{{% /notice %}}


### Dual Port
 
The protocol adapter may be configured to open both a secure and a non-secure port at the same time simply by
configuring both ports as described above. For this to work, both ports must be configured to use different port numbers,
otherwise startup will fail.

### Ephemeral Ports

Both the secure as well as the insecure port numbers may be explicitly set to `0`. The protocol adapter will then use
arbitrary (unused) port numbers determined by the operating system during startup.

## Authentication

The CoAP protocol is UDP based and as such uses the DTLS protocol to secure the communication between a client (device)
and a server (adapter). The CoAP adapter also uses the DTLS handshake to prove its identity to devices and to authenticate
the devices themselves. The DTLS protocol allows for different *cipher suites* to be used for doing so. These suites
mainly differ from each other in the type of secret being used for proving the participants' identity to each other.

One class of suites is based on a secret that is shared between the client and the server, very much like in a
username/password based authentication scheme. This class of suites is called *pre-shared key* or PSK-based and is
very popular for use cases where the devices are very constrained regarding CPU and memory. Another class of cipher
suites is based on certificates which use asymmetric encryption for proving possession of the secret (the private key).

The CoAP adapter supports cipher suites from both classes but only supports cipher suites from the latter class which use
ECDSA algorithm for authentication. In particular, this means that the client and/or server need to use
*elliptic curve cryptography* (ECC) based keys instead of RSA based ones.

When enabling the secure port without configuring an ECC based key and certificate, the adapter will only use PSK based
cipher suites for authentication. When configuring an ECC based key and certificate, the adapter will also offer
certificate based cipher suites to the client to use for authentication.

In any case the device's credentials need to be registered with the device registry. Please refer to the
[Standard Credential Types]({{< relref "/api/credentials#standard-credential-types" >}}) and the
[Device Registry Management API]({{< relref "/api/management" >}}) for additional information.
