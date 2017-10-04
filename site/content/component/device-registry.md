+++
title = "Device Registry"
weight = 340
+++

The Device Registry component exposes an AMQP 1.0 based service endpoint implementing Eclipse Hono&trade;'s [Device Registration]({{< relref "api/Device-Registration-API.md" >}}) API. In addition, it can also be configured to expose RESTful resources representing the operations of the Device Registration API which can be accessed via HTTP.
The Device Registration API is used by other services to assert a device's registration status, e.g. if it is enabled and if it is registered with a particular tenant.

Furthermore, the Device Registry component exposes an AMQP 1.0 based service endpoint implementing Eclipse Hono&trade;'s [Credentials]({{< relref "api/Credentials-API.md" >}}) API.
The Credentials API is used by protocol adapters to authenticate a device before the adapter processes any transfered data.

There is no particular technical reason to implement these two API's in one component, so for production scenarios there might be two different components each implementing one of the API's.

<!--more-->

## Configuration

The Device Registry is implemented as a Spring Boot application. It can be run either directly from the command line or by means of starting the corresponding Docker image (`eclipsehono/hono-service-device-registry`) created from it.

The service can be configured by means of environment variables or corresponding command line options.

### Authentication Service Connection Configuration

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

### Service Configuration

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
| `HONO_REGISTRY_AMQP_TRUST_STORE_PASSWORD`<br>`--hono.registry.amqp.trustStorePassword` | no | - | The password required to read the contents of the trust store. |
| `HONO_REGISTRY_AMQP_TRUST_STORE_PATH`<br>`--hono.registry.amqp.trustStorePath` | no  | - | The absolute path to the Java key store containing the CA certificates the Hono server uses for authenticating clients. The key store format can be either `JKS`, `PKCS12` or `PEM` indicated by a `.jks`, `.p12` or `.pem` file suffix respectively. |
| `HONO_REGISTRY_REST_BIND_ADDRESS`<br>`--hono.registry.rest.bindAddress` | no | `127.0.0.1` | The IP address of the network interface that the secure HTTP port should be bound to.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_REGISTRY_REST_CERT_PATH`<br>`--hono.registry.rest.certPath` | no | - | The absolute path to the PEM file containing the certificate that the server should use for authenticating to clients. This option must be used in conjunction with `HONO_REGISTRY_REST_KEY_PATH`.<br>Alternatively, the `HONO_REGISTRY_REST_KEY_STORE_PATH` option can be used to configure a key store containing both the key as well as the certificate. |
| `HONO_REGISTRY_REST_INSECURE_PORT`<br>`--hono.registry.rest.insecurePort` | no | - | The insecure port the server should listen on for HTTP requests.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_REGISTRY_REST_INSECURE_PORT_BIND_ADDRESS`<br>`--hono.registry.rest.insecurePortBindAddress` | no | `127.0.0.1` | The IP address of the network interface that the insecure HTTP port should be bound to.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_REGISTRY_REST_INSECURE_PORT_ENABLED`<br>`--hono.registry.rest.insecurePortEnabled` | no | `false` | If set to `true` the server will open an insecure port (not secured by TLS) using either the port number set via `HONO_REGISTRY_REST_INSECURE_PORT` or the default AMQP port number (`5672`) if not set explicitly.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_REGISTRY_REST_KEY_PATH`<br>`--hono.registry.rest.keyPath` | no | - | The absolute path to the (PKCS8) PEM file containing the private key that the server should use for authenticating to clients. This option must be used in conjunction with `HONO_REGISTRY_REST_CERT_PATH`. Alternatively, the `HONO_REGISTRY_REST_KEY_STORE_PATH` option can be used to configure a key store containing both the key as well as the certificate. |
| `HONO_REGISTRY_REST_KEY_STORE_PASSWORD`<br>`--hono.registry.rest.keyStorePassword` | no | - | The password required to read the contents of the key store. |
| `HONO_REGISTRY_REST_KEY_STORE_PATH`<br>`--hono.registry.rest.keyStorePath` | no | - | The absolute path to the Java key store containing the private key and certificate that the server should use for authenticating to clients. Either this option or the `HONO_REGISTRY_REST_KEY_PATH` and `HONO_REGISTRY_REST_CERT_PATH` options need to be set in order to enable TLS secured connections with clients. The key store format can be either `JKS` or `PKCS12` indicated by a `.jks` or `.p12` file suffix respectively. |
| `HONO_REGISTRY_REST_PORT`<br>`--hono.registry.rest.port` | no | `5671` | The secure port that the server should listen on for HTTP requests.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_REGISTRY_REST_TRUST_STORE_PASSWORD`<br>`--hono.registry.rest.trustStorePassword` | no | - | The password required to read the contents of the trust store. |
| `HONO_REGISTRY_REST_TRUST_STORE_PATH`<br>`--hono.registry.rest.trustStorePath` | no  | - | The absolute path to the Java key store containing the CA certificates the Hono server uses for authenticating clients. The key store format can be either `JKS`, `PKCS12` or `PEM` indicated by a `.jks`, `.p12` or `.pem` file suffix respectively. |
| `HONO_REGISTRY_SVC_FILENAME`<br>`--hono.registry.svc.filename` | no | `/home/hono/registration/`<br>`device-identities.json` | The path to the file where the server stores identities of registered devices. Hono tries to read device identities from this file during start-up and writes out all identities to this file periodically if property `HONO_REGISTRY_SVC_SAVE_TO_FILE` is set to `true`. The `eclipsehono/hono-service-device-registry` Docker image creates a volume under `/home/hono/registration` so that registration information survives container restarts and/or image updates. If you are running the Hono server from the command line you will probably want to set this variable to a path using an existing folder since Hono will not try to create the path. |
| `HONO_REGISTRY_SVC_MAX_DEVICES_PER_TENANT`<br>`--hono.registry.svc.maxDevicesPerTenant` | no | `100` | The number of devices that can be registered for each tenant. It is an error to set this property to a value <= 0. |
| `HONO_REGISTRY_SVC_MODIFICATION_ENABLED`<br>`--hono.registry.svc.modificationEnabled` | no | `true` | When set to `false` the device information registered cannot be updated nor removed from the registry. |
| `HONO_REGISTRY_SVC_SAVE_TO_FILE`<br>`--hono.registry.svc.saveToFile` | no | `false` | When set to `true` the server will periodically write out the registered device information to the file specified by the `HONO_REGISTRY_FILENAME` property. |
| `HONO_REGISTRY_SVC_SIGNING_KEY_PATH`<br>`--hono.registry.svc.signing.keyPath` | no  | - | The absolute path to the (PKCS8) PEM file containing the private key that the server should use for signing tokens asserting a device's registration status. When using this variable, other services that need to validate the tokens issued by this service need to be configured with the corresponding certificate/public key. Alternatively, a symmetric key can be used for signing (and validating) by setting the `HONO_REGISTRY_SVC_SIGNING_SHARED_SECRET` variable. If none of these variables is set, the server falls back to the key indicated by the `HONO_REGISTRY_AMP_KEY_PATH` variable. If that variable is also not set, startup of the server fails. |
| `HONO_REGISTRY_SVC_SIGNING_SHARED_SECRET`<br>`--hono.registry.svc.signing.sharedSecret` | no  | - | A string to derive a symmetric key from that is used for signing tokens asserting a device's registration status. The key is derived from the string by using the bytes of the String's UTF8 encoding. When setting the signing key using this variable, other services that need to validate the tokens issued by this service need to be configured with the same key. Alternatively, an asymmetric key pair can be used for signing (and validating) by setting the `HONO_REGISTRY_SVC_SIGNING_KEY_PATH` variable. If none of these variables is set, startup of the server fails. |
| `HONO_REGISTRY_SVC_SIGNING_TOKEN_EXPIRATION`<br>`--hono.registry.svc.signing.tokenExpiration` | no | `10` | The expiration period to use for the tokens asserting the registration status of devices. |
| `HONO_CREDENTIALS_SVC_CREDENTIALS_FILENAME`<br>`--hono.credentials.svc.credentialsFilename` | no | `/home/hono/registration/`<br>`credentials.json` | The path to the file where the server stores credentials of devices. Hono tries to read credentials from this file during start-up and writes out all identities to this file periodically if property `HONO_CREDENTIALS_SVC_SAVE_TO_FILE` is set to `true`. The `eclipsehono/hono-service-device-registry` Docker image creates a volume under `/home/hono/registration` so that credentials information survives container restarts and/or image updates. If you are running the Hono server from the command line you will probably want to set this variable to a path using an existing folder since Hono will not try to create the path. |
| `HONO_CREDENTIALS_SVC_MODIFICATION_ENABLED`<br>`--hono.credentials.svc.modificationEnabled` | no | `true` | When set to `false` the credentials stored cannot be updated nor removed. |
| `HONO_CREDENTIALS_SVC_SAVE_TO_FILE`<br>`--hono.credentials.svc.saveToFile` | no | `false` | When set to `true` the server will periodically write out the registered credentials to the file specified by the `HONO_CREDENTIALS_SVC_CREDENTIALS_FILENAME` property. |

The variables only need to be set if the default value does not match your environment.

## Port Configuration

The Device Registry can be configured to listen for connections on

* a secure port only (default) or
* an insecure port only or
* both a secure and an insecure port (dual port configuration)

The server will fail to start if none of the ports is configured properly.

### Secure Port Only

The server needs to be configured with a private key and certificate in order to open a TLS secured port.

There are two alternative ways for doing so:

1. either setting the `HONO_REGISTRY_AMQP_KEY_STORE_PATH` and the `HONO_REGISTRY_AMQP_KEY_STORE_PASSWORD` variables in order to load the key & certificate from a password protected key store, or
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
> eclipsehono/hono-service-device-registry:latest
~~~

{{% note %}}
There are several things noteworthy about the above command to start the service:

1. The *secrets* need to be created once only, i.e. they only need to be removed and re-created if they are changed.
1. The *--network* command line switch is used to specify the *user defined* Docker network that the Hono components should attach to. This is important so that other components can use Docker's DNS service to look up the (virtual) IP address of the server when they want to connect to it.
Please refer to the [Docker Networking Guide](https://docs.docker.com/engine/userguide/networking/#/user-defined-networks) for details regarding how to create a *user defined* network in Docker.
1. In cases where the server container requires a lot of configuration via environment variables (provided by means of *-e* switches), it is more convenient to add all environment variable definitions to a separate *env file* and refer to it using Docker's *--env-file* command line switch when starting the container. This way the command line to start the container is much shorter and can be copied and edited more easily.
{{% /note %}}

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

## Using the Registration API via HTTP

The Device Registry also exposes RESTful resource for invoking operations of the Device Registration API via HTTP. Please note that this mapping to RESTful resources is **not** part of the *official* definition of the Device Registration API but has been implemented for convenient access to the registry using command line tools like *curl* or *HTTPie*.

The following sections describe the resources representing the operations of the Device Registration API and how they can be used to e.g. register a device.

### Register Device

* URI: `/registration/${tenantId}`
* Method: `POST`
* Headers:
  * (required) `Content-Type`: `application/json`
* Parameters (encoded as a JSON object in the request body):
  * (required) `device-id`: The ID of the device to register.
  * (optional) Arbitrary key/value pairs containing additional data to be registered with the device.
* Status Codes:
  * 201 (Created): Device has been registered successfully under resource indicated by `Location` header.
  * 400 (Bad Request): Device has not been registered because the request was malformed, e .g. a required header is missing (the body may contain hints regarding the problem).
  * 409 (Conflict): There already exists a device with the given ID. The request has not been processed.

**Example**

The following command registers a device with ID `4711`

    $ curl -i -X POST -H 'Content-Type: application/json' --data-binary '{"device-id": "4711", "ep": "IMEI4711"}' http://localhost:28080/registration/DEFAULT_TENANT

The response will contain a `Location` header containing the resource path created for the device. In this example it will look
like this:

    HTTP/1.1 201 Created
    Location: /registration/DEFAULT_TENANT/4711
    Content-Length: 0

### Read Registration

* URI: `/registration/${tenantId}/${deviceId}`
* Method: `GET`
* Status Codes:
  * 200 (OK): Device has been found, body contains registration data.
  * 404 (Not Found): No device with the given identifier is registered for the given tenant.

**Example**

The following command retrieves registration data for device `4711`:

    $ curl -i http://localhost:28080/registration/DEFAULT_TENANT/4711

The response will look similar to this:

    HTTP/1.1 200 OK
    Content-Type: application/json; charset=utf-8
    Content-Length: 35

    {
      "data" : {
         "enabled": true,
         "ep": "IMEI4711"
      },
      "device-id" : "4711"
    }

### Update Registration

* URI: `/registration/${tenantId}/${deviceId}`
* Method: `PUT`
* Headers:
  * (required) `Content-Type`: `application/json`
* Parameters (encoded as a JSON object in the request body):
  * (optional) Arbitrary key/value pairs containing additional data to be registered with the device. The existing key/value pairs will be replaced with these key/values.
* Status Codes:
  * 204 (No Content): Device registration data has been updated.
  * 400 (Bad Request): Device registration has not been updated because the request was malformed, e .g. a required header is missing (the body may contain hints regarding the problem).
  * 404 (Not Found): No device with the given identifier is registered for the given tenant.

**Example**

    $ curl -i -X PUT -H 'Content-Type: application/json' --data-binary '{"ep": "IMEI4711", "psk-id": "psk4711"}' http://localhost:28080/registration/DEFAULT_TENANT/4711

The response will look similar to this:

    HTTP/1.1 204 No Content
    Content-Length: 0

### Delete Registration

* URI: `/registration/${tenantId}/${deviceId}`
* Method: `DELETE`
* Status Codes:
  * 204 (No Content): Device registration has been deleted.
  * 404 (Not Found): No device with the given identifier is registered for the given tenant.

**Example**

    $ curl -i -X DELETE http://localhost:28080/registration/DEFAULT_TENANT/4711

The response will look similar to this:

    HTTP/1.1 204 No Content
    Content-Length: 0
    
## Using the Credentials API via HTTP

The Device Registry also exposes RESTful resources for invoking operations of the Credentials API via HTTP. Please note that this mapping to RESTful resources is **not** part of the *official* of the Credentials API but is provided for convenient access to credentials data using command line tools like *curl* or *HTTPie*.

**NB** The *update credentials* operation is not implemented (yet). As a workaround, the *remove credentials* in combination with *add credentials* can be used instead.

The following sections describe the resources representing the REST operations of the Credentials API and how they can be used to e.g. add credentials for a device.
Please refer to the [Credentials API]({{< relref "api/Credentials-API.md" >}}) for the specific elements that are explained in detail there.

### Add Credentials for a Device

* URI: `/credentials/${tenantId}`
* Method: `POST`
* Request Headers:
  * (required) `Content-Type`: `application/json` (no other type supported)
* Request Body (encoded as a JSON object):
  * (required) `device-id`: The ID of the device to add the credentials for.
  * (required) `type`: The type of the credentials to add.
  * (required) `auth-id`: The identity that the device will use for authentication.
  * (required) `secrets`: The secrets of the credentials to add. This is a JSON array and must contain at least one element. The content of each element is defined in the [Credentials API]({{< relref "api/Credentials-API.md" >}}).
* Status Codes:
  * 201 (Created): Credentials have been added successfully under the resource indicated by `Location` header.
  * 400 (Bad Request): Credentials have not been added because the request was malformed, e .g. the payload did not contain required values (the body may contain hints regarding the problem).
  * 409 (Conflict): Credentials of the given type for the given *auth-id* already exist for the device. The request has not been processed.
* Response Headers:
  * `Location`: the URI the newly created resource can be accessed at.

**Example**

The following command adds credentials for device `4720` using type `hashed-password` and auth-id `sensor20`:

    $ curl -i -X POST -H 'Content-Type: application/json' \
    > --data-binary '{"device-id": "4720", "type": "hashed-password", "auth-id": "sensor20", "secrets": [{"hash-function" : "sha-512", \
    > "salt": "aG9ubw==", "pwd-hash": "C9/T62m1tT4ZxxqyIiyN9fvoEqmL0qnM4/+M+GHHDzr0QzzkAUdGYyJBfxRSe4upDzb6TSC4k5cpZG17p4QCvA=="}]}' \
    > http://localhost:28080/credentials/DEFAULT_TENANT

The response will look like this:

    HTTP/1.1 201 Created
    Location: /credentials/DEFAULT_TENANT/sensor20/hashed-password
    Content-Length: 0


### Get Credentials by Type and Authentication Identifier 

* URI: `/credentials/${tenantId}/${authId}/${type}`
* Method: `GET`
* Status Codes:
  * 200 (OK): Credentials for the given parameters have been found, body contains the credentials data.
  * 404 (Not Found): No credentials for the given parameters are registered for the given tenant.

**Example**

The following command retrieves credentials data of type `hashed-password` for the authId `sensor1`:

    $ curl -i http://localhost:28080/credentials/DEFAULT_TENANT/sensor1/hashed-password

The response will look similar to this:

    HTTP/1.1 200 OK
    Content-Type: application/json; charset=utf-8
    Content-Length: 927
    
    {
      "device-id" : "4711",
      "type" : "hashed-password",
      "auth-id" : "sensor1",
      "enabled" : true,
      "secrets" : [ {
        "not-before" : "2017-05-01T14:00:00+01:00",
        "not-after" : "2037-06-01T14:00:00+01:00",
        "hash-function" : "sha-512",
        "salt" : "aG9ubw==",
        "pwd-hash" : "C9/T62m1tT4ZxxqyIiyN9fvoEqmL0qnM4/+M+GHHDzr0QzzkAUdGYyJBfxRSe4upDzb6TSC4k5cpZG17p4QCvA=="
      }, {
        "not-before" : "2017-05-15T14:00:00+01:00",
        "not-after" : "2037-05-01T14:00:00+01:00",
        "hash-function" : "sha-unknown",
        "salt" : "aG9ubzI=",
        "pwd-hash" : "QDhkSQcm0HNBybnuc5irvPIgNUJn0iVoQnFSoltLOsDlfxhcQWa99l8Dhh67jSKBr7fXeSvFZ1mEojReAXz18A=="
      }, {
        "not-before" : "2017-05-15T14:00:00+01:00",
        "not-after" : "2037-05-01T14:00:00+01:00",
        "hash-function" : "sha-256",
        "salt" : "aG9ubzI=",
        "pwd-hash" : "QDhkSQcm0HNBybnuc5irvPIgNUJn0iVoQnFSoltLOsDlfxhcQWa99l8Dhh67jSKBr7fXeSvFZ1mEojReAXz18A=="
      } ]
    }
 
### Get all Credentials for a Device

* URI: `/credentials/${tenantId}/${deviceId}`
* Method: `GET`
* Status Codes:
  * 200 (OK): Credentials for the device have been found, body contains the credentials.
  The body differs from the body for a specific type since it may contain an arbitrary number of credentials. It contains a property `total` indicating the total number of credentials returned. The credentials are containing in property `credentials`.
  * 404 (Not Found): No credentials for the device are registered.

**Example**

The following command retrieves credentials for device `4711`:

    $ curl -i http://localhost:28080/credentials/DEFAULT_TENANT/4711

The response will look similar to this:

    HTTP/1.1 200 OK
    Content-Type: application/json; charset=utf-8
    Content-Length: 1406
    
    {
      "total" : 2,
      "credentials" : [ {
        "device-id" : "4711",
        "type" : "hashed-password",
        "auth-id" : "sensor1",
        "enabled" : true,
        "secrets" : [ {
          "not-before" : "2017-05-01T14:00:00+01:00",
          "not-after" : "2037-06-01T14:00:00+01:00",
          "hash-function" : "sha-512",
          "salt" : "aG9ubw==",
          "pwd-hash" : "C9/T62m1tT4ZxxqyIiyN9fvoEqmL0qnM4/+M    +GHHDzr0QzzkAUdGYyJBfxRSe4upDzb6TSC4k5cpZG17p4QCvA=="
        }, {
          "not-before" : "2017-05-15T14:00:00+01:00",
          "not-after" : "2037-05-01T14:00:00+01:00",
          "hash-function" : "sha-unknown",
          "salt" : "aG9ubzI=",
          "pwd-hash" :     "QDhkSQcm0HNBybnuc5irvPIgNUJn0iVoQnFSoltLOsDlfxhcQWa99l8Dhh67jSKBr7fXeSvFZ1mEojReAXz18A=="
        }, {
          "not-before" : "2017-05-15T14:00:00+01:00",
          "not-after" : "2037-05-01T14:00:00+01:00",
          "hash-function" : "sha-256",
          "salt" : "aG9ubzI=",
          "pwd-hash" :     "QDhkSQcm0HNBybnuc5irvPIgNUJn0iVoQnFSoltLOsDlfxhcQWa99l8Dhh67jSKBr7fXeSvFZ1mEojReAXz18A=="
        } ]
        }, {
        "device-id" : "4711",
        "type" : "psk",
        "auth-id" : "sensor1",
        "enabled" : true,
        "secrets" : [ {
          "not-before" : "2017-05-01T14:00:00+01:00",
          "not-after" : "2037-06-01T14:00:00+01:00",
          "key" : "secretKey"
        }, {
          "not-before" : "2017-06-15T14:00:00+01:00",
          "not-after" : "2037-05-01T14:00:00+01:00",
          "key" : "secretKey2"
        } ]
      } ]
    }


### Delete Credentials by Type and Authentication Identifier

* URI: `/credentials/${tenantId}/${authId}/${type}`
* Method: `DELETE`
* Status Codes:
  * 204 (No Content): The Credentials with the given identifier and type have been deleted.
  * 404 (Not Found): No credentials matching the criteria have been found.

**Example**

    $ curl -i -X DELETE http://localhost:28080/credentials/DEFAULT_TENANT/sensor1/hashed-password

The response will look similar to this:

    HTTP/1.1 204 No Content
    Content-Length: 0


### Delete all Credentials for a Device

* URI: `/credentials/${tenantId}/${deviceId}`
* Method: `DELETE`
* Status Codes:
  * 204 (No Content): All Credentials for the device have been deleted. There is no payload in the response.
  * 404 (Not Found): No credentials have been found for the device and the given tenant.

**Example**

    $ curl -i -X DELETE http://localhost:28080/credentials/DEFAULT_TENANT/4711

The response will look similar to this:

    HTTP/1.1 204 No Content
    Content-Length: 0
