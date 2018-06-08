+++
title = "Metrics"
weight = 420
+++

Hono provides a set of internal metrics which may be used to gain some insight
into the running Hono components. Some of the metrics are considered part
of the overall Hono interface. This section describes those metrics, which
API may be used to access them, their format and what to expect from the
actual values.
<!--more-->

{{%note title="Other metrics"%}}
Hono might provide metrics in addition to the ones described in
this document. However they are not considered part of the interface to Hono and
 may change over time without notice.
{{%/note%}}

## InfluxDB

When metrics via InfluxDB is enabled, then Hono offers the following metrics
in the Hono-local InfluxDB. Metrics are sent to InfluxDB via the Graphite
reporter, however the Graphite reporter is not part of the guarantueed Hono
interfacea. Only the structure of the InfluxDB is. This means that in a future
version Hono might use a different mechanism to feed metrics in the InfluxDB,
but the structure inside the InfluxDB will stay stable.

### Tags

The following tags will be used. Please see the column "Tags" which tags apply
to which metric.:

| Tag     | Description |
| ------- | ----------- |
| host    | The name of the host the service is running on. |
| type    | The type of the messages being processes. May be `telememtry` or `event`. |
| tenant  | The name of the tenant. |

### HTTP Metrics

| Metric | Tags | Description |
| ------ | ---- | ----------- |
| counter.hono.http.messages.undeliverable.count | host, tenant, type | Messages which could not be processed by the HTTP protocol adapter. Total count since application startup. |
| meter.hono.http.messages.processed.count | host, tenant, type | Messages processed by the HTTP protocol adapter. Total count since application startup. |
| meter.hono.http.messages.processed.m1_rate | host, tenant, type | Messages processed by the HTTP protocol adapter. One minute, exponentially weighted, moving average. |
| meter.hono.http.messages.processed.m5_rate | host, tenant, type | Messages processed by the HTTP protocol adapter. Five minute, exponentially weighted, moving average. |
| meter.hono.http.messages.processed.m15_rate | host, tenant, type | Messages processed by the HTTP protocol adapter. Fifteen minute, exponentially weighted, moving average. |
| meter.hono.http.messages.processed.mean_rate | host, tenant, type | Messages processed by the HTTP protocol adapter. Mean rate of messages since the application start. |

### MQTT Metrics

| Metric | Tags | Description |
| ------ | ---- | ----------- |
| counter.hono.mqtt.connection.count | host, tenant | Messages processed by the MQTT protocol adapter. Total count since application startup. |
| counter.hono.mqtt.messages.undeliverable.count | host, tenant, type | Messages which could not be processed by the MQTT protocol adapter- Total count since application startup. |
| meter.hono.mqtt.messages.processed.count | host, tenant, type | Messages processed by the MQTT protocol adapter. Total count since application startup. |
| meter.hono.mqtt.messages.processed.m1_rate | host, tenant, type | Messages processed by the MQTT protocol adapter. One minute, exponentially weighted, moving average. |
| meter.hono.mqtt.messages.processed.m5_rate | host, tenant, type | Messages processed by the MQTT protocol adapter. Five minute, exponentially weighted, moving average. |
| meter.hono.mqtt.messages.processed.m15_rate | host, tenant, type | Messages processed by the MQTT protocol adapter. Fifteen minute, exponentially weighted, moving average. |
| meter.hono.mqtt.messages.processed.mean_rate | host, tenant, type | Messages processed by the MQTT protocol adapter. Mean rate of messages since the application start. |

### Messaging Metrics

| Metric | Tags | Description |
| ------ | ---- | ----------- |
| counter.hono.messaging.messages.discarded.count | host, tenant, type | Messages which could not be processed by the messaging component. Total count since application startup. |
| meter.hono.messaging.messages.processed.count | host, tenant, type | Messages processed by the MQTT protocol adapter. Total count since application startup. |
| meter.hono.messaging.messages.processed.m1_rate | host, tenant, type | Messages processed by the Messaging service. One minute, exponentially weighted, moving average. |
| meter.hono.messaging.messages.processed.m5_rate | host, tenant, type | Messages processed by the Messaging service. Five minute, exponentially weighted, moving average. |
| meter.hono.messaging.messages.processed.m15_rate | host, tenant, type | Messages processed by the Messaging service. Fifteen minute, exponentially weighted, moving average. |
| meter.hono.messaging.messages.processed.mean_rate | host, tenant, type | Messages processed by the Messaging service. Mean rate of messages since the application start. |

## Metrics API

**To Do**: In future releases Hono will focus on providing a set of Metrics via
an API based on (e.g.) Micrometer. This allows you drop in your custom metrics
system adapter (e.g. Prometheus).
