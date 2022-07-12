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

Hono uses [Micrometer](https://micrometer.io/) for collecting metrics. Those metrics can be exported to different
back ends. Please refer to [Configuring Metrics]({{< relref "/admin-guide/monitoring-tracing-config#enabling-collection-of-metrics" >}})
for details.

The container images published on Docker Hub have been compiled with support for [Prometheus](https://prometheus.io/)
as the metrics back end.

When deploying to Kubernetes/OpenShift, the metrics reported by Hono may contain
environment specific tags (like the *pod* name) which are added by the Prometheus
scraper. However, those tags are not part of the Hono metrics definition.

Hono applications may report other metrics in addition to the ones defined here.
In particular, all components report metrics regarding the JVM's internal state, e.g.
memory consumption and garbage collection status. Those metrics are not considered
part of Hono's *official* metrics definition. However, all those metrics
will still contain the common tags described below.

### Common Metrics

Tags for common metrics are:

| Tag              | Value              | Description |
| ---------------- | ------------------ | ----------- |
| *host*           | *string*           | The name of the host that the component reporting the metric is running on |
| *component-type* | `adapter`, `service` | The type of component reporting the metric |
| *component-name* | *string*           | The name of the component reporting the metric. |

The names of Hono's standard components are as follows:

| Component         | *component-name*   |
| ----------------- | ------------------ |
| Auth Server       | `hono-auth`         |
| Device Registry   | `hono-registry`     |
| Command Router    | `hono-command-router` |
| AMQP adapter      | `hono-amqp`         |
| CoAP adapter      | `hono-coap`         |
| HTTP adapter      | `hono-http`         |
| MQTT adapter      | `hono-mqtt`         |
| Lora adapter      | `hono-lora`         |
| Sigfox adapter    | `hono-sigfox`       |

### Protocol Adapter Metrics

Additional tags used for metrics reported by protocol adapters are:

| Name        | Value                                              | Description |
| ----------- | -------------------------------------------------- | ----------- |
| *direction* | `one-way`, `request`, `response`               | The direction in which a Command &amp; Control message is being sent:<br>`one-way` indicates a command sent to a device for which the sending application doesn't expect to receive a response.<br>`request` indicates a command request message sent to a device.<br>`response` indicates a command response received from a device. |
| *qos*       | `0`, `1`, `unknown`                              | The quality of service used for a telemetry or event message.<br>`0` indicates *at most once*,<br>`1` indicates *at least once* and<br> `none` indicates unknown delivery semantics. |
| *status*    | `forwarded`, `unprocessable`, `undeliverable` | The processing status of a message.<br>`forwarded` indicates that the message has been forwarded to a downstream consumer<br>`unprocessable` indicates that the message has not been processed not forwarded, e.g. because the message was malformed<br>`undeliverable` indicates that the message could not be forwarded, e.g. because there is no downstream consumer or due to an infrastructure problem |
| *tenant*    | *string*                                           | The identifier of the tenant that the metric is being reported for. |
| *ttd*       | `command`, `expired`, `none`                    | A status indicating the outcome of processing a TTD value contained in a message received from a device.<br>`command` indicates that a command for the device has been included in the response to the device's request for uploading the message.<br>`expired` indicates that a response without a command has been sent to the device.<br>`none` indicates that either no TTD value has been specified by the device or that the protocol adapter does not support it. |
| *type*      | `command_response`, `credentials`, `event`, `registration`, `telemetry`, `tenant` | The type of message that the metric is being reported for. |

Additional tags for *hono.connections.attempts*:

| Name           | Value                                              | Description |
| -------------- | -------------------------------------------------- | ----------- |
| *cipher-suite* | *string*                                           | The name of the cipher suite that is used for the device's connection to the adapter. The specific value depends on the TLS implementation used by the protocol adapter.<br/>The value `UNKNOWN` is used if the connection does not use TLS or the cipher suite could not be determined, e.g. because the connection attempt failed before the cipher suite has been negotiated. |
| *outcome*      | `adapter-disabled`, `connection-duration-exceeded`,<br/>`data-volume-exceeded`, `registration-assertion-failure`,<br/>`succeeded`, `tenant-connections-exceeded`,<br/>`unauthorized`, `unavailable`, `unknown` | The outcome of a device's connection attempt.<br/>`adapter-connections-exceeded` indicates that the maximum number of connections that the adapter instance can handle are exceeded<br/>`adapter-disabled` indicates that the protocol adapter is not enabled for the device's tenant<br/>`connection-duration-exceeded` indicates that the overall amount of time that a tenant's devices may be connected to an adapter has exceeded<br/>`data-volume-exceeded` indicates that the overall amount of data that a tenant's device may transfer per time period has exceeded<br/>`registration-assertion-failure` indicates that the device is either unknown or disabled<br/>`succeeded` indicates a successfully established connection<br/>`tenant-connections-exceeded` indicates that the maximum number of devices that may be connected simultaneously for a tenant has been exceeded<br/>`unauthorized` indicates that the device failed to authenticate<br/>`unavailable` indicates that some of Hono's (required) services are not available<br/>`unknown` indicates an unknown reason. |

Additional tags for *hono.amqp.delivery.duration*:

| Name        | Value                                              | Description |
| ----------- | -------------------------------------------------- | ----------- |
| *outcome*   | `received`, `accepted`, `rejected`, `released`, `modified`, `declared`, `transactionalState`, and `aborted` | Any of the AMQP 1.0 disposition states, as well as `aborted`, in the case the connection/link was closed before the disposition could be read. | 

Metrics provided by the protocol adapters are:

| Metric                             | Type                | Tags                                                                                         | Description |
| ---------------------------------- | ------------------- | -------------------------------------------------------------------------------------------- | ----------- |
| *hono.amqp.delivery.duration*      | Timer               | *host*, *component-type*, *component-name*, *tenant*, *type*, *outcome*                      | The time it took to send an AMQP 1.0 message and receive the remote peers disposition. |
| *hono.amqp.nocredit*               | Counter             | *host*, *component-type*, *component-name*, *tenant*, *type*                                 | The number of times an AMQP 1.0 message should be sent, but could not because the sender was out of credit. |
| *hono.amqp.timeout*                | Counter             | *host*, *component-type*, *component-name*, *tenant*, *type*                                 | The number of times sending an AMQP 1.0 message timed out, meaning that no disposition was received in the appropriate amount of time. |
| *hono.command.payload*             | DistributionSummary | *host*, *component-type*, *component-name*, *tenant*, *type*, *status*, *direction*          | The number of bytes conveyed in the payload of a command message. |
| *hono.command.processing.duration* | Timer               | *host*, *component-type*, *component-name*, *tenant*, *type*, *status*, *direction*          | The time it took to process a message conveying a command or a response to a command. |
| *hono.connections.authenticated*   | Gauge               | *host*, *component-type*, *component-name*, *tenant*                                         | Current number of connected, authenticated devices. <br/> **NB** This metric is only supported by protocol adapters that maintain *connection state* with authenticated devices. In particular, the HTTP adapter does not support this metric. |
| *hono.connections.unauthenticated* | Gauge               | *host*, *component-type*, *component-name*                                                   | Current number of connected, unauthenticated devices. <br/> **NB** This metric is only supported by protocol adapters that maintain *connection state* with authenticated devices. In particular, the HTTP adapter does not support this metric. |
| *hono.connections.authenticated.duration* | Timer        | *host*, *component-type*, *component-name*, *tenant*                                         | The overall amount of time that authenticated devices have been connected to protocol adapters. <br/> **NB** This metric is only supported by protocol adapters that maintain *connection state* with authenticated devices. In particular, the HTTP adapter does not support this metric. |
| *hono.connections.attempts*        | Counter             | *host*, *component-type*, *component-name*, *tenant*, *outcome*, *cipher-suite*              | The number of attempts made by devices to connect to a protocol adapter. The *outcome* tag's value determines if the attempt was successful or not. In the latter case the outcome also indicates the reason for the failure to connect.<br/>**NB** This metric is only supported by protocol adapters that maintain *connection state* with authenticated devices. In particular, the HTTP adapter does not support this metric. |
| *hono.telemetry.payload*           | DistributionSummary | *host*, *component-type*, *component-name*, *tenant*, *type*, *status*                       | The number of bytes conveyed in the payload of a telemetry or event message. |
| *hono.telemetry.processing.duration* | Timer              | *host*, *component-type*, *component-name*, *tenant*, *type*, *status*, *qos*, *ttd*         | The time it took to process a message conveying telemetry data or an event. |

#### Minimum Message Size

If a minimum message size is configured for a [tenant]({{< relref "/api/tenant#tenant-information-format" >}}), 
then the payload size of the telemetry, event and command messages are calculated in accordance with the configured 
value and then reported to the metrics by the AMQP, HTTP and MQTT protocol adapters. If minimum message size is not 
configured for a tenant then the actual message payload size is reported.

Assume that the minimum message size for a tenant is configured as 4096 bytes (4KB). The payload size of 
an incoming message with size 1KB is calculated as 4KB by the protocol adapters and reported to the metrics system.
For an incoming message of size 10KB, it is reported as 12KB.

### Service Metrics

#### Authentication Server

| Metric                             | Type                | Tags                                                                 | Description |
| ---------------------------------- | ------------------- | -------------------------------------------------------------------- | ----------- |
| *hono.authentication.attempts*     | Counter             | *host*, *component-type*, *component-name*, *outcome*, *client-type* | The number of attempts made by clients to authenticate to the server.<br/><br/>The *outcome* tag's value determines if the attempt was successful or not:<br/>`succeeded` indicates that the client has been authenticated successfully,<br/>`unauthorized` indicates that the client failed to authenticate, e.g. because of wrong credentials and<br/>`unavailable` indicates that some of the services required for verifying the client's credentials are (temporarily) not available.<br/><br/>The *client-type* tag indicates what type of client has attempted to authenticate:<br/>`auth-service` indicates that the client tried to use the server as an implementation of the [Hono Authentication Service]({{< relref "/api/authentication" >}}),<br/>`dispatch-router` indicates that the client tried to use the server as an implementation of the [Qpid Dispatch Router AuthService](https://qpid.apache.org/releases/qpid-dispatch-1.17.0/man/qdrouterd.conf.html#_authserviceplugin).<br/>`unknown` indicates that the type of client is unknown. |

#### Command Router

Command messages get first received by the Command Router component and then get forwarded to the matching protocol adapter
instance, where the command will be reflected in the corresponding protocol adapter metrics with the *hono.command* prefix.
If the command could not be forwarded to a protocol adapter, the Command Router will report the command in its metrics,
as listed below.

| Metric                             | Type                | Tags                                                     | Description |
| ---------------------------------- | ------------------- | -------------------------------------------------------- | ----------- |
| *hono.command.payload*             | DistributionSummary | *host*, *component-type*, *component-name*, *tenant*, *type*, *status*, *direction* | The number of bytes conveyed in the payload of a command message that could not be forwarded to a protocol adapter. |
| *hono.command.processing.duration* | Timer               | *host*, *component-type*, *component-name*, *tenant*, *type*, *status*, *direction* | The time it took to process a message conveying a command that could not be forwarded to a protocol adapter. |

#### Device Registry

| Metric                             | Type                | Tags                                         | Description |
| ---------------------------------- | ------------------- | -------------------------------------------- | ----------- |
| *hono.tenants.total*               | Gauge               | *host*, *component-type*, *component-name*   | The total number of tenants registered. All registry instances will report the same (total) number of tenants so no aggregation along the *host* dimension should be performed. |
