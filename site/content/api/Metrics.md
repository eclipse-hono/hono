+++
title = "Metrics"
weight = 440
+++

Eclipse Hono&trade;'s components report several metrics which may be used to gain some insight
into the running system. For instance, the HTTP adapter reports the number of successfully
processed telemetry messages. Some of these metrics are considered part of Hono's external
interface. This section describes the semantics and format of the metrics, how they can be retrieved
and how to interpret actual values.
<!--more-->

## Reported Metrics

Hono uses [Micrometer](https://micrometer.io/) in combination with Spring Boot
to internally collect metrics. Those metrics can be exported using the different
backends, see [Configuring metrics](/hono/admin-guide/monitoring-tracing-config/#configuring-metrics) on how to enable those.

The default Micrometer backend in the deployment examples is based on [Prometheus](https://prometheus.io/).

The Prometheus scraper, especially when run inside of Kubernetes/OpenShift,
might add additional tags like *pod*, etc. However those tags are not part of
the Hono metrics definition.

Hono applications provide additional metrics as well as the ones described here,
for example is it possible to get information about the internal JVM state, like
memory and garbage collector. However those metrics are not part of of the Hono
metrics definition. Especially for the Hono services, like device registry and
the messaging component, no further metrics are defined. However all metrics
will still support the tags as described.

### Common metrics

Tags for common metrics are:

| Tag        | Applied to  | Description |
| ---------- | ----------- | ----------- |
| *host*     | All metrics | The name of the host that the service reporting the metric is running on. |
| *component*| All metrics | The application component type (either `adapter`, `service`) |

### Protocol adapter metrics

Additional tags for protocols adapter are:

| Tag        | Applied to  | Description |
| ---------- | ----------- | ----------- |
| *protocol* | All metrics | An ID of the protocol (either `amqp`, `coap`, `http`, `mqtt`) |
| *tenant*   | Metrics related to a tenant | The name of the tenant |
| *type*     | Metrics related to a message type | The message type (either `telemetry`, `event` |

Metrics provided by the protocol adapters are:

| Metric                             | Type    | Tags | Description |
| ---------------------------------- | ------- | ---- | ----------- |
| *hono.connections.unauthenticated* | Gauge   | *host*, *component*, *protocol*                   | Current number of connected, unauthenticated devices. <br/> **NB** This metric is only supported by protocol adapters that maintain *connection state* with authenticated devices. In particular, the HTTP adapter does not support this metric. |
| *hono.connections.authenticated*   | Gauge   | *host*, *component*, *protocol*, *tenant*         | Current number of connected, authenticated devices. <br/> **NB** This metric is only supported by protocol adapters that maintain *connection state* with authenticated devices. In particular, the HTTP adapter does not support this metric. |
| *hono.messages.undeliverable*      | Counter | *host*, *component*, *protocol*, *tenant*, *type* | Total number of undeliverable messages |
| *hono.messages.processed*          | Counter | *host*, *component*, *protocol*, *tenant*, *type* | Total number of processed messages |
| *hono.messages.processed.payload*  | Counter | *host*, *component*, *protocol*, *tenant*, *type* | Total number of processed payload bytes |
| *hono.commands.device.delivered*   | Counter | *host*, *component*, *protocol*, *tenant*         | Total number of delivered commands |
| *hono.commands.ttd.expired*        | Counter | *host*, *component*, *protocol*, *tenant*         | Total number of expired TTDs |
| *hono.commands.response.delivered* | Counter | *host*, *component*, *protocol*, *tenant*         | Total number of delivered responses to commands |

### Service metrics

Additional tags for services are:

| Tag        | Applied to  | Description |
| ---------- | ----------- | ----------- |
| *service*  | All metrics | An ID of the service (either `auth`, `registry`, `messaging`) |

No metrics are defined as part of the Hono metrics definition.

## Legacy metrics

{{%note title="Deprecated"%}}
This metrics configuration is still supported in Hono, but is considered
deprecated and will be removed in a future version of Hono.

The legacy metrics support needs to be specifically enabled, starting with
Hono 0.9. See [Legacy support](/hono/admin-guide/monitoring-tracing-config#legacy-metrics-support) 
for more information.
{{%/note%}}

Enabling the legacy support sets up internal support for exporting the metrics
in the pre-0.8 format, using the Graphite Micrometer backend, but with a
custom (legacy) naming scheme. It still is possible to use the InfluxDB Micrometer
backend as well, however this will export metrics in the new format, and not
on the legacy format.

Some users have started to build custom dash boards on top of the names of the metrics and
associated tags that end up in the example InfluxDB. Hono will try to support such users
by allowing future versions of Hono to be configured to report metrics to InfluxDB resulting
in the same database structure as created by InfluxDB's Graphite Input.

**NB** This does not necessarily mean that future versions of Hono will always support
metrics being reported using the Graphite wire format. It only means that there will be
a way to transmit metrics to InfluxDB resulting in the same database structure.

### Tags

The InfluxDB configuration file used for the example deployment contains templates for
extracting the following *tags* from metric names transmitted via the Graphite reporter.

| Tag        | Description |
| ---------- | ----------- |
| *host*     | The name of the host that the service reporting the metric is running on. |
| *type*     | The type of message that the metric is being processed for (either `telemetry` or `event`) |
| *tenant*   | The name of the tenant that the metric is being reported for. |
| *protocol* | The protocol of message that the metric is being processed for (example: `http`, `mqtt`). |

The following sections describe which of these tags are extracted for which metrics specifically.

### Common Metrics

The following table contains metrics that are collected for all protocol adapters (if applicable to the underlying transport protocol).

| Metric                                             | Tags                             | Description |
| -------------------------------------------------- | -------------------------------- | ----------- |
| *counter.hono.connections.authenticated.count*     | *host*, *tenant*, *protocol*     | Current number of connections with authenticated devices. **NB** This metric is only supported by protocol adapters that maintain *connection state* with authenticated devices. In particular, the HTTP adapter does not support this metric. |
| *counter.hono.connections.unauthenticated.count*   | *host*, *protocol*               | Current number of connections with unauthenticated devices. **NB** This metric is only supported by protocol adapters that maintain *connection state* with unauthenticated devices. In particular, the HTTP adapter does not support this metric. |
| *meter.hono.commands.device.delivered.count*       | *host*, *tenant*, *protocol*     | Commands delivered to devices. Total count since application start. |
| *meter.hono.commands.device.delivered.m1_rate*     | *host*, *tenant*, *protocol*     | Commands delivered to devices. One minute, exponentially weighted, moving average. |
| *meter.hono.commands.device.delivered.m5_rate*     | *host*, *tenant*, *protocol*     | Commands delivered to devices. Five minute, exponentially weighted, moving average. |
| *meter.hono.commands.device.delivered.m15_rate*    | *host*, *tenant*, *protocol*     | Commands delivered to devices. Fifteen minute, exponentially weighted, moving average. |
| *meter.hono.commands.device.delivered.mean_rate*   | *host*, *tenant*, *protocol*     | Commands delivered to devices. Mean rate of messages since application start. |
| *meter.hono.commands.response.delivered.count*     | *host*, *tenant*, *protocol*     | Command responses delivered to applications. Total count since application startup. |
| *meter.hono.commands.response.delivered.m1_rate*   | *host*, *tenant*, *protocol*     | Command responses delivered to applications. One minute, exponentially weighted, moving average. |
| *meter.hono.commands.response.delivered.m5_rate*   | *host*, *tenant*, *protocol*     | Command responses delivered to applications. Five minute, exponentially weighted, moving average. |
| *meter.hono.commands.response.delivered.m15_rate*  | *host*, *tenant*, *protocol*     | Command responses delivered to applications. Fifteen minute, exponentially weighted, moving average. |
| *meter.hono.commands.response.delivered.mean_rate* | *host*, *tenant*, *protocol*     | Command responses delivered to applications. Mean rate of messages since the application start. |
| *meter.hono.commands.ttd.expired.count*            | *host*, *tenant*, *protocol*     | Messages containing a TTD that expired with no pending command(s). Total count since application startup. |
| *meter.hono.commands.ttd.expired.m1_rate*          | *host*, *tenant*, *protocol*     | Messages containing a TTD that expired with no pending command(s). One minute, exponentially weighted, moving average. |
| *meter.hono.commands.ttd.expired.m5_rate*          | *host*, *tenant*, *protocol*     | Messages containing a TTD that expired with no pending command(s). Five minute, exponentially weighted, moving average. |
| *meter.hono.commands.ttd.expired.m15_rate*         | *host*, *tenant*, *protocol*     | Messages containing a TTD that expired with no pending command(s). Fifteen minute, exponentially weighted, moving average. |
| *meter.hono.commands.ttd.expired.mean_rate*        | *host*, *tenant*, *protocol*     | Messages containing a TTD that expired with no pending command(s). Mean rate of messages since the application start. |

### HTTP Metrics

The following table contains metrics that are collected specifically for the HTTP protocol adapter.

| Metric                                           | Tags                     | Description |
| ------------------------------------------------ | ------------------------ | ----------- |
| *counter.hono.http.messages.undeliverable.count* | *host*, *tenant*, *type* | Messages which could not be processed by the HTTP protocol adapter. Total count since application startup. |
| *meter.hono.http.messages.processed.count*       | *host*, *tenant*, *type* | Messages processed by the HTTP protocol adapter. Total count since application startup. |
| *meter.hono.http.messages.processed.m1_rate*     | *host*, *tenant*, *type* | Messages processed by the HTTP protocol adapter. One minute, exponentially weighted, moving average. |
| *meter.hono.http.messages.processed.m5_rate*     | *host*, *tenant*, *type* | Messages processed by the HTTP protocol adapter. Five minute, exponentially weighted, moving average. |
| *meter.hono.http.messages.processed.m15_rate*    | *host*, *tenant*, *type* | Messages processed by the HTTP protocol adapter. Fifteen minute, exponentially weighted, moving average. |
| *meter.hono.http.messages.processed.mean_rate*   | *host*, *tenant*, *type* | Messages processed by the HTTP protocol adapter. Mean rate of messages since the application start. |

### MQTT Metrics

The following table contains metrics that are collected specifically for the MQTT protocol adapter.

| Metric                                               | Tags                     | Description |
| ---------------------------------------------------- | ------------------------ | ----------- |
| *counter.hono.mqtt.messages.undeliverable.count*     | *host*, *tenant*, *type* | Messages which could not be processed by the MQTT protocol adapter- Total count since application startup. |
| *meter.hono.mqtt.messages.processed.count*           | *host*, *tenant*, *type* | Messages processed by the MQTT protocol adapter. Total count since application startup. |
| *meter.hono.mqtt.messages.processed.m1_rate*         | *host*, *tenant*, *type* | Messages processed by the MQTT protocol adapter. One minute, exponentially weighted, moving average. |
| *meter.hono.mqtt.messages.processed.m5_rate*         | *host*, *tenant*, *type* | Messages processed by the MQTT protocol adapter. Five minute, exponentially weighted, moving average. |
| *meter.hono.mqtt.messages.processed.m15_rate*        | *host*, *tenant*, *type* | Messages processed by the MQTT protocol adapter. Fifteen minute, exponentially weighted, moving average. |
| *meter.hono.mqtt.messages.processed.mean_rate*       | *host*, *tenant*, *type* | Messages processed by the MQTT protocol adapter. Mean rate of messages since the application start. |

