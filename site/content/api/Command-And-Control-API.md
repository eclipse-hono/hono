+++
title = "Command & Control API"
weight = 415
+++

The *Command and Control* API is used by *Business Applications* to send commands to devices connected to Hono. Command messages can be used to
execute operations on devices, e.g. updating a configuration property, installing a software component or activating an actuator.
<!--more-->

{{% note %}}
This API has been added in Hono 0.6. Previous versions do not support nor implement the Command & Control API.
{{% /note %}}

The Command & Control API is defined by means of AMQP 1.0 message exchanges, i.e. a client needs to connect to Hono using AMQP 1.0 in order to invoke operations of the API as described in the following sections. Throughout the remainder of this page we will simply use AMQP when referring to AMQP 1.0.

There is also a general description of the [Command and Control Concept]({{< relref "concepts/command-and-control.md" >}}) 
and an example setup which is described here: [Command and Control Userguide]({{< relref "user-guide/command-and-control.md" >}}).

# Operations

The following API defines operations that can be used by *Business Applications* to send commands to devices over an AMQP 1.0 Network and get a response in return.

## Send a Command

### Preconditions

1. *Business Application* has established an AMQP connection with the AMQP 1.0 Network.  
2. *Business Application* has established an AMQP link in role *sender* with the target address `control/${tenant_id}/${device_id}`. Where `${device_id}` is the ID of the device to send messages to and `${tenant_id}` is the ID of the tenant that the device belongs to.
3. *Business Application* has established an AMQP link in role *receiver* with the source address `control/${tenant_id}/${reply-id}`. Where `${reply-id}` may be any arbitrary string chosen by the client. This link is used by the client to receive the response of the executed command. This link’s source address is also referred to as the reply-to address for the request messages. 

To scope each response to the device the `${reply-id}` could consist of the device-id plus an arbitrary string to scope the command sender instance. If all responses should be received just in the context of the tenant, the device-id could be omitted.   

**Link establishment and Message Flow**

The following sequence diagram shows the whole message exchange including the response being sent back to the Business Application.

![Send Command](../command_control_Success.png)

**Message Format**

The following table provides an overview of the properties the *Business Application* needs to set on a command message.

| Name | Mandatory | Location | Type      | Description |
| :--- | :-------: | :------- | :-------- | :---------- |
| *subject*        | yes       | *properties*             | *string*     | MUST contain the command name to be executed by a device. |
| *correlation-id* | no        | *properties*             | *message-id* | MAY contain an ID used to correlate a response message to the original request. If set, it is used as the *correlation-id* property in the response, otherwise the value of the *message-id* property is used. |
| *message-id*     | yes       | *properties*             | *string*     | MUST contain an identifier that uniquely identifies the message at the sender side. |
| *reply-to*       | yes       | *properties*             | *string*     | MUST contain the source address that the client wants to receive response messages from. This address MUST be the same as the source address used for establishing the client's receive link (see [Preconditions]({{< relref "#preconditions" >}})). |

A command message MAY contain arbitrary payload to be sent to the device in an AMQP Data section. The value of the command message's *subject* value may provide a hint to the device regarding the format, encoding and semantics of the payload data.

## Receiving the Command's execution result

After execution of a command on a device, the device MUST send a response message indicating the outcome of executing the command.

**Message Format**

The following table provides an overview of the properties set on a Command's response message.

| Name | Mandatory | Location | Type | Description |
| :--- | :-------: | :------- | :-------- | :---------- |
| *correlation-id* | yes | *properties* | *message-id* | MUST contain the correlation ID used to match the command message with the response message containing the result of execution on the device. |
| *status* | yes | *application-properties* | *integer* | MUST indicate the status of the execution. See table below for possible values. |

The status property must contain a valid HTTP status code: <a name="status"></a>

| Code | Description |
| :--- | :---------- |
| *2xx* | The command has been processed successfully. The semantics of the individual codes are specific to the adapter and device, which are involved in the processing of the command. |
| *4xx* | The command could not be processed due to a client error, e.g. malformed message payload. The semantics of the individual codes are specific to the adapter and device, which are involved in the processing of the command. |
| *5xx* | The command could not be processed due to a problem at the device side. The semantics of the individual codes are specific to the adapter and device, which are involved in the processing of the command. |

For status codes indicating an error (codes in the `400 - 599` range) the message body MAY contain a detailed description of the error that occurred.

If a command message response contains a payload, the body of the message MUST consist of a single AMQP *Data* section containing the response message data. 
