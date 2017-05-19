+++
title = "Device Registration API"
weight = 410
+++

The *Device Registration API* is used to make Hono aware of devices that will connect to the service and send telemetry data.
It can be used by *Protocol Adapters* to register devices that are not directly connected to Hono using an AMQP 1.0 connection.
*Solutions* and other consumers may use the API to obtain information about a single device that is registered to Hono.
<!--more-->

Note, however, that in real world applications the registration information will probably be kept and managed by an existing *system of record*, using e.g. a database for persisting the data. The Device Registration API accounts for this fact by means of defining only the [Assert Device Registration]({{< relref "#assert-device-registration" >}}) operation as *mandatory*, i.e. this operation is strictly required by a Hono instance for it to work properly, whereas the remaining operations are defined as *optional* from a Hono perspective.

The Device Registration API is defined by means of AMQP 1.0 message exchanges, i.e. a client needs to connect to Hono using an AMQP 1.0 client in order to invoke operations of the API as described in the following sections.

# Preconditions

The preconditions for performing any of the operations are as follows:

1. Client has established an AMQP connection with Hono.
2. Client has established an AMQP link in role *sender* with Hono using target address `registration/${tenant_id}`. This link is used by the client to send registration commands to Hono.
3. Client has established an AMQP link in role *receiver* with Hono using source address `registration/${tenant_id}/${reply-to}` where *reply-to* may be any arbitrary string chosen by the client. This link is used by the client to receive responses to the registration requests it has sent to Hono. This link's source address is also referred to as the *reply-to* address for the request messages.

# Operations

The operations described in the following sections can be used by clients to manage device registration information. In real world scenarios the provisioning of devices will most likely be an orchestrated process spanning multiple components of which Hono will only be one.

Conducting and orchestrating the overall provisioning process is not in scope of Hono. However, Hono's device registration API can easily be used as part of such an overall provisioning process.


## Register Device

Clients use this command to initially *add* information about a new device to Hono. Each device is registered within a *tenant*, providing the scope of the device's identifier.

This operation is *optional*, implementors of this API may provide other means for adding registration information, e.g. a RESTful API or a configuration file.

**Message Flow**

*TODO* add sequence diagram

**Request Message Format**

The following table provides an overview of the properties a client needs to set on a *register device* message in addition to the [Standard Request Properties]({{< relref "#standard-request-properties" >}}).

| Name        | Mandatory | Location                 | Type     | Description |
| :---------- | :-------: | :----------------------- | :------- | :---------- |
| *subject*   | yes       | *properties*             | *string* | MUST be set to `register`. |

The request message MAY include payload as defined in the [Payload Format]({{< relref "#payload-format" >}}) section below.

The key/value pairs provided in the payload may be useful e.g. for recording metadata about the device like the manufacturer or model.
 
**Response Message Format**

A response to an *add credentials* request contains the [Standard Response Properties]({{< relref "#standard-response-properties" >}}).

The response message's *status* property may contain the following codes:

| Code  | Description |
| :---- | :---------- |
| *201* | Created, the device has been successfully registered. |
| *409* | Conflict, there already exists a device registration for the given *device_id* within this tenant. |

For status codes indicating an error (codes in the `400 - 499` range) the message body MAY contain a detailed description of the error that occurred.

## Get Registration Information

Clients use this command to *retrieve* information about a registered device.

This operation is *optional*, implementors of this API may provide other means for retrieving registration information, e.g. a RESTful API.

**Message Flow**

*TODO* add sequence diagram

**Request Message Format**

The following table provides an overview of the properties a client needs to set on a message to get registration information in addition to the [Standard Request Properties]({{< relref "#standard-request-properties" >}}).

| Name        | Mandatory | Location                 | Type     | Description |
| :---------- | :-------: | :----------------------- | :------- | :---------- |
| *subject*   | yes       | *properties*             | *string* | MUST be set to `get`. |

The body of the message SHOULD be empty and will be ignored if it is not.

**Response Message Format**

A response to a *get registration information* request contains the [Standard Response Properties]({{< relref "#standard-response-properties" >}}).

The response message includes payload as defined in the [Payload Format]({{< relref "#payload-format" >}}) section below. The `data` member contains the key/value pairs that have been registered for the device.

The response message's *status* property may contain the following codes:

| Code | Description |
| :--- | :---------- |
| *200* | OK, the payload contains the registration information for the device. |
| *404* | Not Found, there is no device registered with the given *device_id* within the given *tenant_id*. |

For status codes indicating an error (codes in the `400 - 499` range) the message body MAY contain a detailed description of the error that occurred.

## Assert Device Registration

Clients use this command to get a signed *assertion* that a device is registered for a particular tenant and is enabled. The assertion is supposed to be included when [uploading telemetry data]({{< relref "Telemetry-API.md#upload-telemetry-data" >}}) or [publishing an event]({{< relref "Event-API.md#send-event" >}}) for a device so that the Telemetry or Event service implementation does not need to call out to the Device Registration service on every message in order to verify the device's registration status.

This operation is *mandatory* to implement.

**Message Flow**

*TODO* add sequence diagram

**Request Message Format**

The following table provides an overview of the properties a client needs to set on a message to get registration information in addition to the [Standard Request Properties]({{< relref "#standard-request-properties" >}}).

| Name        | Mandatory | Location                 | Type     | Description |
| :---------- | :-------: | :----------------------- | :------- | :---------- |
| *subject*   | yes       | *properties*             | *string* | MUST be set to `assert`. |

The body of the message SHOULD be empty and will be ignored if it is not.

**Response Message Format**

A response to an *assertion* request contains the [Standard Response Properties]({{< relref "#standard-response-properties" >}}).

The body of the response message consists of a single *AMQP Value* section containing a UTF-8 encoded string representation of a single JSON object having the following properties:

| Name             | Mandatory | Type       | Description |
| :--------------- | :-------: | :--------- | :---------- |
| *device-id*      | *yes*     | *string*   | The ID of the device that is subject of the assertion. |
| *assertion*      | *yes*     | *string*   | A [JSON Web Token](https://jwt.io/introduction/) which MUST contain the device id (`sub` claim), the tenant id (private `ten` claim) and an expiration time (`exp` claim). The token MAY contain additional claims as well. A client SHOULD silently ignore claims it does not understand. |

The response message's *status* property may contain the following codes:

| Code | Description |
| :--- | :---------- |
| *200* | OK, the device is registered for the given tenant and is enabled. The payload contains the signed assertion. |
| *404* | Not Found, there is no device registered with the given *device_id* within the given *tenant_id* or the device is not enabled. |

For status codes indicating an error (codes in the `400 - 499` range) the message body MAY contain a detailed description of the error that occurred.

## Update Device Registration

Clients use this command to *update* information about an already registered device. All of the information that has been previously registered for the device gets *replaced* with the information contained in the request message.

This operation is *optional*, implementors of this API may provide other means for updating registration information, e.g. a RESTful API or a configuration file.

**Message Flow**

*TODO* add sequence diagram

**Request Message Format**

The following table provides an overview of the properties a client needs to set on an *update registration* message in addition to the [Standard Request Properties]({{< relref "#standard-request-properties" >}}).

| Name        | Mandatory | Location                 | Type     | Description |
| :---------- | :-------: | :----------------------- | :------- | :---------- |
| *subject*   | yes       | *properties*             | *string* | MUST be set to `update`. |

The request message MAY include payload as defined in the [Payload Format]({{< relref "#payload-format" >}}) section below.
 
**Response Message Format**

A response to an *update registration* request contains the [Standard Response Properties]({{< relref "#standard-response-properties" >}}).

The response message includes payload as defined in the [Payload Format]({{< relref "#payload-format" >}}) section below. The `data` member contains the key/value pairs that **have been replaced** by this operation.

The response message's *status* property may contain the following codes:

| Code  | Description |
| :---- | :---------- |
| *200* | OK, the device registration has been successfully updated. The message payload contains the data that has been *previously* registered and which has been *replaced* with the payload of the request message. |
| *404* | Not Found, there is no device registered with the given *device_id* within the given *tenant_id*. |

For status codes indicating an error (codes in the `400 - 499` range) the message body MAY contain a detailed description of the error that occurred.

## Deregister Device

Clients use this command to *remove* all information about a device from Hono. Once a device is deregistered, clients can no longer use Hono to interact with the device nor can they consume any more data produced by the device.

This operation is *optional*, implementors of this API may provide other means for deleting registration information, e.g. a RESTful API or a configuration file.

**Message Flow**

*TODO* add sequence diagram

**Request Message Format**

The following table provides an overview of the properties a client needs to set on a *deregister device* message in addition to the [Standard Request Properties]({{< relref "#standard-request-properties" >}}).

| Name        | Mandatory | Location                 | Type     | Description |
| :---------- | :-------: | :----------------------- | :------- | :---------- |
| *subject*   | yes       | *properties*             | *string* | MUST be set to `deregister`. |

The body of the message SHOULD be empty and will be ignored if it is not.


**Response Message Format**

A response to a *deregister device* request contains the [Standard Response Properties]({{< relref "#standard-response-properties" >}}).

The response message includes payload as defined in the [Payload Format]({{< relref "#payload-format" >}}) section below. The payload contains the key/value pairs of the device that has been unregistered.

The response may contain the following status codes:

| Code | Description |
| :--- | :---------- |
| *200* | OK, the device has been successfully deregistered. |
| *404* | Not Found, there is no device registered with the given *device_id* within the given *tenant_id*. |

For status codes indicating an error (codes in the `400 - 499` range) the message body MAY contain a detailed description of the error that occurred.

# Standard Message Properties

Due to the nature of the request/response message pattern of the operations of the Device Registration API, there are some standard properties shared by all of the request and response messages exchanged as part of the operations.

## Standard Request Properties

The following table provides an overview of the properties shared by all request messages regardless of the particular operation being invoked.

| Name             | Mandatory | Location                 | Type         | Description |
| :--------------- | :-------: | :----------------------- | :----------- | :---------- |
| *subject*        | yes       | *properties*             | *string*     | MUST be set to the value defined by the particular operation being invoked. |
| *correlation-id* | no        | *properties*             | *message-id* | MAY contain an ID used to correlate a response message to the original request. If set, it is used as the *correlation-id* property in the response, otherwise the value of the *message-id* property is used. |
| *message-id*     | yes       | *properties*             | *string*     | MUST contain an identifier that uniquely identifies the message at the sender side. |
| *reply-to*       | yes       | *properties*             | *string*     | MUST contain the source address that the client wants to received response messages from. This address MUST be the same as the source address used for establishing the client's receive link (see [Preconditions]({{< relref "#preconditions" >}})). |
| *device_id*      | yes       | *application-properties* | *string*     | MUST contain the ID of the device that is subject to the operation. |

## Standard Response Properties

The following table provides an overview of the properties shared by all response messages regardless of the particular operation being invoked.

| Name             | Mandatory | Location                 | Type         | Description |
| :--------------- | :-------: | :----------------------- | :----------- | :---------- |
| *correlation-id* | yes       | *properties*             | *message-id* | Contains the *message-id* (or the *correlation-id*, if specified) of the request message that this message is the response to. |
| *device_id*      | yes       | *application-properties* | *string*     | Contains the ID of the device. |
| *tenant_id*      | yes       | *application-properties* | *string*     | Contains the ID of the tenant to which the device belongs. |
| *status*         | yes       | *application-properties* | *int*        | Contains the status code indicating the outcome of the operation. Concrete values and their semantics are defined for each particular operation. |

# Delivery States

Hono uses the following AMQP message delivery states when receiving request messages from clients:

| Delivery State | Description |
| :------------- | :---------- |
| *ACCEPTED*     | Indicates that Hono has successfully received and accepted the request for processing. |
| *REJECTED*     | Indicates that Hono has received the request but was not able to process it. The *error* field contains information regarding the reason why. Clients should not try to re-send the request using the same message properties in this case. |

# Payload Format

Most of the operations of the Device Registration API allow or require the inclusion of registration data in the payload of the
request or response messages of the operation. Such payload is carried in the body of the corresponding AMQP 
messages as part of a single *AMQP Value* section.

The registration data is carried in the payload as a UTF-8 encoded string representation of a single JSON object. It is an error to include payload that is not of this type.

## Request Payload

The JSON object conveyed in the payload MAY contain an arbitrary number of members with arbitrary names which MUST be of a scalar type only.

Below is an example for a payload of a request for registering a device:
~~~json
{
  "manufacturer": "ACME Corp.",
  "firmware": "v1.5"
}
~~~

{{% note %}}
The device's *enabled* property will be set to *true* by default if the request message either does not contain any payload at all or 
if the payload does not contain the *enabled* property.
{{% /note %}}

## Response Payload

The JSON object conveyed in a response payload always contains a string typed member of name `device-id` whose value is the ID of the device as provided during registration.
In addition, the object contains a member with name `data` of type `json object`. The `data` object always contains a property called `enabled` which indicates whether the device may be interacted with or not.

Below is an example for a payload of a response to a *get* request for device `4711`:
~~~json
{
  "device-id" : "4711",
  "data" : {
    "enabled": true,
    "manufacturer": "ACME Corp.",
    "firmware": "v1.5"
  }
}
~~~
