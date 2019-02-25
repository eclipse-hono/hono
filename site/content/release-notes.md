+++
title = "Release Notes"
+++

## 0.9 (not released yet)

### New Features

* The MQTT adapter now supports commands to be published using QoS 1. Please refer to
  [MQTT adapter User Guide]({{< relref "/user-guide/mqtt-adapter.md" >}}) for details.
* The MQTT adapter now supports limiting the number of concurrent connections in order
  to prevent running out of resources. Please refer to
  [MQTT Adapter Configuration]({{< relref "/admin-guide/mqtt-adapter-config.md" >}}) for details.
* The new *Helm deployment* for Kubernetes has been added. Please refer to
  [Kubernetes deployment guide]({{< relref "/deployment/kubernetes.md" >}}) for details.

### Fixes & Enhancements

* `org.eclipse.hono.util.RequestResponseResult` now provides access to AMQP application-properties conveyed in the
  response message.
* The `org.eclipse.hono.service.registration.BaseRegistrationService` class now supports authorization of gateways
  (acting on behalf of a device) against a list of gateway identifiers instead of a single identifier only. For that purpose
  the `via` property of the device's registration information may contain either a single string or a JSON array containing
  multiple strings. Based on this, a device can now be configured to connect via arbitrary gateways instead of just a single
  one.

### API Changes

* The layout and structure of the metrics reported by Hono have been changed substantially. Many of the existing meters and tags
  have been changed or replaced in order to provide a more consistent set of metrics and increase the value of the information
  being reported. The legacy metrics still remain unchanged, though.
  Please refer to the [Metrics definition]({{< ref "/api/Metrics.md" >}}) for details.
* In case of a failed connection attempt, `HonoClientImpl` will now determine based on the error whether it will re-try
  to connect to the peer. Before, reconnect attempts were done unconditionally, by default infinitely or up to the
  number of times defined in the *reconnectAttempts* property in the `ClientConfigProperties`. Now, when the outcome
  of the SASL handshake received from the peer during connection establishment indicates that invalid credentials were
  provided or that the server encountered a permanent error when handling the request, no further reconnect attempts
  will be done.
* The deprecated methods have been removed from `org.eclipse.hono.client.MessageSender` and
  its implementations.
* The Command &amp; Control API's *send a request/response command* operation has been changed. The response
  message to a command now must include the device and tenant identifiers of the device. Including
  these two properties should make it much easier to implement competing command response consumers
  in business applications.
  As a consequence, the `org.eclipse.hono.client.CommandResponse`'s factory methods have been changed to
  accept the tenant and device IDs as parameters.
* The *connectTimeout* configuration variable for the `HonoClient` now defines the time to wait not only for the TCP/TLS
  connection establishment but also for the SASL handshake and the exchange of the AMQP *open* frame.
* The (already deprecated) Hono Messaging component has been removed from Hono.

### Depreciations

* The script based deployment to Kubernetes has been deprecated and will be removed in the next version.
  The Helm based deployment should be used instead.
* The *sendCommandResponse(String, String, Buffer, Map, int, SpanContext)* of the
  `org.eclipse.hono.client.CommandResponseSender` interface has been deprecated and
  will be removed in the next version. Custom protocol adapters should use
  *sendCommandResponse(CommandResponse, SpanContext)* instead.

## 0.9-M2

### New Features

* The MQTT protocol adapter now supports authentication of devices using X.509 client certificates. Please refer to
  the [MQTT adapter user guide]({{< ref "/user-guide/mqtt-adapter.md" >}}) for details regarding configuration.

### Fixes & Enhancements

* The OpenShift *source-to-image* (S2I) deployment is now the default
  OpenShift / OKD deployment. The plain OpenShift deployment, which had been deprecated
  in Hono 0.8, has been removed.
* The protocol adapters can now be configured with a custom *DNS timeout* value, limiting the time that the adapter
  will wait for the response to a DNS query. By default, a DNS query will time out after 5 seconds.
  Please refer to the protocol adapter admin guides for details regarding the new configuration variable.
* The following configuration variables have been added to `HonoClient`:

  * *connectTimeout*: Sets a limit on the time that the client will wait for a TCP/TLS connection with the peer
    to be established. By default, a connection attempt will time out after 5 seconds.
  * *idleTimeout*: The idle timeout defines the amount of time after which a connection will be closed when no frames
    have been received from the remote peer. The default value is 16 seconds.
  * *sendMessageTimeout*: Limits the time to wait for a downstream consumer's acknowledgement of
    an event or command response message received from a device. The default value is 1 second.

    Please refer to the [Hono Client Configuration guide]({{< ref "/admin-guide/hono-client-configuration.md" >}})
    for details regarding the new configuration variables.

### API Changes

* Some of the *tags* used by Hono's components when reporting metrics have been changed. The common tag *component*
  has been renamed to *component-type*. The *protocol* tag formerly used by adapters to indicate the transport protocol
  that a message has been received over, has been replaced by the generic *component-name* tag which indicates the name
  of the component that a metric has been reported by. Please refer to the [Metrics API]({{< ref "/api/Metrics.md" >}})
  for details. Note that these changes do not affect the legacy Graphite based metrics back end.

### Depreciations

* The Hono Messaging component is now  deprecated and will be removed from Hono in version 0.9 altogether.
  The example deployment has not been using Hono Messaging since 0.6 and there is no practical reason for
  using it anymore.

## 0.9-M1

### New Features

* The default Micrometer back end is now Prometheus, the Grafana dash boards have been updated
  to retrieve data from Prometheus instead of the old InfluxDB.
  The Graphite based legacy metrics format can still be used but requires building Hono from source and activating
  the `metrics-graphite` Maven build profile.
  Please refer to the [Monitoring admin guide]({{< ref "/admin-guide/monitoring-tracing-config.md" >}}) for details.
* The `org.eclipse.hono.service.credentials.CompleteBaseCredentialsService` class now supports the transparent
  *on-the-fly* hashing of clear text passwords contained in *hashed-password* credentials. Please refer to the
  [Device Registry user guide]({{< ref "/user-guide/device-registry.md#managing-credentials" >}}) for details.

### Fixes & Enhancements

* The base classes for implementing the Device Registration and Tenant APIs have been instrumented
  with OpenTracing. New variants of the `RegistrationService.assertRegistration`, `TenantService.get` and `CredentialsService.get`
  methods have been added which also accept an OpenTracing span as a parameter.
  The default implementations of these methods still default to the previously existing methods.
  In `RegistrationService` implementations based on `BaseRegistrationService` an OpenTracing span will be created,
  passed on to the `assertRegistration` method and finished eventually. The same applies to `TenantService` implementations
  based on `BaseTenantService` concerning the `get` method and to `CredentialsService` implementations based on
  `BaseCredentialsService` concerning the `get` method.

### API Changes

* The `org.eclipse.hono.service.credentials.CompleteBaseCredentialsService` class now requires an
  `org.eclipse.hono.auth.HonoPasswordEncoder` to be passed into its constructor.
  The `org.eclipse.hono.auth.SpringBasedHonoPasswordEncoder` has been added as a default implementation for
  this purpose.
* The [Tenant API]({{< ref "/api/Tenant-API.md#trusted-ca-format" >}}) now optionally allows specifying an
  X.509 certificate instead of a public key when defining a trusted CA.

## 0.8

### New Features

* The already specified new message exchange pattern - called *one-way commands* - is now available to *business applications*.
  Therefore the `CommandClient` class was extended by a method `sendOneWayCommand` that does not expect a response from the device.
  See the `HonoExampleApplicationBase` class for how to use this new command pattern.
  On the adapter side, this pattern is supported by the HTTP, MQTT and AMQP adapter so far.

### Fixes & Enhancements

* The AMQP adapter has been instrumented using OpenTracing. It now collects traces when a device connects to the adapter,
  opens a link for uploading messages and for each message sent by the device. It also adds information to traces created
  for command messages to be sent to a device.
* The AMQP adapter command line client now uses property names that match those of the HonoClient.
* The Command client now has the provision to check the available credits before sending any commands using `getCredit()`.
  Also a handler can be set using `sendQueueDrainHandler(Handler<Void> handler)`, so that the client is notified when credits are replenished. 
* The example application `HonoExampleApplication` can now be configured to only send `one-way commands` in response to
  downstream messages by setting the new system property `sendOneWayCommands`. 

### API Changes

* The `hono-client` module now contains all classes necessary to implement Command & Control in protocol adapters.
  Previously this has only been the case for the sending of a command, as it is typically done by an application, while
  the classes to receive commands, typically used by protocol adapters, were located in the `hono-service-base` module.
  Additionally, the package structure was reworked to allow for implementing protocol adapters that run in an OSGi 
  environment, so several classes are not in the same package anymore.
  Custom protocol adapters thus may need to be slightly refactored to import the Command classes
  from their new packages - the functionality has not changed.
  The only exception to this is the `Device` class that was moved to `hono-core` with a specific
  subclass `DeviceUser`. This subclass is needed in code lines that implement the authentication as defined by the `vertx-auth-common` 
  module, so for this class there might be some very few changes to the custom code necessary (the adapted standard protocol
  adapters may serve as a blue-print for this). You may want to refer to the method `getAuthenticatedDevice` in the 
  `AbstractVertxBasedHttpProtocolAdapter` class as an example.
* The interface `ConnectionEventProducer` has been modified to support
  passing along a context object of type `ConnectionEventProducer.Context`
  which allows the producer implementation to re-use the pre-initialized
  Hono clients from the current protocol adapter instance, in the same threading
  context. The default implementation of the *connection events* still defaults
  to the logging producer.
* The `CommandConnection.getOrCreateCommandConsumer` method has been renamed to `createCommandConsumer`. The new name
  also reflects a change in the method's semantics. The method will no longer return an already existing instance of a command
  consumer for a given device but will instead fail the returned future with a `org.eclipse.hono.client.ResourceConflictException`
  to indicate that a consumer for the given device is already in use. The original behavior allowed an implementation to return
  a consumer that was scoped to another message handler than the one passed into the method as an argument. However, client code
  had no chance to determine whether it got back a newly created instance or an existing one. This has been resolved with the
  new method semantics.
* The `CommandContext.flow()` method has been made private. Client code should instead use the newly introduced variants of the `accept(int)`,
  `release(int)` and `reject(ErrorCondition, int)` methods which all accept an integer indicating the number of credits to flow to the sender.
  These methods will also finish the OpenTracing span contained in the `CommandContext` implicitly.

### Depreciations

* The Kura protocol adapter is being deprecated with 0.8. It will still be part
  of Hono 0.8, but may be removed in a future version. Starting with Kura 4.0
  and Hono 0.8, both projects can now be used together, without the need for
  a special version of the Hono MQTT protocol adapter.
* The `openshift` deployment is being deprecated with 0.8 and is planned to be
  removed in 0.9, in favor of the `openshift_s2i` deployment. While the
  `openshift` deployment still works, it hasn't been updated for more recent
  OpenShift and EnMasse versions. The main focus now is on the *S2I* variant,
  which will become the default *OpenShift*" deployment in Hono 0.9.
* New variants of the `AbstractProtocolAdapterBase.sendConnectedTtdEvent`, `AbstractProtocolAdapterBase.sendDisconnectedTtdEvent`
  and `AbstractProtocolAdapterBase.sendTtdEvent`have been added which also accept an OpenTracing span as a parameter.
  The original variants have been deprecated.

## 0.8-M2

### Fixes & Enhancements

* HonoClientImpl now waits a limited amount of time for the peer's *attach* frame during link establishment before considering the attempt to have failed. The time-out value (default is 1000ms) can be configured using the *linkEstablishmentTimeout* property of `org.eclipse.hono.config.ClientConfigProperties`. See [Hono Client Configuration]({{< ref "/admin-guide/hono-client-configuration.md" >}}) for details.
* The example Device Registry service now supports limiting the number of iterations that are supported in BCrypt based hashed-password credentials. This way the processing time required for verifying credentials can be effectively limited. The `org.eclipse.hono.service.credentials.CompleteBaseCredentialsService` class defines a new method `getMaxBcryptIterations` which subclasses may override to provide a reasonable default value or determine the value based on a configuration property (as `FileBasedCredentialsService` of the demo Device Registry does).
* Hono now uses OpenJDK 11 as the JVM in the service Docker images. Because OpenJDK 11 has better support for detecting resource limits when running in a container, this also has an impact on the command line parameters passed to the JVM. See [Limiting Resource Usage]({{< ref "/deployment/resource-limitation.md" >}}) for details.
* Instead of Dropwizard Hono now uses Micrometer. Hono still allows to produce
  the same graphite wire format as Hono 0.7 supported. This can be enabled
  by the use of the configuration option `hono.metrics.legacy`. For the
  moment this value defaults to `true`. The plan is to disable the legacy
  format for the final 0.8 release, but still support the legacy format at least
  until one version after Hono 0.8.

### API Changes

* `org.eclipse.hono.util.CredentialsObject.fromHashedPassword` now requires a password hash instead of the clear text password to be passed in. Hash values for clear text password can be computed using `ClearTextPassword`'s `encode` and `encodeBCrypt` methods.
* `org.eclipse.hono.util.CredentialsObject.isValid` has been renamed to `checkValidity`. The method also no longer returns a boolean but instead throws an `IllegalStateException` to indicate a failure.

## 0.8-M1_1

Since 0.8-M1 missed an important artifact, the first 0.8 milestone is available as 0.8-M1_1.

### New Features

* A new message exchange pattern - called *one-way commands* - is fully specified for the [Command & Control API]({{< ref "/api/Command-And-Control-API.md" >}}).
  Note that currently there is no implementation included, this is planned for the following milestone.

### Fixes & Enhancements

* Hono-cli now supports Command & Control. Using command line, users can send commands to devices and receive command responses.  See [Using CLI for Command & Control]({{< ref "/getting-started.md#using-cli-command-line-interface-to-send-commands-and-receive-command-responses" >}}) for more information.
* The command client now enables the setting of application properties for command messages. This can be helpful if custom protocol adapters want to react to specifically annotated commands sent by an application. The standard protocol adapters of Hono do not further exploit these properties.
* The command consumer (typically used in protocol adapters) allows access to the application properties of command messages.

### API Changes

* The `org.eclipse.hono.util.TenantObject`'s *getTrustAnchor* method now throws a `GeneralSecurityException` to indicate a problem with decoding/parsing the certificate or public key that is configured as the trusted CA for the tenant. This allows client code to get some insight into the reason for the failure to authenticate a device based on a client certificate.
* The `org.eclipse.hono.service.registration.RegistrationService` interface now describes only the mandatory operations of the API. The complete API is offered in `org.eclipse.hono.service.registration.CompleteRegistrationService`. These interfaces are implemented in `org.eclipse.hono.service.registration.BaseRegistrationService` and `org.eclipse.hono.service.registration.CompleteBaseRegistrationService` respectfully. Device Registries implementations can offer the mandatory only or the full API by extending the according base class.
* The `org.eclipse.hono.service.tenant.TenantService` interface now describes only the mandatory operations of the API. The complete API is offered in `org.eclipse.hono.service.tenant.CompleteTenantService`. These interfaces are implemented in `org.eclipse.hono.service.tenant.BaseTenantService` and `org.eclipse.hono.service.tenant.CompleteBaseTenantService` respectfully. Tenant services implementations can offer the mandatory only or the full API by extending the according base class.
* The `org.eclipse.hono.service.credentials.CredentialsService` interface now describes only the mandatory operations of the API. The complete API is offered in `org.eclipse.hono.service.credentials.CompleteCredentialsService`. These interfaces are implemented in `org.eclipse.hono.service.credentials.BaseCredentialsService` and `org.eclipse.hono.service.credentials.CompleteBaseCredentialsService` respectfully. Credentials services implementations can offer the mandatory only or the full API by extending the according base class.
* All messages containing JSON objects as payload are now encoded using *Data*
  sections and are required to have the content type `application/json`.
  This affects the Tenant, Credentials and Registry API. When evaluating Hono
  still accepts *AMQP Values* of type String or byte[]. But this behavior is
  deprecated and my be dropped in releases after 0.8.

## 0.7

### New Features

* The MQTT protocol adapter now supports Command and Control. Please refer to [MQTT adapter User Guide]({{< relref "/user-guide/mqtt-adapter.md" >}}) for details.
* The Credentials API now explicitly defines [Bcrypt](https://de.wikipedia.org/wiki/Bcrypt) as a supported hash function for [*hashed-password* credentials](https://www.eclipse.org/hono/api/credentials-api/#hashed-password). The protocol adapters also support verification of username/password credentials against Bcrypt hashes.
* Hono's HTTP and MQTT protocol adapters and HonoClient have been instrumented using [OpenTracing](http://opentracing.io) in order to support tracing of the interactions between Hono components that are involved in the processing of messages as they flow through the system. The new [Monitoring & Tracing]({{< relref "/admin-guide/monitoring-tracing-config.md" >}}) admin guide has the details.
* Hono now contains an initial version of an AMQP protocol adapter which can be used to connect devices to Hono using the AMQP 1.0 protocol. The adapter currently exposes Telemetry and Event endpoints only. Support for Command & Control will be added in a future release. Please refer to the AMQP adapter's [Admin Guide]({{< relref "/admin-guide/amqp-adapter-config.md" >}}) and [User Guide]({{< relref "/user-guide/amqp-adapter.md" >}}) for details regarding how to set up and use the new adapter.

### Fixes & Enhancements

* Hono is now licensed under the [Eclipse Public License 2.0](https://www.eclipse.org/legal/epl-2.0/). Please refer to the Eclipse Foundation's [FAQ](https://www.eclipse.org/legal/epl-2.0/faq.php) for details regarding any implications this might have.
* Hono deployment scripts are now available under `deploy` folder. Deployment scripts which were previously available under `example` folder were moved to `deploy`.
* Hono-cli (Command Line Interface) is now available under folder `cli`. A command line argument `message.type` with value `telemetry`, `event` or `all` (default) tells the client what kind of messages to be received. See [Starting a Consumer]({{< relref "getting-started.md#starting-a-consumer" >}}) for more information.
* Added metrics to Command and Control for HTTP and MQTT protocol adapters. Now Hono-Dashboard also shows the metrics from Command and Control.
* Add a *dummy* implementation of the device registry services. This allows to
  do better scale testing as the file based device registry cannot be scaled up
  and thus is a bottleneck in the example setup. The device registry however has
  no real storage. So it still can be part of the test, but is no limiting
  factor anymore.
* The Hono Travis build now also builds for JDK 10 in addition to JDK 8.
  Hono is still intended to run on Java 8, but the JDK 10 build was enabled to
  be better prepared for Java 11.
* The example application in the `example` folder now supports Command and Control for all `ttd` values, including a 
  value of `-1` that signals that a device stays connected for an unlimited time frame. In this case it sends a command
  every 15 seconds, which is helpful for testing this feature with MQTT devices. A `ttd` value of `0` stops this
  behaviour again (both automatically sent by the MQTT adapter for `subscribe` and `unsubscribe`, see 
  [Consuming Messages from Java]({{< relref "dev-guide/java_client_consumer.md" >}}) for details). 
* The maximum value for the value of `ttd` that is allowed for requests to the HTTP adapter is now configurable per tenant. 
  The default value is `60` seconds. 
  Please refer to [HTTP Adapter Tenant Configuration]({{< relref "user-guide/http-adapter.md#tenant-specific-configuration" >}}).


### API Changes

* Fix the `EventBusService` methods handling type safety to handle a
  mismatching type according to their documentation, returning `null`. This
  introduced a method signature change for `getTypesafeValueForField` and
  `removeTypesafeValueForField`. Also see PR [#757](https://github.com/eclipse/hono/pull/757).

## 0.7-M2

### New Features

* The Auth Server can now be used to authenticate clients connecting to the Apache Qpid Dispatch Router which is used in the example deployment. For this purpose the Auth Server is configured as a *remote auth server* implementing [Dispatch Router's *Auth Service Plugin* mechanism](https://qpid.apache.org/releases/qpid-dispatch-1.1.0/man/qdrouterd.conf.html#_authserviceplugin). Using this mechanism it is now possible to manage all identities and authorities using the Auth Server's configuration file.
* The HTTP protocol adapter now supports devices uploading a response to a command that has been sent to the device before. Please refer to the [HTTP adapter User Guide]({{< relref "user-guide/http-adapter.md#sending-a-response-to-a-previously-received-command" >}}) for details.
* Hono's service components can now be configured to use OpenSSL instead of the JVM's default SSL engine. The [admin guide]({{< relref "admin-guide/secure_communication.md#using-openssl" >}}) describes how to do this.
* In addition to number of successful MQTT and HTTP messages now also the
  payload size of the message bodys is being recorded in the metrics system.

### Fixes & Enhancements

* The Device Registry's AMQP endpoints can now be configured with the number of credits they should flow to clients connecting to the endpoints. The default value is 100. See [Device Registry admin guide]({{< relref "admin-guide/device-registry-config.md#service-configuration" >}}) for details.

### API Changes

* The Command & Control API has been changed to be less restrictive on the format of *reply-to* addresses. Response messages are no longer required to be scoped to a single device but may instead be scoped to a tenant. This allows for applications to implement a *generic* command response handler, thus allowing for easier fail-over between nodes.

## 0.7-M1

### Fixes & Enhancements

* `HonoClientImpl`'s strategy for attempting to establish a connection with a peer has been enhanced. The client's *connect* methods by default will only try three times to establish a TCP connection with the peer before giving up. Based on the value of the new *reconnectAttempts* property of `ClientConfigProperties`, the client will then either re-try to connect to the peer (including a fresh DNS lookup of the peer's host name) or fail the overall connection attempt. This way, the client will not get stuck in an endless loop if the peer's IP address has changed or the peer has crashed while the client tries to connect to it.
* The Java Virtual Machines run by Docker images provided by Hono now consider resource limitations defined for a container on startup. See [Limiting Resource Usage]({{< relref "deployment/resource-limitation.md" >}}) for details how this can e.g. be used to limit memory consumption. The example deployment already makes use of this mechanism.

## 0.6

### New Features

* Protocol adapters, services and HonoClient now support TLS 1.2 only by default when using TLS to secure communication. However, additional protocols can be enabled by means of setting environment variables as described in the admin guides.
* The deployment examples for OpenShift got overhauled. The two provided
examples are not both based on EnMasse and follow a similar architecture. The
newly added *source-to-image*" based approach doesn't require a local
development setup but created new images directly in the OpenShift
instance. It also makes more use of ConfigMaps and service key/cert management.
* **Tech preview**: Protocol adapters do have the ability to send out connection events. Those
  events are best-effort events, indicating when a connection has been
  established and when it has been lost. There is a pluggable way of
  handling/creating those events, including two default implementations. A
  logger implementation, which simply logs to the logging system. And one
  implementation which sends out events to the *Hono Event API*.  
  **Note**: This feature is part of the Eclipse IoT integration effort and not
  yet considered a public API.
* The HTTP protocol adapter now supports authentication of devices based on X.509 client certificates. Each tenant can be configured with an individual trust anchor which the HTTP adapter will retrieve using the Tenant API when a device tries to authenticate with a certificate as part of a TLS handshake. The Credentials API now supports a [new credentials type]({{< relref "api/Credentials-API.md#x-509-certificate" >}}) for registering a mapping of the certificate's *subject DN* to the device identifier. Please consult the [HTTP adapter User Guide]({{< relref "user-guide/http-adapter.md#device-authentication" >}}) for details regarding usage.
* The HTTP adapter now supports uploading telemetry messages using QoS 1 (`AT_LEAST_ONCE`). Clients must set the `QoS-Level` request header if they want the HTTP adapter to upload telemetry messages using QoS 1.
* The concept and implementation of *Device notifications* were added. It enables devices to signal that they are ready to receive an upstream message by specifying a `time til disconnect` parameter with any downstream message. Please see [Device notifications]({{< relref "concepts/device-notifications.md" >}}) for details.
* **Tech preview**: *Command and Control* is now available for the HTTP protocol adapter (NB: currently without responses from the device to the application). 
  It enables HTTP devices to signal how long they stay *connected* to the HTTP protocol adapter, resulting in a delayed response.
  The response then may contain a command sent by the application. Please refer to the [Getting Started]({{< ref "getting-started" >}})
  guide and the Command & Control [concept page]({{< relref "concepts/command-and-control.md" >}}) for details.  
  **Note**: This feature is available now as a first fully working version but is considered to possibly have some unknown issues that may not make it
  fully production ready yet.

### Fixes & Enhancements

* Hono's standard protocol adapters can now be connected directly to the AMQP Network, i.e. without going through Hono Messaging. For Hono's standard adapters Hono Messaging does not provide any additional value because the devices' registration status is already being validated by the protocol adapters. Omitting Hono Messaging should therefore reduce message processing latency for standard adapters. However, custom protocol adapters still need to be connected to Hono Messaging. The Getting started guide, the Sandbox and the deployment scripts have been changed accordingly. Note that it is still possible to connect all adapters to Hono Messaging, though.

### API Changes

* The Tenant API's *get Tenant Information* operation has been changed to expect search criteria in the request message's payload instead of the application-properties. This change has been made in order to support other search criteria than just the tenant identifier. In particular, the *get Tenant Information* operation can now be used to find a tenant based on the subject DN of a trusted certificate authority that has been configured for the tenant. See [get Tenant Information]({{< relref "api/Tenant-API.md#get-tenant-information" >}}) for details.
* The result type of `org.eclipse.hono.util.MessageHelper.getPayload(Message msg)` has been changed from `String` to the more generic `io.vertx.core.buffer.Buffer` to be able to handle e.g. binary data. 

* The default way how `HonoClient` instances are being created has changed.
As the default implemention `HonoClientImpl` was located in an internal
`impl` package, it wasn't accessible when using OSGi as this was package wasn't
exported. The old constructor is still available. In combination with that the
`ConnectionFactoryImpl` builder concept was removed as it didn't add anything
on top of the `ClientConfigProperties`. The `ConnectionBuilderImpl` class
was also moved to an `impl` package to follow the pattern of
`HonoClientImpl`. The two new methods to created instances are are:
`org.eclipse.hono.connection.ConnectionFactory.newConnectionFactory(Vertx, ClientConfigProperties)`
and
`org.eclipse.hono.client.HonoClient.newClient(Vertx, ClientConfigProperties)`.

## 0.6-M2

### API Changes

* The `HonoClient.isConnected()` method has been changed to return a `Future<Void>` instead of `Future<Boolean>`. The future will succeed if the client is connected and will fail otherwise. This change makes it easier to compose the check with other futures.
* The signatures of the (base) methods for processing requests of `org.eclipse.hono.service.credentials.BaseCredentialsService`, `org.eclipse.hono.service.registration.BaseRegistrationService` and `org.eclipse.hono.service.tenant.BaseTenantService` have changed to both accept and return an `org.eclipse.hono.util.EventBusMessage`. Subclasses overriding the corresponding methods will need to be adapted accordingly.

### Fixes & Enhancements

* The *hono-client* and *hono-core* artifacts no longer depend on classes from the *Spring* framework which can help reducing the footprint of applications that want to use the Hono client but otherwise do not employ any Spring libraries.
* The Qpid Dispatch Router used in the example configuration has been updated to version 1.0.1.
* vert.x has been updated to version 3.5.1.
* The MQTT adapter now also supports shortened versions of the telemetry and event topic names. Devices may use just `t` instead of `telemetry` and `e` instead of `event`.

## 0.6-M1

### New Features

* The MQTT protocol adapter now supports publishing telemetry data using either QoS 0 or QoS 1. In case of QoS 1 the adapter will send an MQTT *PUBACK* to the device once the downstream peer has settled the message with the AMQP *accepted* outcome.
* Hono now specifies a [Tenant API]({{< relref "api/Tenant-API.md" >}}) and contains an exemplary implementation of this API.
  The purpose of the API is to make Hono aware of the tenants that are available in an installation. This comprises of:
  * a file-based version of the Tenant API service that implements all mandatory and optional operations
  * the implementation of the AMQP 1.0 endpoint as part of the device registry component
  * the AMQP 1.0 based implementation of the mandatory **get** operation of the API
  * an HTTP endpoint to support CRUD operations for tenants (GET, POST, PUT, DELETE) for convenience
* `org.eclipse.hono.client.impl.AbstractRequestResponseClient` now supports generic caching of responses to service invocations based on *cache directives*. See [Hono Client Configuration]({{< relref "admin-guide/hono-client-configuration.md" >}}) for details.
* The protocol adapters now can be enabled/disabled *per tenant* using the [Tenant API]({{< relref "api/Tenant-API.md" >}}). A protocol adapter that has been disabled for a tenant will reject telemetry messages and events published by any device that belongs to the particular tenant.

### Fixes & Enhancements

* HonoClient now fails all futures it returns with instances of `org.eclipse.hono.client.ServiceInvocationException` if something goes wrong. Client code can inspect the exception's *errorCode* property to get a better understanding of the reason for the failure.

## 0.5

### New Features

* We have added a protocol adapter for allowing [Eclipse Kura] (https://www.eclipse.org/kura) gateways to publish *control* and *data* messages to Hono's Telemetry and Event API. See [Kura Adapter]({{< relref "admin-guide/kura-adapter-config.md" >}}) for details.
* `RegistrationClientImpl` now supports caching of registration assertions received from a Device Registration service. The protocol adapters already make use of this feature  so that they do not need to do a remote service invocation unless a cached assertion has expired. The protocol adapters support two new configuration variables to set the minimum and maximum cache size.
* Devices can now be configured to act as *gateways* and publish data *on behalf of* other devices that are not connected to a protocol adapter directly but to the gateway. This is useful for receiving data from devices using narrow band radio communication like [SigFox](https://www.sigfox.com) or [LoRa](https://www.lora-alliance.org/). See [Configuring Gateway Devices]({{< relref "admin-guide/device-registry-config.md#configuring-gateway-devices" >}}) for details.

### Fixes & Enhancements

* See [Git Hub](https://github.com/eclipse/hono/issues?utf8=%E2%9C%93&q=is%3Aissue%20milestone%3A0.5) for the list of issues addressed.
* The documentation of Hono's individual components has been split up into information relevant for *using* the components (*User Guide*) and information relevant for *configuring* the components (*Admin Guide*).

### Configuration Changes

* All Hono Docker images after 0.5-M10 now use `eclipse/` instead of `eclipsehono/` as the prefix in the image repository name.
* The default names of the files used by the Device Registry component for persisting data have changed:
   * `/home/hono/registration/device-identities.json` has been changed to `/var/lib/hono/device-registry/device-identities.json`
   * `/home/hono/registration/credentials.json` has been changed to `/var/lib/hono/device-registry/credentials.json`
* The Device Registry used in the *Getting started* guide now by default persists data to a file system volume.
* The *REST Adapter* has been renamed to *HTTP Adapter* because it does not really comply with the common requirements for RESTful services. As part of this effort, the names of the HTTP adapter's configuration variables have also been changed accordingly. See [HTTP Adapter Configuration]({{< relref "admin-guide/http-adapter-config.md#service-configuration" >}}) for details.
* The Device Registry component's `HONO_CREDENTIALS_SRV_CREDENTIALS_FILENAME` configuration variable has been shortened to just `HONO_CREDENTIALS_SVC_FILENAME` to match its counterpart for configuring the filename of the device registration service implementation.

### API Changes

* The [Telemetry API]({{< relref "api/Telemetry-API.md" >}}) has been updated to recommend clients to use *AT LEAST ONCE* delivery semantics instead of *AT MOST ONCE*. This change has been made to better support end-to-end flow control between protocol adapters (devices) and downstream consumers. Note that this change has no impact on the *quality of service* that devices and consumers experience, i.e. telemetry data published by a device to a protocol adapter is still *not guaranteed* to be delivered to a downstream consumer even if the device has received an acknowledgement from the protocol adapter indicating that it has accepted the data (e.g. a 202 HTTP status code).
* The `org.eclipse.hono.client.HonoClient` interface has been changed:
   * All methods that had previously returned `HonoClient` have been changed to return `Future<HonoClient>` instead. Returning the client instance had originally been intended to be useful for chaining commands. However, there was nothing much to chain because the effect of invoking the (asynchronous) operations is usually not immediately visible in the client, e.g. when invoking the *connect* method, the returned client will most likely not (yet) be connected.
   * All methods that had previously accepted a `Handler<AsyncResult>` have been changed to return a `Future` instead. This makes orchestration of these methods and their results using `Future.compose`, `Future.map` etc. much easier.
* The `org.eclipse.hono.client.MessageSender` interface has been changed:
   * All methods now return a `Future<ProtonDelivery>` to indicate the outcome of the operation according to the sender specific delivery semantics.
     For a Telemetry client this means that the future will be succeeded immediately after the message has been sent, i.e. the client does not wait
     for a downstream container to accept the message.
     For an Event client this means that the future will be succeeded once the downstream container has settled and accepted the message.
   * All operations accepting a disposition handler have been removed in order to relieve clients from the burden of (correctly) implementing the delivery semantics.
* The `org.eclipse.hono.client.RegistrationClient` interface has been changed:
   * All methods that had previously accepted a `Handler<AsyncResult>` have been changed to return a `Future` instead. This makes orchestration of these methods and their results using `Future.compose`, `Future.map` etc. much easier.
* The `org.eclipse.hono.client.CredentialsClient` interface has been changed:
   * All methods that had previously accepted a `Handler<AsyncResult>` have been changed to return a `Future` instead. This makes orchestration of these methods and their results using `Future.compose`, `Future.map` etc. much easier.
* The [assert Device Registration]({{< relref "api/Device-Registration-API.md#assert-device-registration" >}}) operation of the Device Registration API has been extended with an optional *gateway_id* parameter which can be used to get a registration status assertion on behalf of another device. This is mainly intended to support use cases where devices do not connect to a protocol adapter directly but are connected to a *gateway* component which *acts on behalf of* its connected devices when publishing data to a protocol adapter.
A corresponding *assertRegistration* operation has been added to the `org.eclipse.hono.client.RegistrationClient` and `org.eclipse.hono.service.registration.RegistrationService` interfaces which require both a device ID and a gateway ID being passed in as parameters.

## 0.5-M10

### New Features

* We have set up a [Sandbox server]({{< relref "sandbox.md" >}}) at `hono.eclipse.org` which can be used to connect devices and consumers for testing purposes without the need to run a Hono instance locally.

### Fixes & Enhancements

See [Git Hub](https://github.com/eclipse/hono/issues?utf8=%E2%9C%93&q=is%3Aissue%20milestone%3A0.5-M10) for the list of issues addressed.

### Configuration Changes

* The example *Dispatch Router* is configured to use balanced distribution of messages to consumers (vs. multicast before). For full AMQP flow control, this is the preferred option.
