+++
title = "Secure Communication"
weight = 350
+++

The individual components of an Eclipse Hono&trade; installation, e.g. the *Hono server*, *Dispatch Router*, *MQTT adapter* etc, and the clients attaching to Hono in order to send and receive data all communicate with each other using AMQP 1.0 over TCP. The Hono components and the clients will usually not be located on the same local network but will probably communicate over public internet infrastructure. For most use cases it is therefore desirable, if not necessary, to provide for confidentiality of the data being transferred between these components. This section describes how Hono supports confidentiality by means of *Transport Layer Security* (TLS) and how to configure it.
<!--more-->

# Enabling TLS

All of Hono's components can be configured to use TLS for establishing an encrypted communication channel with peers. When a client initiates a connection with a server, the TLS handshake protocol is used to negotiate parameters of a secure channel to be used for exchanging data. The most important of those parameters is a secret (symmetric) encryption key that is only known to the client and the server and which is used to transparently encrypt all data being sent over the connection as long as the connection exists. With each new connection, a new secret key is negotiated.

Using TLS in this way requires configuring the server component with a cryptographic *private/public key* pair and a *certificate* which *binds* an *identity claim* to the public key. It is out of scope of this document to describe the full process of creating such a key pair and acquiring a corresponding certificate. The `demo-certs` module already contains a set of keys and certificates to be used for evaluation and demonstration purposes. Throughout the rest of this section we will use these keys and certificates . Please refer to the `demo-certs/README.md` file for details regarding how to create your own keys and certificates.

Within a Hono installation the following communication channels can be secured with TLS:

1. Consumers connecting to *Dispatch Router* - Client applications consuming e.g. Telemetry data from Hono connect to the Dispatch Router component. This connection can be secured by configuring the client and the Dispatch Router for TLS.
1. *Hono Messaging* connecting to *Dispatch Router* - Hono Messaging connects to the Dispatch Router in order to forward telemetry data and commands hence and forth between downstream components (client applications) and devices. This (internal) connection can be secured by configuring the Dispatch Router and Hono Messaging for TLS.
1. *Hono Messaging* connecting to *Auth Server* - Hono Messaging connects to the Auth Server in order to verify client credentials and determine the client's authorities. This (internal) connection can (should) be secured by configuring the Auth Server and Hono Messaging for TLS.
1. *Device Registry* connecting to *Auth Server* - The Device Registry connects to the Auth Server in order to verify client credentials and determine the client's authorities. This (internal) connection can (should) be secured by configuring the Auth Server and Device Registry for TLS.
1. *Protocol Adapter* to *Device Registry* - A protocol adapter connects to the Device Registry in order to retrieve assertions regarding the registration status of devices. This (internal) connection can be secured by configuring the protocol adapter and the Device Registry for TLS.
1. *Protocol Adapter* to *Hono server* - A protocol adapter connects to Hono to e.g. forward telemetry data received from devices to downstream consumers. This (internal) connection can be secured by configuring the protocol adapter and Hono server for TLS.
1. *Devices* connecting to a *Protocol Adapter* - Devices use TLS to both authenticate the protocol adapter and to establish an encrypted channel that provides integrity and privacy when transmitting data. Note that the specifics of if and how TLS can be used with a particular protocol adapter is specific to the transport protocol the adapter uses for communicating with the devices.

## Auth Server

The Auth Server supports the use of TLS for connections to clients. Please refer to the [Auth Server admin guide]({{< relref "admin-guide/auth-server-config.md" >}}) for details regarding the required configuration steps.

The `demo-certs/certs` folder includes the following demo keys and certificates to be used with the Auth Server for that purpose.

| File                      | Description                                                      |
| :------------------------ | :--------------------------------------------------------------- |
| `auth-server-key.pem`  | The example private key for creating signatures. |
| `auth-server-cert.pem` | The example certificate asserting the server's identity. |
| `trusted-certs.pem`    | Trusted CA certificates to use for verifying signatures. |


## Dispatch Router

The Dispatch Router reads its configuration from a file on startup (the default location is `/etc/qpid-dispatch/qdrouterd.conf`). Please refer to the [Dispatch Router documentation](https://qpid.apache.org/components/dispatch-router/index.html) for details regarding the configuration of TLS/SSL.

The `demo-certs/certs` folder includes the following demo keys and certificates to be used with the Dispatch Router for that purpose:

| File                    | Description                                                      |
| :---------------------- | :--------------------------------------------------------------- |
| `qdrouter-key.pem`    | The example private key for creating signatures. |
| `qdrouter-cert.pem`   | The example certificate asserting the server's identity. |
| `trusted-certs.pem`   | Trusted CA certificates to use for verifying signatures. |

## Hono Messaging

Hono Messaging supports the use of TLS for connections to protocol adapters, the Dispatch Router and the Auth Server.
Please refer to the [Hono Messaging admin guide]({{< relref "admin-guide/hono-messaging-config.md" >}}) for details regarding the required configuration steps.

The `demo-certs/certs` folder contains the following demo keys and certificates to be used with Hono Messaging for that purpose.

| File                      | Description                                                      |
| :------------------------ | :--------------------------------------------------------------- |
| `auth-server-cert.pem` | The certificate of the Auth Server, used to verify the signatures of tokens issued by the Auth Server. |
| `hono-server-key.pem`  | The example private key for creating signatures. |
| `hono-server-cert.pem` | The example certificate asserting the server's identity. |
| `trusted-certs.pem`    | Trusted CA certificates to use for verifying signatures. |


## Device Registry

The Device Registry supports the use of TLS for connections to protocol adapters and the Auth Server.
Please refer to the [Device Registry admin guide]({{< relref "admin-guide/device-registry-config.md" >}}) for details regarding the required configuration steps.

The `demo-certs/certs` folder contains the following demo keys and certificates to be used with the Device Registry for that purpose.

| File                          | Description                                                      |
| :---------------------------- | :--------------------------------------------------------------- |
| `auth-server-cert.pem`     | The certificate of the Auth Server, used to verify the signatures of tokens issued by the Auth Server. |
| `device-registry-key.pem`  | The example private key for creating signatures. |
| `device-registry-cert.pem` | The example certificate asserting the server's identity. |
| `trusted-certs.pem`         | Trusted CA certificates to use for verifying signatures. |


## HTTP Adapter

The HTTP adapter supports the use of TLS for its connections to the Tenant service, the Device Registration service, the Credentials service and the Hono Messaging component. The adapter also supports the use of TLS for connections with devices. For this purpose, the adapter can be configured with a server certificate and private key.
Please refer to the [HTTP adapter admin guide]({{< relref "admin-guide/http-adapter-config.md" >}}) for details regarding the required configuration steps.

The `demo-certs/certs` folder contains the following demo keys and certificates to be used with the HTTP adapter for that purpose.

| File                      | Description                                                      |
| :------------------------ | :--------------------------------------------------------------- |
| `http-adapter-key.pem`  | The example private key for creating signatures. |
| `http-adapter-cert.pem` | The example certificate asserting the adapter's identity. |
| `trusted-certs.pem`     | Trusted CA certificates to use for verifying signatures. |


## MQTT Adapter

The MQTT adapter supports the use of TLS for its connections to the Tenant service, the Device Registration service, the Credentials service and the Hono Messaging component. The adapter also supports the use of TLS for connections with devices. For this purpose, the adapter can be configured with a server certificate and private key.
Please refer to the [MQTT adapter admin guide]({{< relref "admin-guide/mqtt-adapter-config.md" >}}) for details regarding the required configuration steps.

The `demo-certs/certs` folder contains the following demo keys and certificates to be used with the MQTT adapter for that purpose.

| File                      | Description                                                      |
| :------------------------ | :--------------------------------------------------------------- |
| `mqtt-adapter-key.pem`  | The example private key for creating signatures. |
| `mqtt-adapter-cert.pem` | The example certificate asserting the adapter's identity. |
| `trusted-certs.pem`     | Trusted CA certificates to use for verifying signatures. |

## Kura Adapter

The Kura adapter supports the use of TLS for its connections to the Tenant service, the Device Registration service, the Credentials service and the Hono Messaging component. The adapter also supports the use of TLS for connections with devices. For this purpose, the adapter can be configured with a server certificate and private key.
Please refer to the [Kura adapter admin guide]({{< relref "admin-guide/kura-adapter-config.md" >}}) for details regarding the required configuration steps.

The `demo-certs/certs` folder contains the following demo keys and certificates to be used with the Kura adapter for that purpose.

| File                      | Description                                                      |
| :------------------------ | :--------------------------------------------------------------- |
| `kura-adapter-key.pem`  | The example private key for creating signatures. |
| `kura-adapter-cert.pem` | The example certificate asserting the adapter's identity. |
| `trusted-certs.pem`     | Trusted CA certificates to use for verifying signatures. |



## Client Application

When the connection between an application client and Hono (i.e. the Dispatch Router) is supposed to be secured by TLS (which is a good idea), then the client application needs to be configured to trust the CA that signed the Dispatch Router's certificate chain. When the application uses the `org.eclipse.hono.client.HonoClientImpl` class from the `client` module, then this can be done by means of configuring the `org.eclipse.hono.connection.ConnectionFactoryImpl` with a trust store containing the CA's certificate. Please refer to the [Hono Client configuration guide]({{< relref "admin-guide/hono-client-configuration.md" >}}) for details regarding the configuration properties that need to be set.

The `demo-certs/certs` folder contains the following demo keys to be used with client applications for that purpose.

| File                      | Description                                                      |
| :------------------------ | :--------------------------------------------------------------- |
| `trusted-certs.pem`     | Trusted CA certificates to use for verifying signatures. |
