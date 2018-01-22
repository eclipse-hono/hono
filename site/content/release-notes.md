+++
title = "Release Notes"
menu = "main"
weight = 155
+++

## 0.5 (not released yet)

### New Features

* We have added a protocol adapter for allowing [Eclipse Kura](https://www.eclipse.com/kura) gateways to publish *control* and *data* messages to Hono's Telemetry and Event API. See [Kura Adapter]({{< relref "user-guide/kura-adapter.md" >}}) for details.
* `RegistrationClientImpl` now supports caching of registration assertions received from a Device Registration service. The protocol adapters already make use of this feature  so that they do not need to do a remote service invocation unless a cached assertion has expired. The protocol adapters support two new configuration variables to set the minimum and maximum cache size.

### Fixes & Enhancements

See [Git Hub](https://github.com/eclipse/hono/issues?utf8=%E2%9C%93&q=is%3Aissue%20milestone%3A0.5) for the list of issues addressed.

### Configuration Changes

* All Hono Docker images after 0.5-M10 now use `eclipse/` instead of `eclipsehono/` as the prefix in the image repository name.
* The default names of the files used by the Device Registry component for persisting data have changed:
   * `/home/hono/registration/device-identities.json` has been changed to `/var/lib/hono/device-registry/device-identities.json`
   * `/home/hono/registration/credentials.json` has been changed to `/var/lib/hono/device-registry/credentials.json`
* The Device Registry used in the *Getting started* guide now by default persists data to a file system volume.
* The *REST Adapter* has been renamed to *HTTP Adapter* because it does not really comply with the common requirements for RESTful services. As part of this effort, the names of the HTTP adapter's configuration variables have also been changed accordingly. See [HTTP Adapter Service Configuration]({{< relref "user-guide/http-adapter.md#service-configuration" >}}) for details.

### API Changes

* The [Telemetry API]({{< relref "api/Telemetry-API.md" >}}) has been updated to recommend clients to use *AT LEAST ONCE* delivery semantics instead of *AT MOST ONCE*. This change has been made to better support end-to-end flow control between protocol adapters (devices) and downstream consumers.
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
A corresponding *assert* operation has been added to the `org.eclipse.hono.service.registration.RegistrationService` interface which accepts both a device ID and a gateway ID.

## 0.5-M10

### New Features

* We have set up a [Sandbox server]({{< relref "sandbox.md" >}}) at `hono.eclipse.org` which can be used to connect devices and consumers for testing purposes without the need to run a Hono instance locally.

### Fixes & Enhancements

See [Git Hub](https://github.com/eclipse/hono/issues?utf8=%E2%9C%93&q=is%3Aissue%20milestone%3A0.5-M10) for the list of issues addressed.

### Configuration Changes

* The example *Dispatch Router* is configured to use balanced distribution of messages to consumers (vs. multicast before). For full AMQP flow control, this is the preferred option.

## 0.5-M9

### New Features

* Support for publishing Metrics from any component has been added. New metrics for number of published/discarded messages have been added to MQTT Adapter and REST Adapter.
* The *Device Registry* component now also implements the Credentials API and exposes its operations via an AMQP 1.0 endpoint (as defined by the API specification) and an HTTP endpoint providing a set of RESTful resources for managing credentials. The HTTP endpoint is provided for convenience only, so that it is easier to e.g. register credentials for a device from the command line using tools like *curl* and/or *HTTPie*. See the Device Registry documentation for details regarding the API.
* The MQTT and the REST adapter now **require devices to authenticate** using a username and password by default. The example file based credentials implementation includes a set of (*hashed-password*) credentials ("sensor1"/"hono-secret") which can be used for demonstration purposes. Additionally, for special test scenarios the authentication of devices can be disabled in adapters using a configuration property, in which case the adapter is open to all clients (like it was before).

### API Changes

* The Device Registration API's *update* and *deregister* operations have been adapted to match the semantics of the Credentials API. Both operations now return a status code of 204 (*No Content*) on successful execution and do no longer contain the previous value in the response body.
* The `org.eclipse.hono.service.credentials.CredentialsService` interface's methods have been renamed to better reflect their purpose. Users implementing this interface should adapt their code accordingly.

## 0.5-M8

### New Features

* As already hinted at by the change log for 0.5-M7, the *Device Registry* now exposes RESTful resources accessible via HTTP which represent the operations of the Device Registration API. This is in addition to the standard AMQP 1.0 based Device Registration endpoint. In exchange, the REST Adapter no longer exposes these resources, i.e. device can no longer be registered using the REST Adapter. In subsequent milestones the Device Registry will be extended to also expose the Credentials API by means of RESTful resources accessible via HTTP. Please note that the RESTful resources do **not** constitute an *official* Hono API, i.e. the Device Registration API and the Credentials API are still defined by means of AMQP 1.0 message exchanges only. The REST API is simply provided for convenience, so that it is easier to e.g. register a device from the command line using tools like *curl* and/or *HTTPie*.

### Fixes & Enhancements

See [Git Hub](https://github.com/eclipse/hono/issues?utf8=%E2%9C%93&q=is%3Aissue%20milestone%3A0.5-M8) for the list of issues addressed by 0.5-M8.

## 0.5-M7

### New Features

* The metrics reported by Hono are now processed in InfluxDB by means of *templates* which parse some of the information contained in the keys into and InfluxDB *tags*. Consequently, the Grafana dashboard now correctly displays (aggregated) metrics reported by multiple *Hono Messaging* instances and provides means to filter metrics per tenant.
* The Hono Messaging, Device Registry, REST and MQTT Adapter components now expose meaningful *health checks* via HTTP which can be used by container orchestration tools like Kuberetes to e.g. determine a component's readiness to serve requests.

### Fixes & Enhancements

See [Git Hub](https://github.com/eclipse/hono/issues?utf8=%E2%9C%93&q=is%3Aissue%20milestone%3A0.5-M7) for the list of issues addressed by 0.5-M7.

### Configuration Changes

* The *Device Registry* and *Auth Server* components will support the configuration of API endpoints employing other transport protocols than *AMQP* in the future. In preparation for that, the environment variable names for configuring these components have been adapted accordingly.
* Metrics default for the *prefix* in the Graphite reporter is now the component's host name.
* The Docker images for Hono's components do no longer contain demo keys and certificates. The keys, certificates and configuration data are now provided to containers during deployment by means of Docker and/or Kubernetes *Secrets*. Consequently, Hono does no longer provide its custom *Dispatch Router* Docker image but instead uses the standard image provided by the enmasse project.
* Hono no longer uses a Docker Compose file for deploying to Docker Swarm. Instead, a shell script is used which runs multiple Docker Swarm commands for deploying Hono. This approach provides for more flexibility and is more consistent with the approach already taken for the deployment to Kubernetes/OpenShift.

See component docs for details.


## 0.5-M6

### New Features

* Protocol adapters can now be configured to listen on both TLS secured and unsecured ports.
* Initial support for reporting metrics using Spring Boot Actuator. Metrics can be reported to services understanding the *graphite* format.
* Better support for integration with existing device registry and identity management systems by means of refactoring of *Hono server* into three micro-services (see Architectural Changes section).
* Introduced a set of base classes for implementing services in general and services implemening Device Registration and Authentication API in particular.
* Support Kubernetes as a deployment platform.
* Overall improvement of resilience in context of connection failures.
* JMeter plugin for load tests, which includes samplers for a Hono sender (acts as an adapter) and a Hono receiver (acts as a solution/application). Also a sample plan is provided.

### Bug Fixes

See [Git Hub](https://github.com/eclipse/hono/issues?utf8=%E2%9C%93&q=is%3Aissue%20milestone%3A0.5-M6%20) for the list of issues addressed by 0.5-M6.

### Architectural Changes

The biggest change is the fact that we have re-factored the Authorization service and the Device Registration service implementations that where originally part of the *Hono server* component into their own components. The Authorization service has been re-factored into the *Auth Server* component and the Device Registration service has been re-factored into the *Device Registry* component. Consequently, the former *Hono server* component has been re-named to *Hono Messaging* to better reflect its sole responsibility of forwarding telemetry and event messages from/to devices and the *AMQP 1.0 Messaging Network* (see [Component View]({{< relref "Component-View.md" >}}) for an overview of Hono's top level components and their relationships with each other).

As part of the refactoring we have introduced the `services` Maven module which now contains

* the *Auth Server* component in the `auth` module,
* the *Device Registry* component in the `device-registry` module and 
* the *Hono Messaging* component in the `messaging` module, replacing the former `application` and `hono-server` modules. 

Also as part of the refactoring we have changed some Maven artifact IDs

* renamed `org.eclipse.hono:hono-server` to `org.eclipse.hono:hono-service-messaging`

### Configuration Changes

* The *Hono Messaging* and the *Device Registry* components require a connection to an *Authentication* API implementation being configured. 
* The *Hono Messaging* component (former *Hono Server*) now uses the `HONO_MESSAGING` config variable prefix instead of the former `HONO_SERVER` prefix.
* The protocol adapters now require two connections being configured, one to the *Hono Messaging* component (using the `HONO_MESSAGING` environment variable prefix) and another one to the *Device Registration* service implementation (using the `HONO_REGISTRATION` environment variable prefix).

See component docs for details.

### API Changes

The following backwards incompatible changes have been made to existing API, code depending on these APIs needs to be updated accordingly:

1. Renamed several configuration classes in order to better reflect their general usability for configuring client and server components.

  * renamed `org.eclipse.hono.config.AbstractHonoConfig` to `org.eclipse.hono.config.AbstractConfig`
  * renamed `org.eclipse.hono.config.HonoClientConfigProperties` to `org.eclipse.hono.config.ClientConfigProperties`
  * renamed `org.eclipse.hono.config.HonoConfigProperties` to `org.eclipse.hono.config.ServiceConfigProperties`

1. Moved classes to be reused by other services to `hono-service-base` module.

  * moved `org.eclipse.hono.adapter.AdapterConfig` to `org.eclipse.hono.service.AbstractAdapterConfig`
  * moved `org.eclipse.hono.server.Endpoint` to `org.eclipse.hono.service.amqp.Endpoint`
  * moved `org.eclipse.hono.server.BaseEndpoint` to `org.eclipse.hono.service.amqp.BaseEndpoint`
  * moved `org.eclipse.hono.server.UpstreamReceiver` to `org.eclipse.hono.service.amqp.UpstreamReceiver`
  * moved `org.eclipse.hono.server.UpstreamReceiverImpl` to `org.eclipse.hono.service.amqp.UpstreamReceiverImpl`

1. Moved former *Hono Server* classes to `services/messaging` module.

  * renamed package `org.eclipse.hono.server` to `org.eclipse.hono.messaging`
  * moved `org.eclipse.hono.server.HonoServer` to `org.eclipse.hono.server.HonoMessaging`
  * moved `org.eclipse.hono.server.HonoServerConfigProperties` to `org.eclipse.hono.server.HonoMessagingConfigProperties`
  * moved `org.eclipse.hono.server.HonoServerMessageFilter` to `org.eclipse.hono.server.HonoMessagingMessageFilter`
  * moved `org.eclipse.hono.application.HonoApplication` to `org.eclipse.hono.server.HonoMessagingApplication`
  * moved `org.eclipse.hono.application.ApplicationConfig` to `org.eclipse.hono.server.HonoMessagingApplicationConfig`

1. Moved Device Registration related classes to `hono-service-base` module.

  * renamed package `org.eclipse.hono.registration` to `org.eclipse.hono.service.registration`
  * moved `org.eclipse.hono.registration.impl.RegistrationEndpoint` to `org.eclipse.hono.service.registration.RegistrationEndpoint`
  * moved `org.eclipse.hono.registration.impl.BaseRegistrationService` to `org.eclipse.hono.service.registration.BaseRegistrationService`

1. Use standard AMQP 1.0 `subject` property instead of custom `action` application property in Device Registration API.

1. Rename property `id` of Device Registration API's response payload to `device-id` to match the name used in Credentials API.

1. Introduce mandatory to implement [assert Device Registration]({{< relref "api/Device-Registration-API.md#assert-device-registration" >}}) operation to `Device Registration API`. This operation is used by clients to assert that a given device is enabled and is registered with a particular tenant. The assertion is issued in the form of a cryptographically signed JSON Web Token (JWT) which needs to be included in messages sent to Hono containing telemetry data or an event originating from the given device.
   This also had an impact on the [*upload Telemetry Data* operation]({{< relref "api/Telemetry-API.md#upload-telemetry-data" >}}) of the Telemetry API and the [*send Event* operation]({{< relref "api/Event-API.md#send-event" >}}) of the Event API which now both require a *registration assertion* to be included in the messages containing the data to be published.