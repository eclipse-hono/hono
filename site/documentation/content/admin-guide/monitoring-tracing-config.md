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

### Configuring a Metrics Back End

Hono uses [Micrometer](http://micrometer.io/) for providing metrics. It is
possible to drop in any Micrometer compatible back end. Hono also uses the
Micrometer integration with Spring Boot and Vert.x.

Please refer to the [Micrometer documentation](http://micrometer.io/docs)
for details regarding the configuration of a specific Micrometer back end.
In most cases, you only need to add the back end specific jar files to the class path and
add back end specific configuration to the `application.yml` file.

The Hono build supports configuration of a specific metrics back end by means
of Maven profiles. The following build profiles are currently supported:

* `metrics-prometheus` – Enables the Prometheus backend.
* `metrics-graphite` – Enables the Graphite backend.
* `metrics-influxdb` – Enables the InfluxDB backend.

Additionally, to selecting a metrics back end, you may need to configure the
back end using Spring configuration options. See the documentation mentioned
above for more information.

Note that none of the above profiles are active by default, i.e. you need to
explicitly activate one of them when starting the build using Maven's `-p`
command line parameter.

### Using Prometheus

Most of the metrics back ends have data being *pushed* to from the components
reporting the metrics. However, Prometheus is different in that it *polls*
(or *scrapes*) all components periodically for new metrics data.
For this to work, the Prometheus server needs to be configured with the IP
addresses of the components to monitor. In the example deployment that comes
with Hono, the Prometheus server is configured with the names of the Kubernetes
services corresponding to the Hono components that it should scrape.
The components themselves need to expose a corresponding HTTP endpoint that
the Prometheus server can connect to for scraping the meter data. All
Hono components that report metrics can be configured to expose such an
endpoint via their [*Health Check* server]({{< relref "#health-check-server-configuration" >}})
which already exposes endpoints for determining the component's readiness and liveness status.

## Health Check Server Configuration

All of Hono's service components and protocol adapters contain a *Health Check* server which can be configured to
expose several HTTP endpoints for determining the component's status.
In particular, the server exposes a `/readiness`, a `/liveness` and an optional `/prometheus` URI endpoint.

The former two endpoints are supposed to be used by container orchestration platforms like Kubernetes to monitor the runtime status of the containers
that it manages. These endpoints are *always* exposed when the health check server is started.

The `/prometheus` endpoint can be used by a Prometheus server to retrieve collected meter data from the component. It is *only* exposed if Prometheus has
been configured as the metrics back end as described [above]({{< relref "#configuring-a-metrics-back-end" >}}).

The health check server can be configured by means of the following environment variables:

| Environment Variable<br>Command Line Option | Default Value | Description  |
| :------------------------------------------ | :------------ | :------------|
| `HONO_HEALTHCHECK_BINDADDRESS`<br>`--hono.healthCheck.bindAddress` | `127.0.0.1` | The IP address of the network interface that the health check server's secure port should be bound to. The server will only be started if this property is set to some other than the default value and corresponding key material has been configured using the `HONO_HEALTHCHECK_KEYPATH` and `HONO_HEALTHCHECK_CERTPATH` variables. |
| `HONO_HEALTHCHECK_CERTPATH`<br>`--hono.healthCheck.certPath` | - | The absolute path to the PEM file containing the certificate that the secure server should use for authenticating to clients. This option must be used in conjunction with `HONO_HEALTHCHECK_KEYPATH`.<br>Alternatively, the `HONO_HEALTHCHECK_KEYSTOREPATH` option can be used to configure a key store containing both the key as well as the certificate. |
| `HONO_HEALTHCHECK_INSECUREPORTBINDADDRESS`<br>`--hono.healthCheck.insecurePortBindAddress` | `127.0.0.1` | The IP address of the network interface that the health check server's insecure port should be bound to. The server will only be started if this property is set to some other than the default value. |
| `HONO_HEALTHCHECK_INSECUREPORT`<br>`--hono.healthCheck.insecurePort` | `8088` | The port that the insecure server should listen on. |
| `HONO_HEALTHCHECK_KEYPATH`<br>`--hono.healthCheck.keyPath` | - | The absolute path to the (PKCS8) PEM file containing the private key that the secure server should use for authenticating to clients. This option must be used in conjunction with `HONO_HEALTHCHECK_CERTPATH`. Alternatively, the `HONO_HEALTHCHECK_KEYSTOREPATH` option can be used to configure a key store containing both the key as well as the certificate. |
| `HONO_HEALTHCHECK_PORT`<br>`--hono.healthCheck.port` | `8088` | The port that the secure server should listen on. |
| `HONO_HEALTHCHECK_KEYSTOREPASSWORD`<br>`--hono.healthCheck.keyStorePassword` | - | The password required to read the contents of the key store. |
| `HONO_HEALTHCHECK_KEYSTOREPATH`<br>`--hono.healthCheck.keyStorePath` | - | The absolute path to the Java key store containing the private key and certificate that the secure server should use for authenticating to clients. Either this option or the `HONO_HEALTHCHECK_KEYPATH` and `HONO_HEALTHCHECK_CERTPATH` options need to be set in order to enable TLS secured connections with clients. The key store format can be either `JKS` or `PKCS12` indicated by a `.jks` or `.p12` file suffix respectively. The `HONO_HEALTHCHECK_KEYSTOREPASSWORD` variable can be used to set the password required for reading the key store. |


{{% warning title="Failure to start" %}}
The component/service will fail to start if neither the secure not the insecure server have been configured properly.
{{% /warning %}}

## Tracing

In normal operation the vast majority of messages should be flowing through the system without any noteworthy delays or problems. In fact, that is the whole purpose of Hono. However, that doesn't mean that nothing can go wrong. For example, when a tenant's device administrator changes the credentials of a device in the Credentials service but has not yet updated the credentials on the device yet, then the device will start to fail in uploading any data to the protocol adapter it connects to. After a while, a back end application's administrator might notice, that there hasn't been any data being received from that particular device for quite some time. The application administrator therefore calls up the Hono operations team and complains about the data *being lost somewhere*.

The operations team will have a hard time determining what is happening, because it will need to figure out which components have been involved in the processing of the device and why the data hasn't been processed as usual. The metrics alone usually do not help much here because metrics are usually not scoped to individual devices. The logs written by the individual components, on the other hand, might contain enough information to correlate individual entries in the log with each other and thus *trace* the processing of the message throughout the system. However, this is usually a very tedious (and error prone) process and the relevant information is often only logged at a level (e.g. *DEBUG*) that is not used in production (often *INFO* or above).

In order to address this problem, Hono's service components are instrumented using [OpenTracing](https://opentracing.io/). OpenTracing provides *Vendor-neutral APIs and instrumentation for distributed tracing*. The OpenTracing web page provides a [list of supported tracer implementations](https://opentracing.io/docs/supported-tracers/) from which users can choose in order to collect (and examine) the tracing information generated by Hono's individual components.

### Configuring a Tracer

**Hint**: The description in this chapter applies to any compatible OpenTracing implementation. For an easier approach to configure usage of [Jaeger tracing](https://www.jaegertracing.io/), see the next chapter.

Hono's components use the [OpenTracing Tracer Resolver](https://github.com/opentracing-contrib/java-tracerresolver) mechanism to find and initialize a concrete OpenTracing implementation during startup of the component. The discovery mechanism is using Java's [ServiceLoader](https://docs.oracle.com/javase/9/docs/api/java/util/ServiceLoader.html) and as such relies on the required resources to be available on the class path.

When starting up any of Hono's Docker images as a container, the JVM will look for additional jar files to include in its class path in the container's `/opt/hono/extensions` folder. Thus, using a specific implementation of OpenTracing is just a matter of configuring the container to mount a volume or binding a host folder at that location and putting the implementation's jar files and resources into the corresponding volume or host folder.

{{% note %}}
This also means that (currently) only Tracer implementations can be used with Hono that also implement the Tracer Resolver mechanism.
{{% /note %}}

Assuming that the HTTP adapter should be configured to use [Jaeger tracing](https://www.jaegertracing.io/), the following steps are necessary:

1. Download [Jaeger's Java Tracer Resolver](https://github.com/jaegertracing/jaeger-client-java/tree/master/jaeger-tracerresolver) implementation and its dependencies (see the hint at the end).
2. Put the jars to a folder on the Docker host, e.g. `/tmp/jaeger`.
3. Start the HTTP adapter Docker image mounting the host folder:

    ```sh
    docker run --name hono-adapter-http-vertx \
    --mount type=bind,src=/tmp/jaeger,dst=/opt/hono/extensions,ro \
    ... \
    eclipse/hono-adapter-http-vertx
    ```

**Note**: the command given above does not contain the environment variables and secrets that are required to configure the service properly. The environment variables for configuring the Jaeger client are also missing. Please refer to the [Jaeger documentation](https://github.com/jaegertracing/jaeger-client-java/blob/master/jaeger-core/README.md) for details.

When the HTTP adapter starts up, it will look for a working implementation of the Tracer Resolver on its classpath and (if found) initialize and use it for publishing traces. The adapter's log file will indicate the name of the Tracer implementation being used.

Using a Docker *volume* instead of a *bind mount* works the same way but requires the use of `volume` as the *type* of the `--mount` parameter. Please refer to the [Docker reference documentation](https://docs.docker.com/edge/engine/reference/commandline/service_create/#add-bind-mounts-volumes-or-memory-filesystems) for details.

**Hint**: to resolve all dependencies for `jaeger-tracerresolver` in order to provide them to `/opt/hono/extensions`, you may want to rely on Maven's dependency plugin. To obtain all jar files you can invoke the following command in a simple Maven project that contains only the dependency to `jaeger-tracerresolver`:

~~~sh
mvn dependency:copy-dependencies
~~~
    
All jar files can then be found in the directory `target/dependency`.    

## Configuring usage of Jaeger tracing (included in Docker images)

In case [Jaeger tracing](https://www.jaegertracing.io/) shall be used, there is an alternative to putting the jar files in the container's `/opt/hono/extensions` folder as described above.
This is to have the Jaeger tracing jar files be included in the Hono Docker images by using the `jaeger` Maven profile when building Hono.

For example, building the HTTP adapter image with the Jaeger client included:

~~~sh
# in directory: hono/adapters/http-vertx/
mvn clean install -Pbuild-docker-image,jaeger
~~~

Note that when running the created docker image, the environment variables for configuring the Jaeger client still need to be set. Please refer to the [Jaeger documentation](https://github.com/jaegertracing/jaeger-client-java/blob/master/jaeger-core/README.md) for details.

## Enforcing the recording of traces for a tenant

Typically, in production systems, the tracing components will be configured to not store *all* trace spans in the tracing backend, in order to reduce the performance impact. For debugging purposes it can however be beneficial to enforce the recording of certain traces. Hono allows this by providing a configuration option in the Tenant information with which all traces concerning the processing of telemetry, event and command messages for that specific tenant will be recorded. Furthermore, this enforced trace sampling can be restricted to only apply to messages sent in the context of a specific authentication identifier. Please refer to the [description of the `tracing` object]({{< ref "/api/tenant#tracing-format" >}}) in the Tenant Information for details.
