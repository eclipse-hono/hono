+++
title = "Command & Control API"
weight = 440
+++

The *Command and Control* API is used by *Business Applications* to send commands to devices connected to Hono via a *Protocol Adapter*. Control messages can be used to
execute operations on devices, e.g. updating a configuration property, installing a software component or activating an actuator.
<!--more-->

{{% warning %}}
THIS IS A FIRST DRAFT VERSION OF THE API SPEC AND THUS STILL SUBJECT TO DISCUSSION. IN PARTICULAR, THE COMMAND AND CONTROL API IS NOT YET IMPLEMENTED IN HONO.
IF YOU THINK THAT THE SPEC SHOULD BE CHANGED OR CAN BE IMPROVED PLEASE DO NOT HESITATE TO OPEN AN ISSUE OR DROP US A LINE ON THE MAILING LIST.
{{% /warning %}}

The Command & Control API is defined by means of AMQP 1.0 message exchanges, i.e. a client needs to connect to Hono using AMQP 1.0 in order to invoke operations of the API as described in the following sections. Throughout the remainder of this page we will simply use AMQP when referring to AMQP 1.0.

# Operations

The following API defines operations that can be used by *Business Applications* to send control messages to *Protocol Adapters* (and by *Protocol Adapters* to receive them) as well as the response from the *Protocol Adapters* back to the *Business Applications*.

## Send and Receive a Command

**Preconditions**

1. *Protocol Adapter* and *Business Application* have established an AMQP connection with the AMQP 1.0 Network.
2. *Protocol Adapter* has established an AMQP link in role *receiver* with the AMQP 1.0 Network using target address `control/${tenant_id}/${device_id}`. Where `${device_id}` is the ID of the device to receive control messages for and `${tenant_id}` is the ID of the tenant the device belongs to. 
3. *Business Application* has established an AMQP link in role *sender* (also using target address `control/${tenant_id}/${device_id}`). 

**Link establishment and Message Flow**

The following sequence diagram shows the whole message exchange, together with the reply from the *Protocol Adapter* to the *Business Application* (which is defined above in detail).

![Send Command](../command_control_Success.png)

**Message Format**

The following table provides an overview of the properties the *Business Application* needs to set on a *Control* message.

| Name | Mandatory | Location | Type      | Description |
| :--- | :-------: | :------- | :-------- | :---------- |
| *message-id* | yes | *properties* | *string* | MUST contain the message ID used to match incoming control message with the response message containing result of execution. |
| *subject* | yes | *properties* | *string* | MUST contain the command to be executed by a device. |
| *content-type* | no | *properties* | *symbol*  | SHOULD be set to *application/octet-stream* if the message payload is to be considered *opaque* binary data. In most cases though, the client should be able to set a more specific content type indicating the type and characteristics of the data contained in the payload, e.g. `text/plain; charset="utf-8"` for a text message or `application/json` etc. |
| *ttl* | no | *properties* | *int* | Indicates the timeout value (in milliseconds) indicating when server can assume that command has not been executed because of the lack of the response from the client. |

If a control message requires additional data to be properly executed (like command parameters), the body of the message MUST consist of a single AMQP *Data* section containing a control 
message data. The format and encoding of the data is indicated by the *subject*. It is up to the device to interpret the incoming body by taking it into 
consideration.

## Sending back control message execution result

After execution of a control message on a device, device MUST send a response message indicating a status of that execution.

**Preconditions**

1. *Protocol Adapter* and *Business Application* have established an AMQP connection with the AMQP 1.0 Network.
2. *Protocol Adatper* has established an AMQP link in role *sender* with Hono using target address `control-reply/${tenant_id}/{$device_id}`. Where `${device_id}` is an ID of the
device that received the control message and `${tenant_id}` is the ID of the tenant the device belongs to.
3. *Business Application* has established an AMQP link in role *receiver* with the AMQP 1.0 Network. 

(The message flow is shown in the sequence diagram above to show the whole sequence in one place)

**Message Format**

The following table provides an overview of the properties the *Protocol Adapter* needs to set on a *Control-Reply* message.

| Name | Mandatory | Location | Type | Description |
| :--- | :-------: | :------- | :-------- | :---------- |
| *correlation-id* | yes | *properties* | *string* | MUST contain the correlation ID used to match incoming control message with the response message containing result of execution. |
| *status* | yes | *application-properties* | *integer* | MUST indicates the status of the execution. See table below for possible values. |
| *content-type* | no | *properties* | *symbol*  | SHOULD be set to *application/octet-stream* if the message payload is to be considered *opaque* binary data. In most cases though, the client should be able to set a more specific content type indicating the type and characteristics of the data contained in the payload, e.g. `text/plain; charset="utf-8"` for a text message or `application/json` etc. |


The status property must contain a valid HTTP status code:

| Code | Description |
| :--- | :---------- |
| *200* | The command was successfully processed and the response message contains a result. |
| *202* | The command was accepted and will be processed. A response will be sent once the command is processed. |
| *204* | The command was successfully processed with an empty result. |
| *400* | The command could not be processed due to a malformed message. |

For status codes indicating an error (codes in the `400 - 499` range) the message body MAY contain a detailed description of the error that occurred.

If a control message response contains some additional diagnostic information, the body of the message MUST consist of a single AMQP *Data* section containing a control message data. 

