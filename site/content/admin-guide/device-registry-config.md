+++
title = "Device Registry Configuration"
weight = 315
+++

The Device Registry component provides an exemplary implementation of Eclipse Hono&trade;'s [Device Registration]({{< ref "Device-Registration-API.md" >}}) and [Credentials]({{< ref "Credentials-API.md" >}}) APIs. 
Since Hono version 0.6 it also provides an exemplary implementation of the [Tenant]({{< ref "Tenant-API.md" >}}) API.

Protocol adapters use these APIs to assert a device's registration status, e.g. if it is enabled and if it is registered with a particular tenant, and to authenticate a device before accepting any data for processing from it.

<!--more-->

There is no particular technical reason to implement these three API's in one component, so for production scenarios there might be up to three different components each implementing one of the API's.

The Device Registry component also exposes [HTTP based resources]({{< relref "/user-guide/device-registry.md" >}}) for managing tenants and the registration information and credentials of devices.

The Device Registry is implemented as a Spring Boot application. It can be run either directly from the command line or by means of starting the corresponding [Docker image](https://hub.docker.com/r/eclipse/hono-service-device-registry/) created from it.


## Service Configuration

In addition to the following options, this component supports the options described in [Common Configuration]({{< relref "common-config.md" >}}).

The following table provides an overview of the configuration variables and corresponding command line options for configuring the Device Registry.

| Environment Variable<br>Command Line Option | Mandatory | Default | Description                                                             |
| :------------------------------------------ | :-------: | :------ | :-----------------------------------------------------------------------|
| `HONO_APP_MAX_INSTANCES`<br>`--hono.app.maxInstances` | no | *#CPU cores* | The number of verticle instances to deploy. If not set, one verticle per processor core is deployed. |
| `HONO_APP_HEALTH_CHECK_PORT`<br>`--hono.app.healthCheckPort` | no | - | The port that the HTTP server, which exposes the service's health check resources, should bind to. If set, the adapter will expose a *readiness* probe at URI `/readiness` and a *liveness* probe at URI `/liveness`. |
| `HONO_APP_HEALTH_CHECK_BIND_ADDRESS`<br>`--hono.app.healthCheckBindAddress` | no | `127.0.0.1` | The IP address of the network interface that the HTTP server, which exposes the service's health check resources, should be bound to. The HTTP server will only be started if `HONO_APP_HEALTH_CHECK_BIND_ADDRESS` is set explicitly. |
| `HONO_APP_TYPE`<br>`--hono.app.type` | no | `file` | The device registry implementation to use. This may be either `file` or `dummy`. In the case of `dummy` a dummy implementation will be used which will consider all devices queried for as valid devices, having the access credentials `hono-secret`. Of course this shouldn't be used for productive use. |
| `HONO_CREDENTIALS_SVC_FILENAME`<br>`--hono.credentials.svc.filename` | no | `/var/lib/hono/device-registry/`<br>`credentials.json` | The path to the file where the server stores credentials of devices. Hono tries to read credentials from this file during start-up and writes out all identities to this file periodically if property `HONO_CREDENTIALS_SVC_SAVE_TO_FILE` is set to `true`.<br>Please refer to [Credentials File Format]({{< relref "#credentials-file-format" >}}) for details regarding the file's format. |
| `HONO_CREDENTIALS_SVC_MAX_BCRYPT_ITERATIONS`<br>`--hono.credentials.svc.maxBcryptIterations` | no | `10` | The maximum number of iterations that are supported in password hashes using the BCrypt hash function. This limit is enforced by the device registry when adding or updating corresponding credentials. Increasing this number allows for potentially more secure password hashes to be used. However, the time required to compute the hash increases exponentially with the number of iterations. |
| `HONO_CREDENTIALS_SVC_MODIFICATION_ENABLED`<br>`--hono.credentials.svc.modificationEnabled` | no | `true` | When set to `false` the credentials contained in the registry cannot be updated nor removed. |
| `HONO_CREDENTIALS_SVC_RECEIVER_LINK_CREDIT`<br>`--hono.credentials.svc.receiverLinkCredit` | no | `100` | The number of credits to flow to a client connecting to the Credentials endpoint. |
| `HONO_CREDENTIALS_SVC_SAVE_TO_FILE`<br>`--hono.credentials.svc.saveToFile` | no | `false` | When set to `true` the server will periodically write out the registered credentials to the file specified by the `HONO_CREDENTIALS_SVC_FILENAME` property. |
| `HONO_REGISTRY_AMQP_BIND_ADDRESS`<br>`--hono.registry.amqp.bindAddress` | no | `127.0.0.1` | The IP address of the network interface that the secure AMQP port should be bound to.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_REGISTRY_AMQP_CERT_PATH`<br>`--hono.registry.amqp.certPath` | no | - | The absolute path to the PEM file containing the certificate that the server should use for authenticating to clients. This option must be used in conjunction with `HONO_REGISTRY_AMQP_KEY_PATH`.<br>Alternatively, the `HONO_REGISTRY_AMQP_KEY_STORE_PATH` option can be used to configure a key store containing both the key as well as the certificate. |
| `HONO_REGISTRY_AMQP_INSECURE_PORT`<br>`--hono.registry.amqp.insecurePort` | no | - | The insecure port the server should listen on for AMQP 1.0 connections.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_REGISTRY_AMQP_INSECURE_PORT_BIND_ADDRESS`<br>`--hono.registry.amqp.insecurePortBindAddress` | no | `127.0.0.1` | The IP address of the network interface that the insecure AMQP port should be bound to.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_REGISTRY_AMQP_INSECURE_PORT_ENABLED`<br>`--hono.registry.amqp.insecurePortEnabled` | no | `false` | If set to `true` the server will open an insecure port (not secured by TLS) using either the port number set via `HONO_REGISTRY_AMQP_INSECURE_PORT` or the default AMQP port number (`5672`) if not set explicitly.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_REGISTRY_AMQP_KEY_PATH`<br>`--hono.registry.amqp.keyPath` | no | - | The absolute path to the (PKCS8) PEM file containing the private key that the server should use for authenticating to clients. This option must be used in conjunction with `HONO_REGISTRY_AMQP_CERT_PATH`. Alternatively, the `HONO_REGISTRY_AMQP_KEY_STORE_PATH` option can be used to configure a key store containing both the key as well as the certificate. |
| `HONO_REGISTRY_AMQP_KEY_STORE_PASSWORD`<br>`--hono.registry.amqp.keyStorePassword` | no | - | The password required to read the contents of the key store. |
| `HONO_REGISTRY_AMQP_KEY_STORE_PATH`<br>`--hono.registry.amqp.keyStorePath` | no | - | The absolute path to the Java key store containing the private key and certificate that the server should use for authenticating to clients. Either this option or the `HONO_REGISTRY_AMQP_KEY_PATH` and `HONO_REGISTRY_AMQP_CERT_PATH` options need to be set in order to enable TLS secured connections with clients. The key store format can be either `JKS` or `PKCS12` indicated by a `.jks` or `.p12` file suffix respectively. |
| `HONO_REGISTRY_AMQP_NATIVE_TLS_REQUIRED`<br>`--hono.registry.amqp.nativeTlsRequired` | no | `false` | The server will probe for OpenSLL on startup if a secure port is configured. By default, the server will fall back to the JVM's default SSL engine if not available. However, if set to `true`, the server will fail to start at all in this case. |
| `HONO_REGISTRY_AMQP_PORT`<br>`--hono.registry.amqp.port` | no | `5671` | The secure port that the server should listen on for AMQP 1.0 connections.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_REGISTRY_AMQP_SECURE_PROTOCOLS`<br>`--hono.registry.amqp.secureProtocols` | no | `TLSv1.2` | A (comma separated) list of secure protocols that are supported when negotiating TLS sessions. Please refer to the [vert.x documentation](https://vertx.io/docs/vertx-core/java/#ssl) for a list of supported protocol names. |
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
| `HONO_REGISTRY_SVC_RECEIVER_LINK_CREDIT`<br>`--hono.registry.svc.receiverLinkCredit` | no | `100` | The number of credits to flow to a client connecting to the Device Registration endpoint. |
| `HONO_REGISTRY_SVC_SAVE_TO_FILE`<br>`--hono.registry.svc.saveToFile` | no | `false` | When set to `true` the server will periodically write out the registered device information to the file specified by the `HONO_REGISTRY_SVC_FILENAME` property. |
| `HONO_REGISTRY_SVC_SIGNING_KEY_PATH`<br>`--hono.registry.svc.signing.keyPath` | no  | - | The absolute path to the (PKCS8) PEM file containing the private key that the server should use for signing tokens asserting a device's registration status. When using this variable, other services that need to validate the tokens issued by this service need to be configured with the corresponding certificate/public key. Alternatively, a symmetric key can be used for signing (and validating) by setting the `HONO_REGISTRY_SVC_SIGNING_SHARED_SECRET` variable. If none of these variables is set, the server falls back to the key indicated by the `HONO_REGISTRY_AMP_KEY_PATH` variable. If that variable is also not set, startup of the server fails. |
| `HONO_REGISTRY_SVC_SIGNING_SHARED_SECRET`<br>`--hono.registry.svc.signing.sharedSecret` | no  | - | A string to derive a symmetric key from that is used for signing tokens asserting a device's registration status. The key is derived from the string by using the bytes of the String's UTF8 encoding. When setting the signing key using this variable, other services that need to validate the tokens issued by this service need to be configured with the same key. Alternatively, an asymmetric key pair can be used for signing (and validating) by setting the `HONO_REGISTRY_SVC_SIGNING_KEY_PATH` variable. If none of these variables is set, startup of the server fails. |
| `HONO_REGISTRY_SVC_SIGNING_TOKEN_EXPIRATION`<br>`--hono.registry.svc.signing.tokenExpiration` | no | `10` | The expiration period to use for the tokens asserting the registration status of devices. |
| `HONO_TENANT_SVC_FILENAME`<br>`--hono.tenant.svc.filename` | no | `/var/lib/hono/device-registry/`<br>`tenants.json` | The path to the file where the server stores tenants. Hono tries to read tenants from this file during start-up and writes out all identities to this file periodically if property `HONO_TENANT_SVC_SAVE_TO_FILE` is set to `true`.<br>Please refer to [Tenants File Format]({{< relref "#tenants-file-format" >}}) for details regarding the file's format. |
| `HONO_TENANT_SVC_MODIFICATION_ENABLED`<br>`--hono.tenant.svc.modificationEnabled` | no | `true` | When set to `false` the tenants contained in the registry cannot be updated nor removed. |
| `HONO_TENANT_SVC_RECEIVER_LINK_CREDIT`<br>`--hono.tenant.svc.receiverLinkCredit` | no | `100` | The number of credits to flow to a client connecting to the Tenant endpoint. |
| `HONO_TENANT_SVC_SAVE_TO_FILE`<br>`--hono.tenant.svc.saveToFile` | no | `false` | When set to `true` the server will periodically write out the registered tenants to the file specified by the `HONO_TENANTS_SVC_TENANT_FILENAME` property. |
| `HONO_VERTX_DNS_QUERY_TIMEOUT`<br>`--hono.vertx.dnsQueryTimeout` | no | `5000` | The amount of time after which a DNS query is considered to be failed. Setting this variable to a smaller value may help to reduce the time required to establish connections to the services this service depends on. However, setting it to a value that is too small for any DNS query to succeed will effectively prevent any connections to be established at all. |

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

The connection is configured according to [Hono Client Configuration]({{< relref "hono-client-configuration.md" >}})
where the `${PREFIX}` is set to `HONO_AUTH`. Since Hono's Authentication Service does not allow caching of the responses, the cache properties
can be ignored.

In addition to the standard client configuration properties, following properties need to be set for the connection:

| Environment Variable<br>Command Line Option | Mandatory | Default | Description                                                             |
| :------------------------------------------ | :-------: | :------ | :-----------------------------------------------------------------------|
| `HONO_AUTH_CERT_PATH`<br>`--hono.auth.certPath` | no | - | The absolute path to the PEM file containing the public key that the service should use to authenticate when verifying reachability of the Authentication service as part of a periodic health check. The health check needs to be enabled explicitly by means of setting the `HONO_APP_HEALTH_CHECK_PORT` variable. This variable needs to be set in conjunction with `HONO_AUTH_KEY_PATH`. |
| `HONO_AUTH_KEY_PATH`<br>`--hono.auth.keyPath` | no | - | The absolute path to the PEM file containing the private key that the service should use to authenticate when verifying reachability of the Authentication service as part of a periodic health check. The health check needs to be enabled explicitly by means of setting the `HONO_APP_HEALTH_CHECK_PORT` variable. This variable needs to be set in conjunction with `HONO_AUTH_CERT_PATH`. |
| `HONO_AUTH_VALIDATION_CERT_PATH`<br>`--hono.auth.validation.certPath` | no  | - | The absolute path to the PEM file containing the public key that the service should use for validating tokens issued by the Authentication service. Alternatively, a symmetric key can be used for validating tokens by setting the `HONO_AUTH_VALIDATION_SHARED_SECRET` variable. If none of these variables is set, the service falls back to the key indicated by the `HONO_AUTH_CERT_PATH` variable. If that variable is also not set, startup of the service fails. |
| `HONO_AUTH_VALIDATION_SHARED_SECRET`<br>`--hono.auth.validation.sharedSecret` | no  | - | A string to derive a symmetric key from which is used for validating tokens issued by the Authentication service. The key is derived from the string by using the bytes of the String's UTF8 encoding. When setting the validation key using this variable, the Authentication service **must** be configured with the same key. Alternatively, an asymmetric key pair can be used for validating (and signing) by setting the `HONO_AUTH_SIGNING_CERT_PATH` variable. If none of these variables is set, startup of the service fails. |

## Metrics Configuration

See [Monitoring & Tracing Admin Guide]({{< ref "monitoring-tracing-config.md" >}}) for details on how to configure the reporting of metrics.


## Device Identities File Format

The Device Registry supports persisting the device identities and their registration information to a JSON file in the local file system.
The *Getting started Guide* includes an example configuration which illustrates the file format used. The configuration file's location is `/deploy/src/main/deploy/example-device-identities.json`.

## Credentials File Format

The Device Registry supports persisting the devices' credentials to a JSON file in the local file system.
The *Getting started Guide* includes an example configuration which illustrates the file format used. The configuration file's location is `/deploy/src/main/deploy/example-credentials.json`.

## Tenants File Format

The Device Registry supports persisting tenants to a JSON file in the local file system.
The configuration file's location is `/deploy/src/main/deploy/example-tenants.json`.

## Configuring Gateway Devices

The Device Registry supports devices to *act on behalf of* other devices. This is particularly useful for cases where a device does not connect directly to a Hono protocol adapter but is connected to a *gateway* component that is usually specific to the device's communication protocol. It is the gateway component which then connects to a Hono protocol adapter and publishes data on behalf of the device(s). Examples of such a set up include devices using [SigFox](https://www.sigfox.com) or [LoRa](https://www.lora-alliance.org/) for communication.

In these cases the protocol adapter will authenticate the gateway component instead of the device for which it wants to publish data. In order to verify that the gateway is *authorized* to publish data on behalf of the particular device, the protocol adapter should include the gateway's device identifier (as determined during the authentication process) in its invocation of the Device Registration API's *assert Device Registration* operation.

The Device Registry will then do the following:
1. Verify that the device exists and is enabled.
1. Verify that the gateway exists and is enabled.
2. Verify that the device's registration information contains a property called `via` and that its value is either the gateway's device identifier or a JSON array which contains the gateway's device identifier as one of its values.

Only if all conditions are met, the Device Registry returns an assertion of the device's registration status. The protocol adapter can then forward the published data to the AMQP Messaging Network in the same way as for any device that connects directly to the adapter.

The example configuration file (located at `/deploy/src/main/deploy/example-device-identities.json`) includes a device and a corresponding gateway configured in this way.

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
> --secret tenants.json \
> --secret device-identities.json \
> --secret credentials.json \
> -e 'HONO_AUTH_HOST=<name or address of the auth-server>' \
> -e 'HONO_AUTH_NAME=device-registry' \
> -e 'HONO_AUTH_VALIDATION_CERT_PATH=/run/secrets/auth-server-cert.pem' \
> -e 'HONO_AUTH_TRUST_STORE_PATH=/run/secrets/trusted-certs.pem' \
> -e 'HONO_REGISTRY_AMQP_BIND_ADDRESS=0.0.0.0'
> -e 'HONO_REGISTRY_AMQP_KEY_PATH=/run/secrets/device-registry-key.pem' \
> -e 'HONO_REGISTRY_AMQP_CERT_PATH=/run/secrets/device-registry-cert.pem' \
> -e 'HONO_TENANT_SVC_FILENAME=file:/run/secrets/tenants.json' \
> -e 'HONO_REGISTRY_SVC_FILENAME=file:/run/secrets/device-identities.json' \
> -e 'HONO_CREDENTIALS_SVC_FILENAME=file:/run/secrets/credentials.json' \
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

In most cases it is much easier to start all of Hono's components in one shot using the Docker Swarm deployment script provided in the `deploy/target/deploy/docker` folder.

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
