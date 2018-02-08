+++
title = "Implement a Custom Hono HTTP Protocol Adapter"
weight = 395
+++

Eclipse Hono&trade; comes with a default *HTTP Adapter* which can be used to interact with devices via HTTP.
The default HTTP Adapter also serves as a blueprint for implementing a *custom* HTTP protocol adapter.
<!--more-->

This section will guide you through the steps to build your own custom HTTP protocol adapter.

## Prerequisites

You should be familiar with the setup and start of Hono. Refer to the 
[Getting Started]({{< relref "getting-started.md" >}}) guide.

## The standard HTTP Adapter

Hono's HTTP Adapter supports telemetry and event data processing. Please refer to the [HTTP Adapter User Guide]({{< relref "user-guide/http-adapter.md" >}}) and [HTTP Adapter Admin Guide]({{< relref "admin-guide/http-adapter-config.md" >}}) for details regarding the usage and configuration of the HTTP Adapter.

You can find the source of the HTTP Adapter at <https://github.com/eclipse/hono/tree/master/adapters/http-vertx>.

## Anatomy of the standard HTTP Adapter
 
Like many other Hono components, the HTTP Adapter is built on top of the [Vert.x](https://vertx.io) framework.

The HTTP Adapter's `VertxBasedHttpProtocolAdapter` class is derived from an abstract base class. This base class implements the base functionality for component initialization, receiving HTTP requests from devices or external clients, and forwarding of data to *Hono Messaging*.

## Derive a custom HTTP Protocol Adapter

Use the standard HTTP Adapter as a blueprint.

### Adding Routes

In Vert.x, a *route* is a mapping of an HTTP request to a *handler*. Inside a route, Vert.x provides a `RoutingContext` 
instance which gives access to the HTTP request (and response) object containing the HTTP headers.

The standard HTTP Adapter overrides the abstract method `addRoutes()`, provided by the base class, and adds routes for processing telemetry data and events.

```java
// route for uploading telemetry data
router.route(HttpMethod.PUT, String.format("/telemetry/:%s/:%s", PARAM_TENANT, PARAM_DEVICE_ID))
    .handler(ctx -> uploadTelemetryMessage(ctx, getTenantParam(ctx), getDeviceIdParam(ctx)));
```

The route for telemetry data parses the HTTP request, extracts the *tenant* and *deviceId* parameters from the
request URL path, and forwards the message payload to the method `uploadTelemetryMessage()`, provided by the base class.

**NB** Note the Vert.x place holder indicators `:` inside the URL path pattern `/telemetry/:%s/:%s`. Vert.x makes matching 
place holders available as request parameters. See [Capturing path parameters](http://vertx.io/docs/vertx-web/java/#_capturing_path_parameters) in the Vert.x documentation.

The route for events looks very similar to the route for telemetry data. It forwards the event message payload to the `uploadEventMessage()` method.

Please refer to the [Telemetry API]({{< relref "api/Telemetry-API.md" >}}) and [Event API]({{< relref "api/Event-API.md" >}}) 
for details about the different Hono APIs.

In the custom HTTP protocol adapter adapt the routes according to your needs.

## Build and run the custom HTTP Protocol Adapter
 
If you have Hono running, you can launch your custom HTTP protocol adapter as a Docker Container or a Spring Boot application.

You may adopt the Maven profile `build-docker-image` from the Maven POM file of the standard HTTP Adapter into your 
custom adapter's Maven POM file. 

Follow the guidelines for running the HTTP Adapter in [HTTP Adapter]({{< relref "admin-guide/http-adapter-config.md" >}}). Don't forget to configure the custom protocol adapter to bind to a different port than the standard HTTP Adapter if you intend to run them both at the same time. See the [Port Configuration section]({{< relref "admin-guide/http-adapter-config.md#port-configuration" >}}) of the HTTP Adapter documentation for details.

## Using the custom HTTP Protocol Adapter

Now that you have your custom HTTP protocol adapter up and running, you can use any HTTP client, like `curl` or 
`HTTPie`, to publish data to your custom adapter.

Note that before publishing data to your custom HTTP protocol adapter, you need to start a *consumer* for the tenant you intend to publish data for.
Otherwise you will not be able to successfully send data. For this purpose, you may use the example consumer as described in the [Getting Started]({{< relref "getting-started.md" >}}) guide.

## Further extend the custom HTTP Protocl Adapter

The abstract base class includes additional hooks which you may use to *plug into* the adapter's life cycle:

| Hook                    | Description                                   |
| :---------------------- | :-------------------------------------------- |
| `preStartup()`        | called before start of adapter's HTTP server |
| `onStartupSuccess()` | called after successful start of adapter |  
| `preShutdown()`       | called before stop of adapter's HTTP server |
| `postShutdown`        | called after successful stop of adapter |
