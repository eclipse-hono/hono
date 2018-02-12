+++
title = "Release Notes"
+++

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
