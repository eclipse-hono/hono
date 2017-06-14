+++
title = "Secure Communication"
weight = 210
+++

The individual components of an Eclipse Hono&trade; installation, e.g. the *Hono server*, *Dispatch Router*, *MQTT adapter* etc, and the clients attaching to Hono in order to send and receive data all communicate with each other using AMQP 1.0 over TCP. The Hono server components and the clients will usually not be located on the same local network but will probably communicate over public internet infrastructure. For most use cases it is therefore desirable, if not necessary, to provide for confidentiality of the data being transferred between these components. This section describes how Hono supports confidentiality by means of *Transport Layer Security* (TLS) and how to configure it.
<!--more-->

# Enabling TLS

All of Hono's components can be configured to use TLS for establishing an encrypted communication channel with peers. When a client initiates a connection with a server, the TLS handshake protocol is used to negotiate parameters of a secure channel to be used for exchanging data. The most important of those parameters is a secret encryption key that is only known to the client and the server and which is used to transparently encrypt all data being sent over the connection as long as the connection exists. With each new connection, a new secret key is negotiated.

Using TLS in this way requires configuring the server component with a cryptographic *private/public key* pair and a *certificate* which *binds* an *identity claim* to the public key. It is out of scope of this document to describe the full process of creating such a key pair and acquiring a corresponding certificate. The `demo-certs` folder of the `config` module already contains a set of keys and certificates to be used for evaluation and demonstration purposes. Throughout the rest of this section we will use the keys and certificates from that folder as examples. Please refer to the readme.md file in the `demo-certs` folder for details regarding how to create your own keys and certificates.

Within a Hono installation the following communication channels can be secured with TLS:

1. Consumers to Dispatch Router - Client applications consuming e.g. Telemetry data from Hono connect to the Dispatch Router component. This connection can be secured by configuring the Dispatch Router for TLS.
2. Hono server to Dispatch Router - The Hono server connects to the Dispatch Router in order to forward telemetry data and commands hence and forth between downstream components (client applications) and devices. This (internal) connection can be secured by configuring the Dispatch Router and Hono Server for TLS.
3. Protocol Adapter to Hono server - A protocol adapter connects to Hono to e.g. forward telemetry data received from devices to downstream consumers. This (internal) connection can be secured by configuring the protocol adapter and Hono server for TLS.

The certificates and keys for all the Hono components are provided by the `demo-certs` module in the `demo-certs/certs` folder.

## Dispatch Router

The Dispatch Router by default tries to read its configuration from `/etc/qpid-dispatch/qdrouterd.conf`. The default configuration included with the Dispatch Router Docker image can be replaced by means of *mounting a volume* during startup of the Docker image. Please refer to the [Docker documentation for details regarding how to create and use volumes](https://docs.docker.com/engine/tutorials/dockervolumes/).

Regarding the TLS configuration, the needed demo keys and certificates are available in the `/etc/hono/certs` folder of the Docker image.

| File                                     | Description                                                      |
| :--------------------------------------- | :--------------------------------------------------------------- |
| `/etc/hono/certs/qdrouter-key.pem`    | An example private key to use for enabling TLS on the Dispatch Router. |
| `/etc/hono/certs/qdrouter-cert.pem`   | A certificate containing the public key corresponding to the example private key. |
| `/etc/hono/certs/trusted-certs.pem`   | A list of the CA certificates used to sign the example keys and certificates. |

Please refer to the [Dispatch Router documentation](https://qpid.apache.org/components/dispatch-router/index.html) for details regarding the configuration of TLS/SSL.

## Hono Server

Support for TLS needs to be explicitly configured by means of command line options or environment variables.
Hono needs to be configured with a private key and a certificate for that purpose.

The Hono Server Docker image provides following demo keys and certificates in the `/etc/hono/certs` folder.

| File                                     | Description                                                      |
| :--------------------------------------- | :--------------------------------------------------------------- |
| `/etc/hono/certs/trusted-certs.pem`   | A list of the CA certificates used to sign the example keys and certificates. |
| `/etc/hono/certs/honoKeyStore.p12`    | A key store containing an example private key and matching certificate for enabling TLS on the Hono server. |

### Configuration via Key Store

The table below provides an overview of the options/environment variables relevant for configuring TLS support in the Hono server:

| Environment Variable<br>Command Line Option | Mandatory | Default | Description |
| :------------------------------------------ | :-------- | :------ | :---------- |
| `HONO_SERVER_KEY_STORE_PATH`<br>`--hono.server.keyStorePath` | no | - | The absolute path to the Java key store containing the key and certificate chain the Hono server should use for authenticating to clients. The key store format can be either `JKS` or `PKCS12` indicated by a `.jks` or `.p12` file suffix. |
| `HONO_SERVER_KEY_STORE_PASSWORD`<br>`--hono.server.keyStorePassword` | no | - | The password required to read the contents of the key store. |

### Configuration via PEM Files

Alternatively, Hono can be configured with separate files containing the private key and the certificate chain respectively.

| Environment Variable<br>Command Line Option | Mandatory | Default | Description |
| :------------------------------------------ | :-------- | :------ | :---------- |
| `HONO_SERVER_KEY_FILE`<br>`--hono.server.keyFile`     | no | - | The absolute path to the file containing the private key. The file must contain the key in PEM format. |
| `HONO_SERVER_CERT_FILE`<br>`--hono.server.certFile`   | no | - | The absolute path to the file containing the certificate chain to use for authenticating to clients. The file must contain the certificate chain in PEM format. |

Note that in this case the private key is not protected by a password. you should therefore make sure that the key file can only be read by the user that the Hono process is running under.

### Configuration of Trust Store for Accessing the Dispatch Router

When the connection between the Hono server and the Dispatch Router is supposed to be secured by TLS as well (which is a good idea), then the Hono server also needs to be configured with a set of *trusted certificates* which are used to validate the Dispatch Router's certificate chain it provides to Hono during connection establishment in order to authenticate to Hono.

| Environment Variable<br>Command Line Option | Mandatory | Default | Description |
| :------------------------------------------ | :-------- | :------ | :---------- |
| `HONO_DOWNSTREAM_TRUST_STORE_PATH`<br>`--hono.downstream.trustStorePath` | no | - | The absolute path to the trust store containing the CA certificates the Hono server uses for authenticating the Dispatch Router. The key store format can be either `JKS`, `PKCS12` or `PEM` indicated by a `.jks`, `.p12` or `.pem` file suffix. |
| `HONO_DOWNSTRAM_TRUST_STORE_PASSWORD`<br>`--hono.downstream.trustStorePassword` | no | - | The password required to read the contents of the trust store. |

## REST Adapter

When the connection between the REST adapter and the Hono server is supposed to be secured by TLS (which is a good idea), then the REST adapter needs to be configured with a set of *trusted certificates* which are used to validate the Hono server's certificate chain it provides to the REST adapter during connection establishment in order to authenticate to the adapter.

| Environment Variable<br>Command Line Option | Mandatory | Default | Description |
| :------------------------------------------ | :-------- | :------ | :---------- |
| `HONO_CLIENT_TRUST_STORE_PATH`<br>`--hono.client.trustStorePath` | no | - | The absolute path to the trust store containing the CA certificates the adapter uses for authenticating the Hono server. The key store format can be either `JKS`, `PKCS12` or `PEM` indicated by a `.jks`, `.p12` or `.pem` file suffix. |
| `HONO_CLIENT_TRUST_STORE_PASSWORD`<br>`--hono.client.trustStorePassword` | no | - | The password required to read the contents of the trust store. |

The REST Adapter Docker image provides following demo keys and certificates in the `/etc/hono/certs` folder.

| File                                     | Description                                                      |
| :--------------------------------------- | :--------------------------------------------------------------- |
| `/etc/hono/certs/trusted-certs.pem`   | A list of the CA certificates used to sign the example keys and certificates. |
| `/etc/hono/certs/restKeyStore.p12`    | A key store containing an example private key and matching certificate for enabling TLS on the REST adapter. |

## MQTT Adapter

When the connection between the MQTT adapter and the Hono server is supposed to be secured by TLS (which is a good idea), then the MQTT adapter needs to be configured with a set of *trusted certificates* which are used to validate the Hono server's certificate chain it provides to the REST adapter during connection establishment in order to authenticate to the adapter.

| Environment Variable<br>Command Line Option | Mandatory | Default | Description |
| :------------------------------------------ | :-------- | :------ | :---------- |
| `HONO_CLIENT_TRUST_STORE_PATH`<br>`--hono.client.trustStorePath` | no | - | The absolute path to the trust store containing the CA certificates the adapter uses for authenticating the Hono server. The key store format can be either `JKS`, `PKCS12` or `PEM` indicated by a `.jks`, `.p12` or `.pem` file suffix. |
| `HONO_CLIENT_TRUST_STORE_PASSWORD`<br>`--hono.client.trustStorePassword` | no | - | The password required to read the contents of the trust store. |

The MQTT Adapter Docker image provides following demo keys and certificates in the `/etc/hono/certs` folder.

| File                                     | Description                                                      |
| :--------------------------------------- | :--------------------------------------------------------------- |
| `/etc/hono/certs/trusted-certs.pem`   | A list of the CA certificates used to sign the example keys and certificates. |
| `/etc/hono/certs/mqttKeyStore.p12`    | A key store containing an example private key and matching certificate for enabling TLS on the MQTT adapter. |

## Client Application

When the connection between an application client and Hono (i.e. the Dispatch Router) is supposed to be secured by TLS (which is a good idea), then the client application needs to be configured to trust the CA that signed the Dispatch Router's certificate chain. When the application uses the `org.eclipse.hono.client.HonoClientImpl` class from the `client` module, then this can be done by means of configuring the `org.eclipse.hono.connection.ConnectionFactoryImpl` with a trust store containing the CA's certificate. Please refer to that class' JavaDocs for more information.
