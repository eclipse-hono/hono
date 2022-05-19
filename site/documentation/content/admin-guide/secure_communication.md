+++
title = "Secure Communication"
weight = 350
+++

The individual components of an Eclipse Hono&trade; installation, e.g. the protocol adapters, messaging infrastructure,
*Hono Auth* etc. and the clients attaching to Hono in order to send and receive data all communicate with each other
over IP based networking infrastructure. The Hono components and the clients will usually not be located on the same
local network but will probably communicate over public networking infrastructure. For most use cases it is therefore
desirable, if not necessary, to provide for confidentiality of the data being transferred between these components.

This section describes how Hono supports confidentiality by means of *Transport Layer Security* (TLS)
and how to configure it.
<!--more-->

## Enabling TLS

All of Hono's components can be configured to use TLS for establishing an encrypted communication channel with peers.
When a client initiates a connection with a server, the TLS handshake protocol is used to negotiate parameters of a
secure channel to be used for exchanging data. The most important of those parameters is a secret (symmetric) encryption
key that is only known to the client and the server and which is used to transparently encrypt all data being sent over
the connection as long as the connection exists. With each new connection, a new secret key is negotiated.

Using TLS in this way requires configuring the server component with a cryptographic *private/public key* pair and a
*certificate* which *binds* an *identity claim* to the public key. It is out of scope of this document to describe the
full process of creating such a key pair and acquiring a corresponding certificate. The `demo-certs` module already
contains a set of keys and certificates to be used for evaluation and demonstration purposes. Throughout the rest of
this section we will use these keys and certificates. Please refer to the `demo-certs/README.md` file for details
regarding how to create your own keys and certificates.

The following sections provide information regarding the configuration of TLS for Hono's components.

### Auth Server

The Auth Server supports the use of TLS for incoming connections to its Authentication API endpoint. Please refer to the
[Auth Server admin guide]({{< relref "auth-server-config.md" >}}) for details regarding the required configuration steps.

The `demo-certs/certs` folder includes the following demo keys and certificates to be used with the Auth Server for that purpose.

| File                | Description                                                      |
| :------------------ | :--------------------------------------------------------------- |
| `auth-server-key.pem`  | The example private key for creating signatures. |
| `auth-server-cert.pem` | The example certificate asserting the server's identity. |
| `trusted-certs.pem`    | Trusted CA certificates to use for verifying signatures. |

### Kafka Broker

Please refer to the [Apache Kafka documentation](https://kafka.apache.org/documentation/#configuration) for
details regarding the configuration of TLS.

The `demo-certs/certs` folder includes the following demo keys and certificates to be used with the Kafka broker for
that purpose:

| File               | Description                                                      |
| :----------------- | :--------------------------------------------------------------- |
| `trusted-certs.pem`   | Trusted CA certificates to use for verifying signatures. |

### Dispatch Router

Please refer to the [Dispatch Router documentation](https://qpid.apache.org/components/dispatch-router/index.html) for
details regarding the configuration of TLS.

The `demo-certs/certs` folder includes the following demo keys and certificates to be used with the Dispatch Router for
that purpose:

| File               | Description                                                      |
| :----------------- | :--------------------------------------------------------------- |
| `qdrouter-key.pem`    | The example private key for creating signatures. |
| `qdrouter-cert.pem`   | The example certificate asserting the server's identity. |
| `trusted-certs.pem`   | Trusted CA certificates to use for verifying signatures. |

### Command Router


The Command Router supports the use of TLS for incoming connections to its Command Router API endpoint. It also supports
using TLS for outgoing connections to the Authentication, Tenant and Device Registration service endpoints, the
messaging infrastructure and the data grid used for storing routing information.

Please refer to the [Command Router admin guide]({{< relref "command-router-config.md" >}}) for
details regarding the required configuration steps.

The `demo-certs/certs` folder contains the following demo keys and certificates to be used with the file based Device
Registry for that purpose.

| File                   | Description                                                      |
| :--------------------- | :--------------------------------------------------------------- |
| `auth-server-cert.pem`    | The certificate of the Auth Server, used to verify the signatures of tokens issued by the Auth Server. |
| `command-router-key.pem`  | The example private key for creating signatures. |
| `command-router-cert.pem`  | The example certificate asserting the server's identity. |
| `trusted-certs.pem`       | Trusted CA certificates to use for verifying signatures. |


### JDBC Based Device Registry

The JDBC based Device Registry supports the use of TLS for incoming connections to its Tenant, Device Registration,
Credentials and Device Registry Management API endpoints. It also supports using TLS for outgoing connections to the
Authentication service endpoint and the database server.

Please refer to the [JDBC based Device Registry admin guide]({{< relref "jdbc-device-registry-config.md" >}}) for
details regarding the required configuration steps.

The `demo-certs/certs` folder contains the following demo keys and certificates to be used with the file based Device
Registry for that purpose.

| File                   | Description                                                      |
| :--------------------- | :--------------------------------------------------------------- |
| `auth-server-cert.pem`    | The certificate of the Auth Server, used to verify the signatures of tokens issued by the Auth Server. |
| `device-registry-key.pem`  | The example private key for creating signatures. |
| `device-registry-cert.pem` | The example certificate asserting the server's identity. |
| `trusted-certs.pem`       | Trusted CA certificates to use for verifying signatures. |


### MongoDB Based Device Registry

The MongoDB based Device Registry supports the use of TLS for incoming connections to its Tenant, Device Registration,
Credentials and Device Registry Management API endpoints. It also supports using TLS for outgoing connections to the
Authentication service endpoint and the Mongo database server.

Please refer to the [MongoDB based Device Registry admin guide]({{< relref "mongodb-device-registry-config.md" >}}) for
details regarding the required configuration steps.

The `demo-certs/certs` folder contains the following demo keys and certificates to be used with the MongoDB based Device
Registry for that purpose.

| File                   | Description                                                      |
| :--------------------- | :--------------------------------------------------------------- |
| `auth-server-cert.pem`    | The certificate of the Auth Server, used to verify the signatures of tokens issued by the Auth Server. |
| `device-registry-key.pem`  | The example private key for creating signatures. |
| `device-registry-cert.pem` | The example certificate asserting the server's identity. |
| `trusted-certs.pem`       | Trusted CA certificates to use for verifying signatures. |

### AMQP Adapter

The adapter supports the use of TLS for incoming connections from devices. It also supports the use of TLS for
outgoing connections to the Tenant, Device Registration, Credentials and Command Router service endpoints and the
messaging infrastructure.

For this purpose, the adapter can be configured with a server certificate and private key.
Please refer to the [AMQP adapter admin guide]({{< relref "amqp-adapter-config.md" >}}) for details regarding the required
configuration steps.

The `demo-certs/certs` folder contains the following demo keys and certificates to be used with the adapter for that purpose.

| File                | Description                                                      |
| :------------------ | :--------------------------------------------------------------- |
| `amqp-adapter-key.pem`  | The example private key for creating signatures. |
| `amqp-adapter-cert.pem` | The example certificate asserting the adapter's identity. |
| `trusted-certs.pem`    | Trusted CA certificates to use for verifying signatures. |


### CoAP Adapter

The adapter supports the use of *Datagram TLS* (DTLS) for incoming connections from devices. It also supports the use
of TLS for outgoing connections to the Tenant, Device Registration, Credentials and Command Router service endpoints and
the messaging infrastructure.

For this purpose, the adapter can be configured with a server certificate and private key.
Please refer to the [CoAP adapter admin guide]({{< relref "coap-adapter-config.md" >}}) for details regarding the required
configuration steps.

The `demo-certs/certs` folder contains the following demo keys and certificates to be used with the adapter for that purpose.

| File                | Description                                                      |
| :------------------ | :--------------------------------------------------------------- |
| `coap-adapter-key.pem`  | The example private key for creating signatures. |
| `coap-adapter-cert.pem` | The example certificate asserting the adapter's identity. |
| `trusted-certs.pem`    | Trusted CA certificates to use for verifying signatures. |


### HTTP Adapter

The adapter supports the use of TLS for incoming connections from devices. It also supports the use of TLS for
outgoing connections to the Tenant, Device Registration, Credentials and Command Router service endpoints and the
messaging infrastructure.

For this purpose, the adapter can be configured with a server certificate and private key.
Please refer to the [HTTP adapter admin guide]({{< relref "http-adapter-config.md" >}}) for details regarding the
required configuration steps.

The `demo-certs/certs` folder contains the following demo keys and certificates to be used with the adapter for that purpose.

| File                      | Description                                                      |
| :------------------------ | :--------------------------------------------------------------- |
| `http-adapter-key.pem`  | The example private key for creating signatures. |
| `http-adapter-cert.pem` | The example certificate asserting the adapter's identity. |
| `trusted-certs.pem`     | Trusted CA certificates to use for verifying signatures. |


### MQTT Adapter

The adapter supports the use of TLS for incoming connections from devices. It also supports the use of TLS for
outgoing connections to the Tenant, Device Registration, Credentials and Command Router service endpoints and the
messaging infrastructure.

For this purpose, the adapter can be configured with a server certificate and private key.
Please refer to the [MQTT adapter admin guide]({{< relref "mqtt-adapter-config.md" >}}) for details regarding the
required configuration steps.

The `demo-certs/certs` folder contains the following demo keys and certificates to be used with the adapter for that purpose.

| File                      | Description                                                      |
| :------------------------ | :--------------------------------------------------------------- |
| `mqtt-adapter-key.pem`  | The example private key for creating signatures. |
| `mqtt-adapter-cert.pem` | The example certificate asserting the adapter's identity. |
| `trusted-certs.pem`     | Trusted CA certificates to use for verifying signatures. |


### Business Application

When the connection between a business application and Hono's messaging infrastructure (i.e. the Kafka broker or the
Dispatch Router) is supposed to be secured by TLS (which is a good idea), then the application needs to be
configured to trust the CA that signed the messaging infrastructure's certificate.

Applications can use Hono's `org.eclipse.hono.application.client.kafka.impl.KafkaApplicationClientImpl` and/or
`org.eclipse.hono.application.client.amqp.ProtonBasedApplicationClient` classes to interact with Hono's messaging infrastructure.
The clients need to be configured with a trust store that contains the messaging infrastructure's CA certificate.

Please refer to the [Hono Client Configuration Guide]({{< relref "hono-client-configuration" >}}) and
[Hono Kafka Client Configuration Guide]({{< relref "hono-kafka-client-configuration" >}}) for details
regarding the configuration of the clients.

The `demo-certs/certs` folder contains the following demo keys to be used with client applications for that purpose.

| File              | Description                                              |
| :---------------- | :------------------------------------------------------- |
| `trusted-certs.pem`  | Trusted CA certificates to use for verifying signatures. |

## Using OpenSSL

Hono's individual services are implemented in Java and therefore, by default, use the SSL/TLS engine that comes with the
Java Virtual Machine that the services are running on. In case of the Docker images provided by Hono this is the SSL
engine of OpenJDK. While the standard SSL engine has the advantage of being a part of the JVM itself and thus being
available on every operating system that the JVM is running on without further installation, it provides only limited
performance and throughput when compared to native TLS implementations like [OpenSSL](https://www.openssl.org/).

In order to address this problem, the Netty networking library that is used in Hono's components can be configured to
employ the OpenSSL instead of the JVM's SSL engine by means of Netty's
[Forked Tomcat Native](https://netty.io/wiki/forked-tomcat-native.html) (tcnative) module.

The tcnative module comes in several flavors, corresponding to the way that the OpenSSL library has been linked in.
The statically linked versions include a specific version of OpenSSL (or [BoringSSL](https://boringssl.googlesource.com/)
for that matter) and is therefore most easy to use on supported platforms, regardless of whether another version of
OpenSSL is already installed or not. In contrast, the dynamically linked variants depend on a particular version of
OpenSSL being already installed on the operating system. Both approaches have their pros and cons and Hono therefore
does not include tcnative in its Docker images by default, i.e. Hono's services will use the JVM's default SSL engine
by default.

### Configuring Containers

When starting up any of Hono's Docker images as a container, the JVM will look for additional jar files to include in
its classpath in the container's `/opt/hono/extensions` folder. Thus, using a specific variant of tcnative is just a
matter of configuring the container to mount a volume or binding a host folder at that location and putting the desired
variant of tcnative into the corresponding volume or host folder.r
Assuming that the Auth Server should be run with the statically linked, BoringSSL based tcnative variant, the following
steps are necessary:

1. [Download tcnative](https://netty.io/wiki/forked-tomcat-native.html#how-to-download-netty-tcnative-boringssl-static)
   matching the platform architecture (*linux-x86_64*).
2. Put the jar file to a folder on the Docker host, e.g. `/tmp/tcnative`.
3. Start the Auth Server Docker image mounting the host folder:

```sh
docker run --name hono-auth-server --mount type=bind,src=/tmp/tcnative,dst=/opt/hono/extensions,ro ... eclipse/hono-service-auth
```

Note that the command given above does not contain the environment variables and secrets that are usually required to
configure the service properly.

When the Auth Server starts up, it will look for a working variant of tcnative on its classpath and (if found) use it
for establishing TLS connections. The service's log file will indicate whether the JVM's default SSL engine or OpenSSL
is used.

Using a Docker *volume* instead of a *bind mount* works the same way but requires the use of `volume` as the *type* of
the `--mount` parameter. Please refer to the
[Docker reference documentation](https://docs.docker.com/engine/reference/commandline/service_create/#add-bind-mounts-volumes-or-memory-filesystems)
for details.

{{% notice warning %}}
Configuring containers to use OpenSSL the way described above does **not** work with container images based on
native executables.
{{% /notice %}}

## Server Name Indication (SNI)

[Server Name Indication](https://tools.ietf.org/html/rfc6066#section-3) can be used to indicate to a server the host
name that the client wants to connect to as part of the TLS handshake. This is useful in order to be able to host
multiple *virtual* servers on a single network address. In particular, SNI allows server components to select a server
certificate that matches the domain name indicated by the client using SNI.

Hono's protocol adapters support *virtual* servers by means of SNI as described above. Devices can then connect to a
protocol adapter using any one of the configured *virtual* domain names.

The following steps a re necessary in order to configure the protocol adapters with multiple *virtual* servers:

1. Create Server Certificate(s)

   When a device establishes a connection to one of Hono's protocol adapters using one of its *virtual* domain names,
   then it includes the domain name in its TLS *hello* message by means of the SNI extension. The server can then use
   this information to determine the matching server certificate and corresponding private key that is required to
   perform the TLS handshake.

   It is therefore necessary to create a private key and certificate for each *virtual* server to be hosted.
   The *virtual* server's domain name needs to be added to the certificate's *Subject Alternative Name* (SAN) list in
   order for Hono to be able to determine the key/certificate pair to use for the TLS handshake with the device.
   Please refer to the [vert.x SNI guide](https://vertx.io/docs/vertx-core/java/#_server_name_indication_sni) for
   details on how this works under the hood.

   Hono's protocol adapters then need to be configured with the server certificates and keys. In order to do so,
   the certificates and corresponding private keys need to be added to a *key store*. Hono supports the *JKS* and
   *PKCS12* key store formats for that purpose.
   Once the key store has been created, Hono's protocol adapters need to be configured with the path to the key store
   by means of the adapters' `KEY_STORE_PATH` configuration variable. Please refer to the
   [protocol adapter admin guides]({{< relref "admin-guide" >}}) for details on how to configure the key store path.

2. Enable SNI for Hono's Protocol Adapters

   Hono's protocol adapters can be configured to support SNI by means of the `SNI` configuration variable. Please refer to the
   [protocol adapter admin guides]({{< relref "admin-guide" >}}) for details on how to set this variable.

3. Verify Configuration

   The setup can be verified by means of the command line tools that are part of [OpenSSL](https://www.openssl.org/).
   Assuming that the MQTT protocol adapter's IP address is `10.100.84.23`, its secure endpoint is bound to port 31884 and it has
   been configured with a certificate using domain name *my-hono.eclipse.org*, then the following command can be used to test
   if a TLS secured connection with the adapter using that virtual host name can be established successfully:

   ```sh 
   openssl s_client -connect 10.100.84.23:31884 -servername my-hono.eclipse.org
   ```
