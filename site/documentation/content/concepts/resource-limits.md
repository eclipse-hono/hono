+++
title = "Resource limits"
weight = 196
+++

Resource limits such as the maximum number of device connections allowed per tenant or the allowed data volume of the messages over a period of time per tenant can be set in Hono. 

Hono specifies an API `ResourceLimitChecks` that is used by the protocol adapters for the verification of the configured resource limits. A default implementation of this API is shipped with Hono. This default implementation uses the live metrics data retrieved from a Prometheus server to verify the resource-limits, if configured. To enable and use this default implementation, please refer to the protocol adapter admin guides. Based on the requirements, a custom version of the above API can be implemented and used. The resource-limits for a tenant can be set using the tenant configuration. Please refer to the [Tenant API]({{< relref "/api/tenant#tenant-information-format" >}}) for more details.


## Connections Limit

Before accepting a new connection request from a device, the number of existing connections is checked against the configured limit by the protocol adapters. The connection request is declined if the limit is exceeded.

The MQTT and AMQP protocol adapters keep the connections longer opened than their counterparts such as HTTP. Thereby the MQTT and AMQP adapters are enabled to check the connection limits before accepting any new connection to a device.

## Messages Limit

Hono supports limiting the number of messages that devices and north bound applications of a tenant can publish to Hono during a given time interval. Before accepting any telemetry or event or command messages from devices or north bound applications, it is checked by the protocol adapters that if the message limit is exceeded or not. The incoming message is discarded if the limit is exceeded. 

The default Prometheus based implementation uses data volume as the factor to limit the messages. The data volume already consumed by a tenant over the given time interval is compared with the configured message limit before accepting any messages. The default implementation supports two modes of message limits calculation namely `days` and `monthly`. For more details on how to set the mode refer to the [Tenant API]({{< relref "/api/tenant#data-volume-period-configuration-format" >}}).

In the `monthly` mode, the message limit check ensures that the data usage from the beginning till the end of a (Gregorian) calendar month does not exceed the *max-bytes* value. But for the first month on which the message limit became effective, the *effective max-bytes* are calculated based on the *max-bytes* with respect to the remaining days in that month from the *effective-since* date.

Below is a sample resource limit configuration for a tenant and it has been defined that the message limit became effective on 10.Jul.2019 and the maximum bytes allowed for a month is 2 GB. It means that from August 2019, the message limit check ensures that the data usage in a month does not exceed 2 GB. But in case of July 2019, the month on which the message limit became effective, the *effective max-bytes* is calculated by finding the average limit for a day from the *max-bytes* and multiplying it with the number of days from the *effective-since* date till the end of that month. In this case it is calculated as *(2 GB / 31 days) x  22 days* which is 1.4 GB. It means that for the month of July 2019, the data usage should not exceed 1.4GB.

~~~json
"resource-limits": {
  "data-volume": {
    "effective-since": "2019-07-10T14:30:00Z",
    "max-bytes": 2147483648,
    "period": {
      "mode": "monthly"
    }
  }
}
~~~
In the `days` mode, the message limit check ensures that the data usage for the defined *no-of-days* does not exceed the *max-bytes* value. In the below sample configuration, the mode is configured as `days` and the accounting duration as 30 days. In this case the message limit check ensures that the data usage for every 30 days from 10.Jul.2019 (`effective-since`) does not exceed the 2GB limit.
~~~json
"resource-limits": {
  "data-volume": {
    "effective-since": "2019-07-10T14:30:00Z",
    "max-bytes": 2147483648,
    "period": {
      "mode": "days",
      "no-of-days": 30
    }
  }
}
~~~
