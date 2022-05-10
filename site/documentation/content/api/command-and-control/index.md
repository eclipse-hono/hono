---
title: "Command & Control API Specification for AMQP 1.0"
linkTitle: "Command & Control API (AMQP 1.0)"
weight: 419
resources:
  - src: device_not_connected.svg
  - src: malformed_message.svg
  - src: one_way_success.svg
  - src: receive_preconditions.svg
  - src: response_time_out.svg
  - src: send_preconditions.svg
  - src: control_success.svg
---

The *Command & Control* API of Eclipse Hono&trade; is used by *Business Applications* to send commands to connected
devices.

Commands can be used to trigger actions on devices. Examples include updating a configuration property, installing a
software component or switching the state of an actuator.
<!--more-->

Hono distinguishes two types of commands. The first type is *one-way* only. In this case the sender of the command
does not expect the device to send back a message in response to the command. This type of command is referred to as
a *one-way command* in the remainder of this page. One-way commands may be used to e.g. *notify* a device about a
change of state.

The second type of commands expects a *response* to be sent back from the device as a result of processing the command.
In this case the response contains a *status* code which indicates whether the command could be processed successfully.
If so, the response may also include data representing the result of processing the command. This type of command is
plainly referred to as a *command* because it represents the default case.

The Command & Control API is defined by means of AMQP 1.0 message exchanges, i.e. a client needs to connect to Hono
using AMQP 1.0 in order to invoke operations of the API as described in the following sections. Throughout the
remainder of this page we will simply use AMQP when referring to AMQP 1.0.

The Command & Control API for AMQP 1.0 is an alternative to the
[Command & Control API for Kafka]({{< relref "/api/command-and-control-kafka" >}}) for applications that want to
use an AMQP 1.0 Messaging Network instead of an Apache Kafka&trade; broker to send command messages to devices.


## Send a One-Way Command

Business Applications use this operation to send a command to a device for which they do not expect to receive a
response from the device.

**Preconditions**

1. The *Business Application* has established an AMQP connection with the AMQP 1.0 Network.
2. The *Business Application* has established an AMQP link in role *sender* with the target address `command/${tenant_id}`,
   where `${tenant_id}` is the ID of the tenant that the device belongs to. This link is used by the *Business
   Application* to send command messages.

The following sequence diagram illustrates the establishment of the required link:

{{< figure src="send_preconditions.svg" title="Application connecting to the AMQP 1.0 Messaging Network" >}}

**Message Format**

The following table provides an overview of the properties the *Business Application* needs to set on a one-way command
message.

| Name             | Mandatory | Location                 | Type         | Description |
| :--------------- | :-------: | :----------------------- | :----------- | :---------- |
| *to*             | yes       | *properties*             | *string*     | MUST contain the target address `command/${tenant_id}/${device_id}` of the message, where `${device_id}` is the ID of the device to send the message to. |
| *subject*        | yes       | *properties*             | *string*     | The name of the command to be executed by the device. |
| *content-type*   | no        | *properties*             | *string*     | If present, MUST contain a *Media Type* as defined by [RFC 2046](https://tools.ietf.org/html/rfc2046) which describes the semantics and format of the command's input data contained in the message payload. However, not all protocol adapters will support this property as not all transport protocols provide means to convey this information, e.g. MQTT 3.1.1 has no notion of message headers. |
| *message-id*     | no        | *properties*             | *string*     | An identifier that uniquely identifies the message at the sender side. |

The command message MAY contain arbitrary payload to be sent to the device in a single AMQP *Data* section. The value of the message's *subject* property may provide a hint to the device regarding the format, encoding and semantics of the payload data.

Hono indicates the outcome of the operation by means of the following AMQP delivery states:

| Delivery State | Description                                      |
| :------------- | :----------------------------------------------- |
| `accepted`    | The command has been delivered to the device for processing. |
| `released`    | The command has not been delivered to the device or can not be processed by the device due to reasons that are not the responsibility of the sender of the command. Possible reasons are:<ul><li>The device is (currently) not connected.</li><li>In case the transport protocol supports acknowledgements: The device hasn't sent an acknowledgement in time or has indicated that the message cannot be processed.</li><li>There was an error forwarding the command to the device.</li></ul> |
| `rejected`    | The command has not been delivered to or processed by the device because the command message does not contain all required information. Another reason may be that a message limit has been exceeded. |

{{% notice info %}}
Hono relies on the particular protocol adapter to deliver commands to devices. Depending on the used transport
protocol and the adapter's implementation, the `accepted` outcome may thus indicate any of the following:

* An *attempt* has been made to deliver the command to the device. However, it is unclear if the device has received
  (or processed) the command.
* The device has acknowledged the reception of the command but has not processed the command yet.
* The device has received and processed the command.
{{% /notice %}}

**Examples**

The following sequence diagram shows the successful delivery of a one-way command called `switchOn` to device `4711` of
the `DEFAULT_TENANT`:

{{< figure src="one_way_success.svg" title="Successfully send a One-Way Command" >}}

The following sequence diagram shows how the delivery of the same one-way command fails because the device is not
connected:

{{< figure src="device_not_connected.svg" title="Device not connected" >}}

The following sequence diagram illustrates how a malformed command sent by a *Business Application* gets rejected:

{{< figure src="malformed_message.svg" title="Malformed Command message" >}}



## Send a (Request/Response) Command

*Business Applications* use this operation to send a command to a device for which they expect the device to send
back a response.

<a name="receiver-link-precondition"></a>

**Preconditions**

1. The *Business Application* has established an AMQP connection with the AMQP 1.0 Network.
1. The *Business Application* has established an AMQP link in role *sender* with the target address `command/${tenant_id}`,
   where `${tenant_id}` is the ID of the tenant that the device belongs to. This link is used by the *Business
   Application* to send command messages.
1. The *Business Application* has established an AMQP link in role *receiver* with the source address
   `command_response/${tenant_id}/${reply_id}`. This link is used by the *Business Application* to receive the response to
   the command from the device. This link’s source address is also used as the `reply-to` address for the request
   messages. The `${reply_id}` may be any arbitrary string chosen by the application.

The following sequence diagram illustrates the establishment of the required links:

{{< figure src="receive_preconditions.svg" title="Application connecting to the AMQP 1.0 Messaging Network" >}}

**Command Message Format**

The following table provides an overview of the properties the *Business Application* needs to set on a command message.

| Name             | Mandatory | Location                 | Type         | Description |
| :--------------- | :-------: | :----------------------- | :----------- | :---------- |
| *to*             | yes       | *properties*             | *string*     | MUST contain the target address `command/${tenant_id}/${device_id}` of the message, where `${device_id}` is the ID of the device to send the message to. |
| *subject*        | yes       | *properties*             | *string*     | MUST contain the command name to be executed by a device. |
| *content-type*   | no        | *properties*             | *string*     | If present, MUST contain a *Media Type* as defined by [RFC 2046](https://tools.ietf.org/html/rfc2046) which describes the semantics and format of the command's input data contained in the message payload. However, not all protocol adapters will support this property as not all transport protocols provide means to convey this information, e.g. MQTT 3.1.1 has no notion of message headers. |
| *correlation-id* | no        | *properties*             | *string*     | MAY contain an ID used to correlate a response message to the original request. If set, it is used as the *correlation-id* property in the response, otherwise the value of the *message-id* property is used. Either this or the *message-id* property MUST be set. |
| *message-id*     | no        | *properties*             | *string*     | MAY contain an identifier that uniquely identifies the message at the sender side. Either this or the *correlation-id* property MUST be set. |
| *reply-to*       | yes       | *properties*             | *string*     | MUST contain the source address that the application expects to receive the response from. This address MUST be the same as the source address used for establishing the client's receiver link (see [Preconditions](./#receiver-link-precondition) above). |

The command message MAY contain arbitrary payload to be sent to the device in a single AMQP *Data* section. The value of the command message's *subject* value may provide a hint to the device regarding the format, encoding and semantics of the payload data.

Hono uses following AMQP delivery states to indicate the outcome of sending the command to the device:

| Delivery State | Description                                      |
| :------------- | :----------------------------------------------- |
| `accepted`    | The command has been delivered to the device for processing. |
| `released`    | The command has not been delivered to the device or can not be processed by the device due to reasons that are not the responsibility of the sender of the command. Possible reasons are:<ul><li>The device is (currently) not connected.</li><li>In case the transport protocol supports acknowledgements: The device hasn't sent an acknowledgement in time or has indicated that the message cannot be processed.</li><li>There was an error forwarding the command to the device.</li></ul> |
| `rejected`    | The command has not been delivered to or processed by the device because the command message does not contain all required information. Another reason may be that a message limit has been exceeded. |

{{% notice info %}}
Hono relies on the particular protocol adapter to deliver commands to devices. Depending on the used transport
protocol and the adapter's implementation, the `accepted` outcome may thus indicate any of the following:

* An *attempt* has been made to deliver the command to the device. However, it is unclear if the device has received
  (or processed) the command.
* The device has acknowledged the reception of the command but has not processed the command yet.

An application can determine the overall outcome of the operation by means of the response to the command that is
sent back by the device. An application should consider execution of a command to have failed, if it does not receive
a response within a reasonable amount of time.
{{% /notice %}}

**Response Message Format**

The following table provides an overview of the properties set on a message sent in response to a command.

| Name             | Mandatory | Location                 | Type         | Description |
| :--------------- | :-------: | :----------------------- | :----------- | :---------- |
| *content-type*   | no        | *properties*             | *string*     | If present, MUST contain a *Media Type* as defined by [RFC 2046](https://tools.ietf.org/html/rfc2046) which describes the semantics and format of the command's input data contained in the message payload. However, not all protocol adapters will support this property as not all transport protocols provide means to convey this information, e.g. MQTT 3.1.1 has no notion of message headers. |
| *correlation-id* | yes       | *properties*             | *string*     | MUST contain the correlation ID used to match the command message with the response message containing the result of execution on the device. |
| *creation-time*  | yes       | *properties*             | *timestamp*  | The instant in time when the message has been created (see the [AMQP 1.0 specification](http://docs.oasis-open.org/amqp/core/v1.0/amqp-core-messaging-v1.0.html) for details). |
| *device_id*      | yes       | *application-properties* | *string*     | The identifier of the device that sent the response. |
| *status*         | yes       | *application-properties* | *int*        | MUST indicate the status of the execution. See table below for possible values. |
| *tenant_id*      | yes       | *application-properties* | *string*     | The identifier of the tenant that the device belongs to. |

The *status* property must contain an [HTTP 1.1 response status code](https://tools.ietf.org/html/rfc7231#section-6):

| Code | Description |
| :--- | :---------- |
| *2xx* | The command has been processed successfully. |
| *4xx* | The command could not be processed due to a client error, e.g. malformed message payload. |
| *5xx* | The command could not be processed due to an internal problem at the device side. |

The semantics of the individual codes are specific to the device and command. For status codes indicating an error
(codes in the `400 - 599` range) the message body MAY contain a detailed description of the error that occurred.

If a command message response contains a payload, the body of the message MUST consist of a single AMQP *Data* section
containing the response message data.

**Examples**

The following sequence diagram illustrates how a *Business Application* sends a command called `getReading` to device
`4711` of the `DEFAULT_TENANT` and receives a response from the device:

{{< figure src="request_response_success.svg" title="Successfully send a Command" >}}

The sending of a command may fail for the same reasons as those illustrated for sending a one-way command. Additionally,
the sending of a command may be considered unsuccessful by an application if it does not receive the response from the
device in a reasonable amount of time:

{{< figure src="response_time_out.svg" title="Command times out" >}}
