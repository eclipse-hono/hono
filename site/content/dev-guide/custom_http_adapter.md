+++
title = "Implement a Custom Hono HTTP Protocol Adapter"
weight = 260
+++

Hono comes with the Hono REST protocol adapter which you may use as a blueprint for your own HTTP protocol adapter 
implementation. 
<!--more-->



This section will guide you through the steps to build your own custom Hono HTTP protocol adapter.

## Prerequisites

You should be familiar with the setup and start of Hono. Refer to the 
[Getting Started]({{< relref "getting-started.md" >}}) guide.

## The Hono REST Protocol Adapter

The Hono REST protocol adapter supports telemetry and event data processing. Additionally, it provides an interface for 
device registration. 

For documentation of the Hono REST protocol adapter, refer to 
[HTTP Adapter]({{< relref "component/http-adapter.md" >}}) 

You can find the source of the Hono REST protocol adapter at 
<https://github.com/eclipse/hono/tree/master/adapters/rest-vertx>.

## Anatomy of the Hono REST Protocol Adapter
 
Like many other Hono components, the Hono REST protocol adapter makes use of the [Vert.x](https://vertx.io) tool-kit. 
All Hono protocol adapters are implemented as Vert.x verticles.

The Hono REST protocol adapter class `VertxBasedRestProtocolAdapter` is derived from an abstract base class. This base 
class implements the base functionality for component initialization, receiving HTTP requests from devices or external 
clients, and forwarding of data to the Hono server. Additionally, the Hono REST protocol adapter implements an 
interface for device registration.

## Derive Your Custom HTTP Adapter

Use the Hono REST protocol adapter as a blueprint. If not desired, you may remove the methods implementing the 
functionality for device registration.

## Extend Your Custom HTTP Adapter

### Adding Routes
In Vert.x, a *route* is a mapping of a HTTP request to a handler. Inside a route, Vert.x provides a `RoutingContext` 
instance which gives access to the HTTP request (and response) object containing the HTTP headers. 

The Hono REST protocol adapter overrides the abstract method `addRoutes()`, provided by its base class, and adds the 
routes for telemetry data, event data, and device registration. Thereby, it connects incoming requests to their 
appropriate handler.

```java
// route for uploading telemetry data
router.route(HttpMethod.PUT, String.format("/telemetry/:%s/:%s", PARAM_TENANT, PARAM_DEVICE_ID))
    .handler(ctx -> uploadTelemetryMessage(ctx, getTenantParam(ctx), getDeviceIdParam(ctx)));
```
 
The route for telemetry data parses the HTTP request, extracts the two parameters *tenant* and *deviceId* from the 
request URL path, and forwards the message payload to the method `uploadTelemetryMessage()`, provided by the base class.

{{% note %}}
Note the Vert.x placeholder indicators `:` inside the URL path pattern `/telemetry/:%s/:%s`. Vert.x makes matching 
placeholders available as request parameters. See [Capturing path parameters](
http://vertx.io/docs/vertx-web/java/#_capturing_path_parameters) in the Vert.x documentation.
{{% /note %}}

The route for event data looks very similar to the route for telemetry data. It forwards the event message payload to 
the method `uploadEventMessage()`.

Refer to the [Telemetry API]({{< relref "api/Telemetry-API.md" >}}) and [Event API]({{< relref "api/Event-API.md" >}}) 
sections of this documentation for further information about the different Hono data APIs.

In your own custom HTTP protocol adapter adapt the routes according to your needs. 

## Build and Run Your Custom HTTP Adapter
 
If you have Hono running, you can launch your custom HTTP protocol adapter as a Docker Container or a Spring Boot 
application.

You may adopt the Maven profile `build-docker-image` from the Maven POM file of the Hono REST protocol adapter into your 
custom adapter's Maven POM file. 

Follow the guidelines for running the Hono REST protocol adapter in 
[HTTP Adapter]({{< relref "component/http-adapter.md" >}}). Don't forget to configure the custom adapter's port if you 
have the Hono REST protocol adapter running. See the Configuration section of the Hono REST adapter for the details to 
configure the port. 

## Use Your Custom HTTP Adapter

Now that you have your custom HTTP protocol adapter up and running, you can use any HTTP client, like `curl` or 
`HTTPie`, to connect to your custom adapter. 

Note that before starting sending data to your custom HTTP protocol adapter, you need a data consumer connected to Hono. 
Otherwise you will not be able to successfully send data. You may use the Hono example consumer. See the 
[Getting Started]({{< relref "getting-started.md" >}}) guide.

## Further Extend Your Custom HTTP Adapter
The following section provides some additional options to further extend and adapt your custom HTTP protocol adapter. 

### Extension Hooks
The abstract base class includes additional hooks which you may use to further interfere in the lifecycle of the 
adapter.

| Hook | Description |
| ---- | ---- |
| `preStartup()` | called before start of adapter's HTTP server |
| `onStartupSuccess()` | called after successful start of adapter |  
| `preShutdown()` | called before stop of adapter's HTTP server |
| `postShutdown` | called after successful stop of adapter |
