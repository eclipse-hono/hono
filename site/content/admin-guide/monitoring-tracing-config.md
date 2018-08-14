+++
title = "Monitoring & Tracing"
weight = 355
+++

The individual components of an Eclipse Hono&trade; installation need to work together in order to provide their functionality to devices and applications. Under normal circumstances these interactions work flawlessly. However, due to the nature of distributed systems, any one (or more) of the components may crash or become otherwise unavailable due to arbitrary reasons. This page describes how Hono supports operations teams by providing insights into the individual service components and their interactions with each other by means of reporting metrics and tracing the processing of individual messages through the system.
<!--more-->

When a device uploads telemetry data to the HTTP adapter, the adapter invokes operations on the Device Registration, Credentials and the Tenant services in order to authenticate and authorize the device before sending the telemetry data downstream to the AMQP 1.0 Messaging Network. The overall success of this process and the latency involved before the message reaches the consumer is determined by the individual interactions between the service components.

## Monitoring

In a production environment, an operations team will usually want to keep track of some *key performance indicators* (KPI) which allow the team to determine the overall *health* of the system, e.g. memory and CPU consumption etc. Hono supports the tracking of such KPIs by means of metrics it can be configured to report. The metrics are usually collected in a time series database like *InfluxDB* or *Prometheus* and then visualized on a monitoring dash-board built using frameworks like *Grafana*. Such a dash-board can usually also be configured to send alarms when certain thresholds are exceeded.

Metrics usually provide insights into the past and current status of an individual component. The values can be aggregated to provide a picture of the overall system's status. As such, metrics provide a great way to *monitor* system health and, in particular, to anticipate resource shortages and use such knowledge to pro-actively prevent system failure.

### Configuring Metrics

All protocol adapters support the reporting of metrics using the Graphite wire format. The following table provides an overview of the configuration variables and corresponding command line options for enabling and configuring the collection and reporting of metrics in protocol adapters.


| Environment Variable<br>Command Line Option                   | Mandatory | Default | Description |
| :------------------------------------------------------------ | :-------: | :------ | :---------- |
| `HONO_METRIC_REPORTER_CONSOLE_ACTIVE`<br><nobr>`--hono.metric.reporter.console.active`</nobr>   | no  | `false` | Enables the logging of metrics to the console/log. |
| `HONO_METRIC_REPORTER_CONSOLE_PERIOD`<br><nobr>`--hono.metric.reporter.console.period`</nobr>   | no  | `5000` | Sets the time period (in milliseconds) after which metrics are reported to the console (if activated). |
| `HONO_METRIC_REPORTER_GRAPHITE_ACTIVE`<br><nobr>`--hono.metric.reporter.graphite.active`</nobr> | no  | `false` | Enables reporting of metrics to a remote system (e.g. InfluxDB) using the Graphite wire format. |
| `HONO_METRIC_REPORTER_GRAPHITE_HOST`<br><nobr>`--hono.metric.reporter.graphite.host`</nobr>     | no  | `localhost` | The name or IP address of the host, to which metrics should be reported. |
| `HONO_METRIC_REPORTER_GRAPHITE_PORT`<br><nobr>`--hono.metric.reporter.graphite.port`</nobr>     | no  | `2003` | The port to which metrics should be reported. The default value is the standard port for Graphite. |
| `HONO_METRIC_REPORTER_GRAPHITE_PERIOD`<br><nobr>`--hono.metric.reporter.graphite.period`</nobr> | no  | `5000` | Sets the time period (in milliseconds) after which metrics are reported to the remote host (if activated). |
| `HONO_METRIC_REPORTER_GRAPHITE_PREFIX`<br><nobr>`--hono.metric.reporter.graphite.prefix`</nobr> | no  | - | An arbitrary string that all metric names will be prefixed with. |
| `HONO_METRIC_JVM_MEMORY`<br><nobr>`--hono.metric.jvm.memory`</nobr>                             | no  | `false` | Enables reporting of the JVM's memory metrics (from the Dropwizard JVM Instrumentation). The metric name is `hono.http.jvm.memory`. |
| `HONO_METRIC_JVM_THREAD`<br><nobr>`--hono.metric.jvm.thread`</nobr>                             | no  | `false` | Enables reporting of the JVM's thread metrics (from the Dropwizard JVM Instrumentation). The metric name is `hono.http.jvm.thread`.|
| `HONO_METRIC_VERTX`<br><nobr>`--hono.metric.vertx`</nobr>                                       | no  | `false` | Enables reporting of Vert.x' standard metrics (from the Vert.x metrics project). The metric name is `hono.http.vertx`. |

The names and semantics of the individual metrics being reported by the components are described in the [Metrics specification]({{< relref "/api/Metrics.md" >}}).

## Tracing

In normal operation the vast majority of messages should be flowing through the system without any noteworthy delays or problems. In fact, that is the whole purpose of Hono. However, that doesn't mean that nothing can go wrong. For example, when a tenant's device administrator changes the credentials of a device in the Credentials service but has not yet updated the credentials on the device yet, then the device will start to fail in uploading any data to the protocol adapter it connects to. After a while, a back end application's administrator might notice, that there hasn't been any data being received from that particular device for quite some time. The application administrator therefore calls up the Hono operations team and complains about the data *being lost somewhere*.

The operations team will have a hard time determining what is happening, because it will need to figure out which components have been involved in the processing of the device and why the data hasn't been processed as usual. The metrics alone usually do not help much here because metrics are usually not scoped to individual devices. The logs written by the individual components, on the other hand, might contain enough information to correlate individual entries in the log with each other and thus *trace* the processing of the message throughout the system. However, this is usually a very tedious (and error prone) process and the relevant information is often only logged at a level (e.g. *DEBUG*) that is not used in production (often *INFO* or above).

In order to address this problem, Hono's service components are instrumented using [OpenTracing](http://opentracing.io). OpenTracing provides *Vendor-neutral APIs and instrumentation for distributed tracing*. The OpenTracing web page provides a [list of supported tracer implementations](http://opentracing.io/documentation/pages/supported-tracers) from which users can choose in order to collect (and examine) the tracing information generated by Hono's individual components.

### Configuring a Tracer

Hono's components use the [OpenTracing Tracer Resolver](https://github.com/opentracing-contrib/java-tracerresolver) mechanism to find and initialize a concrete OpenTracing implementation during startup of the component. The discovery mechanism is using Java's [ServiceLoader](http://download.java.net/java/jdk9/docs/api/java/util/ServiceLoader.html) and as such relies on the required resources to be available on the class path.

When starting up any of Hono's Docker images as a container, the JVM will look for additional jar files to include in its class path in the container's `/opt/hono/extensions` folder. Thus, using a specific implementation of OpenTracing is just a matter of configuring the container to mount a volume or binding a host folder at that location and putting the implementation's jar files and resources into the corresponding volume or host folder.

{{% note %}}
This also means that (currently) only Tracer implementations can be used with Hono that also implement the Tracer Resolver mechanism.
{{% /note %}}

Assuming that the HTTP adapter should be configured to use [Jaeger tracing](https://www.jaegertracing.io/), the following steps are necessary:

1. Download [Jaeger's Java Tracer Resolver](https://github.com/jaegertracing/jaeger-client-java/tree/master/jaeger-tracerresolver) implementation and its dependencies (see the hint at the end).
2. Put the jars to a folder on the Docker host, e.g. `/tmp/jaeger`.
3. Start the HTTP adapter Docker image mounting the host folder:

    ```sh
    $> docker run --name hono-adapter-http-vertx \
    --mount type=bind,src=/tmp/jaeger,dst=/opt/hono/extensions,ro \
    ... \
    eclipse/hono-adapter-http-vertx
    ```

Note that the command given above does not contain the environment variables and secrets that are required to configure the service properly. The environment variables for configuring the Jaeger client are also missing. Please refer to the [Jaeger documentation](https://github.com/jaegertracing/jaeger-client-java/blob/master/jaeger-core/README.md) for details.

When the HTTP adapter starts up, it will look for a working implementation of the Tracer Resolver on its classpath and (if found) initialize and use it for publishing traces. The adapter's log file will indicate the name of the Tracer implementation being used.

Using a Docker *volume* instead of a *bind mount* works the same way but requires the use of `volume` as the *type* of the `--mount` parameter. Please refer to the [Docker reference documentation](https://docs.docker.com/edge/engine/reference/commandline/service_create/#add-bind-mounts-volumes-or-memory-filesystems) for details.

**Hint**: to resolve all dependencies for `jaeger-tracerresolver` in order to provide them to `/opt/hono/extensions`, you may want to rely on Maven's dependency plugin. To obtain all jar files you can invoke the following command in a simple Maven project that contains only the dependency to `jaeger-tracerresolver`:

   ```bash
   $> mvn dependency:copy-dependencies
   ```
    
All jar files can then be found in the directory `target/dependency`.    

