+++
title = "Device Registry - RESTful resources"
weight = 370
+++

In addition to the AMQP 1.0 based API endpoints, the Device Registry 
also exposes RESTful resources for invoking operations.

<!--more-->
The following APIs are accessible via HTTP:

* [Device Registration API]({{< relref "api/Device-Registration-API.md" >}})
* [Credentials API]({{< relref "api/Credentials-API.md" >}})

Please note that these mappings to RESTful resources are **not** part of the *official* definition of the APIs but have
been implemented for convenient access to the registry using command line tools like *curl* or *HTTPie*. 
 

## Using the Registration API via HTTP

The following sections describe the resources representing the operations of the [Device Registration API]({{< relref "api/Device-Registration-API.md" >}}) and how they can be used to e.g. register a device.

### Register Device

* URI: `/registration/${tenantId}`
* Method: `POST`
* Headers:
  * (required) `Content-Type`: `application/json`
* Parameters (encoded as a JSON object in the request body):
  * (required) `device-id`: The ID of the device to register.
  * (optional) Arbitrary key/value pairs containing additional data to be registered with the device.
* Status Codes:
  * 201 (Created): Device has been registered successfully under resource indicated by `Location` header.
  * 400 (Bad Request): Device has not been registered because the request was malformed, e .g. a required header is missing (the body may contain hints regarding the problem).
  * 409 (Conflict): There already exists a device with the given ID. The request has not been processed.

**Example**

The following command registers a device with ID `4711`

    $ curl -i -X POST -H 'Content-Type: application/json' --data-binary '{
        "device-id": "4711",
        "ep": "IMEI4711"
    }' http://localhost:28080/registration/DEFAULT_TENANT

The response will contain a `Location` header containing the resource path created for the device. In this example it will look
like this:

    HTTP/1.1 201 Created
    Location: /registration/DEFAULT_TENANT/4711
    Content-Length: 0

### Read Registration

* URI: `/registration/${tenantId}/${deviceId}`
* Method: `GET`
* Status Codes:
  * 200 (OK): Device has been found, body contains registration data.
  * 404 (Not Found): No device with the given identifier is registered for the given tenant.

**Example**

The following command retrieves registration data for device `4711`:

    $ curl -i http://localhost:28080/registration/DEFAULT_TENANT/4711

The response will look similar to this:

    HTTP/1.1 200 OK
    Content-Type: application/json; charset=utf-8
    Content-Length: 35

    {
      "data" : {
         "enabled": true,
         "ep": "IMEI4711"
      },
      "device-id" : "4711"
    }

### Update Registration

* URI: `/registration/${tenantId}/${deviceId}`
* Method: `PUT`
* Headers:
  * (required) `Content-Type`: `application/json`
* Parameters (encoded as a JSON object in the request body):
  * (optional) Arbitrary key/value pairs containing additional data to be registered with the device. The existing key/value pairs will be replaced with these key/values.
* Status Codes:
  * 204 (No Content): Device registration data has been updated.
  * 400 (Bad Request): Device registration has not been updated because the request was malformed, e .g. a required header is missing (the body may contain hints regarding the problem).
  * 404 (Not Found): No device with the given identifier is registered for the given tenant.

**Example**

    $ curl -i -X PUT -H 'Content-Type: application/json' --data-binary '{
        "ep": "IMEI4711",
        "psk-id": "psk4711"
    }' http://localhost:28080/registration/DEFAULT_TENANT/4711

The response will look similar to this:

    HTTP/1.1 204 No Content
    Content-Length: 0

### Delete Registration

* URI: `/registration/${tenantId}/${deviceId}`
* Method: `DELETE`
* Status Codes:
  * 204 (No Content): Device registration has been deleted.
  * 404 (Not Found): No device with the given identifier is registered for the given tenant.

**Example**

    $ curl -i -X DELETE http://localhost:28080/registration/DEFAULT_TENANT/4711

The response will look similar to this:

    HTTP/1.1 204 No Content
    Content-Length: 0
    
## Using the Credentials API via HTTP


The following sections describe the resources representing the REST operations of the Credentials API and how they can be used to e.g. add credentials for a device.
Please refer to the [Credentials API]({{< relref "api/Credentials-API.md" >}}) for the specific elements that are explained in detail there.

### Add Credentials for a Device

* URI: `/credentials/${tenantId}`
* Method: `POST`
* Request Headers:
  * (required) `Content-Type`: `application/json` (no other type supported)
* Request Body (encoded as a JSON object):
  * (required) `auth-id`: The identity that the device will use for authentication.
  * (required) `type`: The type of the credentials to add.
  * (required) `device-id`: The ID of the device to add the credentials for.
  * (required) `secrets`: The secrets of the credentials to add. This is a JSON array and must contain at least one element. The content of each element is defined in the [Credentials API]({{< relref "api/Credentials-API.md" >}}).
* Status Codes:
  * 201 (Created): Credentials have been added successfully under the resource indicated by `Location` header.
  * 400 (Bad Request): The credentials have not been updated because the request was malformed, e .g. because the payload did not contain required values. The response body may contain hints regarding the cause of the problem.
  * 409 (Conflict): Credentials of the given type for the given *auth-id* already exist for the tenant. The request has not been processed.
* Response Headers:
  * `Location`: The URI under which the newly created resource can be accessed.

**Example**

The following commands add some `hashed-password` credentials for device `4720` using authentication identifier `sensor20`:

    $ PWD_HASH=$(echo -n "mylittlesecret" | openssl dgst -binary -sha512 | base64 -w 0)
    $ curl -i -X POST -H 'Content-Type: application/json' --data-binary '{
        "device-id": "4720",
        "type": "hashed-password",
        "auth-id": "sensor20",
        "secrets": [{
            "hash-function" : "sha-512",
            "pwd-hash": "'$PWD_HASH'"
        }]
      }' http://localhost:28080/credentials/DEFAULT_TENANT

The response will look like this:

    HTTP/1.1 201 Created
    Location: /credentials/DEFAULT_TENANT/sensor20/hashed-password
    Content-Length: 0

Multiple credentials of different type can be registered for the same authentication identifier.
The following commands add `psk` credentials for the same device `4720` using authentication identifier `sensor20`:

    $ SHARED_KEY=$(echo -n "TheSharedKey" | base64 -w 0)
    $ curl -i -X POST -H 'Content-Type: application/json' --data-binary '{
       "device-id": "4720",
       "type": "psk",
       "auth-id": "sensor20",
       "secrets": [{
         "key" : "'$SHARED_KEY'"
         }]
      }' http://localhost:28080/credentials/DEFAULT_TENANT

The response will look like this:

    HTTP/1.1 201 Created
    Location: /credentials/DEFAULT_TENANT/sensor20/psk
    Content-Length: 0


### Get Credentials by Authentication Identifier and Type

* URI: `/credentials/${tenantId}/${authId}/${type}`
* Method: `GET`
* Status Codes:
  * 200 (OK): Credentials for the given parameters have been found, body contains the credentials data.
  * 404 (Not Found): No credentials for the given parameters are registered for the given tenant.

**Example**

The following command retrieves credentials data of type `hashed-password` for the authentication identifier `sensor20`:

    $ curl -i http://localhost:28080/credentials/DEFAULT_TENANT/sensor20/hashed-password

The response will look similar to this:

    HTTP/1.1 200 OK
    Content-Length: 268
    Content-Type: application/json; charset=utf-8
    
    {
        "auth-id": "sensor20",
        "device-id": "4720",
        "enabled": true,
        "secrets": [
            {
                "hash-function": "sha-512",
                "pwd-hash": "tnxz0zDFs+pJGdCVSuoPE4TnamXsfIjBEOb0rg3e9WFD9KfbCkoRuwVZKgRWInfqp87kCLsoV/HEwdJwgw793Q=="
            }
        ],
        "type": "hashed-password"
    }


### Get all Credentials for a Device

* URI: `/credentials/${tenantId}/${deviceId}`
* Method: `GET`
* Status Codes:
  * 200 (OK): Credentials for the device have been found, body contains the credentials.
  The body differs from the body for a specific type since it may contain an arbitrary number of credentials. It contains a property `total` indicating the total number of credentials returned. The credentials are containing in property `credentials`.
  * 404 (Not Found): No credentials for the device are registered.

**Example**

The following command retrieves credentials for device `4720`:

    $ curl -i http://localhost:28080/credentials/DEFAULT_TENANT/4720

The response will look similar to this:

    HTTP/1.1 200 OK
    Content-Length: 491
    Content-Type: application/json; charset=utf-8
    
    {
        "credentials": [
            {
                "auth-id": "sensor20",
                "device-id": "4720",
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
                "device-id": "4720",
                "enabled": true,
                "secrets": [
                    {
                        "key": "VGhlU2hhcmVkS2V5"
                    }
                ],
                "type": "psk"
            }
        ],
        "total": 2
    }


### Update Credentials

* URI: `/credentials/${tenantId}/${authId}/${type}`
* Method: `PUT`
* Request Headers:
  * (required) `Content-Type`: `application/json` (no other type supported)
* Request Body (encoded as a JSON object):
  * (required) `auth-id`: The identity that the device uses for authentication (MUST match the value of the corresponding URI path parameter).
  * (required) `type`: The type of the credentials to update (MUST match the value of the corresponding URI path parameter).
  * (required) `device-id`: The ID of the device that the credentials belong to.
  * (required) `secrets`: The secrets of the credentials to update. This is a JSON array and must contain at least one element. The content of each element is defined in the [Credentials API]({{< relref "api/Credentials-API.md" >}}).
* Status Codes:
  * 204 (No Content): The credentials have been updated successfully.
  * 400 (Bad Request): The credentials have not been updated because the request was malformed, e .g. because the payload did not contain required values or the type and auth-id in the payload do not match the path parameters. The response body may contain hints regarding the cause of the problem.
  * 404 (Not Found): The request could not be processed because there exist no credentials of the given type and authentication identifier.

This resource can be used to change values of a particular set of credentials. However, it cannot be used to change the type or authentication identifier of the credentials.

**Example**

The following command adds an expiration date to the `hashed-password` credentials for authentication identifier `sensor20`:

    $ PWD_HASH=$(echo -n "mylittlesecret" | openssl dgst -binary -sha512 | base64 -w 0)
    $ curl -i -X PUT -H 'Content-Type: application/json' --data-binary '{
        "device-id": "4720",
        "type": "hashed-password",
        "auth-id": "sensor20",
        "secrets": [{
            "hash-function" : "sha-512",
            "pwd-hash": "'$PWD_HASH'",
            "not-after": "2018-01-01T00:00:00+01:00"
        }]
    }' http://localhost:28080/credentials/DEFAULT_TENANT/sensor20/hashed-password

The response will look like this:

    HTTP/1.1 204 No Content
    Content-Length: 0


### Delete Credentials by Type and Authentication Identifier

* URI: `/credentials/${tenantId}/${authId}/${type}`
* Method: `DELETE`
* Status Codes:
  * 204 (No Content): The Credentials with the given identifier and type have been deleted.
  * 404 (Not Found): No credentials matching the criteria have been found.

**Example**

    $ curl -i -X DELETE http://localhost:28080/credentials/DEFAULT_TENANT/sensor20/hashed-password

The response will look similar to this:

    HTTP/1.1 204 No Content
    Content-Length: 0


### Delete all Credentials of a Device

* URI: `/credentials/${tenantId}/${deviceId}`
* Method: `DELETE`
* Status Codes:
  * 204 (No Content): All Credentials for the device have been deleted. There is no payload in the response.
  * 404 (Not Found): No credentials have been found for the device and the given tenant.

Removes all credentials registered for a particular device.

**Example**

    $ curl -i -X DELETE http://localhost:28080/credentials/DEFAULT_TENANT/4720

The response will look similar to this:

    HTTP/1.1 204 No Content
    Content-Length: 0
