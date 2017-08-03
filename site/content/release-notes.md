+++
title = "Release Notes"
menu = "main"
weight = 800
+++

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
* No longer use a Docker Compose file for deploying to Docker Swarm. Instead, we now use a shell script which runs multiple Docker Swarm commands for deploying Hono. This approach provides for more flexibility and is more consistent with the approach already taken for the deployment to Kubernetes/Openshift.

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