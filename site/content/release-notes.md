+++
title = "Release Notes"
+++

## 0.6 (not yet released)

### API Changes

* The Tenant API's *get Tenant Information* operation has been changed to expect search criteria in the request message's payload instead of the application-properties. This change has been made in order to support other search criteria than just the tenant identifier. In particular, the *get Tenant Information* operation can now be used to find a tenant based on the subject DN of a trusted certificate authority that has been configured for the tenant. See [get Tenant Information]({{< relref "api/Tenant-API.md#get-tenant-information" >}}) for details.

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
A corresponding *assertRegsitration* operation has been added to the `org.eclipse.hono.client.RegistrationClient` and `org.eclipse.hono.service.registration.RegistrationService` interfaces which require both a device ID and a gateway ID being passed in as parameters.

## 0.5-M10

### New Features

* We have set up a [Sandbox server]({{< relref "sandbox.md" >}}) at `hono.eclipse.org` which can be used to connect devices and consumers for testing purposes without the need to run a Hono instance locally.

### Fixes & Enhancements

See [Git Hub](https://github.com/eclipse/hono/issues?utf8=%E2%9C%93&q=is%3Aissue%20milestone%3A0.5-M10) for the list of issues addressed.

### Configuration Changes

* The example *Dispatch Router* is configured to use balanced distribution of messages to consumers (vs. multicast before). For full AMQP flow control, this is the preferred option.
