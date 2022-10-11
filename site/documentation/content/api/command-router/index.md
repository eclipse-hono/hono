---
title: "Command Router API Specification"
linkTitle: "Command Router API"
weight: 425
resources:
  - src: preconditions.svg
  - src: register_cmd_consumer.svg
  - src: unregister_cmd_consumer.svg
  - src: set_last_known_gateway_success.svg
  - src: enable_command_routing.svg
---

*Protocol Adapters* use the *Command Router API* to supply information with which a Command Router service component can route command & control messages to the protocol adapters that the target devices are connected to.
<!--more-->

The Command Router API is defined by means of AMQP 1.0 message exchanges, i.e. a client needs to connect to the service component using an AMQP 1.0 client in order to invoke operations of the API as described in the following sections.

<a name="preconditions"></a>
## Preconditions for invoking the Command Router API

1. Client has established an AMQP connection with the Command Router service.
1. Client has established an AMQP link in role *sender* on the connection using target address `cmd_router/${tenant_id}`. This link is used by the client to send request messages to the Command Router service.
1. Client has established an AMQP link in role *receiver* on the connection using source address `cmd_router/${tenant_id}/${reply-to}` where *reply-to* may be any arbitrary string chosen by the client. This link is used by the client to receive responses to the requests it has sent to the Command Router service. This link's source address is also referred to as the *reply-to* address for the request messages.

{{< figure src="preconditions.svg" alt="A client establishes an AMQP connection and the links required to invoke operations of the Command Router service" title="Client connecting to Command Router service" >}}

## Register command consumer for device

Clients use this command to *register* a protocol adapter instance as the consumer of command & control messages for a device or gateway currently connected to that particular adapter instance.

Clients can provide an optional `send_event` parameter with value `true` to trigger a [Time until Disconnect Notification]({{< relref "/concepts/device-notifications#time-until-disconnect-notification" >}}) with *ttd* value `-1` indicating the device is ready to receive commands.

Clients can provide an optional `lifespan` parameter to make the registration entry expire after the given number of seconds. Note that implementations of this API have to support this feature, otherwise the Command Router service component might fail to correctly route command messages.

This API doesn't mandate checks on the validity of the given device in order not to introduce a dependency on the *Device Registration API*. However, implementations of this API may choose to perform such checks or impose a restriction on the overall amount of data that can be stored per tenant in order to protect against malicious requests.

**Message Flow**


{{< figure src="register_cmd_consumer.svg" title="Client registers the command consumer for a device" alt="A client sends a request message for registering the command consumer and receives a response containing a confirmation" >}}

**Request Message Format**

The following table provides an overview of the properties a client needs to set on a message to register the command consumer for a device in addition to the [Standard Request Properties]({{< relref "#standard-request-properties" >}}).

| Name                  | Mandatory | Location                 | AMQP Type | Description |
| :-------------------- | :-------: | :----------------------- | :-------- | :---------- |
| *subject*             | yes       | *properties*             | *string*  | MUST be set to `register-cmd-consumer`. |
| *adapter_instance_id* | yes       | *application-properties* | *string*  | The identifier of the protocol adapter instance that currently handles commands for the device or gateway identified by the *device_id* property. |
| *device_id*           | yes       | *application-properties* | *string*  | MUST contain the ID of the device that is subject to the operation. |
| *send_event*          | no        | *application-properties* | *boolean* | If set to `true`, a [Time until Disconnect Notification]({{< relref "/concepts/device-notifications#time-until-disconnect-notification" >}}) with *ttd* value `-1` should be sent indicating the device is ready to receive commands. |
| *lifespan*            | no        | *application-properties* | *int*     | The lifespan of the mapping entry in seconds. After that period, the registration entry shall be treated as non-existent by the Command Router service component. A negative value, as well as an omitted property, is interpreted as an unlimited lifespan. |

The body of the message SHOULD be empty and will be ignored if it is not.

**Response Message Format**

A response to a *register command consumer for device* request contains the [Standard Response Properties]({{< relref "#standard-response-properties" >}}).

The response message's *status* property may contain the following codes:

| Code  | Description |
| :---- | :---------- |
| *204* | OK, the command consumer registration entry for the device has been created or updated. |
| *400* | Bad Request, the command consumer registration entry for the device has not been created or updated due to invalid or missing data in the request. |

Implementors of this API may return a *404* status code in order to indicate that no device with the given identifier exists for the given tenant. However, performing such a check is optional.

For status codes indicating an error (codes in the `400 - 499` range) the message body MAY contain a detailed description of the error that occurred.

## Unregister command consumer for device

Clients use this command to *unregister* a command consumer for the given device. The consumer registration entry to be removed is identified by the provided protocol adapter instance handling command & control messages for a given device. The registration entry for the device is only removed, if the adapter instance identifier of the current registration entry matches the one given in the request.

Clients can provide an optional `send_event` parameter with value `true` to trigger a [Time until Disconnect Notification]({{< relref "/concepts/device-notifications#time-until-disconnect-notification" >}}) with *ttd* value `0` indicating the device is not ready to receive commands.

This API doesn't mandate checks on the validity of the given device in order not to introduce a dependency on the *Device Registration API*. However, implementations of this API may choose to perform such checks or impose a restriction on the overall amount of data that can be stored per tenant in order to protect against malicious requests.

**Message Flow**


{{< figure src="unregister_cmd_consumer.svg" title="Client unregisters the command consumer for a device" alt="A client sends a request message for removing the consumer registration entry and receives a response containing a confirmation" >}}

**Request Message Format**

The following table provides an overview of the properties a client needs to set on a message to remove the consumer registration entry for a device in addition to the [Standard Request Properties]({{< relref "#standard-request-properties" >}}).

| Name                  | Mandatory | Location                 | AMQP Type | Description |
| :-------------------- | :-------: | :----------------------- | :-------- | :---------- |
| *subject*             | yes       | *properties*             | *string*  | MUST be set to `unregister-cmd-consumer`. |
| *adapter_instance_id* | yes       | *application-properties* | *string*  | The identifier of the protocol adapter instance to remove the registration entry for. Only if this adapter instance is currently associated with the device or gateway identified by the *device_id* property, the registration entry will be removed. |
| *device_id*           | yes       | *application-properties* | *string*  | MUST contain the ID of the device that is subject to the operation. |
| *send_event*          | no        | *application-properties* | *boolean* | If set to `true`, a [Time until Disconnect Notification]({{< relref "/concepts/device-notifications#time-until-disconnect-notification" >}}) with *ttd* value `0` should be sent indicating the device is not ready to receive commands. |


The body of the message SHOULD be empty and will be ignored if it is not.

**Response Message Format**

A response to a *unregister command consumer for device* request contains the [Standard Response Properties]({{< relref "#standard-response-properties" >}}).

The response message's *status* property may contain the following codes:

| Code  | Description |
| :---- | :---------- |
| *204* | OK, the command consumer registration entry for the device has been removed. |
| *400* | Bad Request, the request message does not contain all required properties. |
| *412* | Precondition failed, the registration entry for the device has not been removed because there is no command consumer registration entry matching the adapter instance assigned to the device. This may happen if the mapping entry has already been removed or its lifespan has elapsed. The entry could also have been updated with a different adapter instance identifier before, which could for example mean the device lost its connection to the protocol adapter and has reconnected to a different adapter instance. The original adapter instance trying to remove the mapping entry at some point after that will result in a *412* error response because the instance identifier doesn't match. |

Implementors of this API may return a *404* status code in order to indicate that no device with the given identifier exists for the given tenant. However, performing such a check is optional.

For status codes indicating an error (codes in the `400 - 499` range) the message body MAY contain a detailed description of the error that occurred.

## Set last known Gateway for Device

Clients use this command to *set* the gateway that last acted on behalf of a given device.

There are two variants of this command, one providing a single gateway and device identifier combination via request application-properties and one providing a map of multiple such combinations in the request payload.

As this operation is invoked frequently by Hono's components, implementors may choose to keep this information in memory. This API doesn't mandate checks on the validity of the given device or gateway IDs in order not to introduce a dependency on the *Device Registration API*. However, implementations of this API may choose to perform such checks or impose a restriction on the overall amount of data that can be stored per tenant in order to protect against malicious requests.

**Message Flow**

{{< figure src="set_last_known_gateway_success.svg" title="Client sets the last known gateway for a device" alt="A client sends a request message for setting the last known gateway and receives a response containing a confirmation" >}}

**Request Message Format - Setting single entry**

The following table provides an overview of the properties a client needs to set on a message to set the last known gateway for a device in addition to the [Standard Request Properties]({{< relref "#standard-request-properties" >}}).

| Name         | Mandatory | Location                 | AMQP Type | Description |
| :----------- | :-------: | :----------------------- | :-------- | :---------- |
| *subject*    | yes       | *properties*             | *string*  | MUST be set to `set-last-gw`. |
| *device_id*  | yes       | *application-properties* | *string*  | MUST contain the ID of the device that is subject to the operation. |
| *gateway_id* | yes       | *application-properties* | *string*  | The identifier of the gateway that last acted on behalf of the device identified by the *device_id* property. For a device that connects to the adapter directly instead of through a gateway, the value of this property MUST be the same as the value of the *device_id* application property. |

The body of the message SHOULD be empty and will be ignored if it is not.

**Request Message Format - Setting multiple entries**

The following table provides an overview of the properties a client needs to set on a message to set multiple last known gateway and device pairs in addition to the [Standard Request Properties]({{< relref "#standard-request-properties" >}}).

| Name         | Mandatory | Location                 | AMQP Type | Description |
| :----------- | :-------: | :----------------------- | :-------- | :---------- |
| *subject*    | yes       | *properties*             | *string*  | MUST be set to `set-last-gw`. |

The body of the request MUST consist of a single *Data* section containing a UTF-8 encoded string representation of a single JSON object. The device identifiers are to be used as fields, the corresponding gateway identifiers as *string* values.
Note that the number of entries supported in the object may be limited by the maximum message size negotiated between the service and the client. In such a case, a client may use multiple consecutive requests to overcome this limitation.

Example payload for setting *gateway-1* as last known gateway for the devices *device-1* and *device-2*.

~~~json
{
  "device-1": "gateway-1",
  "device-2": "gateway-1"
}
~~~

**Response Message Format**

A response to a *set last known gateway for device* request contains the [Standard Response Properties]({{< relref "#standard-response-properties" >}}).

The response message's *status* property may contain the following codes:

| Code  | Description |
| :---- | :---------- |
| *204* | OK, the update of the last known gateway(s) for the device(s) was successful. |
| *400* | Bad Request, the update of the last known gateway(s) for the device(s) failed due to invalid or missing data in the request. |

Implementors of this API may return a *404* status code for the single entry operation in order to indicate that no device and/or gateway with the given identifier exists for the given tenant. However, performing such a check is optional.

For status codes indicating an error (codes in the `400 - 499` range) the message body MAY contain a detailed description of the error that occurred.

## Enable Command Routing

A *Protocol Adapter* uses this operation to inform the Command Router service about the tenants that the devices belong to
that are connected to the adapter and which have subscribed to commands.

During normal operation, the Command Router is able to keep track of these tenants implicitly as part of the information
provided in invocations of the [register command consumer]({{< relref "#register-command-consumer-for-device" >}})
operation. Depending on the service's implementation, this information might be lost after an unexpected restart.
Protocol adapters will perceive such a case by means of a loss of their AMQP connection to the service.
Once the connection has been re-established, an adapter can then use this operation to help the Command Router
service recover and re-establish the downstream network links which are required to receive and forward commands.

**Message Flow**

{{< figure src="enable_command_routing.svg" title="Client submits tenant IDs to enable command routing for"
alt="A client sends a request message for (re-)enabling command routing for a list of tenants and receives a response containing a confirmation" >}}

**Request Message Format**

The following table provides an overview of the properties a client needs to set on a message to enable command routing
in addition to the [Standard Request Properties]({{< relref "#standard-request-properties" >}}).

| Name             | Mandatory | Location                 | AMQP Type    | Description |
| :--------------- | :-------: | :----------------------- | :----------- | :---------- |
| *subject*        | yes       | *properties*             | *string*     | MUST be set to `enable-command-routing`. |

The body of the request MUST consist of a single *Data* section containing a UTF-8 encoded string representation of a
single JSON array containing tenant identifiers. Note that the number of tenant identifiers supported in the array may
be limited by the maximum message size negotiated between the service and the client. In such a case, a client may use
multiple consecutive requests to overcome this limitation.

The following request payload may be used to re-enable command routing of tenants *one*, *two* and *three*:

~~~json
[ "one", "two", "three" ]
~~~

**Response Message Format**

A response to an *enable command routing* request contains the [Standard Response Properties]({{< relref "#standard-response-properties" >}}).

The response message's *status* property may contain the following codes:

| Code  | Description |
| :---- | :---------- |
| *204* | OK, the tenant identifiers have been accepted for processing. Note that this status code does not necessarily mean that command routing has already been enabled (again) for the given tenant identifiers. Implementors may also choose to accept the tenant identifiers and then (asynchronously) start to process them afterwards. In such a case, implementors are advised to implement adequate re-try logic for enabling command routing for each tenant identifier. |
| *400* | Bad Request, the body does not contain a valid JSON array of strings. |

For status codes indicating an error (codes in the `400 - 499` range) the message body MAY contain a detailed description of the error that occurred.


## Standard Message Properties

Due to the nature of the request/response message pattern of the operations of the Command Router API, there are some standard properties shared by all of the request and response messages exchanged as part of the operations.

### Standard Request Properties

The following table provides an overview of the properties shared by all request messages regardless of the particular operation being invoked.

| Name             | Mandatory | Location                 | AMQP Type    | Description |
| :--------------- | :-------: | :----------------------- | :----------- | :---------- |
| *subject*        | yes       | *properties*             | *string*     | MUST be set to the value defined by the particular operation being invoked. |
| *correlation-id* | no        | *properties*             | *message-id* | MAY contain an ID used to correlate a response message to the original request. If set, it is used as the *correlation-id* property in the response, otherwise the value of the *message-id* property is used. Either this or the *message-id* property MUST be set. |
| *message-id*     | no        | *properties*             | *string*     | MAY contain an identifier that uniquely identifies the message at the sender side. Either this or the *correlation-id* property MUST be set. |
| *reply-to*       | yes       | *properties*             | *string*     | MUST contain the source address that the client wants to received response messages from. This address MUST be the same as the source address used for establishing the client's receive link (see [Preconditions]({{< relref "#preconditions-for-invoking-the-command-router-api" >}})). |

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
