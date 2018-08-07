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

{{%note title="Other metrics"%}}
Hono currently maintains metrics in addition to the ones described below. However, these are
not considered part of Hono's external interface and thus may be changed or removed without notice.
{{%/note%}}

## InfluxDB

In the [deployment examples](/hono/deployment) an InfluxDB is used for collecting
metrics reported by Hono's service components. For this purpose, some of the services are
configured to use Dropwizard's *Graphite reporter* to transmit metrics to the InfluxDB instance
using the Graphite wire format.

The Graphite reporter and the names of the transmitted metrics are not considered part
of Hono's external interface. This means that future versions of Hono might use
different mechanisms to report metrics.

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

### Generic Metrics

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

## Metrics API

**To Do**: Future releases of Hono will use a framework for reporting metrics that supports the usage of *tags* out of the box (e.g. [Micrometer](https://micrometer.io/)). Hono will then define a set of metric names and tags its components support as part of Hono's external interface.
This will allow users to more easily drop in a custom metrics system adapter (e.g. for reporting data to [Prometheus](https://prometheus.io/) instead of InfluxDB).
