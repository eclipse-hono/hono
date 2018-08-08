+++
title = "Secure Communication"
weight = 350
+++

The individual components of an Eclipse Hono&trade; installation, e.g. the protocol adapters, *Hono Messaging*, *Hono Auth* etc., and the clients attaching to Hono in order to send and receive data all communicate with each other using AMQP 1.0 over TCP. The Hono components and the clients will usually not be located on the same local network but will probably communicate over public networking infrastructure. For most use cases it is therefore desirable, if not necessary, to provide for confidentiality of the data being transferred between these components. This section describes how Hono supports confidentiality by means of *Transport Layer Security* (TLS) and how to configure it.
<!--more-->

# Enabling TLS

All of Hono's components can be configured to use TLS for establishing an encrypted communication channel with peers. When a client initiates a connection with a server, the TLS handshake protocol is used to negotiate parameters of a secure channel to be used for exchanging data. The most important of those parameters is a secret (symmetric) encryption key that is only known to the client and the server and which is used to transparently encrypt all data being sent over the connection as long as the connection exists. With each new connection, a new secret key is negotiated.

Using TLS in this way requires configuring the server component with a cryptographic *private/public key* pair and a *certificate* which *binds* an *identity claim* to the public key. It is out of scope of this document to describe the full process of creating such a key pair and acquiring a corresponding certificate. The `demo-certs` module already contains a set of keys and certificates to be used for evaluation and demonstration purposes. Throughout the rest of this section we will use these keys and certificates . Please refer to the `demo-certs/README.md` file for details regarding how to create your own keys and certificates.

Within a Hono installation the following communication channels can be secured with TLS:

1. Applications connecting to *Dispatch Router* - Client applications consuming e.g. Telemetry data from Hono connect to the Dispatch Router component. This connection can be secured by configuring the client and the Dispatch Router for TLS.
1. *Hono Messaging* connecting to *Dispatch Router* - Hono Messaging connects to the Dispatch Router in order to forward telemetry data and commands hence and forth between downstream components (client applications) and devices. This (internal) connection can be secured by configuring the Dispatch Router and Hono Messaging for TLS.
1. *Hono Messaging* connecting to *Auth Server* - Hono Messaging connects to the Auth Server in order to verify client credentials and determine the client's authorities. This (internal) connection can (should) be secured by configuring the Auth Server and Hono Messaging for TLS.
1. *Device Registry* connecting to *Auth Server* - The Device Registry connects to the Auth Server in order to verify client credentials and determine the client's authorities. This (internal) connection can (should) be secured by configuring the Auth Server and Device Registry for TLS.
1. *Protocol Adapter* to *Device Registry* - A protocol adapter connects to the Device Registry in order to retrieve assertions regarding the registration status of devices. This (internal) connection can be secured by configuring the protocol adapter and the Device Registry for TLS.
1. *Protocol Adapter* to *Hono server* - A protocol adapter connects to Hono to e.g. forward telemetry data received from devices to downstream consumers. This (internal) connection can be secured by configuring the protocol adapter and Hono server for TLS.
1. *Devices* connecting to a *Protocol Adapter* - Devices use TLS to both authenticate the protocol adapter and to establish an encrypted channel that provides integrity and privacy when transmitting data. Note that the specifics of if and how TLS can be used with a particular protocol adapter is specific to the transport protocol the adapter uses for communicating with the devices.

## Auth Server

The Auth Server supports the use of TLS for connections to clients. Please refer to the [Auth Server admin guide]({{< relref "auth-server-config.md" >}}) for details regarding the required configuration steps.

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
Please refer to the [Hono Messaging admin guide]({{< relref "hono-messaging-config.md" >}}) for details regarding the required configuration steps.

The `demo-certs/certs` folder contains the following demo keys and certificates to be used with Hono Messaging for that purpose.

| File                      | Description                                                      |
| :------------------------ | :--------------------------------------------------------------- |
| `auth-server-cert.pem` | The certificate of the Auth Server, used to verify the signatures of tokens issued by the Auth Server. |
| `hono-server-key.pem`  | The example private key for creating signatures. |
| `hono-server-cert.pem` | The example certificate asserting the server's identity. |
| `trusted-certs.pem`    | Trusted CA certificates to use for verifying signatures. |


## Device Registry

The Device Registry supports the use of TLS for connections to protocol adapters and the Auth Server.
Please refer to the [Device Registry admin guide]({{< relref "device-registry-config.md" >}}) for details regarding the required configuration steps.

The `demo-certs/certs` folder contains the following demo keys and certificates to be used with the Device Registry for that purpose.

| File                          | Description                                                      |
| :---------------------------- | :--------------------------------------------------------------- |
| `auth-server-cert.pem`     | The certificate of the Auth Server, used to verify the signatures of tokens issued by the Auth Server. |
| `device-registry-key.pem`  | The example private key for creating signatures. |
| `device-registry-cert.pem` | The example certificate asserting the server's identity. |
| `trusted-certs.pem`         | Trusted CA certificates to use for verifying signatures. |


## HTTP Adapter

The HTTP adapter supports the use of TLS for its connections to the Tenant service, the Device Registration service, the Credentials service and the Hono Messaging component. The adapter also supports the use of TLS for connections with devices. For this purpose, the adapter can be configured with a server certificate and private key.
Please refer to the [HTTP adapter admin guide]({{< relref "http-adapter-config.md" >}}) for details regarding the required configuration steps.

The `demo-certs/certs` folder contains the following demo keys and certificates to be used with the HTTP adapter for that purpose.

| File                      | Description                                                      |
| :------------------------ | :--------------------------------------------------------------- |
| `http-adapter-key.pem`  | The example private key for creating signatures. |
| `http-adapter-cert.pem` | The example certificate asserting the adapter's identity. |
| `trusted-certs.pem`     | Trusted CA certificates to use for verifying signatures. |


## MQTT Adapter

The MQTT adapter supports the use of TLS for its connections to the Tenant service, the Device Registration service, the Credentials service and the Hono Messaging component. The adapter also supports the use of TLS for connections with devices. For this purpose, the adapter can be configured with a server certificate and private key.
Please refer to the [MQTT adapter admin guide]({{< relref "mqtt-adapter-config.md" >}}) for details regarding the required configuration steps.

The `demo-certs/certs` folder contains the following demo keys and certificates to be used with the MQTT adapter for that purpose.

| File                      | Description                                                      |
| :------------------------ | :--------------------------------------------------------------- |
| `mqtt-adapter-key.pem`  | The example private key for creating signatures. |
| `mqtt-adapter-cert.pem` | The example certificate asserting the adapter's identity. |
| `trusted-certs.pem`     | Trusted CA certificates to use for verifying signatures. |

## Kura Adapter

The Kura adapter supports the use of TLS for its connections to the Tenant service, the Device Registration service, the Credentials service and the Hono Messaging component. The adapter also supports the use of TLS for connections with devices. For this purpose, the adapter can be configured with a server certificate and private key.
Please refer to the [Kura adapter admin guide]({{< relref "kura-adapter-config.md" >}}) for details regarding the required configuration steps.

The `demo-certs/certs` folder contains the following demo keys and certificates to be used with the Kura adapter for that purpose.

| File                      | Description                                                      |
| :------------------------ | :--------------------------------------------------------------- |
| `kura-adapter-key.pem`  | The example private key for creating signatures. |
| `kura-adapter-cert.pem` | The example certificate asserting the adapter's identity. |
| `trusted-certs.pem`     | Trusted CA certificates to use for verifying signatures. |



## Client Application

When the connection between an application client and Hono (i.e. the Dispatch Router) is supposed to be secured by TLS (which is a good idea), then the client application needs to be configured to trust the CA that signed the Dispatch Router's certificate chain. When the application uses the `org.eclipse.hono.client.HonoClientImpl` class from the `client` module, then this can be done by means of configuring the `org.eclipse.hono.connection.ConnectionFactoryImpl` with a trust store containing the CA's certificate. Please refer to the [Hono Client configuration guide]({{< relref "hono-client-configuration.md" >}}) for details regarding the configuration properties that need to be set.

The `demo-certs/certs` folder contains the following demo keys to be used with client applications for that purpose.

| File                      | Description                                                      |
| :------------------------ | :--------------------------------------------------------------- |
| `trusted-certs.pem`     | Trusted CA certificates to use for verifying signatures. |

# Using OpenSSL

Hono's individual services are implemented in Java and therefore, by default, use the SSL/TLS engine that comes with the Java Virtual Machine that the services are running on. In case of the Docker images provided by Hono this is the SSL engine of OpenJDK. While the standard SSL engine has the advantage of being a part of the JVM itself and thus being available on every operating system that the JVM is running on without further installation, it provides only limited performance and throughput when compared to native TLS implementations like [OpenSSL](https://www.openssl.org/).

In order to address this problem, the Netty networking library that is used in Hono's components can be configured to employ the OpenSSL instead of the JVM's SSL engine by means of Netty's [Forked Tomcat Native](http://netty.io/wiki/forked-tomcat-native.html) (tcnative) module.

The tcnative module comes in several flavors, corresponding to the way that the OpenSSL library has been linked in. The statically linked versions include a specific version of OpenSSL (or [BoringSSL](https://boringssl.googlesource.com/) for that matter) and is therefore most easy to use on supported platforms, regardless of whether another version of OpenSSL is already installed or not. In contrast, the dynamically linked variants depend on a particular version of OpenSSL being already installed on the operating system. Both approaches have their pros and cons and Hono therefore does not include tcnative in its Docker images by default, i.e. Hono's services will use the JVM's default SSL engine by default.

## Configuring Containers

When starting up any of Hono's Docker images as a container, the JVM will look for additional jar files to include in its classpath in the container's `/opt/hono/extensions` folder. Thus, using a specific variant of tcnative is just a matter of configuring the container to mount a volume or binding a host folder at that location and putting the desired variant of tcnative into the corresponding volume or host folder.r
Assuming that the Auth Server should be run with the statically linked, BoringSSL based tcnative variant, the following steps are necessary:

1. [Download tcnative](http://netty.io/wiki/forked-tomcat-native.html#how-to-download-netty-tcnative-boringssl-static) matching the platform architecture (*linux-x86_64*).
2. Put the jar file to a folder on the Docker host, e.g. `/tmp/tcnative`.
3. Start the Auth Server Docker image mounting the host folder:

    ```sh
    $> docker run --name hono-auth-server --mount type=bind,src=/tmp/tcnative,dst=/opt/hono/extensions,ro ... eclipse/hono-service-auth
    ```

Note that the command given above does not contain the environment variables and secrets that are usually required to configure the service properly.

When the Auth Server starts up, it will look for a working variant of tcnative on its classpath and (if found) use it for establishing TLS connections. The service's log file will indicate whether the JVM's default SSL engine or OpenSSL is used.

Using a Docker *volume* instead of a *bind mount* works the same way but requires the use of `volume` as the *type* of the `--mount` parameter. Please refer to the [Docker reference documentation](https://docs.docker.com/edge/engine/reference/commandline/service_create/#add-bind-mounts-volumes-or-memory-filesystems) for details.
