---
title: "Device Connection API Specification"
linkTitle: "Device Connection API"
hidden: true # does not show up in the menu, but shows up in the version selector and can be accessed from links
weight: 0
resources:
  - src: preconditions.svg
  - src: get_last_known_gateway_success.svg
  - src: set_last_known_gateway_success.svg
---

The *Device Connection API* is used by *Protocol Adapters* to set and retrieve information about the connections from devices or gateways to the protocol adapters.
<!--more-->

{{% notice info %}}
The Device Connection API has been replaced by the [Command Router API]({{< relref "/api/command-router" >}}).
{{% /notice %}}

The Device Connection API is defined by means of AMQP 1.0 message exchanges, i.e. a client needs to connect to the Device Connection service component using an AMQP 1.0 client in order to invoke operations of the API as described in the following sections.

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

## Set command-handling protocol adapter instance for device

Clients use this command to *set* the protocol adapter instance that currently handles command & control messages for a given device.

Clients can provide an optional `lifespan` parameter to make the protocol adapter instance entry expire after the given number of seconds. Note that implementations of this API have to support this feature, otherwise protocol adapters, as the clients of this API, might fail to correctly route command messages.

This API doesn't mandate checks on the validity of the given device in order not to introduce a dependency on the *Device Registration API*. However, implementations of this API may choose to perform such checks or impose a restriction on the overall amount of data that can be stored per tenant in order to protect against malicious requests.

**Message Flow**


{{< figure src="set_cmd_handling_adapter_instance.svg" title="Client sets the command-handling protocol adapter instance for device" alt="A client sends a request message for setting the adapter instance and receives a response containing a confirmation" >}}

**Request Message Format**

The following table provides an overview of the properties a client needs to set on a message to set the command-handling protocol adapter instance for a device in addition to the [Standard Request Properties]({{< relref "#standard-request-properties" >}}).

| Name                  | Mandatory | Location                 | AMQP Type | Description |
| :-------------------- | :-------: | :----------------------- | :-------- | :---------- |
| *subject*             | yes       | *properties*             | *string*  | MUST be set to `set-cmd-handling-adapter-instance`. |
| *adapter_instance_id* | yes       | *application-properties* | *string*  | The identifier of the protocol adapter instance that currently handles commands for the device or gateway identified by the *device_id* property. |
| *lifespan*            | no        | *application-properties* | *int*     | The lifespan of the mapping entry in seconds. After that period, the mapping entry shall be treated as non-existent by the *Device Connection API* methods. A negative value, as well as an omitted property, is interpreted as an unlimited lifespan. |

The body of the message SHOULD be empty and will be ignored if it is not.

**Response Message Format**

A response to a *set command-handling adapter instance for device* request contains the [Standard Response Properties]({{< relref "#standard-response-properties" >}}).

The response message's *status* property may contain the following codes:

| Code  | Description |
| :---- | :---------- |
| *204* | OK, the command-handling adapter instance for the device has been updated. |
| *400* | Bad Request, the adapter instance for the device has not been set or updated due to invalid or missing data in the request. |

Implementors of this API may return a *404* status code in order to indicate that no device with the given identifier exists for the given tenant. However, performing such a check is optional.

For status codes indicating an error (codes in the `400 - 499` range) the message body MAY contain a detailed description of the error that occurred.

## Remove command-handling protocol adapter instance for device

Clients use this command to *remove* the information, which protocol adapter instance is currently handling command & control messages for the given device. The mapping information is only removed, if the currently associated adapter instance matches the one given in the request.

This API doesn't mandate checks on the validity of the given device in order not to introduce a dependency on the *Device Registration API*. However, implementations of this API may choose to perform such checks or impose a restriction on the overall amount of data that can be stored per tenant in order to protect against malicious requests.

**Message Flow**


{{< figure src="remove_cmd_handling_adapter_instance.svg" title="Client removes the command-handling protocol adapter instance information for device" alt="A client sends a request message for removing the adapter instance mapping and receives a response containing a confirmation" >}}

**Request Message Format**

The following table provides an overview of the properties a client needs to set on a message to remove the mapping information regarding the command-handling protocol adapter instance for a device in addition to the [Standard Request Properties]({{< relref "#standard-request-properties" >}}).

| Name                  | Mandatory | Location                 | AMQP Type | Description |
| :-------------------- | :-------: | :----------------------- | :-------- | :---------- |
| *subject*             | yes       | *properties*             | *string*  | MUST be set to `remove-cmd-handling-adapter-instance`. |
| *adapter_instance_id* | yes       | *application-properties* | *string*  | The identifier of the protocol adapter instance to remove the mapping information for. Only if this adapter instance is currently associated with the device or gateway identified by the *device_id* property, the mapping entry will be removed. |

The body of the message SHOULD be empty and will be ignored if it is not.

**Response Message Format**

A response to a *remove command-handling adapter instance for device* request contains the [Standard Response Properties]({{< relref "#standard-response-properties" >}}).

The response message's *status* property may contain the following codes:

| Code  | Description |
| :---- | :---------- |
| *204* | OK, the adapter instance mapping information for the device has been removed. |
| *400* | Bad Request, the request message does not contain all required properties. |
| *412* | Precondition failed, the adapter instance for the device has not been removed because there is no matching command-handling adapter instance assigned to the device. |

Implementors of this API may return a *404* status code in order to indicate that no device with the given identifier exists for the given tenant. However, performing such a check is optional.

For status codes indicating an error (codes in the `400 - 499` range) the message body MAY contain a detailed description of the error that occurred.

## Get command-handling protocol adapter instances for device

Clients use this command to *get* information about the adapter instances that can handle command & control messages for the given device.

As part of the request message, the client is supposed to provide the list of gateways that may act on behalf of the given device. The client may get this list via the Device Registration API's [*assert Device Registration*]({{< relref "/api/device-registration#assert-device-registration" >}}) operation.

This API doesn't mandate checks on the validity of the given device and the gateway list in order not to introduce a dependency on the *Device Registration API*.

The command implementation MUST determine the adapter instances by applying the following rules (in the given order):

1. If an adapter instance is associated with the given device, this adapter instance is returned as the *single* returned list entry.
1. Otherwise, if there is an adapter instance registered for the last known gateway associated with the given device, this adapter instance is returned as the *single* returned list entry. The last known gateway has to be contained in the given list of gateways for this case.
1. Otherwise, *all* adapter instances associated with any of the given gateway identifiers are returned.

That means that for a device communicating via a gateway, the result is reduced to a *single element* list if an adapter instance for the device itself or its last known gateway is found. The adapter instance registered for the device itself is given precedence in order to ensure that a gateway having subscribed to commands for that *particular device* is chosen over a gateway that has subscribed to commands for *all* devices of a tenant.

**Message Flow**


{{< figure src="get_cmd_handling_adapter_instances.svg" title="Client retrieving the list of command-handling adapter instances for a device" alt="A client sends a request message for retrieving the list of command-handling adapter instances for a device and receives a response containing the information" >}}

**Request Message Format**

The following table provides an overview of the properties a client needs to set on a message to retrieve the command-handling adapter instances for a device in addition to the [Standard Request Properties]({{< relref "#standard-request-properties" >}}).

| Name           | Mandatory | Location                 | AMQP Type | Description |
| :------------- | :-------: | :----------------------- | :-------- | :---------- |
| *content-type* | yes       | *properties*             | *string*  | MUST be set to `application/json`. |
| *subject*      | yes       | *properties*             | *string*  | MUST be set to `get-cmd-handling-adapter-instances`. |

The body of the message MUST consist of a single AMQP *Data* section containing a UTF-8 encoded string representation of a single JSON object. The JSON object has the following properties:

| Name             | Mandatory | JSON Type    | Description |
| :--------------- | :-------: | :----------- | :---------- |
| *gateway-ids*     | *yes*    | *array*      | The IDs of the gateways that may act on behalf of the given device. This list may be obtained via the Device Registration API's [*assert Device Registration*]({{< relref "/api/device-registration#assert-device-registration" >}}) operation. |

Example of a request payload:

~~~json
{
  "gateway-ids": ["gw-1", "gw-2", "gw-3"]
}
~~~

**Response Message Format**

A response to a *get command-handling adapter instances for device* request contains the [Standard Response Properties]({{< relref "#standard-response-properties" >}}) as well as the properties shown in the following table:

| Name             | Mandatory | Location                 | AMQP Type    | Description |
| :--------------- | :-------: | :----------------------- | :----------- | :---------- |
| *content-type*   | no        | *properties*             | *string*     | MUST be set to `application/json` if the invocation of the operation was successful and the body of the response message contains payload as described below. |

The result of a successful invocation is carried in a single Data section of the response message as a UTF-8 encoded string representation of a single JSON object. It is an error to include payload that is not of this type.

The response message JSON object has the following properties:

| Name                | Mandatory | JSON Type     | Description |
| :------------------ | :-------: | :------------ | :---------- |
| *adapter-instances* | *yes*     | *array*       | A non-empty array of JSON objects that represent the command-handling adapter instances along with the device or gateway id. |

Each entry in the *adapter-instances* array has the following properties:

| Name                  | Mandatory | JSON Type     | Description |
| :-------------------- | :-------: | :------------ | :---------- |
| *adapter-instance-id* | *yes*     | *string*      | The ID of the protocol adapter instance handling command & control messages for the device given in the *device-id* property. |
| *device-id*           | *yes*     | *string*      | The ID of the gateway or device that the protocol adapter instance given by the *adapter-instance-id* is handling command & control messages for. This ID is not necessarily the *device_id* given in the request message, it may be the ID of one of the gateways acting on behalf of the device. |


An example of a response message with a single adapter instance result, returned for example if an adapter instance is registered for the given device:

~~~json
{
  "adapter-instances": [
    {
      "adapter-instance-id": "adapter-1",
      "device-id": "4711"
    }
  ]
}
~~~

An example of a response message with multiple contained adapter instances, returned for example if no adapter instance is registered for the given device or its last used gateway, and therefore a list of all adapter instances for the gateways of the device is returned:

~~~json
{
  "adapter-instances": [
    {
      "adapter-instance-id": "adapter-1",
      "device-id": "gw-1"
    },
    {
      "adapter-instance-id": "adapter-1",
      "device-id": "gw-2"
    }
  ]
}
~~~


The response message's *status* property may contain the following codes:

| Code | Description |
| :--- | :---------- |
| *200* | OK, the payload contains the adapter instances. |
| *400* | Bad Request, the request message does not contain all required information/properties. |
| *404* | Not Found, there is no command-handling adapter instance assigned to the device. |

Implementors of this API may also return a *404* status code in order to indicate that no device with the given identifier exists for the given tenant. However, performing such a check is optional.

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
| *reply-to*       | yes       | *properties*             | *string*     | MUST contain the source address that the client wants to received response messages from. This address MUST be the same as the source address used for establishing the client's receive link (see [Preconditions]({{< relref "#preconditions-for-invoking-the-device-connection-api" >}})). |
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
