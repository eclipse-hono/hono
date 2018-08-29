+++
title = "Release Notes"
+++

## 0.8-M1 (Not released yet)

### Fixes & Enhancements

* Hono-cli extends support to Command & Control. Using command line, users can send commands to devices and receive command responses.  See [Using Cli for command & control]({{< relref "getting-started.md#using-cli-command-line-interface-to-send-commands-and-receive-command-responses" >}}) for more information.

### API Changes

* The `org.eclipse.hono.util.TenantObject`'s *getTrustAnchor* method now throws a `GeneralSecurityException` to indicate a problem with decoding/parsing the certificate or public key that is configured as the trusted CA for the tenant. This allows client code to get some insight into the reason for the failure to authenticate a device based on a client certificate.
* The `org.eclipse.hono.service.registration.RegistrationService` interface now describes only the mandatory operations of the API. The complete API is offered in `org.eclipse.hono.service.registration.CompleteRegistrationService`. These interfaces are implemented in `org.eclipse.hono.service.registration.BaseRegistrationService` and `org.eclipse.hono.service.registration.CompleteBaseRegistrationService` respectfully. Device Registries implementations can offer the mandatory only or the full API by extending the according base class.
* The `org.eclipse.hono.service.tenant.TenantService` interface now describes only the mandatory operations of the API. The complete API is offered in `org.eclipse.hono.service.tenant.CompleteTenantService`. These interfaces are implemented in `org.eclipse.hono.service.tenant.BaseTenantService` and `org.eclipse.hono.service.tenant.CompleteBaseTenantService` respectfully. Tenant services implementations can offer the mandatory only or the full API by extending the according base class.
* The `org.eclipse.hono.service.credentials.CredentialsService` interface now describes only the mandatory operations of the API. The complete API is offered in `org.eclipse.hono.service.credentials.CompleteCredentialsService`. These interfaces are implemented in `org.eclipse.hono.service.credentials.BaseCredentialsService` and `org.eclipse.hono.service.credentials.CompleteBaseCredentialsService` respectfully. Credentials services implementations can offer the mandatory only or the full API by extending the according base class.

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
