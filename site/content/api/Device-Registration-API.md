+++
title = "Device Registration API"
weight = 410
+++

The *Device Registration API* is used to make Hono aware of devices that will connect to the service and send telemetry data.
It can be used by *Protocol Adapters* to register devices that are not directly connected to Hono using an AMQP 1.0 connection.
*Solutions* and other consumers may use the API to obtain information about a single device that is registered to Hono.
<!--more-->

The Device Registration API is defined by means of AMQP 1.0 message exchanges, i.e. a client needs to connect to Hono using an AMQP 1.0 client in order to invoke operations of the API as described in the following sections.

# Operations

The operations described in the following sections can be used by clients to manage device registration information. In real world scenarios the provisioning of devices will most likely be an orchestrated process spanning multiple components of which Hono will only be one.

Conducting and orchestrating the overall provisioning process is not in scope of Hono. However, Hono's device registration API can easily be used as part of such an overall provisioning process.

## Preconditions

The preconditions for performing any of the operations are as follows:

1. Client has established an AMQP connection with Hono.
2. Client has established an AMQP link in role *sender* with Hono using target address `registration/${tenant_id}`. This link is used by the client to send registration commands to Hono.
3. Client has established an AMQP link in role *receiver* with Hono using source address `registration/${tenant_id}/${reply-to}` where *reply-to* may be any arbitrary string chosen by the client. This link is used by the client to receive responses to the registration requests it has sent to Hono. This link's source address is also referred to as the *reply-to* address for the request messages.

## Delivery States

Hono uses the following AMQP message delivery states when receiving request messages from clients:

| Delivery State | Description |
| :------------- | :---------- |
| *ACCEPTED*     | Indicates that Hono has successfully received and accepted the request for processing. |
| *REJECTED*     | Indicates that Hono has received the request but was not able to process it. The *error* field contains information regarding the reason why. Clients should not try to re-send the request using the same message properties in this case. |

## Register Device

**Message Flow**

*TODO* add sequence diagram

**Request Message Format**

The following table provides an overview of the properties a client needs to set on a *register device* message.

| Name | Mandatory | Location | Type | Description |
| :--- | :-------: | :------- | :-------- | :---------- |
| *message-id* | yes | *properties* | UTF-8 *string* | MUST contain the message ID used to match incoming message with the response message containing result of execution. |
| *content-type* | no | *properties* | *symbol* | MUST be set to `application/json; charset=utf-8` if the message has a payload containing additional key/value pairs to be registered with the device. |
| *reply-to* | yes | *properties* | UTF-8 *string* | MUST contain the address that the client expects replies being sent to. This address MUST be the same as the source address used for establishing the client's receive link (see preconditions). |
| *correlation-id* | no | *properties* | *message-id* | MAY contain an ID used to correlate a response message to the original request. If set, it is used as the *correlation-id* property in the response, otherwise the value of the *message-id* property is used. | 
| *device_id* | yes | *application-properties* | UTF-8 *string* | MUST contain the ID of the device. |
| *action* | yes | *application-properties* | UTF-8 *string* | MUST be set to `register`. |

The request message MAY include payload as defined in the `Payload Format` section below.

The key/value pairs provided in the payload may be useful e.g. for mapping *technical* identifiers (used when authenticating the device) to the *logical* identifier used in Hono (as indicated by the *device_id* property).
 
**Response Message Format**

| Name | Mandatory | Location | Type | Description |
| :--- | :-------: | :------- | :-------- | :---------- |
| *correlation-id* | yes | *properties* | *message-id* | Contains the *message-id* (or the *correlation-id*, if specified) of the request message that this message represents the response for. |
| *device_id* | yes | *application-properties* | UTF-8 *string* | Contains the ID of the registered device. |
| *tenant_id* | yes | *application-properties* | UTF-8 *string* | Contains the tenant for which the device has been registered. |
| *status* | yes | *application-properties* | *int* | Contains the status code indicating the outcome of the operation. See below for possible values. |

The response may contain the following status codes:

| Code | Description |
| :--- | :---------- |
| *201* | Created, the device has been successfully registered. |
| *409* | Conflict, there already exists a device registration for the given *device_id* within this tenant. |

For status codes indicating an error (codes in the `400 - 499` range) the message body MAY contain a detailed description of the error that occurred.

## Deregister device

**Message Flow**

*TODO* add sequence diagram

**Request Message Format**

The following table provides an overview of the properties a client needs to set on an *Device Registration* message.

| Name | Mandatory | Location | Type | Description |
| :--- | :-------: | :------- | :-------- | :---------- |
| *message-id* | yes | *properties* | UTF-8 *string* | MUST contain the message ID used to match incoming message with the response message containing result of execution. |
| *reply-to* | yes | *properties* | UTF-8 *string* | MUST contain the address that the client expects replies being sent to. This address MUST be the same as the source address used for establishing the client's receive link (see preconditions). |
| *correlation-id* | no | *properties* | *message-id* | MAY contain an ID used to correlate a response message to the original request. If set, it is used as the *correlation-id* property in the response, otherwise the value of the *message-id* property is used. | 
| *device_id* | yes | *application-properties* | UTF-8 *string* | MUST contain the ID of the device to be deregistered. |
| *action* | yes | *application-properties* | UTF-8 *string* | MUST be set to `deregister`. |

The body of the message SHOULD be empty and will be ignored by Hono if it is not.

**Response Message Format**

| Name | Mandatory | Location | Type | Description |
| :--- | :-------: | :------- | :-------- | :---------- |
| *correlation-id* | yes | *properties* | *message-id* | Contains the *message-id* (or the *correlation-id*, if specified) of the request message that this message represents the response for. |
| *content-type* | yes | *properties* | *symbol* | `application/json; charset=utf-8`. |
| *device_id* | yes | *application-properties* | UTF-8 *string* | Contains the ID of the device that has been deregistered. |
| *tenant_id* | yes | *application-properties* | UTF-8 *string* | Contains the tenant that the device has been deregistered from. |
| *status* | yes | *application-properties* | *int* | Contains the status code indicating the outcome of the operation. See below for possible values. |

The response message includes payload as defined in the `Payload Format` section below. The payload contains the key/value pairs of the device that has been deregistered.

The response may contain the following status codes:

| Code | Description |
| :--- | :---------- |
| *200* | Ok, the device has been successfully deregistered. |
| *404* | Not found, there is no device registered with the given *device_id* within the given *tenant_id*. |

For status codes indicating an error (codes in the `400 - 499` range) the message body MAY contain a detailed description of the error that occurred.

## Get Registration Information

Clients use this command to look up registration information for a device by its Hono identifier.

**Message Flow**

*TODO* add sequence diagram

**Request Message Format**

The following table provides an overview of the properties a client needs to set on a message to get registration information.

| Name | Mandatory | Location | Type | Description |
| :--- | :-------: | :------- | :-------- | :---------- |
| *message-id* | yes | *properties* | UTF-8 *string* | MUST contain the message ID used to match incoming message with the response message containing result of execution. |
| *reply-to* | yes | *properties* | UTF-8 *string* | MUST contain the address that the client expects replies being sent to. This address MUST be the same as the source address used for establishing the client's receive link (see preconditions). |
| *correlation-id* | no | *properties* |  *message-id* | MAY contain an ID used to correlate a response message to the original request. If set, it is used as the *correlation-id* property in the response, otherwise the value of the *message-id* property is used.|
| *device_id* | yes | *application-properties* | UTF-8 *string* | MUST contain the ID of the device to get registration information for. |
| *action* | yes | *application-properties* | UTF-8 *string* | MUST be set to `get`. |

The body of the message SHOULD be empty and will be ignored by Hono if it is not.

**Response Message Format**

The client receives a response with status *200* (success) if the device with the given device_id is registered or *404* (not found) if the `device_id` is unknown to Hono.

| Name | Mandatory | Location | Type | Description |
| :--- | :-------: | :------- | :-------- | :---------- |
| *correlation-id* | yes | *properties* | *message-id* | MUST contain the *message-id* (or the *correlation-id*, if specified) of the request message that this message contains the result of execution for. | 
| *device_id* | yes | *application-properties* | UTF-8 *string* | MUST contain the ID of the device. |
| *tenant_id* | yes | *application-properties* | UTF-8 *string* | MUST contain the tenant to which the device belongs to. |
| *status* | yes | *application-properties* | *int* | MUST contain the status code indicating the result of the operation. *200* for success i.e. the device is registered, *404* for failure i.e was not found or the user has no permission to see it. |

The response message includes payload as defined in the `Payload Format` section below. The `data` member contains the key/value pairs that have been registered for the device.

The response may contain the following status codes:

| Code | Description |
| :--- | :---------- |
| *200* | Ok, the payload contains the registration information for the device. |
| *404* | Not found, there is no device registered with the given *device_id* within the given *tenant_id*. |

For status codes indicating an error (codes in the `400 - 499` range) the message body MAY contain a detailed description of the error that occurred.

# Payload Format

Some of the operations of the Registration API allow or require the inclusion of payload data as part of the
request or response messages of the operation. Such payload is carried in the body of the corresponding AMQP 
messages as part of a single *AMQP Value* section. The message's *content-type* property indicates the type of the
payload data in such cases. The *content-type* is always `application/json; charset=utf-8`. 

## Request Payload

The payload included in *request* messages consists of a UTF-8 encoded string representation of a single JSON object.
The object MAY contain an arbitrary number of members with arbitrary names that MUST be of type `string` only.

Below is an example for a payload of a request for registering a device:
````
{
  "psk-id": "temp_sensor",
  "lwm2m-endpoint": "IMEI:456634343"
}
````

## Response Payload

The payload included in *response* messages consists of a UTF-8 encoded string representation of a single JSON object.
The object always contains a string typed member with name `id` whose value is the *device_id* as provided during registration of the device.
In addition, the object contains a member with name `data` of type json object.

Below is an example for a payload of a response for to a *get* request for *device_id* `4711`:
````
{
  "id" : "4711",
  "data" : {
    "psk-id" : "temp_sensor",
    "lwm2m-endpoint" : "IMEI:456634343"
  }
}
````
