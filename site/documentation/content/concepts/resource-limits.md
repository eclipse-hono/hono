+++
title = "Resource limits"
weight = 196
+++

Resource limits such as the maximum number of device connections allowed per tenant or the allowed data volume of the messages over a period of time per tenant can be set in Hono. 

Hono specifies an API `ResourceLimitChecks` that is used by the protocol adapters for the verification of the configured resource limits. A default implementation of this API is shipped with Hono. This default implementation uses the live metrics data retrieved from a Prometheus server to verify the resource-limits, if configured. To enable and use this default implementation, please refer to the protocol adapter admin guides. Based on the requirements, a custom version of the above API can be implemented and used. The resource-limits for a tenant can be set using the tenant configuration. Please refer to the [Tenant API]({{< relref "/api/tenant#request-payload" >}}) for more details.


## Connections Limit

Before accepting a new connection request from a device, the number of existing connections is checked against the configured limit by the protocol adapters. The connection request is declined if the limit is exceeded.

The MQTT and AMQP protocol adapters keep the connections longer opened than their counterparts such as HTTP. Thereby the MQTT and AMQP adapters are enabled to check the connection limits before accepting any new connection to a device.

## Messages Limit

Hono supports limiting the number of messages that devices and north bound applications of a tenant can publish to Hono during a given time interval. Before accepting any telemetry or event or command messages from devices or north bound applications, it is checked by the protocol adapters that if the message limit is exceeded or not. The incoming message is discarded if the limit is exceeded. 

The default prometheus based implementation uses data volume as the factor to limit the messages. The data volume already consumed by a tenant over the given time interval is compared with the configured message limit before accepting any messages.
