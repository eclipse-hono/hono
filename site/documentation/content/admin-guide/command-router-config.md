+++
title = "Configuring the Command Router Service"
linkTitle = "Command Router Service Configuration"
weight = 317
+++

The Command Router service provides an implementation of Eclipse Hono&trade;'s
[Command Router API]({{< relref "/api/command-router" >}}).

*Protocol Adapters* use the *Command Router API* to supply information that the Command Router service component
can use to route command & control messages to the particular protocol adapter instances that the target devices are
connected to.

<!--more-->

The Command Router component provides an implementation of the Command Router API which uses a remote *data grid* for
storing information about device connections. The data grid can be scaled out independently from the Command Router
service components to meet the storage demands at hand.

The Command Router is implemented as a Quarkus application. It can be run either directly from the command line or by
means of starting the corresponding [Docker image](https://hub.docker.com/r/eclipse/hono-service-command-router-quarkus/)
created from it.

{{% notice info %}}
The Command Router had originally been implemented as a Spring Boot application. That variant has been deprecated with Hono
1.10.0 and will be completely removed in Hono 2.0.0.
The [Spring Boot based Docker image](https://hub.docker.com/r/eclipse/hono-service-command-router/) will be available until then.
{{% /notice %}}

## Service Configuration

The following table provides an overview of the configuration variables and corresponding system properties for
configuring the Command Router component.

| OS Environment Variable<br>Java System Property | Mandatory | Default | Description                                |
| :---------------------------------------------- | :-------: | :------ | :----------------------------------------- |
| `HONO_APP_MAXINSTANCES`<br>`hono.app.maxInstances` | no | *#CPU cores* | The number of Verticle instances to deploy. If not set, one Verticle per processor core is deployed. |
| `HONO_COMMANDROUTER_AMQP_BINDADDRESS`<br>`hono.commandRouter.amqp.bindAddress` | no | `127.0.0.1` | The IP address of the network interface that the secure AMQP port should be bound to.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_COMMANDROUTER_AMQP_CERTPATH`<br>`hono.commandRouter.amqp.certPath` | no | - | The absolute path to the PEM file containing the certificate that the server should use for authenticating to clients. This option must be used in conjunction with `HONO_COMMANDROUTER_AMQP_KEYPATH`.<br>Alternatively, the `HONO_COMMANDROUTER_AMQP_KEYSTOREPATH` option can be used to configure a key store containing both the key as well as the certificate. |
| `HONO_COMMANDROUTER_AMQP_INSECUREPORT`<br>`hono.commandRouter.amqp.insecurePort` | no | - | The insecure port the server should listen on for AMQP 1.0 connections.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_COMMANDROUTER_AMQP_INSECUREPORTBINDADDRESS`<br>`hono.commandRouter.amqp.insecurePortBindAddress` | no | `127.0.0.1` | The IP address of the network interface that the insecure AMQP port should be bound to.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_COMMANDROUTER_AMQP_INSECUREPORTENABLED`<br>`hono.commandRouter.amqp.insecurePortEnabled` | no | `false` | If set to `true` the server will open an insecure port (not secured by TLS) using either the port number set via `HONO_COMMANDROUTER_AMQP_INSECUREPORT` or the default AMQP port number (`5672`) if not set explicitly.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_COMMANDROUTER_AMQP_KEYPATH`<br>`hono.commandRouter.amqp.keyPath` | no | - | The absolute path to the (PKCS8) PEM file containing the private key that the server should use for authenticating to clients. This option must be used in conjunction with `HONO_COMMANDROUTER_AMQP_CERTPATH`. Alternatively, the `HONO_COMMANDROUTER_AMQP_KEYSTOREPATH` option can be used to configure a key store containing both the key as well as the certificate. |
| `HONO_COMMANDROUTER_AMQP_KEYSTOREPASSWORD`<br>`hono.commandRouter.amqp.keyStorePassword` | no | - | The password required to read the contents of the key store. |
| `HONO_COMMANDROUTER_AMQP_KEYSTOREPATH`<br>`hono.commandRouter.amqp.keyStorePath` | no | - | The absolute path to the Java key store containing the private key and certificate that the server should use for authenticating to clients. Either this option or the `HONO_COMMANDROUTER_AMQP_KEYPATH` and `HONO_COMMANDROUTER_AMQP_CERTPATH` options need to be set in order to enable TLS secured connections with clients. The key store format can be either `JKS` or `PKCS12` indicated by a `.jks` or `.p12` file suffix respectively. |
| `HONO_COMMANDROUTER_AMQP_NATIVETLSREQUIRED`<br>`hono.commandRouter.amqp.nativeTlsRequired` | no | `false` | The server will probe for OpenSSL on startup if a secure port is configured. By default, the server will fall back to the JVM's default SSL engine if not available. However, if set to `true`, the server will fail to start at all in this case. |
| `HONO_COMMANDROUTER_AMQP_PORT`<br>`hono.commandRouter.amqp.port` | no | `5671` | The secure port that the server should listen on for AMQP 1.0 connections.<br>See [Port Configuration]({{< relref "#port-configuration" >}}) below for details. |
| `HONO_COMMANDROUTER_AMQP_RECEIVERLINKCREDIT`<br>`hono.commandRouter.amqp.receiverLinkCredit` | no | `100` | The number of credits to flow to a client connecting to the service's AMQP endpoint. |
| `HONO_COMMANDROUTER_AMQP_SECUREPROTOCOLS`<br>`hono.commandRouter.amqp.secureProtocols` | no | `TLSv1.3,TLSv1.2` | A (comma separated) list of secure protocols (in order of preference) that are supported when negotiating TLS sessions. Please refer to the [vert.x documentation](https://vertx.io/docs/vertx-core/java/#ssl) for a list of supported protocol names. |
| `HONO_COMMANDROUTER_AMQP_SUPPORTEDCIPHERSUITES`<br>`hono.commandRouter.amqp.supportedCipherSuites` | no | - | A (comma separated) list of names of cipher suites (in order of preference) that are supported when negotiating TLS sessions. Please refer to [JSSE Cipher Suite Names](https://docs.oracle.com/en/java/javase/11/docs/specs/security/standard-names.html#jsse-cipher-suite-names) for a list of supported names. |
| `HONO_COMMANDROUTER_SVC_KUBERNETESBASEDADAPTERINSTANCESTATUSSERVICEENABLED`<br>`hono.commandRouter.svc.kubernetesBasedAdapterInstanceStatusServiceEnabled` | no | `true` | If set to `true` and the Command Router component runs in a Kubernetes cluster, a Kubernetes based service to identify protocol adapter instances will be used to prevent sending command & control messages to already terminated adapter instances. Needs to be set to `false` if not all protocol adapters are part of the Kubernetes cluster and namespace that the Command Router component is in. |

The variables only need to be set if the default value does not match your environment.

In addition to the options described in the table above, this component supports the following standard configuration options:

* [Common Java VM Options]({{< relref "common-config.md/#java-vm-options" >}})
* [Common vert.x Options]({{< relref "common-config.md/#vertx-options" >}})
* [Monitoring Options]({{< relref "monitoring-tracing-config.md" >}})

## Port Configuration

The Command Router component supports configuration of an AMQP based endpoint that can be configured to listen for connections on

* a secure port only (default) or
* an insecure port only or
* both a secure and an insecure port (dual port configuration)

The server will fail to start if none of the ports is configured properly.

### Secure Port Only

The server needs to be configured with a private key and certificate in order to open a TLS secured port.

There are two alternative ways for doing so:

1. Setting the `HONO_COMMANDROUTER_AMQP_KEYSTOREPATH` and the `HONO_COMMANDROUTER_AMQP_KEYSTOREPASSWORD` variables in order to load the key & certificate from a password protected key store, or
1. setting the `HONO_COMMANDROUTER_AMQP_KEYPATH` and `HONO_COMMANDROUTER_AMQP_CERTPATH` variables in order to load the key and certificate from two separate PEM files in PKCS8 format.

When starting up, the server will bind a TLS secured socket to the default secure AMQP port 5671. The port number can also be set explicitly using the `HONO_COMMANDROUTER_AMQP_PORT` variable.

The `HONO_COMMANDROUTER_AMQP_BINDADDRESS` variable can be used to specify the network interface that the port should be exposed on. By default the port is bound to the *loopback device* only, i.e. the port will only be accessible from the local host. Setting this variable to `0.0.0.0` will let the port being bound to **all** network interfaces (be careful not to expose the port unintentionally to the outside world).

### Insecure Port Only

The secure port will mostly be required for production scenarios. However, it might be desirable to expose a non-TLS secured port instead, e.g. for testing purposes. In any case, the non-secure port needs to be explicitly enabled either by

- explicitly setting `HONO_COMMANDROUTER_AMQP_INSECUREPORT` to a valid port number, or by
- implicitly configuring the default AMQP port (5672) by simply setting `HONO_COMMANDROUTER_AMQP_INSECUREPORTENABLED` to `true`.

The server issues a warning on the console if `HONO_COMMANDROUTER_AMQP_INSECUREPORT` is set to the default secure AMQP port (5671).

The `HONO_COMMANDROUTER_AMQP_INSECUREPORTBINDADDRESS` variable can be used to specify the network interface that the port should be exposed on. By default the port is bound to the *loopback device* only, i.e. the port will only be accessible from the local host. This variable might be used to e.g. expose the non-TLS secured port on a local interface only, thus providing easy access from within the local network, while still requiring encrypted communication when accessed from the outside over public network infrastructure.

Setting this variable to `0.0.0.0` will let the port being bound to **all** network interfaces (be careful not to expose the port unintentionally to the outside world).

### Dual Port
 
In test setups and some production scenarios Hono server may be configured to open one secure **and** one insecure port at the same time.
 
This is achieved by configuring both ports correctly (see above). The server will fail to start if both ports are configured to use the same port number.

Since the secure port may need different visibility in the network setup compared to the secure port, it has its own binding address `HONO_COMMANDROUTER_AMQP_INSECUREPORTBINDADDRESS`. 
This can be used to narrow the visibility of the insecure port to a local network e.g., while the secure port may be visible worldwide. 

### Ephemeral Ports

Both the secure as well as the insecure port numbers may be explicitly set to `0`. The Command Router component will then use arbitrary (unused) port numbers determined by the operating system during startup.

## Messaging Configuration

The Command Router component uses a connection to an *AMQP 1.0 Messaging Network* and/or an *Apache Kafka cluster* to
* receive command & control messages sent by downstream applications and to forward these commands on a specific
  address/topic so that they can be received by protocol adapters,
* send delivery failure command response messages in case no consumer exists for a received command (only with Kafka messaging),
* receive notification messages about changes to tenant/device/credentials data sent from the device registry.

Command messages are received on each configured messaging system.

For notification messages, the Kafka connection is used by default, if configured. Otherwise the *AMQP messaging network*
is used.

### AMQP 1.0 Messaging Network Connection Configuration

The connection to the *AMQP 1.0 Messaging Network* is configured according to the 
[Hono Client Configuration]({{< relref "hono-client-configuration.md" >}}) with `HONO_COMMAND` being used as `${PREFIX}`.
The properties for configuring response caching can be ignored.

### Kafka based Messaging Configuration

The connection to an *Apache Kafka cluster* can be configured according to the
[Hono Kafka Client Configuration]({{< relref "hono-kafka-client-configuration.md" >}}).

The following table provides an overview of the prefixes to be used to individually configure the Kafka clients used by
the component. The individual client configuration is optional, a minimal configuration may only contain a common client
configuration consisting of properties prefixed with `HONO_KAFKA_COMMONCLIENTCONFIG_` and `hono.kafka.commonClientConfig.`
respectively.

| OS Environment Variable Prefix<br>Java System Property Prefix                                | Description                                                                                                        |
|:---------------------------------------------------------------------------------------------|:-------------------------------------------------------------------------------------------------------------------|
| `HONO_KAFKA_CLEANUP_ADMINCLIENTCONFIG_`<br>`hono.kafka.cleanup.adminClientConfig.`           | Configures the Kafka admin client that removes Hono internal topics.                                               |
| `HONO_KAFKA_COMMAND_CONSUMERCONFIG_`<br>`hono.kafka.command.consumerConfig.`                 | Configures the Kafka consumer that receives command messages.                                                      |
| `HONO_KAFKA_COMMANDINTERNAL_PRODUCERCONFIG_`<br>`hono.kafka.commandInternal.producerConfig.` | Configures the Kafka producer that publishes command messages to Hono internal topics.                             |
| `HONO_KAFKA_COMMANDRESPONSE_PRODUCERCONFIG_`<br>`hono.kafka.commandResponse.producerConfig.` | Configures the Kafka producer that publishes command response messages.                                            |
| `HONO_KAFKA_NOTIFICATION_CONSUMERCONFIG_`<br>`hono.kafka.notification.consumerConfig.`       | Configures the Kafka consumer that receives notification messages about changes to tenant/device/credentials data. |

## Tenant Service Connection Configuration

The Command Router component requires a connection to an implementation of Hono's [Tenant API]({{< ref "/api/tenant" >}}) in order to retrieve information for a tenant.

The connection to the Tenant Service is configured according to the [Hono Client Configuration]({{< relref "hono-client-configuration.md" >}})
where the `${PREFIX}` is set to `HONO_TENANT` and the additional values for response caching apply.

The adapter caches the responses from the service according to the *cache directive* included in the response.
If the response doesn't contain a *cache directive* no data will be cached.

## Device Registration Service Connection Configuration

The Command Router component requires a connection to an implementation of Hono's [Device Registration API]({{< relref "/api/device-registration" >}}) in order to retrieve registration status assertions for the target devices of incoming command messages.

The connection to the Device Registration Service is configured according to the [Hono Client Configuration]({{< relref "hono-client-configuration.md" >}})
where the `${PREFIX}` is set to `HONO_REGISTRATION`.

The adapter caches the responses from the service according to the *cache directive* included in the response.
If the response doesn't contain a *cache directive* no data will be cached.

Note that the adapter uses a single cache for all responses from the service regardless of the tenant identifier.
Consequently, the Device Registration Service client configuration's *responseCacheMinSize* and *responseCacheMaxSize* properties
determine the overall number of responses that can be cached.

## Data Grid Connection Configuration

The Command Router component requires either an embedded cache or a remote
data grid, using the Infinispan Hotrod protocol to store device information.

The following table provides an overview of the configuration variables and corresponding command line options for
configuring the common aspects of the service:

| OS Environment Variable<br>Java System Property | Mandatory | Default | Description                                                             |
| :------------------------------------------ | :-------: | :------ | :-----------------------------------------------------------------------|
| `HONO_COMMANDROUTER_CACHE_COMMON_CACHENAME`<br>`hono.commandRouter.cache.common.cacheName` | no | `command-router` | The name of the cache |
| `HONO_COMMANDROUTER_CACHE_COMMON_CHECKKEY`<br>`hono.commandRouter.cache.common.checkKey` | no | `KEY_CONNECTION_CHECK` | The key used to check the health of the cache. This is only used in case of a remote cache. |
| `HONO_COMMANDROUTER_CACHE_COMMON_CHECKVALUE`<br>`hono.commandRouter.cache.common.checkValue` | no | `VALUE_CONNECTION_CHECK` | The value used to check the health of the cache. This is only used in case of a remote cache. |

The type of cache (embedded or remote) is determined during startup by means of the `HONO_COMMANDROUTER_CACHE_REMOTE_SERVERLIST`
configuration variable. If the variable has a non empty value, a [remote cache]({{< relref "#remote-cache" >}}) is configured.
Otherwise, an [embedded cache]({{< relref "#embedded-cache" >}}) is configured.

### Remote cache

The following table provides an overview of the configuration variables and corresponding system properties for configuring the connection to the data grid:

| OS Environment Variable<br>Java System Property | Mandatory | Default | Description                                                             |
| :------------------------------------------ | :-------: | :------ | :-----------------------------------------------------------------------|
| `HONO_COMMANDROUTER_CACHE_REMOTE_SERVERLIST`<br>`hono.commandRouter.cache.remote.serverList` | yes | - | A list of remote servers in the form: `host1[:port][;host2[:port]]....`. |
| `HONO_COMMANDROUTER_CACHE_REMOTE_AUTHSERVERNAME`<br>`hono.commandRouter.cache.remote.authServerName` | yes | - | The server name to indicate in the SASL handshake when authenticating to the server. |
| `HONO_COMMANDROUTER_CACHE_REMOTE_AUTHREALM`<br>`hono.commandRouter.cache.remote.authRealm` | yes | - | The authentication realm for the SASL handshake when authenticating to the server. |
| `HONO_COMMANDROUTER_CACHE_REMOTE_AUTHUSERNAME`<br>`hono.commandRouter.cache.remote.authUsername` | yes | - | The username to use for authenticating to the server. |
| `HONO_COMMANDROUTER_CACHE_REMOTE_AUTHPASSWORD`<br>`hono.commandRouter.cache.remote.authPassword` | yes | - | The password to use for authenticating to the server. |
| `HONO_COMMANDROUTER_CACHE_REMOTE_SASLMECHANISM`<br>`hono.commandRouter.cache.remote.saslMechanism` | yes | - | The SASL mechanism to use for authenticating to the server. |

In general, the service supports all configuration properties of the [Infinispan Hotrod client](https://docs.jboss.org/infinispan/12.1/apidocs/org/infinispan/client/hotrod/configuration/package-summary.html#package.description) using `hono.commandRouter.cache.remote` instead of the `infinispan.client.hotrod` prefix.

### Embedded cache

The following table provides an overview of the configuration variables and corresponding system properties for configuring the embedded cache:

| OS Environment Variable<br>Java System Property | Mandatory | Default | Description                                                             |
| :------------------------------------------ | :-------: | :------ | :-----------------------------------------------------------------------|
| `HONO_COMMANDROUTER_CACHE_EMBEDDED_CONFIGURATIONFILE`<br>`hono.commandRouter.cache.embedded.configurationFile` | yes | - | The absolute path to an Infinispan configuration file. Also see the [Infinispan Configuration Schema](https://docs.jboss.org/infinispan/12.1/configdocs/infinispan-config-12.1.html). |

## Authentication Service Connection Configuration

The Command Router component requires a connection to an implementation of Hono's Authentication API in order to authenticate and authorize client requests.

The connection is configured according to the [Hono Client Configuration]({{< relref "hono-client-configuration.md" >}})
where the `${PREFIX}` is set to `HONO_AUTH`. Since Hono's Authentication Service does not allow caching of the responses, the cache properties
can be ignored.

In addition to the standard client configuration properties, following properties need to be set for the connection:

| OS Environment Variable<br>Java System Property | Mandatory | Default | Description                                                             |
| :------------------------------------------ | :-------: | :------ | :-----------------------------------------------------------------------|
| `HONO_AUTH_VALIDATION_CERTPATH`<br>`hono.auth.validation.certPath` | no  | - | The absolute path to the PEM file containing the public key that the service should use for validating tokens issued by the Authentication service. Alternatively, a symmetric key can be used for validating tokens by setting the `HONO_AUTH_VALIDATION_SHAREDSECRET` variable. If none of these variables is set, the service falls back to the key indicated by the `HONO_AUTH_CERTPATH` variable. If that variable is also not set, startup of the service fails. |
| `HONO_AUTH_VALIDATION_SHAREDSECRET`<br>`hono.auth.validation.sharedSecret` | no  | - | A string to derive a symmetric key from which is used for validating tokens issued by the Authentication service. The key is derived from the string by using the bytes of the String's UTF8 encoding. When setting the validation key using this variable, the Authentication service **must** be configured with the same key. Alternatively, an asymmetric key pair can be used for validating (and signing) by setting the `HONO_AUTH_SIGNING_CERTPATH` variable. If none of these variables is set, startup of the service fails. |

## Metrics Configuration

See [Monitoring & Tracing Admin Guide]({{< ref "monitoring-tracing-config.md" >}}) for details on how to configure the reporting of metrics.
