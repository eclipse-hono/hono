+++
title = "Auth Server"
weight = 305
+++

The Auth Server component exposes a service endpoint implementing Eclipse Hono&trade;'s [Authentication]({{< relref "api/Authentication-API.md" >}}) API. Other services use this component authenticating clients and retrieving a token asserting the client's identity and corresponding authorities.
<!--more-->

This component serves as a default implementation of the *Authentication* API only. On startup, it reads in all identities and their authorities from a JSON file from the filesystem. All data is then kept in memory and there are no remote service APIs for managing the identities and their authorities.

In a production environment, a more sophisticated implementation should be used instead.

## Configuration

The Auth Server is implemented as a Spring Boot application. It can be run either directly from the command line or by means of starting the corresponding Docker image (`eclipsehono/hono-service-auth`) created from it.

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
| `HONO_AUTH_AMQP_KEY_PATH`<br>`--hono.auth.keyPath` | no | - | The absolute path to the (PKCS8) PEM file containing the private key that the server should use for authenticating to clients. This option must be used in conjunction with `HONO_AUTH_CERT_PATH`. Alternatively, the `HONO_AUTH_KEY_STORE_PATH` option can be used to configure a key store containing both the key as well as the certificate. |
| `HONO_AUTH_AMQP_KEY_STORE_PASSWORD`<br>`--hono.auth.amqp.keyStorePassword` | no | - | The password required to read the contents of the key store. |
| `HONO_AUTH_AMQP_KEY_STORE_PATH`<br>`--hono.auth.keyStorePath` | no | - | The absolute path to the Java key store containing the private key and certificate that the server should use for authenticating to clients. Either this option or the `HONO_AUTH_AMQP_KEY_PATH` and `HONO_AUTH_AMQP_CERT_PATH` options need to be set in order to enable TLS secured connections with clients. The key store format can be either `JKS` or `PKCS12` indicated by a `.jks` or `.p12` file suffix respectively. |
| `HONO_AUTH_AMQP_PORT`<br>`--hono.auth.port` | no | `5671` | The secure port that the server should listen on.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_AUTH_AMQP_TRUST_STORE_PASSWORD`<br>`--hono.auth.amqp.trustStorePassword` | no | - | The password required to read the contents of the trust store. |
| `HONO_AUTH_AMQP_TRUST_STORE_PATH`<br>`--hono.auth.amqp.trustStorePath` | no  | - | The absolute path to the Java key store containing the CA certificates the Hono server uses for authenticating clients. The key store format can be either `JKS`, `PKCS12` or `PEM` indicated by a `.jks`, `.p12` or `.pem` file suffix respectively. |
| `HONO_AUTH_SVC_PERMISSIONS_PATH`<br>`--hono.auth.svc.permissionsPath` | no | `classpath:/`<br>`permissions.json` | The Spring resource URI of the JSON file defining the identities and corresponding authorities on Hono's endpoint resources. The default file bundled with the Auth Server defines authorities required by protocol adapters and downstream consumer. The default permissions file should **only be used for evaluation purposes**. |
| `HONO_AUTH_SVC_SIGNING_KEY_PATH`<br>`--hono.auth.svc.signing.keyPath` | no  | - | The absolute path to the (PKCS8) PEM file containing the private key that the server should use for signing tokens asserting an authenticated client's identity and authorities. When using this variable, other services that need to validate the tokens issued by this service need to be configured with the corresponding certificate/public key. Alternatively, a symmetric key can be used for signing (and validating) by setting the `HONO_AUTH_SVC_SIGNING_SHARED_SECRET` variable. If none of these variables is set, the server falls back to the key indicated by the `HONO_AUTH_AMQP_KEY_PATH` variable. If that variable is also not set, startup of the server fails. |
| `HONO_AUTH_SVC_SIGNING_SHARED_SECRET`<br>`--hono.auth.svc.signing.sharedSecret` | no  | - | A string to derive a symmetric key from that is used for signing tokens asserting an authenticated client's identity and authorities. The key is derived from the string by using the bytes of the String's UTF8 encoding. When setting the signing key using this variable, other services that need to validate the tokens issued by this service need to be configured with the same key. Alternatively, an asymmetric key pair can be used for signing (and validating) by setting the `HONO_AUTH_SVC_SIGNING_KEY_PATH` variable. If none of these variables is set, startup of the server fails. |

The variables only need to be set if the default value does not match your environment.

## Port Configuration

The Auth Server can be configured to listen for connections on

* a secure port only (default) or
* an insecure port only or
* both a secure and an insecure port (dual port configuration)

The server will fail to start if none of the ports is configured properly.

### Secure Port Only

The server needs to be configured with a private key and certificate in order to open a TLS secured port.

There are two alternative ways for doing so:

1. either setting the `HONO_AUTH_AMQP_KEY_STORE_PATH` and the `HONO_AUTH_AMQP_KEY_STORE_PASSWORD` variables in order to load the key & certificate from a password protected key store, or
1. setting the `HONO_AUTH_AMQP_KEY_PATH` and `HONO_AUTH_AMQP_CERT_PATH` variables in order to load the key and certificate from two separate PEM files in PKCS8 format.

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

## Run as a Docker Container

When running the server as a Docker container, the preferred way of configuration is to pass environment variables to the container during startup using Docker's `-e` or `--env` command line option.

The following command starts a container using the configuration files included in the image under path `/etc/hono`.

~~~sh
$ docker run -d --name auth-server --network hono-net \
> -e 'HONO_AUTH_AMQP_BIND_ADDRESS=0.0.0.0'
> -e 'HONO_AUTH_AMQP_KEY_PATH=/etc/hono/certs/auth-server-key.pem' \
> -e 'HONO_AUTH_AMQP_CERT_PATH=/etc/hono/certs/auth-server-cert.pem' \
> -e 'HONO_AUTH_SVC_PERMISSIONS_PATH=file:/etc/hono/permissions.json' \
> -p5671:5671 eclipsehono/hono-service-auth:latest
~~~

{{% note %}}
The *--network* command line switch is used to specify the *user defined* Docker network that the Hono components should attach to. This is important so that other components can use Docker's DNS service to look up the (virtual) IP address of the server when they want to connect to it.
Please refer to the [Docker Networking Guide](https://docs.docker.com/engine/userguide/networking/#/user-defined-networks) for details regarding how to create a *user defined* network in Docker. When using a *Docker Compose* file to start up a complete Hono stack as a whole, the compose file will either explicitly define one or more networks that the containers attach to or the *default* network is used which is created automatically by Docker Compose for an application stack.

In cases where the server container requires a lot of configuration via environment variables (provided by means of *-e* switches), it is more convenient to add all environment variable definitions to a separate *env file* and refer to it using Docker's *--env-file* command line switch when starting the container. This way the command line to start the container is much shorter and can be copied and edited more easily.
{{% /note %}}

## Run using Docker Compose

In most cases it is much easier to start all of Hono's components in one shot using Docker Compose.
See the `example` module for details. The `example` module also contains an example service definition file that
you can use as a starting point for your own configuration.

## Run the Spring Boot Application

Sometimes it is helpful to run the Hono server from its jar file, e.g. in order to attach a debugger more easily or to take advantage of code replacement.
In order to do so, the server can be started using the `spring-boot:run` maven goal from the `services/auth` folder.
The corresponding command to start up the server with the configuration used in the Docker example above looks like this:

~~~sh
~/hono/services/auth$ mvn spring-boot:run -Drun.arguments=\
> --hono.auth.amqp.bindAddress=0.0.0.0,\
> --hono.auth.amqp.keyPath=target/certs/auth-server-key.pem,\
> --hono.auth.amqp.certPath=target/certs/auth-server-cert.pem,\
> --hono.auth.amqp.trustStorePath=target/certs/trusted-certs.pem
~~~

{{% note %}}
You may want to make logging of the server a little more verbose by enabling the *dev* Spring profile.
To do so, append *-Drun.profiles=authentication-impl,dev* to the command line.
{{% /note %}}
