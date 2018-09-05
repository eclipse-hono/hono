+++
title = "Command & Control API"
weight = 415
+++

The *Command and Control* API is used by *Business Applications* to send **command** or **notification command** messages to devices connected to Hono. 

Command messages can be used to execute operations on devices, e.g. updating a configuration property, installing a software component or activating an actuator.
Commands are bidirectional, i.e. a device receiving a command is expected to send a response to it.

Notification command messages are unidirectional and cannot be responded.
<!--more-->

{{% note %}}
This API has been added in Hono 0.6. Previous versions do not support nor implement the Command & Control API.
The notification commands will be added during the development of Hono 0.8.
{{% /note %}}

The Command & Control API is defined by means of AMQP 1.0 message exchanges, i.e. a client needs to connect to Hono using AMQP 1.0 in order to invoke operations of the API as described in the following sections. Throughout the remainder of this page we will simply use AMQP when referring to AMQP 1.0.

There is also a description of Command & Control [at the conceptual level]({{< relref "/concepts/command-and-control.md" >}}) 
and the [Getting Started]({{< ref "getting-started" >}}) guide contains a walk through example.

# Preconditions
## Preconditions for sending a command or notification command

1. The *Business Application* has established an AMQP connection with the AMQP 1.0 Network.  
2. The *Business Application* has established an AMQP link in role *sender* with the target address `control/${tenant_id}/${device_id}`, where `${device_id}` is the ID of the device to send messages to and `${tenant_id}` is the ID of the tenant that the device belongs to.

**Link establishment**

The following sequence diagram shows the necessary links that need to be established:

![Send Command Preconditions](../command_control_send_preconditions.png)

## Preconditions for receiving a command response

*Command* messages (in contrast to *notification command* messages) need to be responded by the device. For this purpose,
the *Business Application* needs to open a receiver link as precondition: 

1. The *Business Application* has established an AMQP link in role *receiver* with the source address `control/${tenant_id}/${reply-id}`.
This link is used by the *Business Application* to receive the response of the executed command. This link’s source address is also referred to as `the reply-to address` for the request messages. 
2. The `${reply-id}` may be any arbitrary string chosen by the client.  

To scope a response to the device the `${reply-id}` should consist of the device-id plus an arbitrary string to scope the command sender instance. If all responses shall be received just in the context of the tenant, the device-id has to be omitted.   

**Link establishment**

The following sequence diagram shows the necessary links that need to be established to enable sending back a response to the Business Application:

![Send Command](../command_control_receive_preconditions.png)

# Outcomes for sending a command or notification command
Commands and notification commands that are forwarded by the AMQP 1.0 network to a protocol adapter need to be responded with following outcomes:

| Processing in protocol adapter   | AMQP outcome                  |
|----------------------------------|------------------------------ |
| Bad command                      | Rejected ("hono:bad-request") |
| Device not connected (anymore)   | Release                       | 
| Command delivered                | Accepted                      | 

For any outcome except for `Accepted` the *Business application* should immediately close the response receiver link again, 
if it was scoped to exactly one command response. If the receiver link is scoped to the tenant, it usually should remain 
open.


# Operations

The following API defines operations that can be used by *Business Applications* to send commands or notification commands to devices over an AMQP 1.0 Network and get a response in return to a sent command.

## Send a Notification Command

Business Applications use this operation to send a notification command to a device.

A notification command cannot be responded by the device so the result of this operation is purely based on the outcome of sending
it to the AMQP 1.0 network (see [Outcomes]({{< relref "#outcomes-for-sending-a-command-or-notification-command">}})).
 
Note that the outcome is usually determined by the receiving protocol adapter, which is omitted here for simplicity.

To send a notification command to a device, the first precondition needs to be fulfilled.

The following sequence diagram shows the message flow with a successful outcome:

![Send Notification Command successful outcome](../command_control_notification_Success.png)

The following sequence diagram shows the message flow with an unsuccessful outcome:

![Send Notification Command unsuccessful outcome](../command_control_notification_Unsuccessful_outcome.png)

**Message Format**

The following table provides an overview of the properties the *Business Application* needs to set on a notification command message.

| Name             | Mandatory | Location                 | Type         | Description |
| :--------------- | :-------: | :----------------------- | :----------- | :---------- |
| *subject*        | yes       | *properties*             | *string*     | MUST contain the notification command name to be executed by a device. |
| *content-type*   | no        | *properties*             | *string*     | If present, MUST contain a *Media Type* as defined by [RFC 2046](https://tools.ietf.org/html/rfc2046) which describes the semantics and format of the command's input data contained in the message payload. However, not all protocol adapters will support this property as not all transport protocols provide means to convey this information, e.g. MQTT 3.1.1 has no notion of message headers. |
| *message-id*     | yes       | *properties*             | *string*     | MUST contain an identifier that uniquely identifies the message at the sender side. |

The following property must **NOT** be set for a notification command message (to distinguish it from a command message):

| Name             | Mandatory       | Location                 | Type         |
| :--------------- | :---------------| :----------------------- | :----------- |
| *reply-to*       | must NOT be set | *properties*             | *string*     |


A notification command message MAY contain arbitrary payload to be sent to the device in an AMQP Data section. The value of the notification command message's *subject* value may provide a hint to the device regarding the format, encoding and semantics of the payload data.




## Send a Command

Business Applications use this operation to send a command to a device.
Depending on the outcome of sending the command to the AMQP 1.0 network, the application either expects a response from the device 
(to the response receiver link) or closes the receiver link again.

Note that the outcome is usually determined by the receiving protocol adapter, which is omitted here for simplicity.

To send a command to a device, both preconditions need to be fulfilled.

The following sequence diagram shows the message flow with a successful outcome:

![Send Command successful outcome](../command_control_Success.png)

The following sequence diagram shows the message flow with an unsuccessful outcome:

![Send Command unsuccessful outcome](../command_control_Unsuccessful_outcome.png)

**Message Format**

The following table provides an overview of the properties the *Business Application* needs to set on a command message.

| Name             | Mandatory | Location                 | Type         | Description |
| :--------------- | :-------: | :----------------------- | :----------- | :---------- |
| *subject*        | yes       | *properties*             | *string*     | MUST contain the command name to be executed by a device. |
| *content-type*   | no        | *properties*             | *string*     | If present, MUST contain a *Media Type* as defined by [RFC 2046](https://tools.ietf.org/html/rfc2046) which describes the semantics and format of the command's input data contained in the message payload. However, not all protocol adapters will support this property as not all transport protocols provide means to convey this information, e.g. MQTT 3.1.1 has no notion of message headers. |
| *correlation-id* | no        | *properties*             | *message-id* | If present, MUST contain an ID used to correlate a response message to the original request. If set, it is used as the *correlation-id* property in the response, otherwise the value of the *message-id* property is used. |
| *message-id*     | yes       | *properties*             | *string*     | MUST contain an identifier that uniquely identifies the message at the sender side. |
| *reply-to*       | yes       | *properties*             | *string*     | MUST contain the source address that the client wants to receive response messages from. This address MUST be the same as the source address used for establishing the client's receive link (see [Preconditions]({{< relref "#preconditions" >}})). |

A command message MAY contain arbitrary payload to be sent to the device in an AMQP Data section. The value of the command message's *subject* value may provide a hint to the device regarding the format, encoding and semantics of the payload data.

## Receiving the Command's execution result

After execution of a command on a device, the device MUST send a response message indicating the outcome of executing the command.

**Message Format**

The following sequence diagram shows the message flow of a command response with a successful outcome:

![Send Command response successful](../command_control_response_Success.png)

The following table provides an overview of the properties set on a Command's response message.

| Name             | Mandatory | Location                 | Type         | Description |
| :--------------- | :-------: | :----------------------- | :----------- | :---------- |
| *content-type*   | no        | *properties*             | *string*     | If present, MUST contain a *Media Type* as defined by [RFC 2046](https://tools.ietf.org/html/rfc2046) which describes the semantics and format of the command's input data contained in the message payload. However, not all protocol adapters will support this property as not all transport protocols provide means to convey this information, e.g. MQTT 3.1.1 has no notion of message headers. |
| *correlation-id* | yes       | *properties*             | *message-id* | MUST contain the correlation ID used to match the command message with the response message containing the result of execution on the device. |
| *status*         | yes       | *application-properties* | *integer*    | MUST indicate the status of the execution. See table below for possible values. |

The status property must contain a valid HTTP status code: <a name="status"></a>

| Code | Description |
| :--- | :---------- |
| *2xx* | The command has been processed successfully. The semantics of the individual codes are specific to the adapter and device, which are involved in the processing of the command. |
| *4xx* | The command could not be processed due to a client error, e.g. malformed message payload. The semantics of the individual codes are specific to the adapter and device, which are involved in the processing of the command. |
| *5xx* | The command could not be processed due to a problem at the device side. The semantics of the individual codes are specific to the adapter and device, which are involved in the processing of the command. |

For status codes indicating an error (codes in the `400 - 599` range) the message body MAY contain a detailed description of the error that occurred.

If a command message response contains a payload, the body of the message MUST consist of a single AMQP *Data* section containing the response message data. 
