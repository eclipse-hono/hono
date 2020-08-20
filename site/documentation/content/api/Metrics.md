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
to internally collect metrics. Those metrics can be exported to different
back ends. Please refer to [Configuring Metrics]({{< relref "/admin-guide/monitoring-tracing-config#configuring-a-metrics-back-end" >}})
for details.

The example deployment by default uses [Prometheus](https://prometheus.io/) as the metrics back end.

When deploying to Kubernetes/OpenShift, the metrics reported by Hono may contain
environment specific tags (like the *pod* name) which are added by the Prometheus
scraper. However, those tags are not part of the Hono metrics definition.

Hono applications may report other metrics in addition to the ones defined here.
In particular, all components report metrics regarding the JVM's internal state, e.g.
memory consumption and garbage collection status. Those metrics are not considered
part of Hono's *official* metrics definition. However, all those metrics
will still contain tags as described below.

### Common Metrics

Tags for common metrics are:

| Tag              | Value                              | Description |
| ---------------- | ---------------------------------- | ----------- |
| *host*           | *string*                           | The name of the host that the component reporting the metric is running on |
| *component-type* | `adapter`, `service`             | The type of component reporting the metric |
| *component-name* | *string*                           | The name of the component reporting the metric. |

The names of Hono's standard components are as follows:

| Component         | *component-name*      |
| ----------------- | --------------------- |
| Auth Server       | `hono-auth`         |
| Device Registry   | `hono-registry`     |
| AMQP adapter      | `hono-amqp`         |
| CoAP adapter      | `hono-coap`         |
| HTTP adapter      | `hono-http`         |
| Kura adapter      | `hono-kura-mqtt`   |
| MQTT adapter      | `hono-mqtt`         |
| Lora adapter      | `hono-lora`         |
| Sigfox adapter    | `hono-sigfox`       |

### Protocol Adapter Metrics

Additional tags for protocol adapters are:

| Name        | Value                                              | Description |
| ----------- | -------------------------------------------------- | ----------- |
| *direction* | `one-way`, `request`, `response`               | The direction in which a Command &amp; Control message is being sent:<br>`one-way` indicates a command sent to a device for which the sending application doesn't expect to receive a response.<br>`request` indicates a command request message sent to a device.<br>`response` indicates a command response received from a device. |
| *qos*       | `0`, `1`, `unknown`                              | The quality of service used for a telemetry or event message.<br>`0` indicates *at most once*,<br>`1` indicates *at least once* and<br> `none` indicates unknown delivery semantics. |
| *status*    | `forwarded`, `unprocessable`, `undeliverable` | The processing status of a message.<br>`forwarded` indicates that the message has been forwarded to a downstream consumer<br>`unprocessable` indicates that the message has not been processed not forwarded, e.g. because the message was malformed<br>`undeliverable` indicates that the message could not be forwarded, e.g. because there is no downstream consumer or due to an infrastructure problem |
| *tenant*    | *string*                                           | The identifier of the tenant that the metric is being reported for |
| *ttd*       | `command`, `expired`, `none`                    | A status indicating the outcome of processing a TTD value contained in a message received from a device.<br>`command` indicates that a command for the device has been included in the response to the device's request for uploading the message.<br>`expired` indicates that a response without a command has been sent to the device.<br>`none` indicates that either no TTD value has been specified by the device or that the protocol adapter does not support it. |
| *type*      | `telemetry`, `event`                             | The type of (downstream) message that the metric is being reported for. |

Additional tags for *hono.connections.attempts*:

| Name        | Value                                              | Description |
| ----------- | -------------------------------------------------- | ----------- |
| *outcome*   | `adapter-disabled`, `connection-duration-exceeded`,<br/>`data-volume-exceeded`, `registration-assertion-failure`,<br/>`succeeded`, `tenant-connections-exceeded`,<br/>`unauthorized`, `unavailable`, `unknown` | The outcome of a device's connection attempt.<br/>`adapter-connections-exceeded` indicates that the maximum number of connections that the adapter instance can handle are exceeded<br/>`adapter-disabled` indicates that the protocol adapter is not enabled for the device's tenant<br/>`connection-duration-exceeded` indicates that the overall amount of time that a tenant's devices may be connected to an adapter has exceeded<br/>`data-volume-exceeded` indicates that the overall amount of data that a tenant's device may transfer per time period has exceeded<br/>`registration-assertion-failure` indicates that the device is either unknown or disabled<br/>`succeeded` indicates a successfully established connection<br/>`tenant-connections-exceeded` indicates that the maximum number of devices that may be connected simultaneously for a tenant has been exceeded<br/>`unauthorized` indicates that the device failed to authenticate<br/>`unavailable` indicates that some of Hono's (required) services are not available<br/>`unknown` indicates an unknown reason. |

Additional tags for *hono.downstream.sent*:

| Name        | Value                                              | Description |
| ----------- | -------------------------------------------------- | ----------- |
| *outcome*   | `received`, `accepted`, `rejected`, `released`, `modified`, `declared`, `transactionalState`, and `aborted` | Any of the AMQP 1.0 disposition states, as well as `aborted`, in the case the connection/link was closed before the disposition could be read. | 

Metrics provided by the protocol adapters are:

| Metric                             | Type                | Tags                                                                                         | Description |
| ---------------------------------- | ------------------- | -------------------------------------------------------------------------------------------- | ----------- |
| *hono.commands.received*           | Timer               | *host*, *component-type*, *component-name*, *tenant*, *type*, *status*, *direction*          | The time it took to process a message conveying a command or a response to a command. |
| *hono.commands.payload*            | DistributionSummary | *host*, *component-type*, *component-name*, *tenant*, *type*, *status*, *direction*          | The number of bytes conveyed in the payload of a command message. |
| *hono.connections.authenticated*   | Gauge               | *host*, *component-type*, *component-name*, *tenant*                                         | Current number of connected, authenticated devices. <br/> **NB** This metric is only supported by protocol adapters that maintain *connection state* with authenticated devices. In particular, the HTTP adapter does not support this metric. |
| *hono.connections.unauthenticated* | Gauge               | *host*, *component-type*, *component-name*                                                   | Current number of connected, unauthenticated devices. <br/> **NB** This metric is only supported by protocol adapters that maintain *connection state* with authenticated devices. In particular, the HTTP adapter does not support this metric. |
| *hono.connections.authenticated.duration* | Timer        | *host*, *component-type*, *component-name*, *tenant*                                         | The overall amount of time that authenticated devices have been connected to protocol adapters. <br/> **NB** This metric is only supported by protocol adapters that maintain *connection state* with authenticated devices. In particular, the HTTP adapter does not support this metric. |
| *hono.connections.attempts*        | Counter             | *host*, *component-type*, *component-name*, *tenant*, *outcome*                              | The number of attempts made by devices to connect to a protocol adapter. The *outcome* tag's value determines if the attempt was successful or not. In the latter case the outcome also indicates the reason for the failure to connect.<br/>**NB** This metric is only supported by protocol adapters that maintain *connection state* with authenticated devices. In particular, the HTTP adapter does not support this metric. |
| *hono.downstream.full*             | Counter             | *host*, *component-type*, *component-name*, *tenant*, *type*                                 | The number of times a message should be sent, but could not because the sender was out of credit. |
| *hono.downstream.sent*             | Timer               | *host*, *component-type*, *component-name*, *tenant*, *type*, *outcome*                      | The time it took to send a message and receive the remote peers disposition. |
| *hono.downstream.timeout*          | Counter             | *host*, *component-type*, *component-name*, *tenant*, *type*                                 | The number of times a message timed out, meaning that no disposition was received in the appropriate amount of time. |
| *hono.messages.received*           | Timer               | *host*, *component-type*, *component-name*, *tenant*, *type*, *status*, *qos*, *ttd*         | The time it took to process a message conveying telemetry data or an event. |
| *hono.messages.payload*            | DistributionSummary | *host*, *component-type*, *component-name*, *tenant*, *type*, *status*                       | The number of bytes conveyed in the payload of a telemetry or event message. |

#### Minimum Message Size

If a minimum message size is configured for a [tenant]({{< relref "/api/tenant#tenant-information-format" >}}), 
then the payload size of the telemetry, event and command messages are calculated in accordance with the configured 
value and then reported to the metrics by the AMQP, HTTP and MQTT protocol adapters. If minimum message size is not 
configured for a tenant then the actual message payload size is reported.

Assume that the minimum message size for a tenant is configured as 4096 bytes (4KB). The payload size of 
an incoming message with size 1KB is calculated as 4KB by the protocol adapters and reported to the metrics system.
For an incoming message of size 10KB, it is reported as 12KB.

### Service Metrics

Hono's service components do not report any metrics at the moment.
