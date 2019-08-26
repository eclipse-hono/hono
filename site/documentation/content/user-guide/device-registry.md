+++
title = "Device Registry"
weight = 205
+++

The Device Registry component provides exemplary implementations of Hono's [Tenant API]({{< relref "/api/tenant" >}}), [Device Registration API]({{< relref "/api/device-registration" >}}) and [Credentials API]({{< relref "/api/credentials" >}}).

<!--more-->
As such it exposes AMQP 1.0 based endpoints for retrieving the relevant information and persists data in the local filesystem. 

In addition, the Device Registry also exposes HTTP resources for managing the contents of the registry according to the [Device Registry HTTP API]({{< relref "/api/management" >}}).

{{% warning %}}
The Device Registry is not intended to be used in production environments. In particular, access to the HTTP resources described below is not restricted to authorized clients only.

The resources have been designed to provide convenient access to the registry's content using command line tools like *curl* or *HTTPie*.
{{% /warning %}}

## Managing Tenants

The following sections describe the resources representing the operations of the Tenant API and how they can be used to manage tenants.
Please refer to the [Device Registry HTTP API]({{< relref "/api/management" >}}) for the specific elements that are explained in detail there.

### Add a Tenant

* URI: `/v1/tenants/${tenantId}`
* Method: `POST`
* Request Headers:
  * (required) `Content-Type`: `application/json` (no other type supported)
* Request Body:
  * (required) A JSON object as specified by [Tenant schema]({{< relref "/api/management" >}}) of the HTTP API specification.
* Status Codes:
  * 201 (Created): Tenant has been added successfully under the resource indicated by `Location` header.
  * 400 (Bad Request): The tenant has not been created because the request was malformed, e .g. because the payload was malformed. The response body may contain hints regarding the cause of the problem.
  * 409 (Conflict): A tenant with the given `tenantId` already exists. The request has not been processed.
* Response Headers:
  * `Location`: The URI under which the newly created resource can be accessed.
  * `ETag`: Version of the resource

**Example**

The following commands add some tenants with different adapter configurations:

Add a tenant that has all adapters set to enabled:

    curl -i -X POST -H 'Content-Type: application/json' http://localhost:28080/v1/tenants/tenantAllAdapters
    
    HTTP/1.1 201 Created
    ETag: becc93d7-ab0f-48ec-ad26-debdf339cbf4
    Location: /v1/tenants/tenantAllAdapters
    Content-Type: application/json; charset=utf-8
    
    {"id": "tenantAllAdapters"}

Add a tenant that can only use the MQTT adapter:

    curl -i -X POST -H 'Content-Type: application/json' --data-binary '{
        "adapters" : [ {
            "type" : "hono-mqtt",
            "enabled" : true
            } ]
      }' http://localhost:28080/v1/tenants/tenantMqttAdapter
    
    HTTP/1.1 201 Created
    ETag: becc93d7-ab0f-48ec-ad26-debdf339cbf4
    Location:  /v1/tenants/tenantMqttAdapter
    Content-Type: application/json; charset=utf-8
    
    {"id": "tenantMqttAdapter"}

### Get configuration details of a Tenant

* URI: `/v1/tenants/${tenantId}`
* Method: `GET`
* Status Codes:
  * 200 (OK): A tenant with the given identifier has been found. The response body contains the tenant data as specified by [Tenant schema]({{< relref "/api/management" >}}) of the HTTP API specification.
  * 404 (Not Found): No tenant with the given identifier is registered.

**Example**

The following command retrieves the details for the tenant `tenantMqttAdapter`:

    curl -i http://localhost:28080/v1/tenants/tenantMqttAdapter
    
    HTTP/1.1 200 OK
    ETag: becc93d7-ab0f-48ec-ad26-debdf339cbf4
    Content-Type: application/json; charset=utf-8
    
    {
         "enabled" : true,
         "adapters" : [ {
             "type" : "hono-mqtt",
             "enabled" : true
         } ]
    }

### Update Tenant

* URI: `/v1/tenants/${tenantId}`
* Method: `PUT`
* Request Headers:
  * (required) `Content-Type`: `application/json` (no other type supported)
* Request Body:
  * (required) A JSON object as specified by [Tenant schema]({{< relref "/api/management" >}}) of the HTTP API specification.
* Status Codes:
  * 204 (No Content): The tenant has been updated successfully.
  * 400 (Bad Request): The tenant has not been updated because the request was malformed, e .g. because the payload was malformed. The response body may contain hints regarding the cause of the problem.
  * 404 (Not Found): The request could not be processed because no tenant with the given identifier exists.

This resource can be used to change the configuration of a particular tenant.

**Example**

The following command disables the MQTT adapter for devices that belong to the tenant `tenantMqttAdapter`:

    curl -i -X PUT -H 'Content-Type: application/json' --data-binary '{
          "adapters" : [ {
              "type" : "hono-mqtt",
              "enabled" : true
              } ]
      }' http://localhost:28080/v1/tenants/tenantMqttAdapter
    
    HTTP/1.1 204 No Content
    ETag: 8919c736-30aa-40ce-a45a-830b90c4cd42
    Content-Length: 0


### Delete Tenant

* URI: `/v1/tenants/${tenantId}`
* Method: `DELETE`
* Status Codes:
  * 204 (No Content): The tenant with the given identifier has been deleted.
  * 404 (Not Found): The request could not be processed because no tenant with the given identifier exists.

**Example**

    curl -i -X DELETE http://localhost:28080/v1/tenants/tenantMqttAdapter
    
    HTTP/1.1 204 No Content
    Content-Length: 0

## Managing Device Registration Information

The following sections describe the resources representing the operations of the [Device Registration API]({{< relref "/api/device-registration" >}}) and how they can be used to manage device registration information.
Please refer to the [Device Registry HTTP API]({{< relref "/api/management" >}}) for the specific elements that are explained in detail there.

### Register Device

* URI: `/v1/devices/${tenantId}/${deviceId}`
* Method: `POST`
* Headers:
  * (required) `Content-Type`: `application/json`
* Request Body:
  * (required) A JSON object as specified by [Device schema]({{< relref "/api/management" >}}) of the HTTP API specification.
* Status Codes:
  * 201 (Created): Device has been registered successfully under resource indicated by `Location` header.
  * 400 (Bad Request): Device has not been registered because the request was malformed, e .g. a required header is missing (the body may contain hints regarding the problem).
  * 409 (Conflict): There already exists a device with the given ID. The request has not been processed.

**Example**

The following command registers a device with ID `4711` for tenant `DEFAULT_TENANT`

    curl -i -X POST -H 'Content-Type: application/json' --data-binary '{
        "ext": {
            "ep": "IMEI4711"
        }
    }' http://localhost:28080/v1/devices/DEFAULT_TENANT/4711

The response will contain a `Location` header containing the resource path created for the device. In this example it will look
like this:

    HTTP/1.1 201 Created
    Location: /v1/devices/DEFAULT_TENANT/4711
    ETag: becc93d7-ab0f-48ec-ad26-debdf339cbf4
    Content-Type: application/json; charset=utf-8
    
    {"id": "4711"}

### Read Device

* URI: `/v1/devices/${tenantId}/${deviceId}`
* Method: `GET`
* Status Codes:
  * 200 (OK): A device with the given identifier has been found. The response body contains the registration information as specified by [Device schema]({{< relref "/api/management" >}}) of the HTTP API specification.
  * 404 (Not Found): No device with the given identifier is registered for the given tenant.

**Example**

The following command retrieves registration data for device `4711`:

    curl -i http://localhost:28080/v1/devices/DEFAULT_TENANT/4711
    
    HTTP/1.1 200 OK
    Content-Type: application/json; charset=utf-8

    {
        "enabled": true,
        "ext": {
            "ep": "IMEI4711"
        }
    }

### Update Device

* URI: `/devices/v1/${tenantId}/${deviceId}`
* Method: `PUT`
* Headers:
  * (required) `Content-Type`: `application/json`
* Parameters (encoded as a JSON object in the request body):
* Request Body:
  * (required) A JSON object as specified by [Device schema]({{< relref "/api/management" >}}) of the HTTP API specification. All existing registration information will be replaced by the data provided in the object.
* Status Codes:
  * 204 (No Content): Device registration data has been updated.
  * 400 (Bad Request): Device registration has not been updated because the request was malformed, e .g. a required header is missing (the body may contain hints regarding the problem).
  * 404 (Not Found): No device with the given identifier is registered for the given tenant.

**Example**

    curl -i -X PUT -H 'Content-Type: application/json' --data-binary '{
        "ext": {
            "ep": "IMEI4711",
            "psk-id": "psk4711"
        }
    }' http://localhost:28080/v1/devices/DEFAULT_TENANT/4711
    
    HTTP/1.1 204 No Content
    Content-Length: 0

### Delete Device

* URI: `/v1/devices/${tenantId}/${deviceId}`
* Method: `DELETE`
* Status Codes:
  * 204 (No Content): Device registration has been deleted.
  * 404 (Not Found): No device with the given identifier is registered for the given tenant.

**Example**

    curl -i -X DELETE http://localhost:28080/v1/devices/DEFAULT_TENANT/4711
    
    HTTP/1.1 204 No Content
    Content-Length: 0
    
## Managing Credentials

The following sections describe the resources representing the operations of the Credentials API and how they can be used to manage credentials for devices.
Please refer to the [Device Registry HTTP API]({{< relref "/api/management" >}}) for the specific elements that are explained in detail there.

### Update Credentials for a Device

* URI: `/v1/credentials/${tenantId}/${deviceId}`
* Method: `PUT`
* Request Headers:
  * (required) `Content-Type`: `application/json` (no other type supported)
* Request Body:
  * (required) A JSON object as specified by [Credentials schema]({{< relref "/api/management" >}}) of the HTTP API specification.
* Status Codes:
  * 204 (No Content): Credentials have been updated successfully.
  * 400 (Bad Request): The credentials have not been added because the request was malformed, e .g. because the payload did not contain required values. The response body may contain hints regarding the cause of the problem.
* Response Headers:
  * `ETag`: Version of the resource

**Example**

The following command adds some `hashed-password` credentials from a given plain text password for device `4710` using authentication identifier `sensor10`: 

    curl -i -X PUT -H 'Content-Type: application/json' --data-binary '[{
      "type": "hashed-password",
      "auth-id": "sensor10",
      "secrets": [{
          "pwd-plain": "mylittlesecret"
      }]
    }]' http://localhost:28080/v1/credentials/DEFAULT_TENANT/4710
      
    HTTP/1.1 204 No Content
    ETag: becc93d7-ab0f-48ec-ad26-debdf339cbf4x
    
This uses a convenient option which lets the Device Registry do the hashing of the password. The following command retrieves the credentials that are stored by the Device Registry as a result of the command above: 
    
    
    curl -i http://localhost:28080/v1/credentials/DEFAULT_TENANT/4710

    HTTP/1.1 200 OK
    Content-Type: application/json; charset=utf-8
    ETag: becc93d7-ab0f-48ec-ad26-debdf339cbf4x

    [{
      "type": "hashed-password",
      "auth-id": "sensor10",
      "enabled": true,
      "secrets": [
        {
          "pwd-hash": "$2a$10$uc.qVDwXeDRE1DWa1sM9iOaY9wuevjfALGMtXmHKP.SJDEqg0q7M6",
          "hash-function": "bcrypt"
        }
      ]
    }]
    
The following commands add some `hashed-password` credentials for device `4720` using authentication identifier `sensor20`:

    PWD_HASH=$(echo -n "mylittlesecret" | openssl dgst -binary -sha512 | base64 -w 0)
    curl -i -X PUT -H 'Content-Type: application/json' --data-binary '[{
        "type": "hashed-password",
        "auth-id": "sensor20",
        "secrets": [{
            "hash-function" : "sha-512",
            "pwd-hash": "'$PWD_HASH'"
        }]
      }]' http://localhost:28080/v1/credentials/DEFAULT_TENANT/4720
    
    HTTP/1.1 204 No Content
    ETag: 02c99fb5-af8c-409f-8520-b405e224b27f

The following command adds an expiration date to the `hashed-password` credentials for authentication identifier `sensor20`:

    PWD_HASH=$(echo -n "mylittlesecret" | openssl dgst -binary -sha512 | base64 -w 0)
    curl -i -X PUT -H 'Content-Type: application/json' --data-binary '{
        "device-id": "4720",
        "type": "hashed-password",
        "auth-id": "sensor20",
        "secrets": [{
            "hash-function" : "sha-512",
            "pwd-hash": "'$PWD_HASH'",
            "not-after": "2018-01-01T00:00:00+01:00"
        }]
    }' http://localhost:28080/v1/credentials/DEFAULT_TENANT/4720

    HTTP/1.1 204 No Content
    ETag: becc93d7-ab0f-48ec-ad26-debdf339cbf4x

Multiple credentials of different type can be registered for the same authentication identifier.
The following commands add `psk` credentials for the same device `4720` using authentication identifier `sensor20`:

    SHARED_KEY=$(echo -n "TheSharedKey" | base64 -w 0)
    curl -i -X PUT -H 'Content-Type: application/json' --data-binary '[
    {
        "type": "hashed-password",
        "auth-id": "sensor20",
        "secrets": [{
            "hash-function" : "bcrypt",
            "pwd-hash": "$2a$10$uc.qVDwXeDRE1DWa1sM9iOaY9wuevjfALGMtXmHKP.SJDEqg0q7M6"
        }]
    },
    {
       "device-id": "4720",
       "type": "psk",
       "auth-id": "sensor20",
       "secrets": [{
         "key" : "'$SHARED_KEY'"
         }]
      }]' http://localhost:28080/v1/credentials/DEFAULT_TENANT/4720
    
    HTTP/1.1 204 No Content
    ETag: 122c971a-505a-4336-8f7d-640360e909bc


The following command deletes all credentials for device `4710`:

    curl -i -X PUT -H 'Content-Type: application/json' --data-binary '[]' http://localhost:28080/v1/credentials/DEFAULT_TENANT/4710

    HTTP/1.1 204 No Content
    ETag: d383ba4d-1853-4d03-b322-b7ff5af05ae2

### Get all Credentials for a Device

* URI: `/v1/credentials/${tenantId}/${deviceId}`
* Method: `GET`
* Status Codes:
  * 200 (OK): Credentials for the device have been found, body contains the credentials. The response body contains the registration information as specified by [Credentials schema]({{< relref "/api/management" >}}) of the HTTP API specification.
  * 404 (Not Found): No credentials for the device are registered.

**Example**

The following command retrieves credentials for device `4720`:

    curl -i http://localhost:28080/v1/credentials/DEFAULT_TENANT/4720

    HTTP/1.1 200 OK
    Content-Type: application/json; charset=utf-8
    ETag: f3449b77-2c84-4b09-8d04-2b305876e0cb
    
    {[
        {
            "auth-id": "sensor20",
            "enabled": true,
            "secrets": [
                {
                    "hash-function": "sha-512",
                    "pwd-hash": "tnxz0zDFs+pJGdCVSuoPE4TnamXsfIjBEOb0rg3e9WFD9KfbCkoRuwVZKgRWInfqp87kCLsoV/HEwdJwgw793Q=="
                }
            ],
            "type": "hashed-password"
        },
        {
            "auth-id": "sensor20",
            "enabled": true,
            "secrets": [
                {
                    "key": "VGhlU2hhcmVkS2V5"
                }
            ],
            "type": "psk"
        }
    ]}
