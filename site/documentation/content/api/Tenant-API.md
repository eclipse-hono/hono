+++
title = "Tenant API Specification"
linkTitle = "Tenant API"
weight = 420
+++

The *Tenant API* is used by Hono's protocol adapters to retrieve information that affects all devices belonging to a particular tenant.
A tenant is a logical entity, which groups together a set of devices. The information registered for a tenant is used for example to
determine if devices belonging to the tenant are allowed to connect to a certain protocol adapter or if devices are required to authenticate.
<!--more-->

The Tenant API also present an HTTP endpoint for management operations, such as creating, updating and deleting tenants.  

This document *describes* the Tenant API's operations and the payload data format used by them.
Please refer to [Multi Tenancy]({{< ref "/concepts/tenancy.md" >}}) for details regarding the way Hono supports multiple tenants.

## AMQP API
The Tenant API is defined by means of AMQP 1.0 message exchanges, i.e. a client needs to connect to Hono using an AMQP 1.0 client in order to invoke operations of the API as described in the following sections.

<a name="preconditions"></a>
## Preconditions for invoking the Tenant API

1. Client has established an AMQP connection with the Tenant service.
1. Client has established an AMQP link in role *sender* on the connection using target address `tenant`. This link is used by the client to send request messages to the Tenant service.
1. Client has established an AMQP link in role *receiver* on the connection using source address `tenant/${reply-to}` where *reply-to* may be any arbitrary string chosen by the client. This link is used by the client to receive responses to the requests it has sent to the Tenant service. This link's source address is also referred to as the *reply-to* address for the request messages.

{{< figure src="../tenant_ConnectToTenant.png" alt="A client establishes an AMQP connection and the links required to invoke operations of the Tenant service" title="Client connecting to Tenant service" >}}

### Get Tenant Information

Clients use this operation to *retrieve* information about a tenant.

**Message Flow**

The following sequence diagram illustrates the flow of messages involved in a *Client* retrieving tenant information.

{{< figure src="../tenant_GetTenantSuccess.png" title="Client retrieving tenant information" alt="A client sends a request message for retrieving tenant information and receives a response containing the information" >}}

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
### Tenant Information Format

Tenant information is carried in a single *Data* section of the response message as a UTF-8 encoded string representation of a single JSON object.
It is an error to include payload that is not of this type.

The table below provides an overview of the standard members defined for the JSON response object:

| Name                     | Mandatory | JSON Type     | Description |
| :------------------------| :-------: | :------------ | :---------- |
| *adapters*               | *no*      | *array*       | A list of configuration options valid for certain adapters only. The format of a configuration option is described here [Adapter Configuration Format]({{< relref "#adapter-configuration-format" >}}). **NB** If the element is provided then the list MUST NOT be empty. **NB** Only a single entry per *type* is allowed. If multiple entries for the same *type* are present it is handled as an error. **NB** If the element is omitted then all adapters are *enabled* in their default configuration. |
| *defaults*               | *no*      | *object*      |  Arbitrary *default* properties for devices belonging to the tenant. The properties can be used by protocol adapters to augment downstream messages with missing information, e.g. setting a default content type or time-to-live. |
| *enabled*                | *yes*     | *boolean*     | If set to `false` the tenant is currently disabled. Protocol adapters MUST NOT allow devices of a disabled tenant to connect and MUST NOT accept data published by such devices. |
| *resource-limits*        | *no*      | *object*      | Any resource limits that should be enforced for the tenant, e.g. the maximum number of concurrent connections and the maximum data volume for a given period. Refer to [Resource Limits Configuration Format]({{< relref "#resource-limits-configuration-format" >}}) for details. |
| *tenant-id*              | *yes*     | *string*      | The ID of the tenant. |
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
      "period-in-days": 30,
      "effective-since": "2019-04-27"
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

| Name                     | Mandatory | JSON Type     | Default Value | Description |
| :------------------------| :-------: | :------------ | :------------ | :---------- |
| *max-connections*        | *no*      | *number*      | `-1`          | The maximum number of concurrent connections allowed from devices of this tenant. The default value `-1` indicates that no limit is set. |
| *data-volume*            | *no*      | *object*      | `-`           | The maximum data volume allowed for the given tenant. Refer to  [Data Volume Configuration Format]({{< relref "#data-volume-configuration-format" >}}) for details.|

Protocol adapters SHOULD use the *max-connections* property to determine if a device's connection request should be accepted or rejected.

The JSON object MAY contain an arbitrary number of additional members with arbitrary names of either scalar or complex type.
This allows for future *well-known* additions and also allows to add further information which might be relevant to a *custom* adapter only.

### Data Volume Configuration Format

The table below contains the properties which are used to configure a tenant's data volume limit:

| Name                     | Mandatory | JSON Type     | Default Value | Description |
| :------------------------| :-------: | :------------ | :------------ | :---------- |
| *max-bytes*              | *no*      | *number*      | `-1`          | The maximum number of bytes allowed for the tenant for each accounting period. MUST be an integer. A negative value indicates that no limit is set. |
| *period-in-days*         | *no*      | *number*      | `30`          | The length of an accounting period, i.e. the number of days over which the data usage is to be limited. MUST be a positive integer. |
| *effective-since*        | *yes*     | *string*      | `-`           | The point in time at which the current settings became effective, i.e. the start of the first accounting period based on these settings. The value MUST be an [ISO 8601 compliant *combined date and time representation in extended format*](https://en.wikipedia.org/wiki/ISO_8601#Combined_date_and_time_representations).

Protocol adapters SHOULD use this information to determine if a message originating from or destined to a device should be accepted for processing.

## Delivery States used by the Tenant API

A Tenant service implementation uses the following AMQP message delivery states when receiving request messages from clients:

| Delivery State | Description |
| :------------- | :---------- |
| *ACCEPTED*     | Indicates that the request message has been received and accepted for processing. |
| *REJECTED*     | Indicates that the request message has been received but cannot be processed. The disposition frame's *error* field contains information regarding the reason why. Clients should not try to re-send the request using the same message properties and payload in this case. |

## REST Management API 

All the management operations are accessible through the `/tenants` HTTP endpoint.

### Create a Tenant

An HTTP `POST` method request on the tenant endpoint triggers the tenant creation in the device registry.

#### URL pattern
The tenant id value can be optionally appended to the URL : `tenants/{tenantId}`.
If no parameter is given, the tenant will be assigned an auto-generated ID.

#### Request Format
The body should be of type `application/json`.

##### Body
The following table provides an overview of the properties that can be set in the request :

| Name             | Type        | Mandatory |  Description |
| :--------------- | :-------:   | :-------: |  :---------- |
| *enabled*        | Boolean     | no        | Default: true. |
| *ext*            | Json Object | no        | Arbitrary properties as extension to the ones specified by the Hono API. |
| *adapters*       | Json Array  | no        | Only a single entry per type is allowed. If multiple entries for the same type are present it is handled as an error. See [adapters](#Adapters)
| *limits*         | Json Object | no        | The resource limits to apply for this tenant. See [object format](#Resource Limits)
| *trusted-ca*     | Json Object | no        | The signed certificate of the trusted CA. See [the object format](#Trusted Certificate)

Example value:
~~~json
{
  "enabled": true,
  "ext": {
    "additionalProp1": {}
  },
  "adapters": [
    {
      "type": "string",
      "enabled": false,
      "device-authentication-required": true,
      "ext": {
        "additionalProp1": {}
      },
      "additionalProp1": {}
    }
  ],
  "defaults": {
    "additionalProp1": {}
  },
  "limits": {
    "max-connections": 0,
    "ext": {
      "additionalProp1": {}
    }
  },
  "trusted-ca": {
    "subject-dn": "string",
    "public-key": "string",
    "cert": "string",
    "algorithm": "EC"
  }
}
~~~

#### Expected Response Overview
| Response | Reason |
| -------- | ------ |
| 201      | Object created |
| 400      | Malformed request |
| 401      | Authentication error. |
| 403      | Operation not allowed. If the user does not have read access for this object, then `404` will be returned instead. |
| 409      | Object already exists.   |

#### Success Response
If the tenant is created successfully, the return code should be `201`.

##### Headers
| Header Name      | Type        |  Description |
| :--------------- | :-------:   |  :---------- |
| Location         | string      | URL to the resource |
| ETag             | string      | The version of the resource |

##### Body
The body should be a Json Object:
| Field Name       | Type        |  Description |
| :--------------- | :-------:   |  :---------- |
| id               | string      | The created Tenant ID. |

Example value:
~~~json
{
  "id": "string"
}
~~~

#### Failure Response
If the tenant could not be created, the return code can be one of the following : `400`, `401`, `403`, `409`.


##### Headers

A `401` Unauthorized error MUST carry the following header: 

| Header Name      | Type        |  Description |
| :--------------- | :-------:   |  :---------- |
| WWW-Authenticate | string      |  Defines the authentication method that should be used to authenticate.|

##### Body
The body MAY contain a Json Object with more details on the error. See the [error structure](#Error Result).

~~~json
{
  "error": "string",
  "additionalProp1": {}
}
~~~

### Retrieve Tenant Information

Tenant information is retrieved with the `GET` method. 

#### URL pattern
The tenant id parameter MUST should appended to the URL : `tenants/{tenantId}`.

#### Request Format
The only parameter must be passed in the URL, the body should be empty.

#### Expected Response Overview
| Response | Reason                    |
| -------- | ------                    |
| 200      | Operation successful.     |
| 400      | Malformed request         |
| 401      | Authentication error.     |
| 404      | Resource cannot be found. |

#### Success Response
If the tenant is created successfully, the return code should be `200`.

##### Headers
| Header Name      | Type        |  Description |
| :--------------- | :-------:   |  :---------- |
| ETag             | string      | The version of the resource |

##### Body
The body MUST be of type `application/json`. Here is the expected Json Object fields:
| Name             | Type        |  Description |
| :--------------- | :-------:   |  :---------- |
| *enabled*        | Boolean     | Tenant state. |
| *ext*            | Json Object | Arbitrary properties as extension to the ones specified by the Hono API. |
| *adapters*       | Json Array  | the adapters details for this tenant. See [adapters](#Adapters)
| *limits*         | Json Object | The resource limits to apply for this tenant. See [object format](#Resource Limits)
| *trusted-ca*     | Json Object | The signed certificate of the trusted CA. See [the object format](#Trusted Certificate)


Example value:
~~~json
{
  "enabled": true,
  "ext": {
    "additionalProp1": {}
  },
  "adapters": [
    {
      "type": "string",
      "enabled": false,
      "device-authentication-required": true,
      "ext": {
        "additionalProp1": {}
      },
      "additionalProp1": {}
    }
  ],
  "defaults": {
    "additionalProp1": {}
  },
  "limits": {
    "max-connections": 0,
    "ext": {
      "additionalProp1": {}
    }
  },
  "trusted-ca": {
    "subject-dn": "string",
    "public-key": "string",
    "cert": "string",
    "algorithm": "EC"
  }
}
~~~

#### Failure Response
If the tenant could not be retrieved, the return code can be one of the following : `400`, `401`, `404`.

##### Headers
A `401` Unauthorized error MUST carry the following header: 

| Header Name      | Type        |  Description |
| :--------------- | :-------:   |  :---------- |
| WWW-Authenticate | string      |  Defines the authentication method that should be used to authenticate.|

##### Body
The body MAY contain a Json Object with more details on the error. See the [error structure](#Error Result).

~~~json
{
  "error": "string",
  "additionalProp1": {}
}
~~~

### Update a Tenant information

An HTTP `PUT` method request on the tenant endpoint will update the tenant details in the device registry.

#### URL pattern
The tenant id value MUST be appended to the URL : `tenants/{tenantId}`.

#### Request Format
The body should be of type `application/json`.

##### Headers
The request MAY contain the following headers :
| Header Name      | Type        |  Description |
| :--------------- | :-------:   |  :---------- |
| If-Match         | string      | The expected resource version |

##### Body
The following table provides an overview of the properties that can be set in the request :

| Name             | Type        | Mandatory |  Description |
| :--------------- | :-------:   | :-------: |  :---------- |
| *enabled*        | Boolean     | no        | Default: true. |
| *ext*            | Json Object | no        | Arbitrary properties as extension to the ones specified by the Hono API. |
| *adapters*       | Json Array  | no        | Only a single entry per type is allowed. If multiple entries for the same type are present it is handled as an error. See [adapters](#Adapters)
| *limits*         | Json Object | no        | The resource limits to apply for this tenant. See [object format](#Resource Limits)
| *trusted-ca*     | Json Object | no        | The signed certificate of the trusted CA. See [the object format](#Trusted Certificate)

Example value:
~~~json
{
  "enabled": true,
  "ext": {
    "additionalProp1": {}
  },
  "adapters": [
    {
      "type": "string",
      "enabled": false,
      "device-authentication-required": true,
      "ext": {
        "additionalProp1": {}
      },
      "additionalProp1": {}
    }
  ],
  "defaults": {
    "additionalProp1": {}
  },
  "limits": {
    "max-connections": 0,
    "ext": {
      "additionalProp1": {}
    }
  },
  "trusted-ca": {
    "subject-dn": "string",
    "public-key": "string",
    "cert": "string",
    "algorithm": "EC"
  }
}
~~~

#### Expected Response Overview
| Response | Reason |
| -------- | ------ |
| 204      | Object updated. |
| 400      | Malformed request. |
| 401      | Authentication error. |
| 403      | Operation not allowed. If the user does not have read access for this object, then `404` will be returned instead. |
| 404      | Tenant not found.   |
| 412      | The given resource Version does not match current. This can only happen when the request header If-Match was set. |

#### Success Response
If the tenant is created successfully, the return code should be `201`.

##### Headers
| Header Name      | Type        |  Description |
| :--------------- | :-------:   |  :---------- |
| Location         | string      | URL to the resource |
| ETag             | string      | The version of the resource |

##### Body
The body should be a Json Object:
| Field Name       | Type        |  Description |
| :--------------- | :-------:   |  :---------- |
| id               | string      | The created Tenant ID. |

Example value:
~~~json
{
  "id": "string"
}
~~~

#### Failure Response
If the tenant could not be updated, the return code can be one of the following : `400`, `401`, `403`, `404`, `412`.


##### Headers

A `401` Unauthorized error MUST carry the following header: 

| Header Name      | Type        |  Description |
| :--------------- | :-------:   |  :---------- |
| WWW-Authenticate | string      |  Defines the authentication method that should be used to authenticate.|

##### Body
The body MAY contain a Json Object with more details on the error. See the [error structure](#Error Result).

~~~json
{
  "error": "string",
  "additionalProp1": {}
}
~~~

### Delete a Tenant information

An HTTP `DELETE` method request on the tenant endpoint will remove the tenant details in the device registry.

#### URL pattern
The tenant id value MUST be appended to the URL : `tenants/{tenantId}`.

#### Request Format
The only parameter must be passed in the URL, the body should be empty.

##### Headers
The request MAY contain the following headers :
| Header Name      | Type        |  Description |
| :--------------- | :-------:   |  :---------- |
| If-Match         | string      | The expected resource version |

#### Expected Response Overview
| Response | Reason |
| -------- | ------ |
| 204      | Object deleted. |
| 401      | Authentication error. |
| 403      | Operation not allowed. If the user does not have read access for this object, then `404` will be returned instead. |
| 404      | Tenant not found.   |
| 412      | The given resource Version does not match current. This can only happen when the request header If-Match was set. |

#### Success Response
If the tenant is deleted successfully, the return code should be `204`. No additional will be provided. 

#### Failure Response
If the tenant could not be deleted, the return code can be one of the following : `401`, `403`, `404`, `412`.

##### Headers

A `401` Unauthorized error MUST carry the following header: 

| Header Name      | Type        |  Description |
| :--------------- | :-------:   |  :---------- |
| WWW-Authenticate | string      |  Defines the authentication method that should be used to authenticate.|

##### Body
The body MAY contain a Json Object with more details on the error. See the [error structure](#Error Result).

~~~json
{
  "error": "string",
  "additionalProp1": {}
}
~~~
### Schemas

#### Adapters
TODO : what adapters are really ? 
Json Object :
 
| Name             | Type        | Mandatory |  Description |
| :--------------- | :-------:   | :-------: |  :---------- |
| *type*           | String      | yes       | The adapter type.
| *enabled*        | Boolean     | no        | Default: true. |
| *device-authentication-required* | Boolean | no        | Default: true.
| *ext*            | Json Object | no        | 	Allows arbitrary properties as extension to the ones specified by the Hono API.

#### Resource Limits
Defines a resource limit for a tenant.
Json Object :
 
| Name             | Type        | Mandatory |  Description |
| :--------------- | :-------:   | :-------: |  :---------- |
| *type*           | Integer     | no       | The maximum number of concurrent connections allowed from devices of this tenant. The default value -1 indicates that no limit is set.
| *ext*            | Json Object | no        | 	Allows arbitrary properties as extension to the ones specified by the Hono API.

#### Trusted Certificate
TODO : add a description ? 
Json Object :
 
| Name             | Type         | Mandatory |  Description |
| :--------------- | :-------:    | :-------: |  :---------- |
| *subject-dn*     | String       | yes       | The subject DN of the trusted root certificate in the format defined by RFC 2253.
| *public-key*     | string($byte)| no        | The Base64 encoded binary DER encoding of the trusted root certificate’s public key. Either this property or cert must be set. |
| *cert*           | string($byte)| no        | The Base64 encoded binary DER encoding of the trusted root certificate. Either this property or public-key must be set.
| *algorithm*      | string       | no        | The algorithm used for the public key of the CA. If the cert property is used to provide an X.509 certificate then the algorithm is determined from the certificate and this property is ignored. Otherwise, i.e. if the public-key property is used, this property must be set to the algorithm used, if other than the default.

#### Error Result
This object MAY be returned by the device registry to provide more details on a failure.
Json Object :

| Name             | Type        | Mandatory |  Description |
| :--------------- | :-------:   | :-------: |  :---------- |
| *error*          | string       | yes      | A human readable error message of what went wrong.
