+++
title = "Secure Communication"
weight = 210
+++

The individual components of an Eclipse Hono&trade; installation, e.g. the *Hono server*, *Dispatch Router*, *MQTT adapter* etc, and the clients attaching to Hono in order to send and receive data all communicate with each other using AMQP 1.0 over TCP. The Hono components and the clients will usually not be located on the same local network but will probably communicate over public internet infrastructure. For most use cases it is therefore desirable, if not necessary, to provide for confidentiality of the data being transferred between these components. This section describes how Hono supports confidentiality by means of *Transport Layer Security* (TLS) and how to configure it.
<!--more-->

# Enabling TLS

All of Hono's components can be configured to use TLS for establishing an encrypted communication channel with peers. When a client initiates a connection with a server, the TLS handshake protocol is used to negotiate parameters of a secure channel to be used for exchanging data. The most important of those parameters is a secret (symmetric) encryption key that is only known to the client and the server and which is used to transparently encrypt all data being sent over the connection as long as the connection exists. With each new connection, a new secret key is negotiated.

Using TLS in this way requires configuring the server component with a cryptographic *private/public key* pair and a *certificate* which *binds* an *identity claim* to the public key. It is out of scope of this document to describe the full process of creating such a key pair and acquiring a corresponding certificate. The `demo-certs` module already contains a set of keys and certificates to be used for evaluation and demonstration purposes. Throughout the rest of this section we will use these keys and certificates . Please refer to the `demo-certs/README.md` file for details regarding how to create your own keys and certificates.

Within a Hono installation the following communication channels can be secured with TLS:

1. Consumers connecting to *Dispatch Router* - Client applications consuming e.g. Telemetry data from Hono connect to the Dispatch Router component. This connection can be secured by configuring the client and the Dispatch Router for TLS.
1. *Hono server* connecting to *Dispatch Router* - The Hono server connects to the Dispatch Router in order to forward telemetry data and commands hence and forth between downstream components (client applications) and devices. This (internal) connection can be secured by configuring the Dispatch Router and Hono Server for TLS.
1. *Hono server* connecting to *Auth Server* - The Hono server connects to the Auth Server in order to verify client credentials and determine the client's authorities. This (internal) connection can (should) be secured by configuring the Auth Server and Hono Server for TLS.
1. *Device Registry* connecting to *Auth Server* - The Device Registry connects to the Auth Server in order to verify client credentials and determine the client's authorities. This (internal) connection can (should) be secured by configuring the Auth Server and Device Registry for TLS.
1. *Protocol Adapter* to *Device Registry* - A protocol adapter connects to the Device Registry in order to retrieve assertions regarding the registration status of devices. This (internal) connection can be secured by configuring the protocol adapter and the Device Registry for TLS.
1. *Protocol Adapter* to *Hono server* - A protocol adapter connects to Hono to e.g. forward telemetry data received from devices to downstream consumers. This (internal) connection can be secured by configuring the protocol adapter and Hono server for TLS.

The certificates and keys for all the Hono components are contained in the `demo-certs/certs` folder.

## Auth Server

Support for TLS needs to be explicitly configured by means of command line options or environment variables.
The Auth Server needs to be configured with a private key and a certificate for that purpose.

The Auth Server Docker image (`eclipsehono/hono-service-auth`) includes the following demo keys and certificates from the `demo-certs/certs` folder.

| File                      | Description                                                      |
| :------------------------ | :--------------------------------------------------------------- |
| `auth-server-key.pem`  | The example private key for creating signatures. |
| `auth-server-cert.pem` | The example certificate asserting the server's identity. |
| `trusted-certs.pem`    | Trusted CA certificates to use for verifying signatures. |

### Configuration of Identity

The table below provides an overview of the options/environment variables relevant for configuring the server's identity:

| Environment Variable<br>Command Line Option | Mandatory | Default | Description |
| :------------------------------------------ | :-------- | :------ | :---------- |
| `HONO_AUTH_KEY_FILE`<br>`--hono.auth.keyFile`     | no | - | The absolute path to the file containing the private key. The file must contain the key in PKCS8 PEM format. |
| `HONO_AUTH_CERT_FILE`<br>`--hono.auth.certFile`   | no | - | The absolute path to the file containing the certificate chain to use for authenticating to clients. The file must contain the certificate chain in PEM format. |

Note that the private key is not protected by a password. You should therefore make sure that the key file can only be read by the user that the server process is running under.

### Configuration of Trust Store for authenticating Clients

When the Auth Server is supposed to support authentication of clients by means of client certificates, then the Auth Server also needs to be configured with a set of *trusted certificates* which are used to validate the certificate chain presented by clients in order to authenticate.

| Environment Variable<br>Command Line Option | Mandatory | Default | Description |
| :------------------------------------------ | :-------- | :------ | :---------- |
| `HONO_AUTH_TRUST_STORE_PATH`<br>`--hono.auth.trustStorePath` | no | - | The absolute path to the key store containing the trusted CA certificates. The key store format can be either `JKS`, `PKCS12` or `PEM` indicated by a `.jks`, `.p12` or `.pem` file suffix. |
| `HONO_AUTH_TRUST_STORE_PASSWORD`<br>`--hono.auth.trustStorePassword` | no | - | The password required to read the contents of the trust store. |

## Dispatch Router

The *Dispatch Router* reads its configuration from a file on startup (the default location is `/etc/qpid-dispatch/qdrouterd.conf`). Please refer to the [Dispatch Router documentation](https://qpid.apache.org/components/dispatch-router/index.html) for details regarding the configuration of TLS/SSL.

The Dispatch Router Docker image (`eclipsehono/dispatch-router`) that is used with Hono's example stack includes the following keys and certificates from `demo-certs/certs` to secure its listeners:

| File                    | Description                                                      |
| :---------------------- | :--------------------------------------------------------------- |
| `qdrouter-key.pem`    | The example private key for creating signatures. |
| `qdrouter-cert.pem`   | The example certificate asserting the server's identity. |
| `trusted-certs.pem`   | Trusted CA certificates to use for verifying signatures. |

You can use the `example/pom.xml` file as a blueprint for building your own Dispatch Router image containing your custom keys, certificate and configuration file(s).

## Hono Server

Support for TLS needs to be explicitly configured by means of command line options or environment variables.
Hono needs to be configured with a private key and a certificate for that purpose.

The Hono Server Docker image (`eclipsehono/hono-server`) includes the following demo keys and certificates from the `demo-certs/certs` folder.

| File                      | Description                                                      |
| :------------------------ | :--------------------------------------------------------------- |
| `auth-server-cert.pem` | The certificate of the Auth Server, used to verify the signatures of tokens issued by the Auth Server. |
| `hono-server-key.pem`  | The example private key for creating signatures. |
| `hono-server-cert.pem` | The example certificate asserting the server's identity. |
| `trusted-certs.pem`    | Trusted CA certificates to use for verifying signatures. |

### Configuration of Identity

The table below provides an overview of the options/environment variables relevant for configuring the server's identity:

| Environment Variable<br>Command Line Option | Mandatory | Default | Description |
| :------------------------------------------ | :-------- | :------ | :---------- |
| `HONO_SERVER_KEY_FILE`<br>`--hono.server.keyFile`     | no | - | The absolute path to the file containing the private key for authenticating to clients. The file must contain the key in PKCS8 PEM format. |
| `HONO_SERVER_CERT_FILE`<br>`--hono.server.certFile`   | no | - | The absolute path to the file containing the certificate chain to use for authenticating to clients. The file must contain the certificate chain in PEM format. |
| `HONO_DOWNSTREAM_KEY_FILE`<br>`--hono.downstream.keyFile`     | no | - | The absolute path to the file containing the private key for authenticating to the AMQP 1.0 Messaging Network (e.g. the Dispatch Router). The file must contain the key in PKCS8 PEM format. |
| `HONO_DOWNSTREAM_CERT_FILE`<br>`--hono.downstream.certFile`   | no | - | The absolute path to the file containing the certificate chain to use for authenticating to the AMQP 1.0 Messaging Network (e.g. the Dispatch Router). The file must contain the certificate chain in PEM format. |

Note that the private keys are not protected by a password. You should therefore make sure that the key file can only be read by the user that the Hono process is running under.

### Configuration of Trust Stores for authenticating Peers

When the connection between the Hono server and the downstream AMQP 1.0 Messaging Network (e.g. the Dispatch Router) is supposed to be secured by TLS as well (which is a good idea), then the Hono server also needs to be configured with a set of *trusted certificates* which are used to validate the peer's certificate chain it provides to Hono during connection establishment in order to authenticate to Hono.

| Environment Variable<br>Command Line Option | Mandatory | Default | Description |
| :------------------------------------------ | :-------- | :------ | :---------- |
| `HONO_DOWNSTREAM_TRUST_STORE_PATH`<br>`--hono.downstream.trustStorePath` | no | - | The absolute path to the key store containing the trusted CA certificates. The key store format can be either `JKS`, `PKCS12` or `PEM` indicated by a `.jks`, `.p12` or `.pem` file suffix. |
| `HONO_DOWNSTRAM_TRUST_STORE_PASSWORD`<br>`--hono.downstream.trustStorePassword` | no | - | The password required to read the contents of the trust store. |

When the connection between the Hono server and the Auth Server is supposed to be secured by TLS as well (which is a good idea), then the Hono server also needs to be configured with a set of *trusted certificates* which are used to validate the Auth Server's certificate chain it provides to Hono during connection establishment in order to authenticate to Hono.

| Environment Variable<br>Command Line Option | Mandatory | Default | Description |
| :------------------------------------------ | :-------- | :------ | :---------- |
| `HONO_AUTH_TRUST_STORE_PATH`<br>`--hono.auth.trustStorePath` | no | - | The absolute path to the key store containing the trusted CA certificates. The key store format can be either `JKS`, `PKCS12` or `PEM` indicated by a `.jks`, `.p12` or `.pem` file suffix. |
| `HONO_AUTH_TRUST_STORE_PASSWORD`<br>`--hono.auth.trustStorePassword` | no | - | The password required to read the contents of the trust store. |

## Device Registry

Support for TLS needs to be explicitly configured by means of command line options or environment variables.
The Device Registry needs to be configured with a private key and a certificate for that purpose.

The Device Registry Docker image (`eclipsehono/hono-service-device-registry`) includes the following demo keys and certificates from the `demo-certs/certs` folder.

| File                          | Description                                                      |
| :---------------------------- | :--------------------------------------------------------------- |
| `auth-server-cert.pem`     | The certificate of the Auth Server, used to verify the signatures of tokens issued by the Auth Server. |
| `device-registry-key.pem`  | The example private key for creating signatures. |
| `device-registry-cert.pem` | The example certificate asserting the server's identity. |
| `trusted-certs.pem`         | Trusted CA certificates to use for verifying signatures. |

### Configuration of Identity

The table below provides an overview of the options/environment variables relevant for configuring the server's identity:

| Environment Variable<br>Command Line Option | Mandatory | Default | Description |
| :------------------------------------------ | :-------- | :------ | :---------- |
| `HONO_DEVICE_REGISTRY_KEY_FILE`<br>`--hono.device.registry.keyFile`     | no | - | The absolute path to the file containing the private key for authenticating to clients. The file must contain the key in PKCS8 PEM format. |
| `HONO_DEVICE_REGISTRY_CERT_FILE`<br>`--hono.device.registry.certFile`   | no | - | The absolute path to the file containing the certificate chain to use for authenticating to clients. The file must contain the certificate chain in PEM format. |

Note that the private keys are not protected by a password. You should therefore make sure that the key file can only be read by the user that the Hono process is running under.

### Configuration of Trust Stores for authenticating Peers

When the connection between the Device Registry and the Auth Server is supposed to be secured by TLS as well (which is a good idea), then the Hono server also needs to be configured with a set of *trusted certificates* which are used to validate the Auth Server's certificate chain it provides to the Device Registry during connection establishment in order to authenticate to Hono.

| Environment Variable<br>Command Line Option | Mandatory | Default | Description |
| :------------------------------------------ | :-------- | :------ | :---------- |
| `HONO_AUTH_TRUST_STORE_PATH`<br>`--hono.auth.trustStorePath` | no | - | The absolute path to the key store containing the trusted CA certificates. The key store format can be either `JKS`, `PKCS12` or `PEM` indicated by a `.jks`, `.p12` or `.pem` file suffix. |
| `HONO_AUTH_TRUST_STORE_PASSWORD`<br>`--hono.auth.trustStorePassword` | no | - | The password required to read the contents of the trust store. |

## REST Adapter

Support for TLS needs to be explicitly configured by means of command line options or environment variables.
The REST adapter needs to be configured with a private key and a certificate for that purpose.

The REST Adapter Docker image (`eclipsehono/hono-adapter-rest-vertx`) includes the following demo keys and certificates from the `demo-certs/certs` folder.

| File                      | Description                                                      |
| :------------------------ | :--------------------------------------------------------------- |
| `rest-adapter-key.pem`  | The example private key for creating signatures. |
| `rest-adapter-cert.pem` | The example certificate asserting the adapter's identity. |
| `trusted-certs.pem`     | Trusted CA certificates to use for verifying signatures. |

### Configuration of Identity

The table below provides an overview of the options/environment variables relevant for configuring the adapter's identity:

| Environment Variable<br>Command Line Option                            | Mandatory | Default | Description |
| :--------------------------------------------------------------------- | :-------- | :------ | :---------- |
| `HONO_HTTP_KEY_FILE`<br>`--hono.http.keyFile`                     | no | - | The absolute path to the file containing the private key for authenticating to devices. The file must contain the key in PKCS8 PEM format. |
| `HONO_HTTP_CERT_FILE`<br>`--hono.http.certFile`                   | no | - | The absolute path to the file containing the certificate chain to use for authenticating to devices. The file must contain the certificate chain in PEM format. |

Note that the private key is not protected by a password. You should therefore make sure that the key files can only be read by the user that the REST adapter process is running under.

### Configuration of Trust Stores for authenticating Peers

When the connection between the adapter and the Hono server is supposed to be secured by TLS (which is a good idea), then the adapter needs to be configured with a set of *trusted certificates* which are used to validate the Hono server's certificate chain it presents to the adapter during connection establishment.

| Environment Variable<br>Command Line Option | Mandatory | Default | Description |
| :------------------------------------------ | :-------- | :------ | :---------- |
| `HONO_CLIENT_TRUST_STORE_PATH`<br>`--hono.client.trustStorePath` | no | - | The absolute path to the key store containing the trusted CA certificates.. The key store format can be either `JKS`, `PKCS12` or `PEM` indicated by a `.jks`, `.p12` or `.pem` file suffix. |
| `HONO_CLIENT_TRUST_STORE_PASSWORD`<br>`--hono.client.trustStorePassword` | no | - | The password required to read the contents of the trust store. |

When the connection between the adapter and the Device Registry is supposed to be secured by TLS (which is a good idea), then the adapter needs to be configured with a set of *trusted certificates* which are used to validate the Device Registry's certificate chain it presents to the adapter during connection establishment.

| Environment Variable<br>Command Line Option                                                    | Mandatory | Default | Description |
| :--------------------------------------------------------------------------------------------- | :-------- | :------ | :---------- |
| `HONO_REGISTRATION_TRUST_STORE_PATH`<br>`--hono.registration.trustStorePath`          | no | - | The absolute path to the key store containing the trusted CA certificates.. The key store format can be either `JKS`, `PKCS12` or `PEM` indicated by a `.jks`, `.p12` or `.pem` file suffix. |
| `HONO_REGISTRATION_TRUST_STORE_PASSWORD`<br>`--hono.registration.trustStorePassword` | no | - | The password required to read the contents of the trust store. |

The following properties are used to define the *trusted certificates* to use for verifying the certificate chain presented by a device if it is configured to use a client certificate to authenticate to the adapter.

| Environment Variable<br>Command Line Option                                  | Mandatory | Default | Description |
| :--------------------------------------------------------------------------- | :-------- | :------ | :---------- |
| `HONO_HTTP_TRUST_STORE_PATH`<br>`--hono.http.trustStorePath`          | no | - | The absolute path to the key store containing the trusted CA certificates. The key store format can be either `JKS`, `PKCS12` or `PEM` indicated by a `.jks`, `.p12` or `.pem` file suffix. |
| `HONO_HTTP_TRUST_STORE_PASSWORD`<br>`--hono.http.trustStorePassword` | no | - | The password required to read the contents of the trust store. |

## MQTT Adapter

Support for TLS needs to be explicitly configured by means of command line options or environment variables.
The MQTT adapter needs to be configured with a private key and a certificate for that purpose.

The MQTT Adapter Docker image (`eclipsehono/hono-adapter-mqtt-vertx`) includes the following demo keys and certificates from the `demo-certs/certs` folder.

| File                      | Description                                                      |
| :------------------------ | :--------------------------------------------------------------- |
| `mqtt-adapter-key.pem`  | The example private key for creating signatures. |
| `mqtt-adapter-cert.pem` | The example certificate asserting the adapter's identity. |
| `trusted-certs.pem`     | Trusted CA certificates to use for verifying signatures. |


### Configuration of Identity

The table below provides an overview of the options/environment variables relevant for configuring the adapter's identity:

| Environment Variable<br>Command Line Option                            | Mandatory | Default | Description |
| :--------------------------------------------------------------------- | :-------- | :------ | :---------- |
| `HONO_MQTT_KEY_FILE`<br>`--hono.mqtt.keyFile`                     | no | - | The absolute path to the file containing the private key for authenticating to devices. The file must contain the key in PKCS8 PEM format. |
| `HONO_MQTT_CERT_FILE`<br>`--hono.mqtt.certFile`                   | no | - | The absolute path to the file containing the certificate chain to use for authenticating to devices. The file must contain the certificate chain in PEM format. |

Note that the private keys are not protected by a password. You should therefore make sure that the key files can only be read by the user that the MQTT adapter process is running under.

### Configuration of Trust Stores for authenticating Peers

When the connection between the adapter and the Hono server is supposed to be secured by TLS (which is a good idea), then the adapter needs to be configured with a set of *trusted certificates* which are used to validate the Hono server's certificate chain that it presents to the adapter during connection establishment.

| Environment Variable<br>Command Line Option                                       | Mandatory | Default | Description |
| :-------------------------------------------------------------------------------- | :-------- | :------ | :---------- |
| `HONO_CLIENT_TRUST_STORE_PATH`<br>`--hono.client.trustStorePath`          | no | - | The absolute path to the trust store containing the CA certificates the adapter uses for authenticating the Hono server. The key store format can be either `JKS`, `PKCS12` or `PEM` indicated by a `.jks`, `.p12` or `.pem` file suffix. |
| `HONO_CLIENT_TRUST_STORE_PASSWORD`<br>`--hono.client.trustStorePassword` | no | - | The password required to read the contents of the trust store. |

When the connection between the adapter and the Device Registry is supposed to be secured by TLS (which is a good idea), then the adapter needs to be configured with a set of *trusted certificates* which are used to validate the Device Registry's certificate chain it presents to the adapter during connection establishment.

| Environment Variable<br>Command Line Option                                                    | Mandatory | Default | Description |
| :--------------------------------------------------------------------------------------------- | :-------- | :------ | :---------- |
| `HONO_REGISTRATION_TRUST_STORE_PATH`<br>`--hono.registration.trustStorePath`          | no | - | The absolute path to the key store containing the trusted CA certificates.. The key store format can be either `JKS`, `PKCS12` or `PEM` indicated by a `.jks`, `.p12` or `.pem` file suffix. |
| `HONO_REGISTRATION_TRUST_STORE_PASSWORD`<br>`--hono.registration.trustStorePassword` | no | - | The password required to read the contents of the trust store. |

The following properties are used to define the *trusted certificates* to use for verifying the certificate chain presented by a device if it is configured to use a client certificate to authenticate to the adapter.

| Environment Variable<br>Command Line Option                                  | Mandatory | Default | Description |
| :--------------------------------------------------------------------------- | :-------- | :------ | :---------- |
| `HONO_MQTT_TRUST_STORE_PATH`<br>`--hono.mqtt.trustStorePath`          | no | - | The absolute path to the key store containing the trusted CA certificates. The key store format can be either `JKS`, `PKCS12` or `PEM` indicated by a `.jks`, `.p12` or `.pem` file suffix. |
| `HONO_MQTT_TRUST_STORE_PASSWORD`<br>`--hono.mqtt.trustStorePassword` | no | - | The password required to read the contents of the trust store. |


## Client Application

When the connection between an application client and Hono (i.e. the Dispatch Router) is supposed to be secured by TLS (which is a good idea), then the client application needs to be configured to trust the CA that signed the Dispatch Router's certificate chain. When the application uses the `org.eclipse.hono.client.HonoClientImpl` class from the `client` module, then this can be done by means of configuring the `org.eclipse.hono.connection.ConnectionFactoryImpl` with a trust store containing the CA's certificate. Please refer to that class' JavaDocs for more information.
