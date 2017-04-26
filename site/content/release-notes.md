+++
title = "Release Notes"
menu = "main"
weight = 800
+++

## 0.5-M6-SNAPSHOT

Not released yet.

### API Changes

1. Renamed several configuration classes in order to better reflect their general usability for configuring client and server components.

  * renamed `org.eclipse.hono.config.AbstractHonoConfig` to `org.eclipse.hono.config.AbstractConfig`
  * renamed `org.eclipse.hono.config.HonoClientConfigProperties` to `org.eclipse.hono.config.ClientConfigProperties`
  * renamed `org.eclipse.hono.config.HonoConfigProperties` to `org.eclipse.hono.config.ServiceConfigProperties`

    

1. Moved classes to be reused by other services to `hono-service-base` module.

  * moved `org.eclipse.hono.server.Endpoint` to `org.eclipse.hono.service.amqp.Endpoint`
  * moved `org.eclipse.hono.server.BaseEndpoint` to `org.eclipse.hono.service.amqp.BaseEndpoint`
  * moved `org.eclipse.hono.server.UpstreamReceiver` to `org.eclipse.hono.service.amqp.UpstreamReceiver`
  * moved `org.eclipse.hono.server.UpstreamReceiverImpl` to `org.eclipse.hono.service.amqp.UpstreamReceiverImpl`

1. Moved Device Registration related classes to `hono-service-base` module.

  * renamed package `org.eclipse.hono.registration` to `org.eclipse.hono.service.registration`
  * moved `org.eclipse.hono.registration.impl.RegistrationEndpoint` to `org.eclipse.hono.service.registration.RegistrationEndpoint`
  * moved `org.eclipse.hono.registration.impl.BaseRegistrationService` to `org.eclipse.hono.service.registration.BaseRegistrationService`