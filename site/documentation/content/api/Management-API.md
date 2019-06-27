## Eclipse Hono™ Device Registry API
This API defines how to manage *Tenants*, *Devices*, and *Credentials*.
It acts as a common basis which all Hono device registries should
implement.

## Required APIs

All operations, except the `tenants` resource are required. The tenant
management might be outside of the scope of the device registry and
managed by a higher level system. In this case all calls should simply
return `404`. However, if the `tenants` resource is implemented, then all
operations of it must be implemented.

## Tenant Management API 

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
If the tenant is successfully obtained, the return code should be `200`.

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
If the tenant is updated successfully, the return code should be `201`. The body will be empty.

##### Headers
| Header Name      | Type        |  Description |
| :--------------- | :-------:   |  :---------- |
| ETag             | string      | The version of the resource |

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

## Device Management API 

All the management operations are accessible through the `/devices` HTTP endpoint.

### Create a device

An HTTP `POST` method request on the device endpoint triggers the device creation in the device registry.

*NOTE* : when a device is created, an empty set of credentials MUST be created for this device. 

#### URL pattern
The device id value can be optionally appended to the URL : `devices/{tenantID}/{deviceId}`.
The tenant id parameter MUST be given.
If no device id parameter is given, the device will be created with an auto-generated ID.

#### Request Format
The body should be of type `application/json`.

##### Body
The following table provides an overview of the properties that can be set in the request :

| Name             | Type        | Mandatory |  Description |
| :--------------- | :-------:   | :-------: |  :---------- |
| *enabled*        | Boolean     | no        | Default: true. |
| *defaults*       | Json Object | no        | Defaults for properties defined on the tenant and device level.
| *via*            | Json Array  | no        | The device IDs of the gateways this device is assigned to.
| *ext*            | Json Object | no        | Arbitrary properties as extension to the ones specified by the Hono API. |

Example value:
~~~json
{
  "enabled": true,
  "defaults": {
    "additionalProp1": {}
  },
  "via": [
    "string"
  ],
  "ext": {
    "additionalProp1": {}
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
If the device is created successfully, the return code should be `201`.

##### Headers
| Header Name      | Type        |  Description |
| :--------------- | :-------:   |  :---------- |
| Location         | string      | URL to the resource |
| ETag             | string      | The version of the resource |

##### Body
The body should be a Json Object:
| Field Name       | Type        |  Description |
| :--------------- | :-------:   |  :---------- |
| id               | string      | The created device ID. |

Example value:
~~~json
{
  "id": "string"
}
~~~

#### Failure Response
If the device could not be created, the return code can be one of the following : `400`, `401`, `403`, `409`.


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

### Retrieve Device Information

Device information is retrieved with the `GET` method. 

#### URL pattern
The device id parameter MUST should appended to the URL : `devices/{deviceId}`.

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
If the device information is successfully obtained, the return code should be `200`.

##### Headers
| Header Name      | Type        |  Description |
| :--------------- | :-------:   |  :---------- |
| ETag             | string      | The version of the resource |

##### Body
The body MUST be of type `application/json`. Here is the expected Json Object fields:
| Name             | Type        |  Description |
| :--------------- | :-------:   |  :---------- |
| *enabled*        | Boolean     | Device state. |
| *defaults*       | Json Object | Defaults for properties defined on the tenant and device level.
| *via*            | Json Array  | The device IDs of the gateways this device is assigned to.
| *ext*            | Json Object | Arbitrary properties as extension to the ones specified by the Hono API. |


Example value:
~~~json
{
  "enabled": true,
  "defaults": {
    "additionalProp1": {}
  },
  "via": [
    "string"
  ],
  "ext": {
    "additionalProp1": {}
  }
}
~~~

#### Failure Response
If the device could not be retrieved, the return code can be one of the following : `400`, `401`, `404`.

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

### Update a Device information

An HTTP `PUT` method request on the devices endpoint will update the device details in the device registry.

#### URL pattern
The device id value MUST be appended to the URL : `devices/{deviceId}`.

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
| *defaults*       | Json Object | no        | Defaults for properties defined on the tenant and device level.
| *via*            | Json Array  | no        | The device IDs of the gateways this device is assigned to.
| *ext*            | Json Object | no        | Arbitrary properties as extension to the ones specified by the Hono API. |

Example value:
~~~json
{
  "enabled": true,
  "defaults": {
    "additionalProp1": {}
  },
  "via": [
    "string"
  ],
  "ext": {
    "additionalProp1": {}
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
| 404      | Device not found.   |
| 412      | The given resource version does not match current. This can only happen when the request header If-Match was set. |

#### Success Response
If the device is updated successfully, the return code should be `201`. The body will be empty.

##### Headers
| Header Name      | Type        |  Description |
| :--------------- | :-------:   |  :---------- |
| ETag             | string      | The version of the resource |

#### Failure Response
If the device could not be updated, the return code can be one of the following : `400`, `401`, `403`, `404`, `412`.


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

### Delete a Device information

An HTTP `DELETE` method request on the device endpoint will remove the device details in the device registry.

*NOTE* : when a device is deleted, all the associated credentials MUST be removed. 

#### URL pattern
The device id value MUST be appended to the URL : `devices/{deviceId}`.

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
| 404      | Device not found.   |
| 412      | The given resource Version does not match current. This can only happen when the request header If-Match was set. |

#### Success Response
If the device is deleted successfully, the return code should be `204`. No additional will be provided. 

#### Failure Response
If the device could not be deleted, the return code can be one of the following : `401`, `403`, `404`, `412`.

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

#### Error Result
This object MAY be returned by the device registry to provide more details on a failure.
Json Object :

| Name             | Type        | Mandatory |  Description |
| :--------------- | :-------:   | :-------: |  :---------- |
| *error*          | string       | yes      | A human readable error message of what went wrong.

## Credentials Management API

#### URL pattern
All the operations for credentials follows the same URL pattern.
The tenant id and Device id values MUST be appended to the URL : `credentials/{tenantId}/{deviceId}`.

### Get Credentials for a device

An HTTP `GET` method request on the credentials endpoint will retrieve all the credentials associated with a device.

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
If the credentials are successfully obtained, the return code should be `200`.

##### Headers
| Header Name      | Type        |  Description |
| :--------------- | :-------:   |  :---------- |
| ETag             | string      | The version of the resource |

##### Body
The body MUST be of type `application/json`. The response object is an array of [typed credentials](#Typed Credential) :
Below are the fields contained in a secret, regardless of it's type : 
| Name             | Type        |  Description |
| :--------------- | :-------:   |  :---------- |
| *type*           | string      | The credential type. See [typed credentials](#Typed Credential) |
| *auth-id*        | string      | The id used by the device for authentication with this credential. |
| *enabled*        | Boolean     | Whether the credential is active or not.
| *ext*            | Json Object | Arbitrary properties as extension to the ones specified by the Hono API. |
| *secrets*        | Json Array  | The array of secrets, according to the type field.


Example value:
~~~json
[
  {
    "type": "string",
    "auth-id": "string",
    "enabled": true,
    "ext": {
      "additionalProp1": {}
    },
    "secrets": [
      {
        "enabled": true,
        "not-before": "2019-06-26T13:09:48.184Z",
        "not-after": "2019-06-26T13:09:48.184Z",
        "comment": "string",
        "hash-function": "sha-512",
        "pwd-hash": "string",
        "salt": "string"
      }
    ]
  },
  {
    "type": "string",
    "auth-id": "string",
    "enabled": true,
    "ext": {
      "additionalProp1": {}
    },
    "secrets": [
      {
        "enabled": true,
        "not-before": "2019-06-26T13:09:48.184Z",
        "not-after": "2019-06-26T13:09:48.184Z",
        "comment": "string",
        "key": "string"
      }
    ]
  },
  {
    "type": "string",
    "auth-id": "string",
    "enabled": true,
    "ext": {
      "additionalProp1": {}
    },
    "secrets": [
      {
        "enabled": true,
        "not-before": "2019-06-26T13:09:48.184Z",
        "not-after": "2019-06-26T13:09:48.184Z",
        "comment": "string"
      }
    ]
  }
]
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

### Set Credentials for a device

An HTTP `PUT` method request on the credentials endpoint will set the credentials associated with a device.

#### Request Format
The request should be of type `application/json`.

##### Headers
| Header Name      | Type        |  Description |
| :--------------- | :-------:   |  :---------- |
| ETag             | string      | The version of the resource |

##### Body
The body MUST be of type `application/json`. It must contains is an array of [typed credentials](#Typed Credential) :
Below are the fields contained in a secret, regardless of it's type : 
| Name             | Type        |  Description |
| :--------------- | :-------:   |  :---------- |
| *type*           | string      | The credential type. See [typed credentials](#Typed Credential) |
| *auth-id*        | string      | The id used by the device for authentication with this credential. |
| *enabled*        | Boolean     | Whether the credential is active or not.
| *ext*            | Json Object | Arbitrary properties as extension to the ones specified by the Hono API. |
| *secrets*        | Json Array  | The array of secrets, that **MUST** match the type field.

Example Request value:
~~~json
[
  {
    "type": "string",
    "auth-id": "string",
    "enabled": true,
    "ext": {
      "additionalProp1": {}
    },
    "secrets": [
      {
        "enabled": true,
        "not-before": "2019-06-26T14:15:09.004Z",
        "not-after": "2019-06-26T14:15:09.004Z",
        "comment": "string",
        "hash-function": "sha-512",
        "pwd-hash": "string",
        "salt": "string"
      }
    ]
  },
  {
    "type": "string",
    "auth-id": "string",
    "enabled": true,
    "ext": {
      "additionalProp1": {}
    },
    "secrets": [
      {
        "enabled": true,
        "not-before": "2019-06-26T14:15:09.004Z",
        "not-after": "2019-06-26T14:15:09.004Z",
        "comment": "string",
        "key": "string"
      }
    ]
  },
  {
    "type": "string",
    "auth-id": "string",
    "enabled": true,
    "ext": {
      "additionalProp1": {}
    },
    "secrets": [
      {
        "enabled": true,
        "not-before": "2019-06-26T14:15:09.004Z",
        "not-after": "2019-06-26T14:15:09.004Z",
        "comment": "string"
      }
    ]
  }
]


~~~

#### Expected Response Overview
| Response | Reason                    |
| -------- | ------                    |
| 204      | Object updated. |
| 400      | Malformed request. |
| 401      | Authentication error. |
| 403      | Operation not allowed. If the user does not have read access for this object, then `404` will be returned instead. |
| 404      | Credential not found.   |
| 412      | The given resource Version does not match current. This can only happen when the request header If-Match was set. |



#### Success Response
If the credentials are successfully saved, the return code should be `204`.

##### Headers
| Header Name      | Type        |  Description |
| :--------------- | :-------:   |  :---------- |
| Etag             | string      | The new resource version |


#### Failure Response
If the tenant could not be retrieved, the return code can be one of the following : `400`, `401`, `404`.

##### Headers
A `401` Unauthorized error MUST carry the following header: 

| Header Name      | Type        |  Description |
| :--------------- | :-------:   |  :---------- |
| WWW-Authenticate | string      |  Defines the authentication method that should be used to authenticate.|

##### Body
The body MAY contain a Json Object with more details on the error. See the [error structure](#Error Result).


### Schemas

#### Typed Credential
A generic credential that can contains different types of secrets.
| Name             | Type        |  Description |
| :--------------- | :-------:   |  :---------- |
| *type*           | string      | The credential type. |
| *auth-id*        | string      | The id used by the device for authentication with this credential. |
| *enabled*        | Boolean     | Whether the credential is active or not.
| *ext*            | Json Object | Arbitrary properties as extension to the ones specified by the Hono API. |
| *secrets*        | Json Array  | The array of secrets. | 
The secrets contained in the `secrets` field an be either [Password Secret](#Password Secret), [PSK Secret](#PSK Secret) or [X509 Certificate Secret](#X509 Certificate Secret).
The `type` field and the nature of secrets stored in the `secrets` field **MUST** match.

#### Password Secret
| Name             | Type        |  Description |
| :--------------- | :-------:   |  :---------- |
| *enabled*        | Boolean     | Whether the secret is active or not.
| *not-before*     | string      | The date and time from which the secret is valid. |
| *not-after*      | string      | The date and time from which the secret is expired. |
| *comment*        | string      | Arbitrary comment. |
| *hash-function*  | string      | The hashing algorithm used to hash the password.
| *pwd-hash*       | string      | The result of the hashed password.
| *salt*           | string      | The salt used in the hashing process.

#### PSK Secret
| Name             | Type        |  Description |
| :--------------- | :-------:   |  :---------- |
| *enabled*        | Boolean     | Whether the secret is active or not.
| *not-before*     | string      | The date and time from which the secret is valid. |
| *not-after*      | string      | The date and time from which the secret is expired. |
| *comment*        | string      | Arbitrary comment. |
| *key*            | string      | The pre shared key value.

#### X509 Certificate Secret
| Name             | Type        |  Description |
| :--------------- | :-------:   |  :---------- |
| *enabled*        | Boolean     | Whether the secret is active or not.
| *not-before*     | string      | The date and time from which the secret is valid. |
| *not-after*      | string      | The date and time from which the secret is expired. |
| *comment*        | string      | Arbitrary comment. |

#### Error Result
This object MAY be returned by the device registry to provide more details on a failure.
Json Object :

| Name             | Type        | Mandatory |  Description |
| :--------------- | :-------:   | :-------: |  :---------- |
| *error*          | string       | yes      | A human readable error message of what went wrong.
