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

## Reported Metrics

Hono uses the [Micrometer](https://micrometer.io) framework for reporting metrics in its components.
In Micrometer a metric always is of a specific type (e.g. a *meter*, *counter* or *gauge*), has a name and associated *tags*
(or *dimensions*). For example the HTTP adapter mentioned above reports the number of processed messages by means of
a metric of type *counter* having name *hono.messages.processed* and tags

* *host* - containing the host name of the HTTP adapter instance that reported the value,
* *type* - indicating if the message contains an event or telemetry data,
* *tenant* - containing the ID of the tenant that the device that sent the message belongs to and
* *protocol* - containing the name of the transport protocol that the message has been received over (`http` in this case).

The metrics reported by Hono's components can be transmitted to or collected by external monitoring systems like
[Prometheus](https://prometheus.io/) or [InfluxDB](https://https://www.influxdata.com/) by means of plug-ins.
Please refer to the [Micrometer documentation](https://micrometer.io/docs) for details.

As indicated above, the metrics collected via Micrometer have a type, a name and zero or more tags associated with them.
Most of the tags are used with multiple metrics because they contain information that is relevant to all of these metrics.

The table below provides an overview of the tags used with metrics in Hono:

| Tag        | Value                    | Description |
| :--------- | :----------------------- | :---------- |
| *host*     | arbitrary string         | The name of the host that the service reporting the value is running on. |
| *type*     | `telemetry`, `event`   | The type of message that the metric is being reported for. |
| *tenant*   | arbitrary string         | The name of the tenant that the metric is being reported for. |
| *protocol* | `http`, `mqtt`, `amqp` | The protocol used for transmitting the message that the metric is being reported for. |

The table below provides an overview of the metrics that are reported by Hono's components.

| Name                                 | Type        | Tags                                 | Description |
| :----------------------------------- | :---------- | ------------------------------------ | ----------- |
| *hono.commands.device.delivered*     | *counter*   | *host*, *tenant*, *protocol*         | Commands delivered to devices. Total count since application start. |
| *hono.commands.response.delivered*   | *counter*   | *host*, *tenant*, *protocol*         | Command responses delivered to applications. Total count since application startup. |
| *hono.commands.ttd.expired*          | *counter*   | *host*, *tenant*, *protocol*         | Messages containing a TTD that expired with no pending command(s). Total count since application startup. |
| *hono.connections.authenticated*     | *gauge*     | *host*, *tenant*, *protocol*         | Current number of connections with authenticated devices. **NB** This metric is only reported by protocol adapters that maintain *connection state* with authenticated devices. In particular, the HTTP adapter does not report this metric. |
| *hono.connections.unauthenticated*   | *gauge*     | *host*, *protocol*                   | Current number of connections with unauthenticated devices. **NB** This metric is only reported by protocol adapters that maintain *connection state* with unauthenticated devices. In particular, the HTTP adapter does not report this metric. |
| *hono.messages.processed*            | *counter*   | *host*, *type*, *tenant*, *protocol* | Messages successfully processed by a protocol adapter. Total count since application startup. |
| *hono.messages.processed.payload*    | *counter*   | *host*, *type*, *tenant*, *protocol* | Accumulated payload size of messages successfully processed by a protocol adapter. Total number of bytes since application startup. |
| *hono.messages.undeliverable*        | *counter*   | *host*, *type*, *tenant*, *protocol* | Messages that could not be forwarded downstream by a protocol adapter. Total count since application startup. |


## InfluxDB

In the [deployment examples](/hono/deployment) an InfluxDB is used for collecting
metrics reported by Hono's service components. For this purpose, some of the services are
configured to use Micrometer's *Graphite reporter* to transmit metrics to the InfluxDB instance
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


