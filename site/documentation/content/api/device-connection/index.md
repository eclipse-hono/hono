---
title: "Device Connection API Specification"
linkTitle: "Device Connection API"
weight: 425
resources:
  - src: preconditions.svg
  - src: get_last_known_gateway_success.svg
  - src: set_last_known_gateway_success.svg
---

The *Device Connection API* is used by *Protocol Adapters* to set and retrieve information about the connections from devices or gateways to the protocol adapters.
<!--more-->

The Device Connection API is defined by means of AMQP 1.0 message exchanges, i.e. a client needs to connect to Hono using an AMQP 1.0 client in order to invoke operations of the API as described in the following sections.

<a name="preconditions"></a>
## Preconditions for invoking the Device Connection API

1. Client has established an AMQP connection with the Device Connection service.
1. Client has established an AMQP link in role *sender* on the connection using target address `device_con/${tenant_id}`. This link is used by the client to send commands concerning device connections to Hono.
1. Client has established an AMQP link in role *receiver* on the connection using source address `device_con/${tenant_id}/${reply-to}` where *reply-to* may be any arbitrary string chosen by the client. This link is used by the client to receive responses to the requests it has sent to the Device Connection service. This link's source address is also referred to as the *reply-to* address for the request messages.

{{< figure src="preconditions.svg" alt="A client establishes an AMQP connection and the links required to invoke operations of the Device Connection service" title="Client connecting to Device Connection service" >}}

## Set last known Gateway for Device

Clients use this command to *set* the gateway that last acted on behalf of a given device.

As this operation is invoked frequently by Hono's components, implementors may choose to keep this information in memory. This API doesn't mandate checks on the validity of the given device or gateway IDs in order not to introduce a dependency on the *Device Registration API*. However, implementations of this API may choose to perform such checks or impose a restriction on the overall amount of data that can be stored per tenant in order to protect against malicious requests.

**Message Flow**


{{< figure src="set_last_known_gateway_success.svg" title="Client sets the last known gateway for a device" alt="A client sends a request message for setting the last known gateway and receives a response containing a confirmation" >}}

**Request Message Format**

The following table provides an overview of the properties a client needs to set on a message to set the last known gateway for a device in addition to the [Standard Request Properties]({{< relref "#standard-request-properties" >}}).

| Name         | Mandatory | Location                 | AMQP Type | Description |
| :----------- | :-------: | :----------------------- | :-------- | :---------- |
| *subject*    | yes       | *properties*             | *string*  | MUST be set to `set-last-gw`. |
| *gateway_id* | yes       | *application-properties* | *string*  | The identifier of the gateway that last acted on behalf of the device identified by the *device_id* property. If a device connects directly instead of through a gateway, the device's identifier MUST be specified here. |

The body of the message SHOULD be empty and will be ignored if it is not.

**Response Message Format**

A response to a *set last known gateway for device* request contains the [Standard Response Properties]({{< relref "#standard-response-properties" >}}).

The response message's *status* property may contain the following codes:

| Code  | Description |
| :---- | :---------- |
| *204* | OK, the last known gateway for the device has been updated. |
| *400* | Bad Request, the last known gateway has not been updated due to invalid or missing data in the request. |

Implementors of this API may return a *404* status code in order to indicate that no device and/or gateway with the given identifier exists for the given tenant. However, performing such a check is optional.

For status codes indicating an error (codes in the `400 - 499` range) the message body MAY contain a detailed description of the error that occurred.

## Get last known Gateway for Device

Clients use this command to *retrieve* the gateway that last acted on behalf of a given device.

**Message Flow**


{{< figure src="get_last_known_gateway_success.svg" title="Client retrieving the last known gateway for a device" alt="A client sends a request message for retrieving the last known gateway and receives a response containing the information" >}}

**Request Message Format**

The following table provides an overview of the properties a client needs to set on a message to retrieve the last known gateway for a device in addition to the [Standard Request Properties]({{< relref "#standard-request-properties" >}}).

| Name        | Mandatory | Location                 | AMQP Type | Description |
| :---------- | :-------: | :----------------------- | :-------- | :---------- |
| *subject*   | yes       | *properties*             | *string*  | MUST be set to `get-last-gw`. |

The body of the message SHOULD be empty and will be ignored if it is not.

**Response Message Format**

A response to a *get last known gateway for device* request contains the [Standard Response Properties]({{< relref "#standard-response-properties" >}}) as well as the properties shown in the following table:

| Name             | Mandatory | Location                 | AMQP Type    | Description |
| :--------------- | :-------: | :----------------------- | :----------- | :---------- |
| *content-type*   | no        | *properties*             | *string*     | MUST be set to `application/json` if the invocation of the operation was successful and the body of the response message contains payload as described below. |

The result of a successful invocation is carried in a single Data section of the response message as a UTF-8 encoded string representation of a single JSON object. It is an error to include payload that is not of this type.

The response message JSON object has the following properties:

| Name             | Mandatory | JSON Type     | Description |
| :--------------- | :-------: | :------------ | :---------- |
| *gateway-id*     | *yes*     | *string*      | The ID of the last known gateway for the device. |
| *last-updated*   | *no*      | *string*      | The date that the information about the last known gateway for the device was last updated. The value MUST be an [ISO 8601 compliant *combined date and time representation in extended format*](https://en.wikipedia.org/wiki/ISO_8601#Combined_date_and_time_representations). |

The response message's *status* property may contain the following codes:

| Code | Description |
| :--- | :---------- |
| *200* | OK, the payload contains the gateway ID. |
| *400* | Bad Request, the request message does not contain all required information/properties. |
| *404* | Not Found, there is no last known gateway assigned to the device. |

Implementors of this API may return a *404* status code in order to indicate that no device with the given identifier exists for the given tenant. However, performing such a check is optional.

For status codes indicating an error (codes in the `400 - 499` range) the message body MAY contain a detailed description of the error that occurred.

## Standard Message Properties

Due to the nature of the request/response message pattern of the operations of the Device Connection API, there are some standard properties shared by all of the request and response messages exchanged as part of the operations.

### Standard Request Properties

The following table provides an overview of the properties shared by all request messages regardless of the particular operation being invoked.

| Name             | Mandatory | Location                 | AMQP Type    | Description |
| :--------------- | :-------: | :----------------------- | :----------- | :---------- |
| *subject*        | yes       | *properties*             | *string*     | MUST be set to the value defined by the particular operation being invoked. |
| *correlation-id* | no        | *properties*             | *message-id* | MAY contain an ID used to correlate a response message to the original request. If set, it is used as the *correlation-id* property in the response, otherwise the value of the *message-id* property is used. Either this or the *message-id* property MUST be set. |
| *message-id*     | no        | *properties*             | *string*     | MAY contain an identifier that uniquely identifies the message at the sender side. Either this or the *correlation-id* property MUST be set. |
| *reply-to*       | yes       | *properties*             | *string*     | MUST contain the source address that the client wants to received response messages from. This address MUST be the same as the source address used for establishing the client's receive link (see [Preconditions]({{< relref "#preconditions" >}})). |
| *device_id*      | yes       | *application-properties* | *string*     | MUST contain the ID of the device that is subject to the operation. |

### Standard Response Properties

The following table provides an overview of the properties shared by all response messages regardless of the particular operation being invoked.

| Name             | Mandatory | Location                 | AMQP Type    | Description |
| :--------------- | :-------: | :----------------------- | :----------- | :---------- |
| *correlation-id* | yes       | *properties*             | *message-id* | Contains the *message-id* (or the *correlation-id*, if specified) of the request message that this message is the response to. |
| *status*         | yes       | *application-properties* | *int*        | Contains the status code indicating the outcome of the operation. Concrete values and their semantics are defined for each particular operation. |
| *cache_control*  | no        | *application-properties* | *string*     | Contains an [RFC 2616](https://tools.ietf.org/html/rfc2616#section-14.9) compliant <em>cache directive</em>. The directive contained in the property MUST be obeyed by clients that are caching responses. |

## Delivery States

Hono uses the following AMQP message delivery states when receiving request messages from clients:

| Delivery State | Description |
| :------------- | :---------- |
| *ACCEPTED*     | Indicates that the request message has been received and accepted for processing. |
| *REJECTED*     | Indicates that Hono has received the request but was not able to process it. The *error* field contains information regarding the reason why. Clients should not try to re-send the request using the same message properties in this case. |
