+++
title = "Kura Adapter"
weight = 380
+++

The Kura protocol adapter exposes an MQTT topic hierarchy allowing Eclipse Kura&trade; based gateways to publish *control* and *data* messages to Eclipse Hono&trade;'s Telemetry and Event endpoints.
<!--more-->

The adapter is implemented as a Spring Boot application. It can be run either directly from the command line or by means of starting the corresponding Docker image created from it.

## Service Configuration

The following table provides an overview of the configuration variables and corresponding command line options for configuring the MQTT adapter.

| Environment Variable<br>Command Line Option | Mandatory | Default Value | Description  |
| :------------------------------------------ | :-------: | :------------ | :------------|
| `HONO_APP_MAX_INSTANCES`<br>`--hono.app.maxInstances` | no | *#CPU cores* | The number of verticle instances to deploy. If not set, one verticle per processor core is deployed. |
| `HONO_APP_HEALTH_CHECK_PORT`<br>`--hono.app.healthCheckPort` | no | - | The port that the HTTP server, which exposes the service's health check resources, should bind to. If set, the adapter will expose a *readiness* probe at URI `/readiness` and a *liveness* probe at URI `/liveness`. |
| `HONO_APP_HEALTH_CHECK_BIND_ADDRESS`<br>`--hono.app.healthCheckBindAddress` | no | `127.0.0.1` | The IP address of the network interface that the HTTP server, which exposes the service's health check resources, should be bound to. The HTTP server will only be started if `HONO_APP_HEALTH_CHECK_BIND_ADDRESS` is set explicitly. |
| `HONO_KURA_AUTHENTICATION_REQUIRED`<br>`--hono.kura.authenticationRequired` | no | `true` | If set to `true` the protocol adapter requires devices to authenticate when connecting to the adapter. The credentials provided by the device are verified using the configured [Credentials Service]({{< relref "#credentials-service-connection-configuration" >}}). Devices that have failed to authenticate are not allowed to publish any data. |
| `HONO_KURA_BIND_ADDRESS`<br>`--hono.kura.bindAddress` | no | `127.0.0.1` | The IP address of the network interface that the secure port should be bound to.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_KURA_CERT_PATH`<br>`--hono.kura.certPath` | no | - | The absolute path to the PEM file containing the certificate that the protocol adapter should use for authenticating to clients. This option must be used in conjunction with `HONO_KURA_KEY_PATH`.<br>Alternatively, the `HONO_KURA_KEY_STORE_PATH` option can be used to configure a key store containing both the key as well as the certificate. |
| `HONO_KURA_CONTROL_PREFIX`<br>`--hono.kura.controlPrefix` | no | `$EDC` | The *topic.control-prefix* to use for determining if a message published by a Kura gateway is a *control* message. All messages published to a topic that does not start with this prefix are considered *data* messages. |
| `HONO_KURA_CTRL_MSG_CONTENT_TYPE`<br>`--hono.kura.ctrlMsgContentType` | no | `application/vnd.eclipse.kura-control` | The content type to set on AMQP messages created from Kura *control* messages. |
| `HONO_KURA_DATA_MSG_CONTENT_TYPE`<br>`--hono.kura.dataMsgContentType` | no | `application/vnd.eclipse.kura-data` | The content type to set on AMQP messages created from Kura *data* messages. |
| `HONO_KURA_INSECURE_PORT`<br>`--hono.kura.insecurePort` | no | - | The insecure port the protocol adapter should listen on.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_KURA_INSECURE_PORT_BIND_ADDRESS`<br>`--hono.kura.insecurePortBindAddress` | no | `127.0.0.1` | The IP address of the network interface that the insecure port should be bound to.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_KURA_INSECURE_PORT_ENABLED`<br>`--hono.kura.insecurePortEnabled` | no | `false` | If set to `true` the protocol adapter will open an insecure port (not secured by TLS) using either the port number set via `HONO_KURA_INSECURE_PORT` or the default MQTT port number (`1883`) if not set explicitly.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_KURA_KEY_PATH`<br>`--hono.kura.keyPath` | no | - | The absolute path to the (PKCS8) PEM file containing the private key that the protocol adapter should use for authenticating to clients. This option must be used in conjunction with `HONO_KURA_CERT_PATH`. Alternatively, the `HONO_KURA_KEY_STORE_PATH` option can be used to configure a key store containing both the key as well as the certificate. |
| `HONO_KURA_KEY_STORE_PASSWORD`<br>`--hono.kura.keyStorePassword` | no | - | The password required to read the contents of the key store. |
| `HONO_KURA_KEY_STORE_PATH`<br>`--hono.kura.keyStorePath` | no | - | The absolute path to the Java key store containing the private key and certificate that the protocol adapter should use for authenticating to clients. Either this option or the `HONO_KURA_KEY_PATH` and `HONO_KURA_CERT_PATH` options need to be set in order to enable TLS secured connections with clients. The key store format can be either `JKS` or `PKCS12` indicated by a `.jks` or `.p12` file suffix respectively. |
| `HONO_KURA_MAX_PAYLOAD_SIZE`<br>`--hono.kura.maxPayloadSize` | no | `2048` | The maximum allowed size of an incoming MQTT message's payload in bytes. When a client sends a message with a larger payload, the message is discarded and the connection to the client gets closed. |
| `HONO_KURA_PORT`<br>`--hono.kura.port` | no | `8883` | The secure port that the protocol adapter should listen on.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |

The variables only need to be set if the default values do not match your environment.


## Hono Messaging Connection Configuration

The adapter requires a connection to the Hono Messaging component in order to forward telemetry data and events received from devices to downstream consumers.

The following table provides an overview of the configuration variables and corresponding command line options for configuring the connection to the Hono Messaging component.

| Environment Variable<br>Command Line Option | Mandatory | Default Value | Description  |
| :------------------------------------------ | :-------: | :------------ | :------------|
| `HONO_MESSAGING_HOST`<br>`--hono.messaging.host` | yes | `localhost` | The IP address or name of the Hono Messaging host to connect to. NB: This needs to be set to an address that can be resolved within the network the adapter runs on. When running as a Docker container, use Docker's `--network` command line option to attach the adapter container to the Docker network that the *Hono Server* container is running on. |
| `HONO_MESSAGING_PORT`<br>`--hono.messaging.port` | yes | `5671` | The port that the Hono Messaging component is listening on. |
| `HONO_MESSAGING_USERNAME`<br>`--hono.messaging.username` | yes | - | The username to use for authenticating to the Hono Messaging component. |
| `HONO_MESSAGING_PASSWORD`<br>`--hono.messaging.password` | yes | - | The password to use for authenticating to the Hono Messaging component. |
| `HONO_MESSAGING_TRUST_STORE_PATH`<br>`--hono.messaging.trustStorePath` | no  | - | The absolute path to the Java key store containing the CA certificates the adapter uses for authenticating the Hono Messaging component. This property **must** be set if the Hono Messaging component has been configured to support TLS. The key store format can be either `JKS`, `PKCS12` or `PEM` indicated by a `.jks`, `.p12` or `.pem` file suffix respectively. |
| `HONO_MESSAGING_TRUST_STORE_PASSWORD`<br>`--hono.messaging.trustStorePassword` | no | - | The password required to read the contents of the trust store. |

## Device Registration Service Connection Configuration

The adapter requires a connection to an implementation of Hono's Device Registration API in order to retrieve registration status assertions for connected devices.

The following table provides an overview of the configuration variables and corresponding command line options for configuring the connection to the Device Registration service.

| Environment Variable<br>Command Line Option | Mandatory | Default Value | Description  |
| :------------------------------------------ | :-------: | :------------ | :------------|
| `HONO_REGISTRATION_HOST`<br>`--hono.registration.host` | yes | `localhost` | The IP address or name of the Device Registration service. The adapter uses this service to get an assertion regarding a device's registration status, i.e. whether it is enabled and if it is registered with a particular tenant. NB: This variable needs to be set to an address that can be resolved within the network the adapter runs on. When running as a Docker container, use Docker's `--network` command line option to attach the adapter container to the same network the Device Registration service container is running on. |
| `HONO_REGISTRATION_PORT`<br>`--hono.registration.port` | yes | `5671` | The port that the Device Registration service is listening on. |
| `HONO_REGISTRATION_USERNAME`<br>`--hono.registration.username` | yes | - | The username to use for authenticating to the Device Registration service. |
| `HONO_REGISTRATION_PASSWORD`<br>`--hono.registration.password` | yes | - | The password to use for authenticating to the Device Registration service. |
| `HONO_REGISTRATION_TRUST_STORE_PATH`<br>`--hono.registration.trustStorePath` | no  | - | The absolute path to the Java key store containing the CA certificates the adapter uses for authenticating the Device Registration service. This property **must** be set if the Device Registration service has been configured to use TLS. The key store format can be either `JKS`, `PKCS12` or `PEM` indicated by a `.jks`, `.p12` or `.pem` file suffix. |
| `HONO_REGISTRATION_TRUST_STORE_PASSWORD`<br>`--hono.registration.trustStorePassword` | no | - | The password required to read the contents of the trust store. |

## Credentials Service Connection Configuration

The adapter requires a connection to an implementation of Hono's Credentials API in order to retrieve credentials stored for devices that need to be authenticated.

The following table provides an overview of the configuration variables and corresponding command line options for configuring the connection to the Credentials service.

| Environment Variable<br>Command Line Option | Mandatory | Default Value | Description  |
| :------------------------------------------ | :-------: | :------------ | :------------|
| `HONO_CREDENTIALS_HOST`<br>`--hono.credentials.host` | yes | `localhost` | The IP address or name of the Credentials service. The adapter uses this service to get credentials stored for a device to authenticate it. NB: This variable needs to be set to an address that can be resolved within the network the adapter runs on. When running as a Docker container, use Docker's `--network` command line option to attach the adapter container to the same network the Credentials service container is running on. |
| `HONO_CREDENTIALS_PORT`<br>`--hono.credentials.port` | yes | `5671` | The port that the Credentials service is listening on. |
| `HONO_CREDENTIALS_USERNAME`<br>`--hono.credentials.username` | yes | - | The username to use for authenticating to the Credentials service. |
| `HONO_CREDENTIALS_PASSWORD`<br>`--hono.credentials.password` | yes | - | The password to use for authenticating to the Credentials service. |
| `HONO_CREDENTIALS_TRUST_STORE_PATH`<br>`--hono.credentials.trustStorePath` | no  | - | The absolute path to the Java key store containing the CA certificates the adapter uses for authenticating the Credentials service. This property **must** be set if the Credentials service has been configured to use TLS. The key store format can be either `JKS`, `PKCS12` or `PEM` indicated by a `.jks`, `.p12` or `.pem` file suffix. |
| `HONO_CREDENTIALS_TRUST_STORE_PASSWORD`<br>`--hono.credentials.trustStorePassword` | no | - | The password required to read the contents of the trust store. |


## Port Configuration

The Kura protocol adapter can be configured to listen for connections on

* a secure port only (default) or
* an insecure port only or
* both a secure and an insecure port (dual port configuration)

The Kura protocol adapter will fail to start if none of the ports is configured properly.

### Secure Port Only

The protocol adapter needs to be configured with a private key and certificate in order to open a TLS secured port.

There are two alternative ways for doing so:

1. either setting the `HONO_KURA_KEY_STORE_PATH` and the `HONO_KURA_KEY_STORE_PASSWORD` variables in order to load the key & certificate from a password protected key store, or
1. setting the `HONO_KURA_KEY_PATH` and `HONO_KURA_CERT_PATH` variables in order to load the key and certificate from two separate PEM files in PKCS8 format.

When starting up, the protocol adapter will bind a TLS secured socket to the default secure MQTT port 8883. The port number can also be set explicitly using the `HONO_KURA_PORT` variable.

The `HONO_KURA_BIND_ADDRESS` variable can be used to specify the network interface that the port should be exposed on. By default the port is bound to the *loopback device* only, i.e. the port will only be accessible from the local host. Setting this variable to `0.0.0.0` will let the port being bound to **all** network interfaces (be careful not to expose the port unintentionally to the outside world).

### Insecure Port Only

The secure port will mostly be required for production scenarios. However, it might be desirable to expose a non-TLS secured port instead, e.g. for testing purposes. In any case, the non-secure port needs to be explicitly enabled either by

- explicitly setting `HONO_KURA_INSECURE_PORT` to a valid port number, or by
- implicitly configuring the default MQTT port (1883) by simply setting `HONO_KURA_INSECURE_PORT_ENABLED` to `true`.

The protocol adapter issues a warning on the console if `HONO_KURA_INSECURE_PORT` is set to the default secure MQTT port (8883).

The `HONO_KURA_INSECURE_PORT_BIND_ADDRESS` variable can be used to specify the network interface that the port should be exposed on. By default the port is bound to the *loopback device* only, i.e. the port will only be accessible from the local host. This variable might be used to e.g. expose the non-TLS secured port on a local interface only, thus providing easy access from within the local network, while still requiring encrypted communication when accessed from the outside over public network infrastructure.

Setting this variable to `0.0.0.0` will let the port being bound to **all** network interfaces (be careful not to expose the port unintentionally to the outside world).

### Dual Port
 
The protocol adapter may be configured to open both a secure and a non-secure port at the same time simply by configuring both ports as described above. For this to work, both ports must be configured to use different port numbers, otherwise startup will fail.

### Ephemeral Ports

Both the secure as well as the insecure port numbers may be explicitly set to `0`. The protocol adapter will then use arbitrary (unused) port numbers determined by the operating system during startup.

## Run as a Docker Swarm Service

The adapter can be run as a Docker container from the command line. The following commands create and start the adapter as a Docker Swarm service using the default keys  contained in the `demo-certs` module:

~~~sh
~/hono$ docker secret create trusted-certs.pem demo-certs/certs/trusted-certs.pem
~/hono$ docker secret create kura-adapter-key.pem demo-certs/certs/kura-adapter-key.pem
~/hono$ docker secret create kura-adapter-cert.pem demo-certs/certs/kura-adapter-cert.pem
~/hono$ docker service create --detach --name hono-adapter-kura --network hono-net -p 1883:1883 -p 8883:8883 \
> --secret trusted-certs.pem \
> --secret kura-adapter-key.pem \
> --secret kura-adapter-cert.pem \
> -e 'HONO_MESSAGING_HOST=hono-service-messaging.hono' \
> -e 'HONO_MESSAGING_USERNAME=kura-adapter@HONO' \
> -e 'HONO_MESSAGING_PASSWORD=kura-secret' \
> -e 'HONO_MESSAGING_TRUST_STORE_PATH=/run/secrets/trusted-certs.pem' \
> -e 'HONO_REGISTRATION_HOST=hono-service-device-registry.hono' \
> -e 'HONO_REGISTRATION_USERNAME=kura-adapter@HONO' \
> -e 'HONO_REGISTRATION_PASSWORD=kura-secret' \
> -e 'HONO_REGISTRATION_TRUST_STORE_PATH=/run/secrets/trusted-certs.pem' \
> -e 'HONO_KURA_BIND_ADDRESS=0.0.0.0' \
> -e 'HONO_KURA_KEY_PATH=/run/secrets/kura-adapter-key.pem' \
> -e 'HONO_KURA_CERT_PATH=/run/secrets/kura-adapter-cert.pem' \
> -e 'HONO_KURA_INSECURE_PORT_ENABLED=true' \
> -e 'HONO_KURA_INSECURE_PORT_BIND_ADDRESS=0.0.0.0'
> eclipsehono/hono-adapter-kura:latest
~~~

{{% note %}}
There are several things noteworthy about the above command to start the service:

1. The *secrets* need to be created once only, i.e. they only need to be removed and re-created if they are changed.
1. The *--network* command line switch is used to specify the *user defined* Docker network that the Kura adapter container should attach to. It is important that the adapter container is attached to the same network that the Hono Messaging component is attached to so that the Kura adapter can use the Hono Messaging component's host name to connect to it via the Docker network. Please refer to the [Docker Networking Guide](https://docs.docker.com/engine/userguide/networking/#/user-defined-networks) for details regarding how to create a *user defined* network in Docker.
1. In cases where the Kura adapter container requires a lot of configuration via environment variables (provided by means of *-e* switches), it is more convenient to add all environment variable definitions to a separate *env file* and refer to it using Docker's *--env-file* command line switch when starting the container. This way the command line to start the container is much shorter and can be copied and edited more easily.
{{% /note %}}

## Run using the Docker Swarm Deployment Script

In most cases it is much easier to start all of Hono's components in one shot using the Docker Swarm deployment script provided in the `example/target/deploy/docker` folder.

## Run the Spring Boot Application

Sometimes it is helpful to run the adapter from its jar file, e.g. in order to attach a debugger more easily or to take advantage of code replacement.
In order to do so, the adapter can be started using the `spring-boot:run` maven goal from the `adapters/kura` folder.
The corresponding command to start up the adapter with the configuration used in the Docker example above looks like this:

~~~sh
~/hono/adapters/kura$ mvn spring-boot:run -Drun.arguments=\
> --hono.messaging.host=hono-service-messaging.hono,\
> --hono.messaging.username=kura-adapter@HONO,\
> --hono.messaging.password=kura-secret,\
> --hono.messaging.trustStorePath=target/certs/trusted-certs.pem \
> --hono.registration.host=hono-service-device-registry.hono,\
> --hono.registration.username=kura-adapter@HONO,\
> --hono.registration.password=kura-secret,\
> --hono.registration.trustStorePath=target/certs/trusted-certs.pem \
> --hono.kura.bindAddress=0.0.0.0 \
> --hono.kura.insecurePortEnabled=true,\
> --hono.kura.insecurePortBindAddress=0.0.0.0
~~~

{{% note %}}
In the example above the *--hono.messaging.host=hono-service-messaging.hono* command line option indicates that the Hono Messaging component is running on a host
with name *hono-service-messaging.hono*. However, if the Hono Messaging component has been started as a Docker container then the *hono-service-messaging.hono* host name will most likely only be resolvable on the network that Docker has created for running the container on, i.e. when you run the Kura adapter
from the Spring Boot application and want it to connect to a Hono Messaging instance run as a Docker container then you need to set the
value of the *--hono.messaging.host* option to the IP address (or name) of the Docker host running the Hono Messaging container.
The same holds true analogously for the *hono-service-device-registry.hono* address.
{{% /note %}}

## Using the Kura Adapter

The Kura adapter by default requires devices (gateways) to authenticate during connection establishment. In order to do so, gateways need to provide a *username* and a *password* in the MQTT *CONNECT* packet. The *username* must have the form *auth-id@tenant*, e.g. `sensor1@DEFAULT_TENANT`. The adapter verifies the credentials provided by the gateway against the credentials the [configured Credentials service]({{< relref "#credentials-service-configuration" >}}) has on record for the gateway. The adapter uses the Credentials API's *get* operation to retrieve the credentials-on-record with the *tenant* and *auth-id* provided by the device in the *username* and `hashed-password` as the *type* of secret as query parameters.

Please refer to the [Eclipse Kura documentation](http://eclipse.github.io/kura/cloud/kura-cloud.html) on how to configure the gateway's cloud service connection accordingly. It is important to set the gateway's *topic.context.account-name* to the ID of the Hono tenant that the gateway has been registered with whereas the gateway's *client-id* needs to be set to the corresponding Hono device ID. The *auth-id* used as part of the gateway's *username* property needs to match the authentication identifier of a set of credentials registered for the device ID in Hono's Credentials service. In other words, the credentials configured on the gateway need to belong to the corresponding device ID.

Once the gateway has established a connection to the Kura adapter, all *control* and *data* messages published by applications running on the gateway are sent to the adapter and mapped to Hono's Telemetry and Event API endpoints as follows:

1. The adapter treats all messages that are published to a topic starting with the configured `HONO_KURA_CONTROL_PREFIX` as control messages. All other messages are considered to be data messages.
1. *control* messages with QoS 0 are forwarded to Hono's telemetry endpoint whereas messages with QoS 1 are forwarded to the event endpoint. The corresponding AMQP 1.0 messages that are sent downstream have a content type of `application/vnd.eclipse.kura-control`.
1. *data* messages with QoS 0 are forwarded to the telemetry endpoint whereas messages with QoS 1 are forwarded to the event endpoint. The corresponding AMQP 1.0 messages that are sent downstream have a content type of `application/vnd.eclipse.kura-data`.
1. All AMQP messages sent downstream include a `hono-orig-address` application property containing the name of the topic that the message has been originally published to by the gateway.
