+++
title = "Setup Device Registry"
weight = 290
+++

The Device Registry component exposes an AMQP 1.0 based service endpoint implementing Eclipse Hono&trade;'s [Device Registration]({{< relref "api/Device-Registration-API.md" >}}) API. 
The Device Registration API is used by other services to assert a device's registration status, e.g. if it is enabled and if it is registered with a particular tenant.

Furthermore, the Device Registry component exposes an AMQP 1.0 based service endpoint implementing Eclipse Hono&trade;'s [Credentials]({{< relref "api/Credentials-API.md" >}}) API.
The Credentials API is used by protocol adapters to authenticate a device before the adapter processes any transfered data.


<!--more-->

There is no particular technical reason to implement these two API's in one component, so for production scenarios there might be two different components each implementing one of the API's.

In addition, the Device Registry component can also be configured to expose [RESTful resources]({{< relref "user-guide/device-registry-http.md" >}}) representing the operations of the APIs 
which can be accessed via HTTP.

The Device Registry is implemented as a Spring Boot application. It can be run either directly from the command line or by means of starting the corresponding Docker image (`eclipse/hono-service-device-registry`) created from it.


## Service Configuration

The following table provides an overview of the configuration variables and corresponding command line options for configuring the Device Registry.

| Environment Variable<br>Command Line Option | Mandatory | Default | Description                                                             |
| :------------------------------------------ | :-------: | :------ | :-----------------------------------------------------------------------|
| `HONO_APP_MAX_INSTANCES`<br>`--hono.app.maxInstances` | no | *#CPU cores* | The number of verticle instances to deploy. If not set, one verticle per processor core is deployed. |
| `HONO_APP_HEALTH_CHECK_PORT`<br>`--hono.app.healthCheckPort` | no | - | The port that the HTTP server, which exposes the service's health check resources, should bind to. If set, the adapter will expose a *readiness* probe at URI `/readiness` and a *liveness* probe at URI `/liveness`. |
| `HONO_APP_HEALTH_CHECK_BIND_ADDRESS`<br>`--hono.app.healthCheckBindAddress` | no | `127.0.0.1` | The IP address of the network interface that the HTTP server, which exposes the service's health check resources, should be bound to. The HTTP server will only be started if `HONO_APP_HEALTH_CHECK_BIND_ADDRESS` is set explicitly. |
| `HONO_REGISTRY_AMQP_BIND_ADDRESS`<br>`--hono.registry.amqp.bindAddress` | no | `127.0.0.1` | The IP address of the network interface that the secure AMQP port should be bound to.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_REGISTRY_AMQP_CERT_PATH`<br>`--hono.registry.amqp.certPath` | no | - | The absolute path to the PEM file containing the certificate that the server should use for authenticating to clients. This option must be used in conjunction with `HONO_REGISTRY_AMQP_KEY_PATH`.<br>Alternatively, the `HONO_REGISTRY_AMQP_KEY_STORE_PATH` option can be used to configure a key store containing both the key as well as the certificate. |
| `HONO_REGISTRY_AMQP_INSECURE_PORT`<br>`--hono.registry.amqp.insecurePort` | no | - | The insecure port the server should listen on for AMQP 1.0 connections.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_REGISTRY_AMQP_INSECURE_PORT_BIND_ADDRESS`<br>`--hono.registry.amqp.insecurePortBindAddress` | no | `127.0.0.1` | The IP address of the network interface that the insecure AMQP port should be bound to.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_REGISTRY_AMQP_INSECURE_PORT_ENABLED`<br>`--hono.registry.amqp.insecurePortEnabled` | no | `false` | If set to `true` the server will open an insecure port (not secured by TLS) using either the port number set via `HONO_REGISTRY_AMQP_INSECURE_PORT` or the default AMQP port number (`5672`) if not set explicitly.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_REGISTRY_AMQP_KEY_PATH`<br>`--hono.registry.amqp.keyPath` | no | - | The absolute path to the (PKCS8) PEM file containing the private key that the server should use for authenticating to clients. This option must be used in conjunction with `HONO_REGISTRY_AMQP_CERT_PATH`. Alternatively, the `HONO_REGISTRY_AMQP_KEY_STORE_PATH` option can be used to configure a key store containing both the key as well as the certificate. |
| `HONO_REGISTRY_AMQP_KEY_STORE_PASSWORD`<br>`--hono.registry.amqp.keyStorePassword` | no | - | The password required to read the contents of the key store. |
| `HONO_REGISTRY_AMQP_KEY_STORE_PATH`<br>`--hono.registry.amqp.keyStorePath` | no | - | The absolute path to the Java key store containing the private key and certificate that the server should use for authenticating to clients. Either this option or the `HONO_REGISTRY_AMQP_KEY_PATH` and `HONO_REGISTRY_AMQP_CERT_PATH` options need to be set in order to enable TLS secured connections with clients. The key store format can be either `JKS` or `PKCS12` indicated by a `.jks` or `.p12` file suffix respectively. |
| `HONO_REGISTRY_AMQP_PORT`<br>`--hono.registry.amqp.port` | no | `5671` | The secure port that the server should listen on for AMQP 1.0 connections.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_REGISTRY_REST_BIND_ADDRESS`<br>`--hono.registry.rest.bindAddress` | no | `127.0.0.1` | The IP address of the network interface that the secure HTTP port should be bound to.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_REGISTRY_REST_CERT_PATH`<br>`--hono.registry.rest.certPath` | no | - | The absolute path to the PEM file containing the certificate that the server should use for authenticating to clients. This option must be used in conjunction with `HONO_REGISTRY_REST_KEY_PATH`.<br>Alternatively, the `HONO_REGISTRY_REST_KEY_STORE_PATH` option can be used to configure a key store containing both the key as well as the certificate. |
| `HONO_REGISTRY_REST_INSECURE_PORT`<br>`--hono.registry.rest.insecurePort` | no | - | The insecure port the server should listen on for HTTP requests.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_REGISTRY_REST_INSECURE_PORT_BIND_ADDRESS`<br>`--hono.registry.rest.insecurePortBindAddress` | no | `127.0.0.1` | The IP address of the network interface that the insecure HTTP port should be bound to.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_REGISTRY_REST_INSECURE_PORT_ENABLED`<br>`--hono.registry.rest.insecurePortEnabled` | no | `false` | If set to `true` the server will open an insecure port (not secured by TLS) using either the port number set via `HONO_REGISTRY_REST_INSECURE_PORT` or the default AMQP port number (`5672`) if not set explicitly.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_REGISTRY_REST_KEY_PATH`<br>`--hono.registry.rest.keyPath` | no | - | The absolute path to the (PKCS8) PEM file containing the private key that the server should use for authenticating to clients. This option must be used in conjunction with `HONO_REGISTRY_REST_CERT_PATH`. Alternatively, the `HONO_REGISTRY_REST_KEY_STORE_PATH` option can be used to configure a key store containing both the key as well as the certificate. |
| `HONO_REGISTRY_REST_KEY_STORE_PASSWORD`<br>`--hono.registry.rest.keyStorePassword` | no | - | The password required to read the contents of the key store. |
| `HONO_REGISTRY_REST_KEY_STORE_PATH`<br>`--hono.registry.rest.keyStorePath` | no | - | The absolute path to the Java key store containing the private key and certificate that the server should use for authenticating to clients. Either this option or the `HONO_REGISTRY_REST_KEY_PATH` and `HONO_REGISTRY_REST_CERT_PATH` options need to be set in order to enable TLS secured connections with clients. The key store format can be either `JKS` or `PKCS12` indicated by a `.jks` or `.p12` file suffix respectively. |
| `HONO_REGISTRY_REST_PORT`<br>`--hono.registry.rest.port` | no | `5671` | The secure port that the server should listen on for HTTP requests.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_REGISTRY_SVC_FILENAME`<br>`--hono.registry.svc.filename` | no | `/var/lib/hono/device-registry/`<br>`device-identities.json` | The path to the file where the server stores identities of registered devices. Hono tries to read device identities from this file during start-up and writes out all identities to this file periodically if property `HONO_REGISTRY_SVC_SAVE_TO_FILE` is set to `true`.<br>Please refer to [Device Identities File Format]({{< relref "#device-identities-file-format" >}}) for details regarding the file's format. |
| `HONO_REGISTRY_SVC_MAX_DEVICES_PER_TENANT`<br>`--hono.registry.svc.maxDevicesPerTenant` | no | `100` | The number of devices that can be registered for each tenant. It is an error to set this property to a value <= 0. |
| `HONO_REGISTRY_SVC_MODIFICATION_ENABLED`<br>`--hono.registry.svc.modificationEnabled` | no | `true` | When set to `false` the device information contained in the registry cannot be updated nor removed from the registry. |
| `HONO_REGISTRY_SVC_SAVE_TO_FILE`<br>`--hono.registry.svc.saveToFile` | no | `false` | When set to `true` the server will periodically write out the registered device information to the file specified by the `HONO_REGISTRY_FILENAME` property. |
| `HONO_REGISTRY_SVC_SIGNING_KEY_PATH`<br>`--hono.registry.svc.signing.keyPath` | no  | - | The absolute path to the (PKCS8) PEM file containing the private key that the server should use for signing tokens asserting a device's registration status. When using this variable, other services that need to validate the tokens issued by this service need to be configured with the corresponding certificate/public key. Alternatively, a symmetric key can be used for signing (and validating) by setting the `HONO_REGISTRY_SVC_SIGNING_SHARED_SECRET` variable. If none of these variables is set, the server falls back to the key indicated by the `HONO_REGISTRY_AMP_KEY_PATH` variable. If that variable is also not set, startup of the server fails. |
| `HONO_REGISTRY_SVC_SIGNING_SHARED_SECRET`<br>`--hono.registry.svc.signing.sharedSecret` | no  | - | A string to derive a symmetric key from that is used for signing tokens asserting a device's registration status. The key is derived from the string by using the bytes of the String's UTF8 encoding. When setting the signing key using this variable, other services that need to validate the tokens issued by this service need to be configured with the same key. Alternatively, an asymmetric key pair can be used for signing (and validating) by setting the `HONO_REGISTRY_SVC_SIGNING_KEY_PATH` variable. If none of these variables is set, startup of the server fails. |
| `HONO_REGISTRY_SVC_SIGNING_TOKEN_EXPIRATION`<br>`--hono.registry.svc.signing.tokenExpiration` | no | `10` | The expiration period to use for the tokens asserting the registration status of devices. |
| `HONO_CREDENTIALS_SVC_CREDENTIALS_FILENAME`<br>`--hono.credentials.svc.credentialsFilename` | no | `/var/lib/hono/device-registry/`<br>`credentials.json` | The path to the file where the server stores credentials of devices. Hono tries to read credentials from this file during start-up and writes out all identities to this file periodically if property `HONO_CREDENTIALS_SVC_SAVE_TO_FILE` is set to `true`.<br>Please refer to [Credentials File Format]({{< relref "#credentials-file-format" >}}) for details regarding the file's format. |
| `HONO_CREDENTIALS_SVC_MODIFICATION_ENABLED`<br>`--hono.credentials.svc.modificationEnabled` | no | `true` | When set to `false` the credentials contained in the registry cannot be updated nor removed. |
| `HONO_CREDENTIALS_SVC_SAVE_TO_FILE`<br>`--hono.credentials.svc.saveToFile` | no | `false` | When set to `true` the server will periodically write out the registered credentials to the file specified by the `HONO_CREDENTIALS_SVC_CREDENTIALS_FILENAME` property. |

The variables only need to be set if the default value does not match your environment.

## Port Configuration

The Device Registry supports configuration of both, an AMQP based endpoint as well as an HTTP based endpoint proving RESTful resources for managing registration information and credentials. Both endpoints can be configured to listen for connections on

* a secure port only (default) or
* an insecure port only or
* both a secure and an insecure port (dual port configuration)

The server will fail to start if none of the ports is configured properly.

The following sections apply to configuring both, the AMQP endpoint as well as the REST endpoint. The environment variables to use for configuring the REST endpoint are the same as the ones for the AMQP endpoint, substituting `_AMQP_` with `_REST_`, e.g. `HONO_REGISTRY_REST_KEY_PATH` instead of `HONO_REGISTRY_AMQP_KEY_PATH`.

### Secure Port Only

The server needs to be configured with a private key and certificate in order to open a TLS secured port.

There are two alternative ways for doing so:

1. Setting the `HONO_REGISTRY_AMQP_KEY_STORE_PATH` and the `HONO_REGISTRY_AMQP_KEY_STORE_PASSWORD` variables in order to load the key & certificate from a password protected key store, or
1. setting the `HONO_REGISTRY_AMQP_KEY_PATH` and `HONO_REGISTRY_AMQP_CERT_PATH` variables in order to load the key and certificate from two separate PEM files in PKCS8 format.

When starting up, the server will bind a TLS secured socket to the default secure AMQP port 5671. The port number can also be set explicitly using the `HONO_REGISTRY_AMQP_PORT` variable.

The `HONO_REGISTRY_AMQP_BIND_ADDRESS` variable can be used to specify the network interface that the port should be exposed on. By default the port is bound to the *loopback device* only, i.e. the port will only be accessible from the local host. Setting this variable to `0.0.0.0` will let the port being bound to **all** network interfaces (be careful not to expose the port unintentionally to the outside world).

### Insecure Port Only

The secure port will mostly be required for production scenarios. However, it might be desirable to expose a non-TLS secured port instead, e.g. for testing purposes. In any case, the non-secure port needs to be explicitly enabled either by

- explicitly setting `HONO_REGISTRY_AMQP_INSECURE_PORT` to a valid port number, or by
- implicitly configuring the default AMQP port (5672) by simply setting `HONO_REGISTRY_AMQP_INSECURE_PORT_ENABLED` to `true`.

The server issues a warning on the console if `HONO_REGISTRY_AMQP_INSECURE_PORT` is set to the default secure AMQP port (5671).

The `HONO_REGISTRY_AMQP_INSECURE_PORT_BIND_ADDRESS` variable can be used to specify the network interface that the port should be exposed on. By default the port is bound to the *loopback device* only, i.e. the port will only be accessible from the local host. This variable might be used to e.g. expose the non-TLS secured port on a local interface only, thus providing easy access from within the local network, while still requiring encrypted communication when accessed from the outside over public network infrastructure.

Setting this variable to `0.0.0.0` will let the port being bound to **all** network interfaces (be careful not to expose the port unintentionally to the outside world).

### Dual Port
 
In test setups and some production scenarios Hono server may be configured to open one secure **and** one insecure port at the same time.
 
This is achieved by configuring both ports correctly (see above). The server will fail to start if both ports are configured to use the same port number.

Since the secure port may need different visibility in the network setup compared to the secure port, it has it's own binding address `HONO_REGISTRY_AMQP_INSECURE_PORT_BIND_ADDRESS`. 
This can be used to narrow the visibility of the insecure port to a local network e.g., while the secure port may be visible worldwide. 

### Ephemeral Ports

The server may be configured to open both a secure and a non-secure port at the same time simply by configuring both ports as described above. For this to work, both ports must be configured to use different port numbers, otherwise startup will fail.

## Authentication Service Connection Configuration

The Device Registry requires a connection to an implementation of Hono's Authentication API in order to authenticate and authorize client requests.
The following table provides an overview of the configuration variables and corresponding command line options for configuring the service's connection to the Authentication service.

| Environment Variable<br>Command Line Option | Mandatory | Default | Description                                                             |
| :------------------------------------------ | :-------: | :------ | :-----------------------------------------------------------------------|
| `HONO_AUTH_HOST`<br>`--hono.auth.host` | yes | `localhost` | The IP address or name of the Authentication service host. NB: This needs to be set to an address that can be resolved within the network the service runs on. When running as a Docker container, use Docker's `--network` command line option to attach the Device Registry container to the Docker network that the Authentication service container is running on. |
| `HONO_AUTH_CERT_PATH`<br>`--hono.auth.certPath` | no | - | The absolute path to the PEM file containing the public key that the service should use to authenticate when verifying reachability of the Authentication service as part of a periodic health check. The health check needs to be enabled explicitly by means of setting the `HONO_APP_HEALTH_CHECK_PORT` variable. This variable needs to be set in conjunction with `HONO_AUTH_KEY_PATH`. |
| `HONO_AUTH_KEY_PATH`<br>`--hono.auth.keyPath` | no | - | The absolute path to the PEM file containing the private key that the service should use to authenticate when verifying reachability of the Authentication service as part of a periodic health check. The health check needs to be enabled explicitly by means of setting the `HONO_APP_HEALTH_CHECK_PORT` variable. This variable needs to be set in conjunction with `HONO_AUTH_CERT_PATH`. |
| `HONO_AUTH_PORT`<br>`--hono.auth.port` | yes | `5671` | The port that the Authentication service is listening on for connections. |
| `HONO_AUTH_TRUST_STORE_PASSWORD`<br>`--hono.auth.trustStorePassword` | no | - | The password required to read the contents of the trust store. |
| `HONO_AUTH_TRUST_STORE_PATH`<br>`--hono.auth.trustStorePath` | no  | - | The absolute path to the Java key store containing the CA certificates the service uses for authenticating the Authentication service. This property **must** be set if the Authentication service has been configured to use TLS. The key store format can be either `JKS`, `PKCS12` or `PEM` indicated by a `.jks`, `.p12` or `.pem` file suffix. |
| `HONO_AUTH_VALIDATION_CERT_PATH`<br>`--hono.auth.validation.certPath` | no  | - | The absolute path to the PEM file containing the public key that the service should use for validating tokens issued by the Authentication service. Alternatively, a symmetric key can be used for validating tokens by setting the `HONO_AUTH_VALIDATION_SHARED_SECRET` variable. If none of these variables is set, the service falls back to the key indicated by the `HONO_AUTH_CERT_PATH` variable. If that variable is also not set, startup of the service fails. |
| `HONO_AUTH_VALIDATION_SHARED_SECRET`<br>`--hono.auth.validation.sharedSecret` | no  | - | A string to derive a symmetric key from which is used for validating tokens issued by the Authentication service. The key is derived from the string by using the bytes of the String's UTF8 encoding. When setting the validation key using this variable, the Authentication service **must** be configured with the same key. Alternatively, an asymmetric key pair can be used for validating (and signing) by setting the `HONO_AUTH_SIGNING_CERT_PATH` variable. If none of these variables is set, startup of the service fails. |

## Device Identities File Format

The Device Registry supports persisting the device identities and their registration information to a JSON file in the local file system.
The *Getting started Guide* includes an example configuration which illustrates the file format used. The configuration file's location is `/example/src/main/config/example-device-identities.json`.

## Credentials File Format

The Device Registry supports persisting the devices' credentials to a JSON file in the local file system.
The *Getting started Guide* includes an example configuration which illustrates the file format used. The configuration file's location is `/example/src/main/config/example-credentials.json`.

## Configuring Gateway Devices

The Device Registry supports devices to *act on behalf of* other devices. This is particularly useful for cases where a device does not connect directly to a Hono protocol adapter but is connected to a *gateway* component that is usually specific to the device's communication protocol. It is the gateway component which then connects to a Hono protocol adapter and publishes data on behalf of the device(s). Examples of such a set up include devices using [SigFox](https://www.sigfox.com) or [LoRa](https://www.lora-alliance.org/) for communication.

In these cases the protocol adapter will usually authenticate the gateway component instead of the device for which it wants to publish data. In order to verify that the gateway is *authorized* to publish data on behalf of the particular device, the protocol adapter should include the gateway's device identifier (as determined during the authentication process) in its invocation of the Device Registry's *assert Device Registration* operation.

The Device Registry will then do the following:
1. Verify that the device exists and is enabled.
1. Verify that the gateway exists and is enabled.
2. Verify that the device's registration information contains a property called `via` and that its value is the gateway's device identifier.

Only if all conditions are met, the Device Registry returns an assertion of the device's registration status. The protocol adapter can then forward the published data to Hono Messaging in the same way as for any device that connects directly to the adapter.

The example configuration file (located at `/example/src/main/config/example-device-identities.json`) includes a device and a corresponding gateway configured in this way.

## Run as a Docker Swarm Service

The Device Registry can be run as a Docker container from the command line. The following commands create and start the Device Registry as a Docker Swarm service using the default keys and configuration files contained in the `demo-certs` and `services/device-registry` modules:

~~~sh
~/hono$ docker secret create auth-server-key.pem demo-certs/certs/auth-server-key.pem
~/hono$ docker secret create auth-server-cert.pem demo-certs/certs/auth-server-cert.pem
~/hono$ docker secret create device-registry-key.pem demo-certs/certs/device-registry-key.pem
~/hono$ docker secret create device-registry-cert.pem demo-certs/certs/device-registry-cert.pem
~/hono$ docker secret create trusted-certs.pem demo-certs/certs/trusted-certs.pem
~/hono$ docker secret create device-identities.json services/device-registry/src/test/resources/device-identities.json
~/hono$ docker service create --detach --name hono-service-device-registry --network hono-net -p 25671:5671 \
> --secret auth-server-key.pem \
> --secret auth-server-cert.pem \
> --secret device-registry-key.pem \
> --secret device-registry-cert.pem \
> --secret trusted-certs.pem \
> --secret device-identities.json \
> --secret credentials.json \
> -e 'HONO_AUTH_HOST=<name or address of the auth-server>' \
> -e 'HONO_AUTH_NAME=device-registry' \
> -e 'HONO_AUTH_VALIDATION_CERT_PATH=/run/secrets/auth-server-cert.pem' \
> -e 'HONO_AUTH_TRUST_STORE_PATH=/run/secrets/trusted-certs.pem' \
> -e 'HONO_REGISTRY_AMQP_BIND_ADDRESS=0.0.0.0'
> -e 'HONO_REGISTRY_AMQP_KEY_PATH=/run/secrets/device-registry-key.pem' \
> -e 'HONO_REGISTRY_AMQP_CERT_PATH=/run/secrets/device-registry-cert.pem' \
> -e 'HONO_REGISTRY_SVC_FILENAME=file:/run/secrets/device-identities.json' \
> -e 'HONO_CREDENTIALS_SVC_CREDENTIALS_FILENAME=file:/run/secrets/credentials.json' \
> -e 'HONO_REGISTRY_SVC_SIGNING_SHARED_SECRET=asharedsecretforvalidatingassertions' \
> eclipse/hono-service-device-registry:latest
~~~

{{% note %}}
There are several things noteworthy about the above command to start the service:

1. The *secrets* need to be created once only, i.e. they only need to be removed and re-created if they are changed.
1. The *--network* command line switch is used to specify the *user defined* Docker network that the Hono components should attach to. This is important so that other components can use Docker's DNS service to look up the (virtual) IP address of the server when they want to connect to it.
Please refer to the [Docker Networking Guide](https://docs.docker.com/engine/userguide/networking/#/user-defined-networks) for details regarding how to create a *user defined* network in Docker.
1. In cases where the server container requires a lot of configuration via environment variables (provided by means of *-e* switches), it is more convenient to add all environment variable definitions to a separate *env file* and refer to it using Docker's *--env-file* command line switch when starting the container. This way the command line to start the container is much shorter and can be copied and edited more easily.
{{% /note %}}

### Configuring the Java VM

The Device Registry Docker image by default does not pass any specific configuration options to the Java VM. The VM can be configured using the standard `-X` options by means of setting the `_JAVA_OPTIONS` environment variable which is evaluated by the Java VM during start up.

Using the example from above, the following environment variable definition needs to be added to limit the VM's heap size to 128MB:

~~~sh
...
> -e '_JAVA_OPTIONS=-Xmx128m' \
> eclipse/hono-service-device-registry:latest
~~~

## Run using the Docker Swarm Deployment Script

In most cases it is much easier to start all of Hono's components in one shot using the Docker Swarm deployment script provided in the `example/target/deploy/docker` folder.

## Run the Spring Boot Application

Sometimes it is helpful to run the Device Registry server from its jar file, e.g. in order to attach a debugger more easily or to take advantage of code replacement.
In order to do so, the server can be started using the `spring-boot:run` maven goal from the `services/device-registry` folder.
The corresponding command to start up the server with the configuration used in the Docker example above looks like this:

~~~sh
~/hono/services/device-registry $ mvn spring-boot:run -Drun.arguments=\
> --hono.registry.amqp.bindAddress=0.0.0.0,\
> --hono.registry.amqp.keyPath=target/certs/device-registry-key.pem,\
> --hono.registry.amqp.certPath=target/certs/device-registry-cert.pem,\
> --hono.registry.amqp.trustStorePath=target/certs/trusted-certs.pem,\
> --hono.auth.host=localhost,\
> --hono.auth.name='device-registry',\
> --hono.auth.validation.certPath=target/certs/auth-server-cert.pem,\
> --hono.auth.trustStorePath=target/certs/trusted-certs.pem
~~~

{{% note %}}
You may want to make logging of the server a little more verbose by enabling the *dev* Spring profile.
To do so, append *-Drun.profiles=default,dev* to the command line.

Replace **localhost** by the name or the IP address of the auth-server you are using, if you do not start it on localhost as well.
{{% /note %}}
