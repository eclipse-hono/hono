+++
title = "Setup MQTT Adapter"
weight = 320
+++

The MQTT protocol adapter exposes an MQTT topic hierarchy for publishing messages and events to Eclipse Hono&trade;'s Telemetry and Event endpoints.
<!--more-->

The adapter is implemented as a Spring Boot application. It can be run either directly from the command line or by means of starting the corresponding Docker image created from it.

## Service Configuration

The following table provides an overview of the configuration variables and corresponding command line options for configuring the MQTT adapter.

| Environment Variable<br>Command Line Option | Mandatory | Default Value | Description  |
| :------------------------------------------ | :-------: | :------------ | :------------|
| `HONO_APP_MAX_INSTANCES`<br>`--hono.app.maxInstances` | no | *#CPU cores* | The number of verticle instances to deploy. If not set, one verticle per processor core is deployed. |
| `HONO_APP_HEALTH_CHECK_PORT`<br>`--hono.app.healthCheckPort` | no | - | The port that the HTTP server, which exposes the service's health check resources, should bind to. If set, the adapter will expose a *readiness* probe at URI `/readiness` and a *liveness* probe at URI `/liveness`. |
| `HONO_APP_HEALTH_CHECK_BIND_ADDRESS`<br>`--hono.app.healthCheckBindAddress` | no | `127.0.0.1` | The IP address of the network interface that the HTTP server, which exposes the service's health check resources, should be bound to. The HTTP server will only be started if `HONO_APP_HEALTH_CHECK_BIND_ADDRESS` is set explicitly. |
| `HONO_MQTT_AUTHENTICATION_REQUIRED`<br>`--hono.mqtt.authenticationRequired` | no | `true` | If set to `true` the protocol adapter requires devices to authenticate when connecting to the adapter. The credentials provided by the device are verified using the configured [Credentials Service]({{< relref "#credentials-service-connection-configuration" >}}). Devices that have failed to authenticate are not allowed to publish any data. |
| `HONO_MQTT_BIND_ADDRESS`<br>`--hono.mqtt.bindAddress` | no | `127.0.0.1` | The IP address of the network interface that the secure port should be bound to.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_MQTT_CERT_PATH`<br>`--hono.mqtt.certPath` | no | - | The absolute path to the PEM file containing the certificate that the protocol adapter should use for authenticating to clients. This option must be used in conjunction with `HONO_MQTT_KEY_PATH`.<br>Alternatively, the `HONO_MQTT_KEY_STORE_PATH` option can be used to configure a key store containing both the key as well as the certificate. |
| `HONO_MQTT_DEFAULTS_ENABLED`<br>`--hono.mqtt.defaultsEnabled` | no | `true` | If set to `true` the protocol adapter uses *default values* registered for a device to augment messages published by the device with missing information like a content type. In particular, the protocol adapter adds default values registered for the device as (application) properties with the same name to the AMQP 1.0 messages it sends downstream to the Hono Messaging service. |
| `HONO_MQTT_INSECURE_PORT_BIND_ADDRESS`<br>`--hono.mqtt.insecurePortBindAddress` | no | `127.0.0.1` | The IP address of the network interface that the insecure port should be bound to.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_MQTT_INSECURE_PORT_ENABLED`<br>`--hono.mqtt.insecurePortEnabled` | no | `false` | If set to `true` the protocol adapter will open an insecure port (not secured by TLS) using either the port number set via `HONO_MQTT_INSECURE_PORT` or the default MQTT port number (`1883`) if not set explicitly.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_MQTT_KEY_PATH`<br>`--hono.mqtt.keyPath` | no | - | The absolute path to the (PKCS8) PEM file containing the private key that the protocol adapter should use for authenticating to clients. This option must be used in conjunction with `HONO_MQTT_CERT_PATH`. Alternatively, the `HONO_MQTT_KEY_STORE_PATH` option can be used to configure a key store containing both the key as well as the certificate. |
| `HONO_MQTT_KEY_STORE_PASSWORD`<br>`--hono.mqtt.keyStorePassword` | no | - | The password required to read the contents of the key store. |
| `HONO_MQTT_KEY_STORE_PATH`<br>`--hono.mqtt.keyStorePath` | no | - | The absolute path to the Java key store containing the private key and certificate that the protocol adapter should use for authenticating to clients. Either this option or the `HONO_MQTT_KEY_PATH` and `HONO_MQTT_CERT_PATH` options need to be set in order to enable TLS secured connections with clients. The key store format can be either `JKS` or `PKCS12` indicated by a `.jks` or `.p12` file suffix respectively. |
| `HONO_MQTT_MAX_PAYLOAD_SIZE`<br>`--hono.mqtt.maxPayloadSize` | no | `2048` | The maximum allowed size of an incoming MQTT message's payload in bytes. When a client sends a message with a larger payload, the message is discarded and the connection to the client gets closed. |
| `HONO_MQTT_PORT`<br>`--hono.mqtt.port` | no | `8883` | The secure port that the protocol adapter should listen on.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_METRIC_REPORTER_GRAPHITE_ACTIVE`<br>`--hono.metric.reporter.graphite.active` | no  | `false` | Activates the metrics reporter to Graphite (or a graphite compatible system - we use InfluxDB in the `example`). |
| `HONO_METRIC_REPORTER_GRAPHITE_HOST`<br>`--hono.metric.reporter.graphite.host` | no  | `localhost` | Sets the host, to which the metrics will be reported. |
| `HONO_METRIC_REPORTER_GRAPHITE_PORT`<br>`--hono.metric.reporter.graphite.host` | no  | `2003` | Sets the port - 2003 ist standard for Graphite. |
| `HONO_METRIC_REPORTER_GRAPHITE_PERIOD`<br>`--hono.metric.reporter.graphite.period` | no  | `5000` | Sets the time interval for reporting. |
| `HONO_METRIC_REPORTER_GRAPHITE_PREFIX`<br>`--hono.metric.reporter.graphite.prefix` | no  | - | Prefix all metric names with the given string. |
| `HONO_METRIC_REPORTER_CONSOLE_ACTIVE`<br>`--hono.metric.reporter.console.active` | no  | `false` | Activates the metrics reporter to the console/log. |
| `HONO_METRIC_REPORTER_CONSOLE_PERIOD`<br>`--hono.metric.reporter.console.period` | no  | `5000` | Sets the time interval for reporting. |
| `HONO_METRIC_JVM_MEMORY`<br>`--hono.metric.jvm.memory` | no  | `false` | Activates JVM memory metrics (from the Dropwizard JVM Instrumentation). The metric name is `hono.mqtt.jvm.memory`. |
| `HONO_METRIC_JVM_THREAD`<br>`--hono.metric.jvm.thread` | no  | `false` | Activates JVM thread metrics (from the Dropwizard JVM Instrumentation). The metric name is `hono.mqtt.jvm.thread`.|
| `HONO_METRIC_VERTX`<br>`--hono.metric.vertx` | no  | `false` | Activates the Vert.x metrics (from the Vert.x metrics project). The metric name is `hono.mqtt.vertx`. |

The variables only need to be set if the default values do not match your environment.


## Port Configuration

The MQTT protocol adapter can be configured to listen for connections on

* a secure port only (default) or
* an insecure port only or
* both a secure and an insecure port (dual port configuration)

The MQTT protocol adapter will fail to start if none of the ports is configured properly.

### Secure Port Only

The protocol adapter needs to be configured with a private key and certificate in order to open a TLS secured port.

There are two alternative ways for doing so:

1. either setting the `HONO_MQTT_KEY_STORE_PATH` and the `HONO_MQTT_KEY_STORE_PASSWORD` variables in order to load the key & certificate from a password protected key store, or
1. setting the `HONO_MQTT_KEY_PATH` and `HONO_MQTT_CERT_PATH` variables in order to load the key and certificate from two separate PEM files in PKCS8 format.

When starting up, the protocol adapter will bind a TLS secured socket to the default secure MQTT port 8883. The port number can also be set explicitly using the `HONO_MQTT_PORT` variable.

The `HONO_MQTT_BIND_ADDRESS` variable can be used to specify the network interface that the port should be exposed on. By default the port is bound to the *loopback device* only, i.e. the port will only be accessible from the local host. Setting this variable to `0.0.0.0` will let the port being bound to **all** network interfaces (be careful not to expose the port unintentionally to the outside world).

### Insecure Port Only

The secure port will mostly be required for production scenarios. However, it might be desirable to expose a non-TLS secured port instead, e.g. for testing purposes. In any case, the non-secure port needs to be explicitly enabled either by

- explicitly setting `HONO_MQTT_INSECURE_PORT` to a valid port number, or by
- implicitly configuring the default MQTT port (1883) by simply setting `HONO_MQTT_INSECURE_PORT_ENABLED` to `true`.

The protocol adapter issues a warning on the console if `HONO_MQTT_INSECURE_PORT` is set to the default secure MQTT port (8883).

The `HONO_MQTT_INSECURE_PORT_BIND_ADDRESS` variable can be used to specify the network interface that the port should be exposed on. By default the port is bound to the *loopback device* only, i.e. the port will only be accessible from the local host. This variable might be used to e.g. expose the non-TLS secured port on a local interface only, thus providing easy access from within the local network, while still requiring encrypted communication when accessed from the outside over public network infrastructure.

Setting this variable to `0.0.0.0` will let the port being bound to **all** network interfaces (be careful not to expose the port unintentionally to the outside world).

### Dual Port
 
The protocol adapter may be configured to open both a secure and a non-secure port at the same time simply by configuring both ports as described above. For this to work, both ports must be configured to use different port numbers, otherwise startup will fail.

### Ephemeral Ports

Both the secure as well as the insecure port numbers may be explicitly set to `0`. The protocol adapter will then use arbitrary (unused) port numbers determined by the operating system during startup.

## Hono Messaging Connection Configuration

The adapter requires a connection to the Hono Messaging component in order to forward telemetry data and events received from devices to downstream consumers.

The following table provides an overview of the configuration variables and corresponding command line options for configuring the connection to the Hono Messaging component.

| Environment Variable<br>Command Line Option | Mandatory | Default Value | Description  |
| :------------------------------------------ | :-------: | :------------ | :------------|
| `HONO_MESSAGING_HOST`<br>`--hono.messaging.host` | yes | `localhost` | The IP address or name of the Hono Messaging host to connect to. NB: This needs to be set to an address that can be resolved within the network the adapter runs on. When running as a Docker container, use Docker's `--network` command line option to attach the adapter container to the Docker network that the *Hono Server* container is running on. |
| `HONO_MESSAGING_PORT`<br>`--hono.messaging.port` | yes | `5671` | The port that the Hono Messaging component is listening on. |
| `HONO_MESSAGING_USERNAME`<br>`--hono.messaging.username` | yes | - | The username to use for authenticating to the Hono Messaging component. |
| `HONO_MESSAGING_PASSWORD`<br>`--hono.messaging.password` | yes | - | The password to use for authenticating to the Hono Messaging component. |
| `HONO_MESSAGING_TLS_ENABLED`<br>`--hono.messaging.tlsEnabled` | no | `false` | If set to `true` the connection to the peer will be encrypted using TLS and the peer's identity will be verified using the JVM's configured standard trust store.<br>This variable only needs to be set to enable TLS explicitly if no specific trust store is configured using the `HONO_MESSAGING_TRUST_STORE_PATH` variable. |
| `HONO_MESSAGING_TRUST_STORE_PATH`<br>`--hono.messaging.trustStorePath` | no  | - | The absolute path to the Java key store containing the CA certificates the adapter uses for authenticating the Hono Messaging component. This property **must** be set if the Hono Messaging component has been configured to support TLS. The key store format can be either `JKS`, `PKCS12` or `PEM` indicated by a `.jks`, `.p12` or `.pem` file suffix respectively. |
| `HONO_MESSAGING_TRUST_STORE_PASSWORD`<br>`--hono.messaging.trustStorePassword` | no | - | The password required to read the contents of the trust store. |

## Device Registration Service Connection Configuration

The adapter requires a connection to an implementation of Hono's Device Registration API in order to retrieve registration status assertions for connected devices.

The following table provides an overview of the configuration variables and corresponding command line options for configuring the connection to the Device Registration service.

| Environment Variable<br>Command Line Option | Mandatory | Default Value | Description  |
| :------------------------------------------ | :-------: | :------------ | :------------|
| `HONO_REGISTRATION_FLOW_LATENCY`<br>`--hono.registration.flowLatency` | no | `20` | The maximum amount of time (milliseconds) that the adapter should wait for *credits* after a link to the Device Registration service has been established. In system environments where the network connection between the individual Hono services is slow or has a high latency, increasing the value of this property will increase a device's chance of successfully publishing the first message for the tenant it belongs to. |
| `HONO_REGISTRATION_HOST`<br>`--hono.registration.host` | yes | `localhost` | The IP address or name of the Device Registration service. The adapter uses this service to get an assertion regarding a device's registration status, i.e. whether it is enabled and if it is registered with a particular tenant. NB: This variable needs to be set to an address that can be resolved within the network the adapter runs on. When running as a Docker container, use Docker's `--network` command line option to attach the adapter container to the same network the Device Registration service container is running on. |
| `HONO_REGISTRATION_PORT`<br>`--hono.registration.port` | yes | `5671` | The port that the Device Registration service is listening on. |
| `HONO_REGISTRATION_USERNAME`<br>`--hono.registration.username` | yes | - | The username to use for authenticating to the Device Registration service. |
| `HONO_REGISTRATION_PASSWORD`<br>`--hono.registration.password` | yes | - | The password to use for authenticating to the Device Registration service. |
| `HONO_REGISTRATION_REQUEST_TIMEOUT`<br>`--hono.registration.requestTimeout` | no | `200` | The maximum amount of time (milliseconds) that the adapter should wait for a response to its requests to the Device Registration service. In cases where the registration service takes more time to respond to requests, this value needs to be adjusted accordingly in order for the adapter to successfully authorize devices publishing messages. |
| `HONO_REGISTRATION_RESPONSE_CACHE_MIN_SIZE`<br>`--hono.registration.responseCacheMinSize` | no | `20` | The minimum number of responses from the Device Registration service to cache in the adapter.<br>The adapter caches responses for the *assert Device Registration* operation until the returned assertion tokens expire. It is highly recommended to enable the cache (which is the default) because it greatly reduces load on the Device Registration service. |
| `HONO_REGISTRATION_RESPONSE_CACHE_MAX_SIZE`<br>`--hono.registration.responseCacheMaxSize` | no | `1000` | The maximum number of responses from the Device Registration service to cache in the adapter. Setting this variable to 0 disables caching.<br>The adapter caches responses for the *assert Device Registration* operation until the returned assertion tokens expire. It is highly recommended to enable the cache (which is the default) because it greatly reduces load on the Device Registration service. |
| `HONO_REGISTRATION_TLS_ENABLED`<br>`--hono.registration.tlsEnabled` | no | `false` | If set to `true` the connection to the peer will be encrypted using TLS and the peer's identity will be verified using the JVM's configured standard trust store.<br>This variable only needs to be set to enable TLS explicitly if no specific trust store is configured using the `HONO_REGISTRATION_TRUST_STORE_PATH` variable. |
| `HONO_REGISTRATION_TRUST_STORE_PATH`<br>`--hono.registration.trustStorePath` | no  | - | The absolute path to the Java key store containing the CA certificates the adapter uses for authenticating the Device Registration service. This property **must** be set if the Device Registration service has been configured to use TLS. The key store format can be either `JKS`, `PKCS12` or `PEM` indicated by a `.jks`, `.p12` or `.pem` file suffix. |
| `HONO_REGISTRATION_TRUST_STORE_PASSWORD`<br>`--hono.registration.trustStorePassword` | no | - | The password required to read the contents of the trust store. |

## Credentials Service Connection Configuration

The adapter requires a connection to an implementation of Hono's Credentials API in order to retrieve credentials stored for devices that need to be authenticated.

The following table provides an overview of the configuration variables and corresponding command line options for configuring the connection to the Credentials service.

| Environment Variable<br>Command Line Option | Mandatory | Default Value | Description  |
| :------------------------------------------ | :-------: | :------------ | :------------|
| `HONO_CREDENTIALS_FLOW_LATENCY`<br>`--hono.credentials.flowLatency` | no | `20` | The maximum amount of time (milliseconds) that the adapter should wait for *credits* after a link to the Credentials service has been established. In system environments where the network connection between the individual Hono services is slow or has a high latency, increasing the value of this property will increase a device's chance of successfully publishing the first message for the tenant it belongs to. |
| `HONO_CREDENTIALS_HOST`<br>`--hono.credentials.host` | yes | `localhost` | The IP address or name of the Credentials service. The adapter uses this service to get credentials stored for a device to authenticate it. NB: This variable needs to be set to an address that can be resolved within the network the adapter runs on. When running as a Docker container, use Docker's `--network` command line option to attach the adapter container to the same network the Credentials service container is running on. |
| `HONO_CREDENTIALS_PORT`<br>`--hono.credentials.port` | yes | `5671` | The port that the Credentials service is listening on. |
| `HONO_CREDENTIALS_USERNAME`<br>`--hono.credentials.username` | yes | - | The username to use for authenticating to the Credentials service. |
| `HONO_CREDENTIALS_PASSWORD`<br>`--hono.credentials.password` | yes | - | The password to use for authenticating to the Credentials service. |
| `HONO_CREDENTIALS_REQUEST_TIMEOUT`<br>`--hono.credentials.requestTimeout` | no | `200` | The maximum amount of time (milliseconds) that the adapter should wait for a response to its requests to the Credentials service. In cases where the credentials service takes more time to respond to requests, this value needs to be adjusted accordingly in order for the adapter to successfully authenticate devices connecting to the adapter. |
| `HONO_CREDENTIALS_TRUST_STORE_PATH`<br>`--hono.credentials.trustStorePath` | no  | - | The absolute path to the Java key store containing the CA certificates the adapter uses for authenticating the Credentials service. This property **must** be set if the Credentials service has been configured to use TLS. The key store format can be either `JKS`, `PKCS12` or `PEM` indicated by a `.jks`, `.p12` or `.pem` file suffix. |
| `HONO_CREDENTIALS_TRUST_STORE_PASSWORD`<br>`--hono.credentials.trustStorePassword` | no | - | The password required to read the contents of the trust store. |


## Run as a Docker Swarm Service

The MQTT adapter can be run as a Docker container from the command line. The following commands create and start the MQTT adapter as a Docker Swarm service using the default keys  contained in the `demo-certs` module:

~~~sh
~/hono$ docker secret create trusted-certs.pem demo-certs/certs/trusted-certs.pem
~/hono$ docker secret create mqtt-adapter-key.pem demo-certs/certs/mqtt-adapter-key.pem
~/hono$ docker secret create mqtt-adapter-cert.pem demo-certs/certs/mqtt-adapter-cert.pem
~/hono$ docker service create --detach --name hono-adapter-mqtt-vertx --network hono-net -p 1883:1883 -p 8883:8883 \
> --secret trusted-certs.pem \
> --secret mqtt-adapter-key.pem \
> --secret mqtt-adapter-cert.pem \
> -e 'HONO_MESSAGING_HOST=hono-service-messaging.hono' \
> -e 'HONO_MESSAGING_USERNAME=mqtt-adapter@HONO' \
> -e 'HONO_MESSAGING_PASSWORD=mqtt-secret' \
> -e 'HONO_MESSAGING_TRUST_STORE_PATH=/run/secrets/trusted-certs.pem' \
> -e 'HONO_REGISTRATION_HOST=hono-service-device-registry.hono' \
> -e 'HONO_REGISTRATION_USERNAME=mqtt-adapter@HONO' \
> -e 'HONO_REGISTRATION_PASSWORD=mqtt-secret' \
> -e 'HONO_REGISTRATION_TRUST_STORE_PATH=/run/secrets/trusted-certs.pem' \
> -e 'HONO_MQTT_BIND_ADDRESS=0.0.0.0' \
> -e 'HONO_MQTT_KEY_PATH=/run/secrets/mqtt-adapter-key.pem' \
> -e 'HONO_MQTT_CERT_PATH=/run/secrets/mqtt-adapter-cert.pem' \
> -e 'HONO_MQTT_INSECURE_PORT_ENABLED=true' \
> -e 'HONO_MQTT_INSECURE_PORT_BIND_ADDRESS=0.0.0.0'
> eclipse/hono-adapter-mqtt-vertx:latest
~~~

{{% note %}}
There are several things noteworthy about the above command to start the service:

1. The *secrets* need to be created once only, i.e. they only need to be removed and re-created if they are changed.
1. The *--network* command line switch is used to specify the *user defined* Docker network that the MQTT adapter container should attach to. It is important that the MQTT adapter container is attached to the same network that the Hono Messaging component is attached to so that the MQTT adapter can use the Hono Messaging component's host name to connect to it via the Docker network. Please refer to the [Docker Networking Guide](https://docs.docker.com/engine/userguide/networking/#/user-defined-networks) for details regarding how to create a *user defined* network in Docker.
1. In cases where the MQTT adapter container requires a lot of configuration via environment variables (provided by means of *-e* switches), it is more convenient to add all environment variable definitions to a separate *env file* and refer to it using Docker's *--env-file* command line switch when starting the container. This way the command line to start the container is much shorter and can be copied and edited more easily.
{{% /note %}}

### Configuring the Java VM

The MQTT adapter Docker image by default does not pass any specific configuration options to the Java VM. The VM can be configured using the standard `-X` options by means of setting the `_JAVA_OPTIONS` environment variable which is evaluated by the Java VM during start up.

Using the example from above, the following environment variable definition needs to be added to limit the VM's heap size to 128MB:

~~~sh
...
> -e '_JAVA_OPTIONS=-Xmx128m' \
> eclipse/hono-adapter-mqtt-vertx:latest
~~~

## Run using the Docker Swarm Deployment Script

In most cases it is much easier to start all of Hono's components in one shot using the Docker Swarm deployment script provided in the `example/target/deploy/docker` folder.

## Run the Spring Boot Application

Sometimes it is helpful to run the adapter from its jar file, e.g. in order to attach a debugger more easily or to take advantage of code replacement.
In order to do so, the adapter can be started using the `spring-boot:run` maven goal from the `adapters/mqtt-vertx` folder.
The corresponding command to start up the adapter with the configuration used in the Docker example above looks like this:

~~~sh
~/hono/adapters/mqtt-vertx$ mvn spring-boot:run -Drun.arguments=\
> --hono.messaging.host=hono-service-messaging.hono,\
> --hono.messaging.username=mqtt-adapter@HONO,\
> --hono.messaging.password=mqtt-secret,\
> --hono.messaging.trustStorePath=target/certs/trusted-certs.pem \
> --hono.registration.host=hono-service-device-registry.hono,\
> --hono.registration.username=mqtt-adapter@HONO,\
> --hono.registration.password=mqtt-secret,\
> --hono.registration.trustStorePath=target/certs/trusted-certs.pem \
> --hono.mqtt.bindAddress=0.0.0.0 \
> --hono.mqtt.insecurePortEnabled=true,\
> --hono.mqtt.insecurePortBindAddress=0.0.0.0
~~~

{{% note %}}
In the example above the *--hono.messaging.host=hono-service-messaging.hono* command line option indicates that the Hono Messaging component is running on a host
with name *hono-service-messaging.hono*. However, if the Hono Messaging component has been started as a Docker container then the *hono-service-messaging.hono* host name will most likely only be resolvable on the network that Docker has created for running the container on, i.e. when you run the MQTT adapter
from the Spring Boot application and want it to connect to a Hono Messaging instance run as a Docker container then you need to set the
value of the *--hono.messaging.host* option to the IP address (or name) of the Docker host running the Hono Messaging container.
The same holds true analogously for the *hono-service-device-registry.hono* address.
{{% /note %}}

## Using the MQTT Adapter Topic Hierarchy

The MQTT adapter by default requires clients (devices or gateway components) to authenticate during connection establishment. In order to do so, clients need to provide a *username* and a *password* in the MQTT *CONNECT* packet. The *username* must have the form *auth-id@tenant*, e.g. `sensor1@DEFAULT_TENANT`. The adapter verifies the credentials provided by the client against the credentials the [configured Credentials service]({{< relref "#credentials-service-connection-configuration" >}}) has on record for the client. The adapter uses the Credentials API's *get* operation to retrieve the credentials on record with the *tenant* and *auth-id* provided by the client in the *username* and `hashed-password` as the *type* of secret as query parameters.

When running the Hono example installation as described in the [Getting Started guide]({{< relref "getting-started.md" >}}), the demo Credentials service comes pre-configured with a `hashed-password` secret for devices `4711` and `gw-1` of tenant `DEFAULT_TENANT` having *auth-ids* `sensor1` and `gw1` and (hashed) *passwords* `hono-secret` and `gw-secret` respectively. These credentials are used in the following examples illustrating the usage of the adapter. Please refer to the [Credentials API]({{< relref "api/Credentials-API.md#standard-credential-types" >}}) for details regarding the different types of secrets.

{{% note %}}
There is a subtle difference between the *device identifier* (*device-id*) and the *auth-id* a device uses for authentication. See [Device Identity]({{< relref "concepts/device-identity.md" >}}) for a discussion of the concepts.
{{% /note %}}

### Publish Telemetry Data (authenticated Device)

* Topic: `telemetry`
* Authentication: required
* Payload:
  * (required) Arbitrary payload

This is the preferred way for devices to publish telemetry data. It is available only if the protocol adapter is configured to require devices to authenticate (which is the default). When using this topic, the MQTT adapter determines the device's tenant and device identity as part of the authentication process.

**Example**

Publish some JSON data for device `4711`:

    $ mosquitto_pub -u 'sensor1@DEFAULT_TENANT' -P hono-secret -t telemetry -m '{"temp": 5}'

### Publish Telemetry Data (unauthenticated Device)

* Topic: `telemetry/${tenant-id}/${device-id}`
* Authentication: none
* Payload:
  * (required) Arbitrary payload

This topic can be used by devices that have not authenticated to the protocol adapter. Note that this requires the
`HONO_MQTT_AUTHENTICATION_REQUIRED` configuration property to be explicitly set to `false`.

**Examples**

Publish some JSON data for device `4711`:

    $ mosquitto_pub -t telemetry/DEFAULT_TENANT/4711 -m '{"temp": 5}'


### Publish Telemetry Data (authenticated Gateway)

* Topic: `telemetry/${tenant-id}/${device-id}`
* Authentication: required
* Payload:
  * (required) Arbitrary payload

This topic can be used by *gateway* components to publish data *on behalf of* other devices which do not connect to a protocol adapter directly but instead are connected to the gateway, e.g. using some low-bandwidth radio based technology like [SigFox](https://www.sigfox.com) or [LoRa](https://www.lora-alliance.org/). In this case the credentials provided by the gateway during connection establishment with the protocol adapter are used to authenticate the gateway whereas the parameters from the topic name are used to identify the device that the gateway publishes data for.

The protocol adapter checks the gateway's authority to publish data on behalf of the device implicitly by means of retrieving a *registration assertion* for the device from the [configured Device Registration service]({{< relref "#device-registration-service-connection-configuration" >}}).

**Examples**

Publish some JSON data for device `4712` via gateway `gw-1`:

    $ mosquitto_pub -u 'gw@DEFAULT_TENANT' -P gw-secret -t telemetry/DEFAULT_TENANT/4712 -m '{"temp": 5}'

**NB**: The example above assumes that a gateway device with ID `gw-1` has been registered with `hashed-password` credentials with *auth-id* `gw` and password `gw-secret`.

### Publish an Event (authenticated Device)

* Topic: `event`
* Authentication: required
* Payload:
  * (required) Arbitrary payload

This is the preferred way for devices to publish events. It is available only if the protocol adapter has been configured to require devices to authenticate (which is the default).

**Example**

Upload a JSON string for device `4711`:

    $ mosquitto_pub -u 'sensor1@DEFAULT_TENANT' -P hono-secret -t event -q 1 -m '{"alarm": 1}'

### Publish an Event (unauthenticated Device)

* Topic: `event/${tenant-id}/${device-id}`
* Authentication: none
* Payload:
  * (required) Arbitrary payload

This topic can be used by devices that have not authenticated to the protocol adapter. Note that this requires the
`HONO_MQTT_AUTHENTICATION_REQUIRED` configuration property to be explicitly set to `false`.

**Examples**

Publish some JSON data for device `4711`:

    $ mosquitto_pub -t event/DEFAULT_TENANT/4711 -q 1 -m '{"alarm": 1}'

### Publish an Event (authenticated Gateway)

* Topic: `event/${tenant-id}/${device-id}`
* Authentication: required
* Payload:
  * (required) Arbitrary payload

This topic can be used by *gateway* components to publish data *on behalf of* other devices which do not connect to a protocol adapter directly but instead are connected to the gateway, e.g. using some low-bandwidth radio based technology like [SigFox](https://www.sigfox.com) or [LoRa](https://www.lora-alliance.org/). In this case the credentials provided by the gateway during connection establishment with the protocol adapter are used to authenticate the gateway whereas the parameters from the topic name are used to identify the device that the gateway publishes data for.

The protocol adapter checks the gateway's authority to publish data on behalf of the device implicitly by means of retrieving a *registration assertion* for the device from the [configured Device Registration service]({{< relref "#device-registration-service-connection-configuration" >}}).

**Examples**

Publish some JSON data for device `4712` via gateway `gw-1`:

    $ mosquitto_pub -u 'gw@DEFAULT_TENANT' -P gw-secret -t event/DEFAULT_TENANT/4712 -q 1 -m '{"temp": 5}'

**NB**: The example above assumes that a gateway device with ID `gw-1` has been registered with `hashed-password` credentials with *auth-id* `gw` and password `gw-secret`.

## Downstream Meta Data

The adapter includes the following meta data in messages being sent downstream:

| Name               | Location        | Type      | Description                                                     |
| :----------------- | :-------------- | :-------- | :-------------------------------------------------------------- |
| `orig_adapter`   | *application*   | *string*  | Contains the adapter's *type name* which can be used by downstream consumers to determine the protocol adapter that the message has been received over. The MQTT adapter's type name is `hono-mqtt`. |
| `orig_address`   | *application*   | *string*  | Contains the name of the MQTT topic that the device has originally published the data to. |

The adapter also considers [*defaults* registered for the device]({{< relref "api/Device-Registration-API.md#payload-format" >}}). For each default value the adapter checks if a corresponding property is already set on the message and if not, sets the message's property to the registered default value or adds a corresponding application property.

Note that of the standard AMQP 1.0 message properties only the `content-type` can be set this way to a registered default value.
