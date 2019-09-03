---
title: "Tenant API Specification"
linkTitle: "Tenant API"
weight: 420
resources:
  - src: preconditions.svg
  - src: get_tenant_success.svg
---

The *Tenant API* is used by Hono's protocol adapters to retrieve information that affects all devices belonging to a particular tenant.
A tenant is a logical entity, which groups together a set of devices. The information registered for a tenant is used for example to
determine if devices belonging to the tenant are allowed to connect to a certain protocol adapter or if devices are required to authenticate.
<!--more-->

This document *describes* the Tenant API's operations and the payload data format used by them.
Please refer to [Multi Tenancy]({{< relref "/concepts/tenancy.md" >}}) for details regarding the way Hono supports multiple tenants.

The Tenant API is defined by means of AMQP 1.0 message exchanges, i.e. a client needs to connect to Hono using an AMQP 1.0 client in order to invoke operations of the API as described in the following sections.

<a name="preconditions"></a>
## Preconditions for invoking the Tenant API

1. Client has established an AMQP connection with the Tenant service.
1. Client has established an AMQP link in role *sender* on the connection using target address `tenant`. This link is used by the client to send request messages to the Tenant service.
1. Client has established an AMQP link in role *receiver* on the connection using source address `tenant/${reply-to}` where *reply-to* may be any arbitrary string chosen by the client. This link is used by the client to receive responses to the requests it has sent to the Tenant service. This link's source address is also referred to as the *reply-to* address for the request messages.

{{< figure src="preconditions.svg" alt="A client establishes an AMQP connection and the links required to invoke operations of the Tenant service" title="Client connecting to Tenant service" >}}

## Get Tenant Information

Clients use this operation to *retrieve* information about a tenant.

**Message Flow**

The following sequence diagram illustrates the flow of messages involved in a *Client* retrieving tenant information.

{{< figure src="get_tenant_success.svg" title="Client retrieving tenant information" alt="A client sends a request message for retrieving tenant information and receives a response containing the information" >}}

**Request Message Format**

The following table provides an overview of the properties a client needs to set on a message to get tenant information:

| Name             | Mandatory | Location                 | AMQP Type    | Description |
| :--------------- | :-------: | :----------------------- | :----------- | :---------- |
| *correlation-id* | no        | *properties*             | *message-id* | MAY contain an ID used to correlate a response message to the original request. If set, it is used as the *correlation-id* property in the response, otherwise the value of the *message-id* property is used. Either this or the *message-id* property MUST be set. |
| *message-id*     | no        | *properties*             | *string*     | MAY contain an identifier that uniquely identifies the message at the sender side. Either this or the *correlation-id* property MUST be set. |
| *reply-to*       | yes       | *properties*             | *string*     | MUST contain the source address that the client wants to receive response messages from. This address MUST be the same as the source address used for establishing the client's receive link (see [Preconditions]({{< relref "#preconditions" >}})). |
| *subject*        | yes       | *properties*             | *string*     | MUST be set to `get`. |

The body of the request message MUST consist of a single *Data* section containing a UTF-8 encoded string representation of a single JSON object.

The JSON object MUST contain *exactly one* of the following search criteria properties:

| Name             | Mandatory | JSON Type  | Description |
| :--------------- | :-------: | :--------- | :---------- |
| *subject-dn*     | *no*      | *string*   | The subject DN of the trusted certificate authority's public key in the format defined by [RFC 2253](https://www.ietf.org/rfc/rfc2253.txt). |
| *tenant-id*      | *no*      | *string*   | The identifier of the tenant to get. |

The following request payload may be used to look up the tenant with identifier `ACME Corporation`:

~~~json
{
  "tenant-id": "ACME Corporation"
}
~~~

The following request payload may be used to look up the tenant for which a trusted certificate authority with subject DN `O=ACME Corporation, CN=devices` has been configured:

~~~json
{
  "subject-dn": "CN=devices,O=ACME Corporation"
}
~~~

**Response Message Format**

The following table provides an overview of the properties contained in a response message to a *get tenant information* request:

| Name             | Mandatory | Location                 | AMQP Type    | Description |
| :--------------- | :-------: | :----------------------- | :----------- | :---------- |
| *correlation-id* | yes       | *properties*             | *message-id* | Contains the *message-id* (or the *correlation-id*, if specified) of the request message that this message is the response to. |
| *content-type*   | no        | *properties*             | *string*     | MUST be set to `application/json` if the invocation of the operation was successful and the body of the response message contains payload as described below. |
| *status*         | yes       | *application-properties* | *int*        | Contains the status code indicating the outcome of the operation. Concrete values and their semantics are defined for each particular operation. |
| *cache_control*  | no        | *application-properties* | *string*     | Contains an [RFC 2616](https://tools.ietf.org/html/rfc2616#section-14.9) compliant <em>cache directive</em>. The directive contained in the property MUST be obeyed by clients that are caching responses. |

The response message's *status* property may contain the following codes:

| Code  | Description |
| :---- | :---------- |
| *200* | OK, The response message body contains the requested tenant information. |
| *400* | Bad Request, the request message did not contain all mandatory properties.
| *403* | Forbidden, the client is not authorized to retrieve information for the given tenant. |
| *404* | Not Found, there is no tenant matching the given search criteria. |

For status codes indicating an error (codes in the `400 - 499` range) the message body MAY contain a detailed description of the error that occurred. In this case, the response message's *content-type* property SHOULD be set accordingly.

Otherwise the response message contains the information for the requested tenant as described in the following sections.

<a name="payload-format"></a>

## Tenant Information Format

Tenant information is carried in a single *Data* section of the response message as a UTF-8 encoded string representation of a single JSON object.
It is an error to include payload that is not of this type.

The table below provides an overview of the standard members defined for the JSON response object:

| Name                     | Mandatory | JSON Type     | Description |
| :------------------------| :-------: | :------------ | :---------- |
| *adapters*               | *no*      | *array*       | A list of configuration options valid for certain adapters only. The format of a configuration option is described here [Adapter Configuration Format]({{< relref "#adapter-configuration-format" >}}). **NB** If the element is provided then the list MUST NOT be empty. **NB** Only a single entry per *type* is allowed. If multiple entries for the same *type* are present it is handled as an error. **NB** If the element is omitted then all adapters are *enabled* in their default configuration. |
| *defaults*               | *no*      | *object*      |  Arbitrary *default* properties for devices belonging to the tenant. The properties can be used by protocol adapters to augment downstream messages with missing information, e.g. setting a default content type or time-to-live. |
| *enabled*                | *yes*     | *boolean*     | If set to `false` the tenant is currently disabled. Protocol adapters MUST NOT allow devices of a disabled tenant to connect and MUST NOT accept data published by such devices. |
| *minimum-message-size*   | *no*      | *number*      | The minimum message size in bytes. If it is set then the payload size of the telemetry, event and command messages are calculated in accordance with the configured value and then reported to the metrics. See [Metrics]({{< relref "/api/Metrics.md#minimum-message-size" >}}) for more details.| 
| *resource-limits*        | *no*      | *object*      | Any resource limits that should be enforced for the tenant, e.g. the maximum number of concurrent connections and the maximum data volume for a given period. Refer to [Resource Limits Configuration Format]({{< relref "#resource-limits-configuration-format" >}}) for details. |
| *tenant-id*              | *yes*     | *string*      | The ID of the tenant. |
| *tracing*                | *no*      | *object*      | A set of options regarding the tracing of messages for the tenant. See [Tracing Format]({{< relref "#tracing-format" >}}) for a definition of the content model of the object. |
| *trusted-ca*             | *no*      | *object*      | The trusted certificate authority to use for validating certificates presented by devices of the tenant for authentication purposes. See [Trusted Certificate Authority Format]({{< relref "#trusted-ca-format" >}}) for a definition of the content model of the object. |

The JSON object MAY contain an arbitrary number of additional members with arbitrary names which can be of a scalar or a complex type.
This allows for future *well-known* additions and also allows to add further information which might be relevant to a *custom* adapter only.

**Examples**

The JSON structure below contains example information for tenant `TEST_TENANT`. Note that the structure contains some custom properties at both the root level (*customer*) as well as the adapter configuration level (*deployment*) and also defines a default TTL for downstream messages.

~~~json
{
  "tenant-id" : "TEST_TENANT",
  "defaults": {
    "ttl": 30
  },
  "enabled" : true,
  "customer": "ACME Inc.",
  "resource-limits": {
    "max-connections": 100000,
    "data-volume": {
      "max-bytes": 2147483648,
      "period": {
        "mode": "days",
        "no-of-days": 30
      },
      "effective-since": "2019-07-27T14:30:00Z"
    }
  },
  "adapters" : [
    {
      "type" : "hono-mqtt",
      "enabled" : true,
      "device-authentication-required" : true
    }, {
      "type" : "hono-http",
      "enabled" : true,
      "device-authentication-required" : true,
      "deployment": {
        "maxInstances": 4
      }
    }
  ]
}
~~~

### Tracing Format

The table below provides an overview of the members defined for the *tracing* JSON object:

| Name                        | Mandatory  | Type          | Default Value | Description |
| :---------------------------| :--------: | :------------ | :------------ | :---------- |
| *sampling-mode*             | *no*       | *string*      | `default`     | Defines in how far OpenTracing spans created when processing messages for this tenant shall be recorded (sampled) by the tracing system. The value `default` lets the default sampling mechanism be used. The value `all` marks the spans related to this tenant so that they should all be sampled. The value `none` marks the spans as not to be sampled. The mode defined here may be overridden for a particular auth-id by means of the `sampling-mode-per-auth-id` property. |
| *sampling-mode-per-auth-id* | *no*       | *object*      |               | Defines in how far OpenTracing spans created when processing messages for this tenant and a particular auth-id shall be recorded (sampled) by the tracing system. The child properties have the auth-id as name. A child property value of `default` lets the default sampling mechanism be used. The child property value `all` marks the spans related to this tenant and the auth-id so that they should all be sampled. The child property value `none` marks the spans as not to be sampled. The mode defined for a particular auth-id has precedence over the value defined by the `sampling-mode` property. |

### Trusted CA Format

The table below provides an overview of the members defined for the *trusted-ca* JSON object:

| Name                     | Mandatory  | Type          | Default Value | Description |
| :------------------------| :--------: | :------------ | :------------ | :---------- |
| *subject-dn*             | *yes*      | *string*      |               | The subject DN of the trusted root certificate in the format defined by [RFC 2253](https://www.ietf.org/rfc/rfc2253.txt). |
| *cert*                   | *no*       | *string*      |               | The Base64 encoded binary DER encoding of the trusted root X.509 certificate. |
| *public-key*             | *no*       | *string*      |               | The Base64 encoded binary DER encoding of the trusted root certificate's public key. |
| *algorithm*              | *no*       | *string*      | `RSA`        | The name of the public key algorithm. Supported values are `RSA` and `EC`. This property is ignored if the *cert* property is used to store a certificate. |

* The *subject-dn* MUST be unique among all registered tenants.
* Either the *cert* or the *public-key* MUST be set.

### Adapter Configuration Format

The table below contains the properties which are used to configure a *Hono protocol adapter*:

| Name                               | Mandatory | JSON Type  | Default Value | Description |
| :--------------------------------- | :-------: | :--------- | :------------ | :---------- |
| *type*                             | *yes*     | *string*   | `-`          | The type of the adapter which this configuration belongs to.|
| *enabled*                          | *no*      | *boolean*  | `false`      | If set to false the tenant is not allowed to receive / send data utilizing the given adapter. |
| *device-authentication-required*   | *no*      | *boolean*  | `true`       | If set to false, devices are not required to authenticate with the adapter before sending / receiving data. |

Protocol adapters SHOULD use the configuration properties set for a tenant when interacting with devices of that tenant, e.g. in order to make authorization decisions etc.

The JSON object MAY contain an arbitrary number of additional members with arbitrary names of either scalar or complex type.
This allows for future *well-known* additions and also allows to add further information which might be relevant to a *custom* adapter only.

### Resource Limits Configuration Format

The table below contains the properties which are used to configure a tenant's resource limits:

| Name                | Mandatory | JSON Type     | Default Value | Description |
| :------------------ | :-------: | :------------ | :------------ | :---------- |
| *max-connections*   | *no*      | *number*      | `-1`          | The maximum number of concurrent connections allowed from devices of this tenant. The default value `-1` indicates that no limit is set. |
| *max-ttl*           | *no*      | *number*      | `-1`          | The maximum time-to-live (in seconds) to use for events published by devices of this tenant. Any default TTL value specified at either the tenant or device level will be limited to the max value specified here. If this property is set to a value greater than `-1` and no default TTL is specified for a device, the max value will be used for events published by the device. A value of `-1` (the default) indicates that no limit is set. **Note** that this property contains the TTL in *seconds* whereas the AMQP 1.0 specification defines a message's *ttl* header to use milliseconds. |
| *data-volume*       | *no*      | *object*      | `-`           | The maximum data volume allowed for the given tenant. Refer to  [Data Volume Configuration Format]({{< relref "#data-volume-configuration-format" >}}) for details.|

Protocol adapters SHOULD use the *max-connections* property to determine if a device's connection request should be accepted or rejected.

Protocol adapters SHOULD use the *max-ttl* property to determine the *effective time-to-live* for events published by devices as follows:

1. If a non-default *max-ttl* is set for the tenant, use that value as the *effective ttl*, otherwise set *effective ttl* to `-1`.
1. If the event published by the device
   1. contains a *ttl* header and *effective ttl* is not `-1` and the *ttl* value (in seconds) provided by the device is smaller than the
      *effective ttl*, use the device provided *ttl* value as the new *effective ttl*.
   1. does not contain a *ttl* header but a default *ttl* value is configured for the device (with the device level taking precedence
      over the tenant level) and *effective ttl* is not `-1` and the default value is smaller than the *effective ttl*,
      use the default *ttl* value as the new *effective ttl*.
1. If *effective ttl* is not `-1`, set the downstream event message's *ttl* header to its value (in milliseconds).

The JSON object MAY contain an arbitrary number of additional members with arbitrary names of either scalar or complex type.
This allows for future *well-known* additions and also allows to add further information which might be relevant to a *custom* adapter only.

### Data Volume Configuration Format

The table below contains the properties which are used to configure a tenant's data volume limit:

| Name                     | Mandatory | JSON Type     | Default Value | Description |
| :------------------------| :-------: | :------------ | :------------ | :---------- |
| *effective-since*        | *yes*     | *string*      | `-`           | The point in time at which the current settings became effective, i.e. the start of the first accounting period based on these settings. The value MUST be an [ISO 8601 compliant *combined date and time representation in extended format*](https://en.wikipedia.org/wiki/ISO_8601#Combined_date_and_time_representations).|
| *max-bytes*              | *no*      | *number*      | `-1`          | The maximum number of bytes allowed for the tenant for each accounting period. MUST be an integer. A negative value indicates that no limit is set. |
| *period*                 | *no*      | *object*      | `-`          | The mode and length of an accounting period, i.e. the data usage can limited based on the defined number of days or on a monthly basis. |

Protocol adapters SHOULD use this information to determine if a message originating from or destined to a device should be accepted for processing.

### Data Volume Period Configuration Format

The table below contains the properties which are used to configure a tenant's data volume period:

| Name                     | Mandatory | JSON Type     | Default Value | Description |
| :------------------------| :-------: | :------------ | :------------ | :---------- |
| *mode*                   | *yes*     | *string*      | `-`           | The mode of the data usage calculation. The default implementation supports two modes namely `days` and `monthly`.|
| *no-of-days*             | *no*      | *number*      | `-`           | When the mode is set as `days`, then this value represents the length of an accounting period , i.e. the number of days over which the data usage is to be limited. MUST be a positive integer.|

## Delivery States used by the Tenant API

A Tenant service implementation uses the following AMQP message delivery states when receiving request messages from clients:

| Delivery State | Description |
| :------------- | :---------- |
| *ACCEPTED*     | Indicates that the request message has been received and accepted for processing. |
| *REJECTED*     | Indicates that the request message has been received but cannot be processed. The disposition frame's *error* field contains information regarding the reason why. Clients should not try to re-send the request using the same message properties and payload in this case. |
