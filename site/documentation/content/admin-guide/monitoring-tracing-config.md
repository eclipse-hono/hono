+++
title = "Monitoring & Tracing"
weight = 355
+++

The individual components of an Eclipse Hono&trade; installation need to work together in order to provide their
functionality to devices and applications. Under normal circumstances these interactions work flawlessly.
However, due to the nature of distributed systems, any one (or more) of the components may crash or become otherwise
unavailable due to arbitrary reasons. This page describes how Hono supports operations teams by providing insights
into the individual service components and their interactions with each other by means of reporting metrics and
tracing the processing of individual messages through the system.
<!--more-->

When a device uploads telemetry data to the HTTP adapter, the adapter invokes operations on the Device Registration,
Credentials and the Tenant services in order to authenticate and authorize the device before sending the telemetry
data downstream to the messaging infrastructure. The overall success of this process and the latency involved before
the message reaches the consumer is determined by the individual interactions between the service components.

## Monitoring

In a production environment, an operations team will usually want to keep track of some *key performance indicators* (KPI)
which allow the team to determine the overall *health* of the system, e.g. memory and CPU consumption etc.
Hono supports the tracking of such KPIs by means of metrics it can be configured to report. The metrics are usually
collected in a time series database like *InfluxDB* or *Prometheus* and then visualized on a monitoring dash-board built
using frameworks like *Grafana*. Such a dash-board can usually also be configured to send alarms when certain
thresholds are exceeded.

Metrics usually provide insights into the past and current status of an individual component. The values can be
aggregated to provide a picture of the overall system's status. As such, metrics provide a great way to *monitor*
system health and, in particular, to anticipate resource shortages and use such knowledge to pro-actively prevent
system failure.

### Enabling Collection of Metrics

The Hono build supports configuration of the Prometheus metrics back end by means of the `metrics-prometheus`
Maven profile.

The profile is not active by default and needs to be activated explicitly:

```sh
mvn clean install -Pmetrics-prometheus ...
```

Most of the metrics back ends expect a component to actively *push* metrics to an endpoint provided by the back end.
However, Prometheus is different in that it *polls* (or *scrapes*) all components periodically for new metrics data.
For this to work, the Prometheus server needs to be configured with the host names/IP addresses of the components to
monitor.

The components themselves need to expose a corresponding HTTP endpoint that the Prometheus server can connect to for
scraping the meter data. All Hono components that report metrics can be configured to expose such an endpoint via
their [*Health Check* server]({{< relref "#health-check-server-configuration" >}}) which already exposes endpoints for
determining the component's readiness and liveness status.

## Health Check Server Configuration

All of Hono's service components and protocol adapters contain a *Health Check* server which can be configured to
expose several HTTP endpoints for determining the component's status. Under the hood, Hono uses Quarkus' *SmallRye
Health* extension to implement the health check server.

In particular, the server exposes a `/started`, a `/readiness`, a `/liveness` and an optional `/prometheus`
URI endpoint.

The former three endpoints are supposed to be used by container orchestration platforms like Kubernetes to monitor the
runtime status of the containers that it manages. These endpoints are *always* exposed when the health check server is
started.

The `/prometheus` endpoint can be used by a Prometheus server to retrieve collected meter data from the component.
It is *only* exposed if Prometheus has been configured as the metrics back end as described
[above]({{< relref "#configuring-a-metrics-back-end" >}}).

Please refer to the [Quarkus SmallRye Health extension](https://quarkus.io/guides/smallrye-health) documentation for
details regarding configuration. The table below provides an overview of the configuration properties that have a Hono
specific default value that differs from the Quarkus default:

| OS Environment Variable<br>Java System Property                          | Value      |
| :----------------------------------------------------------------------- | :--------- |
| `QUARKUS_HTTP_NON_APPLICATION_ROOT_PATH`<br>`quarkus.http.non-application-root-path` | `/`         |
| `QUARKUS_HTTP_PORT`<br>`quarkus.http.port`                                     | `8088`      |
| `QUARKUS_SMALLRYE_HEALTH_LIVENESS_PATH`<br>`quarkus.smallrye-health.liveness-path`   | `liveness`   |
| `QUARKUS_SMALLRYE_HEALTH_READINESS_PATH`<br>`quarkus.smallrye-health.readiness-path` | `readiness`  |
| `QUARKUS_SMALLRYE_HEALTH_ROOT_PATH`<br>`quarkus.smallrye-health.root-path`          | `/`         |
 
## Tracing

In normal operation the vast majority of messages should be flowing through the system without any noteworthy delays
or problems. In fact, that is the whole purpose of Hono. However, that doesn't mean that nothing can go wrong.
For example, when a tenant's device administrator changes the credentials of a device in the Credentials service but
has not yet updated the credentials on the device yet, then the device will start to fail in uploading any data to the
protocol adapter it connects to. After a while, a back end application's administrator might notice, that there hasn't
been any data being received from that particular device for quite some time. The application administrator therefore
calls up the Hono operations team and complains about the data *being lost somewhere*.

The operations team will have a hard time determining what is happening, because it will need to figure out which
components have been involved in the processing of the device and why the data hasn't been processed as usual.
The metrics alone usually do not help much here because metrics are usually not scoped to individual devices.
The logs written by the individual components, on the other hand, might contain enough information to correlate
individual entries in the log with each other and thus *trace* the processing of the message throughout the system.
However, this is usually a very tedious (and error prone) process and the relevant information is often only logged at
a level (e.g. *DEBUG*) that is not used in production (often *INFO* or above).

In order to address this problem, Hono's service components are instrumented using [OpenTracing](https://opentracing.io/).
OpenTracing provides *Vendor-neutral APIs and instrumentation for distributed tracing*. The Hono container images all
include the [Jaeger Java client](https://www.jaegertracing.io/docs/1.32/client-libraries/) and can be configured to
send tracing information to a [Jaeger collector](https://www.jaegertracing.io/docs/1.32/architecture/).
Please refer to the
[Jaeger client documentation](https://github.com/jaegertracing/jaeger-client-java/blob/master/jaeger-core/README.md)
for details regarding configuration.

## Configuring usage of Jaeger tracing (included in Docker images)

The Maven based Hono build process does not include the Jaeger client libraries by default. In order to include them,
the `jaeger` Maven profile needs to be activated:

~~~sh
mvn clean install -Pbuild-docker-image,jaeger
~~~

## Enforcing the recording of traces for a tenant

Typically, in production systems, the tracing components will be configured to not store *all* trace spans in the tracing
back end, in order to reduce the performance impact. For debugging purposes it can however be beneficial to enforce the
recording of certain traces. Hono allows this by providing a configuration option in the Tenant information with which
all traces concerning the processing of telemetry, event and command messages for that specific tenant will be recorded.
Furthermore, this enforced trace sampling can be restricted to only apply to messages sent in the context of a specific
authentication identifier. Please refer to the [description of the `tracing` object]({{< ref "/api/tenant#tracing-format" >}})
in the Tenant Information for details.

## Enabling Jaeger Client Metrics

The Jaeger client contained in Hono components can be configured to report metrics via Micrometer. By default,
reporting of these metrics is disabled. In order to enable it, Hono needs to be compiled setting the
*jaeger.metrics.enabled* Maven property to `true`:

```sh
mvn clean install -Djaeger.metrics.enabled=true -Pmetrics-prometheus,jaeger,build-docker-image
```
