---
title: "Device Registration API Specification"
linkTitle: "Device Registration API"
weight: 425
---

The *Device Registration API* is used by Hono's protocol adapters to get information about devices connecting to the adapters.
<!--more-->

The Device Registration API is defined by means of AMQP 1.0 message exchanges, i.e. a client needs to connect to Hono using an AMQP 1.0 client in order to invoke operations of the API as described in the following sections.

<a name="preconditions"></a>
## Preconditions for invoking the Device Registration API

1. Client has established an AMQP connection with the Device Registration service.
2. Client has established an AMQP link in role *sender* on the connection using target address `registration/${tenant_id}`. This link is used by the client to send request messages to the Device Registration service.
3. Client has established an AMQP link in role *receiver* on the connection using source address `registration/${tenant_id}/${reply-to}` where *reply-to* may be any arbitrary string chosen by the client. This link is used by the client to receive responses to the requests it has sent to the Device Registration service. This link's source address is also referred to as the *reply-to* address for the request messages.

{{< figure src="preconditions.svg" alt="A client establishes an AMQP connection and the links required to invoke operations of the Device Registration service" title="Client connecting to Device Registration service" >}}

## Assert Device Registration

Clients use this command to verify that a device is registered for a particular tenant and is enabled.

**Message Flow**

The following sequence diagram illustrates the flow of messages involved in a *Client* asserting a device's registration status.

{{< figure src="assert_success.svg" title="Client asserting a device's registration status" alt="A client sends a request message for asserting a device's registration status and receives a response containing the registration status" >}}


**Request Message Format**

The following table provides an overview of the properties a client needs to set on a message to assert a device's registration status:

| Name             | Mandatory | Location                 | AMQP Type    | Description |
| :--------------- | :-------: | :----------------------- | :----------- | :---------- |
| *correlation-id* | no        | *properties*             | *message-id* | MAY contain an ID used to correlate a response message to the original request. If set, it is used as the *correlation-id* property in the response, otherwise the value of the *message-id* property is used. Either this or the *message-id* property MUST be set. |
| *device_id*      | yes       | *application-properties* | *string*     | MUST contain the ID of the device that is subject to the operation. |
| *gateway_id*     | no        | *application-properties* | *string*     | The identifier of the gateway that wants to get an assertion *on behalf* of another device (given in the *device_id* property).<br>An implementation SHOULD verify that the gateway exists, is enabled and is authorized to get an assertion for, and thus send data on behalf of, the device. |
| *message-id*     | no        | *properties*             | *string*     | MAY contain an identifier that uniquely identifies the message at the sender side. Either this or the *correlation-id* property MUST be set. |
| *reply-to*       | yes       | *properties*             | *string*     | MUST contain the source address that the client wants to received response messages from. This address MUST be the same as the source address used for establishing the client's receive link (see [Preconditions]({{< relref "#preconditions-for-invoking-the-device-registration-api" >}})). |
| *subject*        | yes       | *properties*             | *string*     | MUST be set to `assert`. |

The body of the message SHOULD be empty and will be ignored if it is not.

**Response Message Format**

The following table provides an overview of the properties contained in a response message to an *assert* request:

| Name             | Mandatory | Location                 | AMQP Type    | Description |
| :--------------- | :-------: | :----------------------- | :----------- | :---------- |
| *correlation-id* | yes       | *properties*             | *message-id* | Contains the *message-id* (or the *correlation-id*, if specified) of the request message that this message is the response to. |
| *content-type*   | no        | *properties*             | *string*     | MUST be set to `application/json` if the invocation of the operation was successful and the body of the response message contains payload as described below. |
| *status*         | yes       | *application-properties* | *int*        | Contains the status code indicating the outcome of the operation. Concrete values and their semantics are defined below. |
| *cache_control*  | no        | *application-properties* | *string*     | Contains an [RFC 2616](https://tools.ietf.org/html/rfc2616#section-14.9) compliant <em>cache directive</em>. The directive contained in the property MUST be obeyed by clients that are caching responses. |

In case of a successful invocation of the operation, the body of the response message consists of a single *Data* section containing a UTF-8 encoded string representation of a single JSON object having the following properties:

| Name             | Mandatory | JSON Type     | Description |
| :--------------- | :-------: | :------------ | :---------- |
| *device-id*      | *yes*     | *string*      | The ID of the device that is subject of the assertion. |
| *via*            | *no*      | *array*       | The IDs (JSON strings) of gateways which may act on behalf of the device. This property MUST be set if any gateways are registered for the device. If the assertion request contained a *gateway_id* property and the response's *status* property has value `200` (indicating a successful assertion) then the array MUST at least contain the gateway ID from the request. |
| *defaults*       | *no*      | *object*      | Default values to be used by protocol adapters for augmenting messages from devices with missing information like a *content type*. It is up to the discretion of a protocol adapter if and how to use the given default values when processing messages published by the device. |
| *mapper*         | *no*      | *string*      | The name of a service that can be used to transform messages uploaded by the device before they are forwarded to downstream consumers. The client needs to map this name to the particular service to invoke. |

Below is an example for a payload of a response to an *assert* request for device `4711` which also includes a default *content-type* and a mapper service:
~~~json
{
  "device-id": "4711",
  "via": ["4712"],
  "defaults": {
    "content-type": "application/vnd.acme+json"
  },
  "mapper": "my-payload-transformation"
}
~~~

The response message's *status* property may contain the following codes:

| Code  | Description |
| :---- | :---------- |
| *200* | OK, the device is registered for the given tenant and is enabled. The response message body contains the asserted device's registration status. |
| *400* | Bad Request, the request message did not contain all mandatory properties. |
| *403* | Forbidden, the gateway with the given *gateway id* either does not exist, is not enabled or is not authorized to get an assertion for the device with the given *device id*. |
| *404* | Not Found, there is no device registered with the given *device id* within the given *tenant id* or the device is not enabled. |

For status codes indicating an error (codes in the `400 - 499` range) the message body MAY contain a detailed description of the error that occurred. In this case, the response message's *content-type* property SHOULD be set accordingly.

## Delivery States

The Device Registration service uses the following AMQP message delivery states when receiving request messages from clients:

| Delivery State | Description |
| :------------- | :---------- |
| *ACCEPTED*     | Indicates that the request message has been received and accepted for processing. |
| *REJECTED*     | Indicates that the request message has been received but cannot be processed. The disposition frame's *error* field contains information regarding the reason why. Clients should not try to re-send the request using the same message properties in this case. |
