+++
title = "Auth Server Configuration"
weight = 305
+++

The Auth Server component exposes a service endpoint implementing Eclipse Hono&trade;'s [Authentication]({{< ref "/api/Authentication-API.md" >}}) API. Other services use this component for authenticating clients and retrieving a token asserting the client's identity and corresponding authorities.
<!--more-->

This component serves as a default implementation of the *Authentication* API only. On startup, it reads in all identities and their authorities from a JSON file from the file system. All data is then kept in memory and there are no remote service APIs for managing the identities and their authorities.

The Auth Server is implemented as a Spring Boot application. It can be run either directly from the command line or by means of starting the corresponding [Docker image](https://hub.docker.com/r/eclipse/hono-service-auth/) created from it.

## Service Configuration

In addition to the following options, this component supports the options described in [Common Configuration]({{< relref "common-config.md" >}}).

The server can be configured by means of environment variables or corresponding command line options.
The following table provides an overview of the configuration variables and corresponding command line options that the server supports:

| Environment Variable<br>Command Line Option | Mandatory | Default | Description                                                             |
| :------------------------------------------ | :-------: | :------ | :-----------------------------------------------------------------------|
| `HONO_APP_MAX_INSTANCES`<br>`--hono.app.maxInstances` | no | *#CPU cores* | The number of verticle instances to deploy. If not set, one verticle per processor core is deployed. |
| `HONO_AUTH_AMQP_BIND_ADDRESS`<br>`--hono.auth.amqp.bindAddress` | no | `127.0.0.1` | The IP address of the network interface that the secure port should be bound to.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_AUTH_AMQP_CERT_PATH`<br>`--hono.auth.amqp.certPath` | no | - | The absolute path to the PEM file containing the certificate that the server should use for authenticating to clients. This option must be used in conjunction with `HONO_AUTH_AMQP_KEY_PATH`.<br>Alternatively, the `HONO_AUTH_AMQP_KEY_STORE_PATH` option can be used to configure a key store containing both the key as well as the certificate. |
| `HONO_AUTH_AMQP_INSECURE_PORT`<br>`--hono.auth.amqp.insecurePort` | no | - | The insecure port the server should listen on.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_AUTH_AMQP_INSECURE_PORT_BIND_ADDRESS`<br>`--hono.auth.amqp.insecurePortBindAddress` | no | `127.0.0.1` | The IP address of the network interface that the insecure port should be bound to.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_AUTH_AMQP_INSECURE_PORT_ENABLED`<br>`--hono.auth.amqp.insecurePortEnabled` | no | `false` | If set to `true` the server will open an insecure port (not secured by TLS) using either the port number set via `HONO_AUTH_AMQP_INSECURE_PORT` or the default AMQP port number (`5672`) if not set explicitly.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_AUTH_AMQP_KEY_PATH`<br>`--hono.auth.amqp.keyPath` | no | - | The absolute path to the (PKCS8) PEM file containing the private key that the server should use for authenticating to clients. Note that the private key is not protected by a password. You should therefore make sure that the key file can only be read by the user that the server process is running under. This option must be used in conjunction with `HONO_AUTH_CERT_PATH`.<br>Alternatively, the `HONO_AUTH_KEY_STORE_PATH` option can be used to configure a key store containing both the key as well as the certificate. |
| `HONO_AUTH_AMQP_KEY_STORE_PASSWORD`<br>`--hono.auth.amqp.keyStorePassword` | no | - | The password required to read the contents of the key store. |
| `HONO_AUTH_AMQP_KEY_STORE_PATH`<br>`--hono.auth.amqp.keyStorePath` | no | - | The absolute path to the Java key store containing the private key and certificate that the server should use for authenticating to clients. Either this option or the `HONO_AUTH_AMQP_KEY_PATH` and `HONO_AUTH_AMQP_CERT_PATH` options need to be set in order to enable TLS secured connections with clients. The key store format can be either `JKS` or `PKCS12` indicated by a `.jks` or `.p12` file suffix respectively. |
| `HONO_AUTH_AMQP_NATIVE_TLS_REQUIRED`<br>`--hono.auth.amqp.nativeTlsRequired` | no | `false` | The server will probe for OpenSLL on startup if a secure port is configured. By default, the server will fall back to the JVM's default SSL engine if not available. However, if set to `true`, the server will fail to start at all in this case. |
| `HONO_AUTH_AMQP_PORT`<br>`--hono.auth.amqp.port` | no | `5671` | The secure port that the server should listen on.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_AUTH_AMQP_SECURE_PROTOCOLS`<br>`--hono.auth.amqp.secureProtocols` | no | `TLSv1.2` | A (comma separated) list of secure protocols that are supported when negotiating TLS sessions. Please refer to the [vert.x documentation](https://vertx.io/docs/vertx-core/java/#ssl) for a list of supported protocol names. |
| `HONO_AUTH_AMQP_TRUST_STORE_PASSWORD`<br>`--hono.auth.amqp.trustStorePassword` | no | - | The password required to read the contents of the trust store. |
| `HONO_AUTH_AMQP_TRUST_STORE_PATH`<br>`--hono.auth.amqp.trustStorePath` | no  | - | The absolute path to the Java key store containing the CA certificates the service uses for authenticating clients. The key store format can be either `JKS`, `PKCS12` or `PEM` indicated by a `.jks`, `.p12` or `.pem` file suffix respectively. |
| `HONO_AUTH_SVC_PERMISSIONS_PATH`<br>`--hono.auth.svc.permissionsPath` | no | `classpath:/`<br>`permissions.json` | The Spring resource URI of the JSON file defining the identities and corresponding authorities on Hono's endpoint resources. The default file bundled with the Auth Server defines authorities required by protocol adapters and downstream consumer. The default permissions file should **only be used for evaluation purposes**. |
| `HONO_AUTH_SVC_SIGNING_KEY_PATH`<br>`--hono.auth.svc.signing.keyPath` | no  | - | The absolute path to the (PKCS8) PEM file containing the private key that the server should use for signing tokens asserting an authenticated client's identity and authorities. When using this variable, other services that need to validate the tokens issued by this service need to be configured with the corresponding certificate/public key. Alternatively, a symmetric key can be used for signing (and validating) by setting the `HONO_AUTH_SVC_SIGNING_SHARED_SECRET` variable. If none of these variables is set, the server falls back to the key indicated by the `HONO_AUTH_AMQP_KEY_PATH` variable. If that variable is also not set, startup of the server fails. |
| `HONO_AUTH_SVC_SIGNING_SHARED_SECRET`<br>`--hono.auth.svc.signing.sharedSecret` | no  | - | A string to derive a symmetric key from that is used for signing tokens asserting an authenticated client's identity and authorities. The key is derived from the string by using the bytes of the String's UTF8 encoding. When setting the signing key using this variable, other services that need to validate the tokens issued by this service need to be configured with the same key. Alternatively, an asymmetric key pair can be used for signing (and validating) by setting the `HONO_AUTH_SVC_SIGNING_KEY_PATH` variable. If none of these variables is set, startup of the server fails. |
| `HONO_AUTH_SVC_SIGNING_TOKEN_EXPIRATION`<br>`--hono.auth.svc.signing.tokenExpiration` | no  | 600 | The number of seconds after which the tokens created by this service for asserting an authenticated client's identity should be considered invalid. Other Hono components will close AMQP connections with clients after this period in order to force the client to authenticate again and create a new token. In closed environments it should be save to set this value to a much higher value, e.g. several hours. |
| `HONO_HEALTH_CHECK_BIND_ADDRESS`<br>`--hono.healthCheck.bindAddress` | no | - | The IP address of the network interface that the secure port, which exposes the service's health check resources, should be bound to. The HTTPS server will only be started if this value is set and the required key material is present in the configuration. |
| `HONO_HEALTH_CHECK_PORT`<br>`--hono.healthCheck.port` | no | `8088` | The secure port for the HTTPS server, which exposes the service's health check resources, should listen on. The server will expose a *readiness* probe at URI `/readiness` and a *liveness* probe at URI `/liveness`. |
| `HONO_HEALTH_CHECK_INSECURE_PORT_BIND_ADDRESS`<br>`--hono.healthCheck.insecurePortBindAddress` | no | - | The IP address of the network interface that the insecure port, which exposes the service's health check resources, should be bound to. The HTTP server will only be started if this value is set explicitly. |
| `HONO_HEALTH_CHECK_INSECURE_PORT`<br>`--hono.healthCheck.insecurePort` | no | `8088` | The insecure port for the HTTP server, which exposes the service's health check resources, should listen on. The server will expose a *readiness* probe at URI `/readiness` and a *liveness* probe at URI `/liveness`. |

The variables only need to be set if the default value does not match your environment.


## Port Configuration

The Auth Server can be configured to listen for connections on

* a secure port only (default) or
* an insecure port only or
* both a secure and an insecure port (dual port configuration)

The server will fail to start if none of the ports is configured properly.

### Secure Port Only

The server needs to be configured with a private key, a certificate holding the public key and a trust store in order to open a TLS secured port.

There are two alternative ways for setting the private key and certificate:

1. Setting the `HONO_AUTH_AMQP_KEY_STORE_PATH` and the `HONO_AUTH_AMQP_KEY_STORE_PASSWORD` variables in order to load the key & certificate from a password protected key store, or
1. setting the `HONO_AUTH_AMQP_KEY_PATH` and `HONO_AUTH_AMQP_CERT_PATH` variables in order to load the key and certificate from two separate PEM files in PKCS8 format.

In order to set the trust store, the `HONO_AUTH_AMQP_TRUST_STORE_PATH` variable needs to be set to a key store containing the trusted root CA certificates. The `HONO_AUTH_AMQP_TRUST_STORE_PASSWORD` variable needs to be set if the key store requires a pass phrase for reading its contents.

When starting up, the server will bind a TLS secured socket to the default secure AMQP port 5671. The port number can also be set explicitly using the `HONO_AUTH_AMQP_PORT` variable.

The `HONO_AUTH_AMQP_BIND_ADDRESS` variable can be used to specify the network interface that the port should be exposed on. By default the port is bound to the *loopback device* only, i.e. the port will only be accessible from the local host. Setting this variable to `0.0.0.0` will let the port being bound to **all** network interfaces (be careful not to expose the port unintentionally to the outside world).

### Insecure Port Only

The secure port will mostly be required for production scenarios. However, it might be desirable to expose a non-TLS secured port instead, e.g. for testing purposes. In any case, the non-secure port needs to be explicitly enabled either by

- explicitly setting `HONO_AUTH_AMQP_INSECURE_PORT` to a valid port number, or by
- implicitly configuring the default AMQP port (5672) by simply setting `HONO_AUTH_AMQP_INSECURE_PORT_ENABLED` to `true`.

The server issues a warning on the console if `HONO_AUTH_AMQP_INSECURE_PORT` is set to the default secure AMQP port (5671).

The `HONO_AUTH_AMQP_INSECURE_PORT_BIND_ADDRESS` variable can be used to specify the network interface that the port should be exposed on. By default the port is bound to the *loopback device* only, i.e. the port will only be accessible from the local host. This variable might be used to e.g. expose the non-TLS secured port on a local interface only, thus providing easy access from within the local network, while still requiring encrypted communication when accessed from the outside over public network infrastructure.

Setting this variable to `0.0.0.0` will let the port being bound to **all** network interfaces (be careful not to expose the port unintentionally to the outside world).

### Dual Port
 
In test setups and some production scenarios Hono server may be configured to open one secure **and** one insecure port at the same time.
 
This is achieved by configuring both ports correctly (see above). The server will fail to start if both ports are configured to use the same port number.

Since the secure port may need different visibility in the network setup compared to the secure port, it has it's own binding address `HONO_AUTH_AMQP_INSECURE_PORT_BIND_ADDRESS`. 
This can be used to narrow the visibility of the insecure port to a local network e.g., while the secure port may be visible worldwide. 

### Ephemeral Ports

The server may be configured to open both a secure and a non-secure port at the same time simply by configuring both ports as described above. For this to work, both ports must be configured to use different port numbers, otherwise startup will fail.

## Metrics Configuration

See [Monitoring & Tracing Admin Guide]({{< ref "/admin-guide/monitoring-tracing-config.md" >}}) for details on how to configure the reporting of metrics.
