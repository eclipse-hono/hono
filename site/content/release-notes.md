+++
title = "Release Notes"
menu = "main"
weight = 800
+++

## 0.5-M6-SNAPSHOT

Not released yet.

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

1. Moved Device Registration related classes to `hono-service-base` module.

  * renamed package `org.eclipse.hono.registration` to `org.eclipse.hono.service.registration`
  * moved `org.eclipse.hono.registration.impl.RegistrationEndpoint` to `org.eclipse.hono.service.registration.RegistrationEndpoint`
  * moved `org.eclipse.hono.registration.impl.BaseRegistrationService` to `org.eclipse.hono.service.registration.BaseRegistrationService`

1. Use standard AMQP 1.0 `subject` property instead of custom `action` application property in Device Registration API.

1. Renamed property `id` of Device Registration API's response payload to `device-id` to match the name used in Credentials API.

1. Introduce mandatory to implement [assert Device Registration]({{< relref "api/Device-Registration-API.md#assert-device-registration" >}}) operation to `Device Registration API`. This operation is used by clients to assert that a given device is enabled and is registered with a particular tenant. The assertion is issued in the form of a cryptographically signed JSON Web Token (JWT) which needs to be included in messages sent to Hono containing telemetry data or an event originating from the given device.
   This also had an impact on the [*upload Telemetry Data* operation]({{< relref "api/Telemetry-API.md#upload-telemetry-data" >}}) of the Telemetry API and the [*send Event* operation]({{< relref "api/Event-API.md#send-event" >}}) of the Event API which now both require a *registration assertion* to be included in the messages containing the data to be published.