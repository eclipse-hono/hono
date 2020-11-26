+++
title = "Configuring the Device Connection Service"
linkTitle = "Device Connection Service Configuration"
weight = 317
+++

The Device Connection service provides an implementation of Eclipse Hono&trade;'s [Device Connection API]({{< relref "/api/device-connection" >}}).

Protocol adapters use this API to store and retrieve information about the gateway that a device is using to connect to Hono's protocol adapters. This information is necessary for routing commands to the particular protocol adapter instance that the gateway used by the device is connected to.

<!--more-->

The Device Connection component provides a production grade implementation of the Device Connection API which uses a remote *data grid* for storing information about device connections. The data grid can be scaled out independently from the Device Connection service components to meet the storage demands at hand.

The Device Connection component is implemented as a Spring Boot application. It can be run either directly from the command line or by means of starting the corresponding [Docker image](https://hub.docker.com/r/eclipse/hono-service-device-connection/) created from it.


## Service Configuration

In addition to the following options, this component supports the options described in [Common Configuration]({{< relref "common-config.md" >}}).

The following table provides an overview of the configuration variables and corresponding command line options for configuring the Device Connection component.

| Environment Variable<br>Command Line Option | Mandatory | Default | Description                                                             |
| :------------------------------------------ | :-------: | :------ | :-----------------------------------------------------------------------|
| `HONO_APP_MAX_INSTANCES`<br>`--hono.app.maxInstances` | no | *#CPU cores* | The number of Verticle instances to deploy. If not set, one Verticle per processor core is deployed. |
| `HONO_DEVICECONNECTION_AMQP_BIND_ADDRESS`<br>`--hono.registry.amqp.bindAddress` | no | `127.0.0.1` | The IP address of the network interface that the secure AMQP port should be bound to.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_DEVICECONNECTION_AMQP_CERT_PATH`<br>`--hono.deviceConnection.amqp.certPath` | no | - | The absolute path to the PEM file containing the certificate that the server should use for authenticating to clients. This option must be used in conjunction with `HONO_DEVICECONNECTION_AMQP_KEY_PATH`.<br>Alternatively, the `HONO_DEVICECONNECTION_AMQP_KEY_STORE_PATH` option can be used to configure a key store containing both the key as well as the certificate. |
| `HONO_DEVICECONNECTION_AMQP_INSECURE_PORT`<br>`--hono.deviceConnection.amqp.insecurePort` | no | - | The insecure port the server should listen on for AMQP 1.0 connections.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_DEVICECONNECTION_AMQP_INSECURE_PORT_BIND_ADDRESS`<br>`--hono.deviceConnection.amqp.insecurePortBindAddress` | no | `127.0.0.1` | The IP address of the network interface that the insecure AMQP port should be bound to.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_DEVICECONNECTION_AMQP_INSECURE_PORT_ENABLED`<br>`--hono.deviceConnection.amqp.insecurePortEnabled` | no | `false` | If set to `true` the server will open an insecure port (not secured by TLS) using either the port number set via `HONO_REGISTRY_AMQP_INSECURE_PORT` or the default AMQP port number (`5672`) if not set explicitly.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_DEVICECONNECTION_AMQP_KEY_PATH`<br>`--hono.deviceConnection.amqp.keyPath` | no | - | The absolute path to the (PKCS8) PEM file containing the private key that the server should use for authenticating to clients. This option must be used in conjunction with `HONO_DEVICECONNECTION_AMQP_CERT_PATH`. Alternatively, the `HONO_DEVICECONNECTION_AMQP_KEY_STORE_PATH` option can be used to configure a key store containing both the key as well as the certificate. |
| `HONO_DEVICECONNECTION_AMQP_KEY_STORE_PASSWORD`<br>`--hono.deviceConnection.amqp.keyStorePassword` | no | - | The password required to read the contents of the key store. |
| `HONO_DEVICECONNECTION_AMQP_KEY_STORE_PATH`<br>`--hono.deviceConnection.amqp.keyStorePath` | no | - | The absolute path to the Java key store containing the private key and certificate that the server should use for authenticating to clients. Either this option or the `HONO_DEVICECONNECTION_AMQP_KEY_PATH` and `HONO_DEVICECONNECTION_AMQP_CERT_PATH` options need to be set in order to enable TLS secured connections with clients. The key store format can be either `JKS` or `PKCS12` indicated by a `.jks` or `.p12` file suffix respectively. |
| `HONO_DEVICECONNECTION_AMQP_NATIVE_TLS_REQUIRED`<br>`--hono.deviceConnection.amqp.nativeTlsRequired` | no | `false` | The server will probe for OpenSLL on startup if a secure port is configured. By default, the server will fall back to the JVM's default SSL engine if not available. However, if set to `true`, the server will fail to start at all in this case. |
| `HONO_DEVICECONNECTION_AMQP_PORT`<br>`--hono.deviceConnection.amqp.port` | no | `5671` | The secure port that the server should listen on for AMQP 1.0 connections.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_DEVICECONNECTION_AMQP_RECEIVER_LINK_CREDIT`<br>`--hono.deviceConnection.amqp.receiverLinkCredit` | no | `100` | The number of credits to flow to a client connecting to the service's AMQP endpoint. |
| `HONO_DEVICECONNECTION_AMQP_SECURE_PROTOCOLS`<br>`--hono.deviceConnection.amqp.secureProtocols` | no | `TLSv1.2` | A (comma separated) list of secure protocols that are supported when negotiating TLS sessions. Please refer to the [vert.x documentation](https://vertx.io/docs/vertx-core/java/#ssl) for a list of supported protocol names. |

The variables only need to be set if the default value does not match your environment.

## Port Configuration

The Device Connection component supports configuration of both, an AMQP based endpoint as well as an HTTP based endpoint proving RESTful resources for managing registration information and credentials. Both endpoints can be configured to listen for connections on

* a secure port only (default) or
* an insecure port only or
* both a secure and an insecure port (dual port configuration)

The server will fail to start if none of the ports is configured properly.

### Secure Port Only

The server needs to be configured with a private key and certificate in order to open a TLS secured port.

There are two alternative ways for doing so:

1. Setting the `HONO_DEVICECONNECTION_AMQP_KEY_STORE_PATH` and the `HONO_DEVICECONNECTION_AMQP_KEY_STORE_PASSWORD` variables in order to load the key & certificate from a password protected key store, or
1. setting the `HONO_DEVICECONNECTION_AMQP_KEY_PATH` and `HONO_DEVICECONNECTION_AMQP_CERT_PATH` variables in order to load the key and certificate from two separate PEM files in PKCS8 format.

When starting up, the server will bind a TLS secured socket to the default secure AMQP port 5671. The port number can also be set explicitly using the `HONO_DEVICECONNECTION_AMQP_PORT` variable.

The `HONO_DEVICECONNECTION_AMQP_BIND_ADDRESS` variable can be used to specify the network interface that the port should be exposed on. By default the port is bound to the *loopback device* only, i.e. the port will only be accessible from the local host. Setting this variable to `0.0.0.0` will let the port being bound to **all** network interfaces (be careful not to expose the port unintentionally to the outside world).

### Insecure Port Only

The secure port will mostly be required for production scenarios. However, it might be desirable to expose a non-TLS secured port instead, e.g. for testing purposes. In any case, the non-secure port needs to be explicitly enabled either by

- explicitly setting `HONO_DEVICECONNECTION_AMQP_INSECURE_PORT` to a valid port number, or by
- implicitly configuring the default AMQP port (5672) by simply setting `HONO_DEVICECONNECTION_AMQP_INSECURE_PORT_ENABLED` to `true`.

The server issues a warning on the console if `HONO_DEVICECONNECTION_AMQP_INSECURE_PORT` is set to the default secure AMQP port (5671).

The `HONO_DEVICECONNECTION_AMQP_INSECURE_PORT_BIND_ADDRESS` variable can be used to specify the network interface that the port should be exposed on. By default the port is bound to the *loopback device* only, i.e. the port will only be accessible from the local host. This variable might be used to e.g. expose the non-TLS secured port on a local interface only, thus providing easy access from within the local network, while still requiring encrypted communication when accessed from the outside over public network infrastructure.

Setting this variable to `0.0.0.0` will let the port being bound to **all** network interfaces (be careful not to expose the port unintentionally to the outside world).

### Dual Port
 
In test setups and some production scenarios Hono server may be configured to open one secure **and** one insecure port at the same time.
 
This is achieved by configuring both ports correctly (see above). The server will fail to start if both ports are configured to use the same port number.

Since the secure port may need different visibility in the network setup compared to the secure port, it has it's own binding address `HONO_DEVICECONNECTION_AMQP_INSECURE_PORT_BIND_ADDRESS`. 
This can be used to narrow the visibility of the insecure port to a local network e.g., while the secure port may be visible worldwide. 

### Ephemeral Ports

The server may be configured to open both a secure and a non-secure port at the same time simply by configuring both ports as described above. For this to work, both ports must be configured to use different port numbers, otherwise startup will fail.

## Data Grid Connection Configuration

The Device Connection component requires either an embedded cache or a remote
data grid, using the Infinispan Hotrod protocol to store device information.

The following table provides an overview of the configuration variables and corresponding command line options for configuring the common aspects of the service:

| Environment Variable<br>Command Line Option | Mandatory | Default | Description                                                             |
| :------------------------------------------ | :-------: | :------ | :-----------------------------------------------------------------------|
| `HONO_DEVICECONNECTION_COMMON_CACHENAME`<br>`--hono.deviceConnection.common.cacheName` | no | `device-connection` | The name of the cache |
| `HONO_DEVICECONNECTION_COMMON_CHECKKEY`<br>`--hono.deviceConnection.common.checkKey` | no | `KEY_CONNECTION_CHECK` | The key used to check the health of the cache. This is only used in case of a remote cache. |
| `HONO_DEVICECONNECTION_COMMON_CHECKVALUE`<br>`--hono.deviceConnection.common.checkValue` | no | `VALUE_CONNECTION_CHECK` | The value used to check the health of the cache. This is only used in case of a remote cache. |

The type of the cache is selected on startup by enabling or disabling the
profile `embedded-cache`. If the profile is enabled the embedded cache is
used, otherwise the remote cache is being used. The remote cache is the default.

### Remote cache

The following table provides an overview of the configuration variables and corresponding command line options for configuring the connection to the data grid:

| Environment Variable<br>Command Line Option | Mandatory | Default | Description                                                             |
| :------------------------------------------ | :-------: | :------ | :-----------------------------------------------------------------------|
| `HONO_DEVICECONNECTION_REMOTE_SERVERLIST`<br>`--hono.deviceConnection.remote.serverList` | yes | - | A list of remote servers in the form: `host1[:port][;host2[:port]]....`. |
| `HONO_DEVICECONNECTION_REMOTE_AUTHSERVERNAME`<br>`--hono.deviceConnection.remote.authServerName` | yes | - | The server name to indicate in the SASL handshake when authenticating to the server. |
| `HONO_DEVICECONNECTION_REMOTE_AUTHREALM`<br>`--hono.deviceConnection.remote.authRealm` | yes | - | The authentication realm for the SASL handshake when authenticating to the server. |
| `HONO_DEVICECONNECTION_REMOTE_AUTHUSERNAME`<br>`--hono.deviceConnection.remote.authUsername` | yes | - | The username to use for authenticating to the server. |
| `HONO_DEVICECONNECTION_REMOTE_AUTHPASSWORD`<br>`--hono.deviceConnection.remote.authPassword` | yes | - | The password to use for authenticating to the server. |

In general, the service supports all configuration properties of the [Infinispan Hotrod client](https://docs.jboss.org/infinispan/9.4/apidocs/org/infinispan/client/hotrod/configuration/package-summary.html#package.description) using `hono.deviceConnection.remote` instead of the `infinispan.client.hotrod` prefix.

### Embedded cache

The following table provides an overview of the configuration variables and corresponding command line options for configuring the embedded cache:

hono.device-connection.embedded.configuration-file

| Environment Variable<br>Command Line Option | Mandatory | Default | Description                                                             |
| :------------------------------------------ | :-------: | :------ | :-----------------------------------------------------------------------|
| `HONO_DEVICECONNECTION_EMBEDDED_CONFIGURATIONFILE`<br>`--hono.deviceConnection.embedded.configurationFile` | yes | - | The absolute path to an Infinispan configuration file. Also see the [Infinispan Configuration Schema](https://docs.jboss.org/infinispan/9.4/configdocs/). |

## Authentication Service Connection Configuration

The Device Connection component requires a connection to an implementation of Hono's Authentication API in order to authenticate and authorize client requests.

The connection is configured according to [Hono Client Configuration]({{< relref "hono-client-configuration.md" >}})
where the `${PREFIX}` is set to `HONO_AUTH`. Since Hono's Authentication Service does not allow caching of the responses, the cache properties
can be ignored.

In addition to the standard client configuration properties, following properties need to be set for the connection:

| Environment Variable<br>Command Line Option | Mandatory | Default | Description                                                             |
| :------------------------------------------ | :-------: | :------ | :-----------------------------------------------------------------------|
| `HONO_AUTH_VALIDATION_CERT_PATH`<br>`--hono.auth.validation.certPath` | no  | - | The absolute path to the PEM file containing the public key that the service should use for validating tokens issued by the Authentication service. Alternatively, a symmetric key can be used for validating tokens by setting the `HONO_AUTH_VALIDATION_SHARED_SECRET` variable. If none of these variables is set, the service falls back to the key indicated by the `HONO_AUTH_CERT_PATH` variable. If that variable is also not set, startup of the service fails. |
| `HONO_AUTH_VALIDATION_SHARED_SECRET`<br>`--hono.auth.validation.sharedSecret` | no  | - | A string to derive a symmetric key from which is used for validating tokens issued by the Authentication service. The key is derived from the string by using the bytes of the String's UTF8 encoding. When setting the validation key using this variable, the Authentication service **must** be configured with the same key. Alternatively, an asymmetric key pair can be used for validating (and signing) by setting the `HONO_AUTH_SIGNING_CERT_PATH` variable. If none of these variables is set, startup of the service fails. |

## Metrics Configuration

See [Monitoring & Tracing Admin Guide]({{< ref "monitoring-tracing-config.md" >}}) for details on how to configure the reporting of metrics.
