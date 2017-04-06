+++
title = "Command & Control API"
weight = 440
+++

The *Command and Control* API is used by *Business Applications* to send commands to devices connected to Hono. Control messages can be used to
execute operations on devices, e.g. updating a configuration property, installing a software component or activating an actuator.
<!--more-->

{{% warning %}}
THIS IS A FIRST DRAFT VERSION OF THE API SPEC AND THUS STILL SUBJECT TO DISCUSSION. IN PARTICULAR, THE COMMAND AND CONTROL API IS NOT YET IMPLEMENTED IN HONO.
IF YOU THINK THAT THE SPEC SHOULD BE CHANGED OR CAN BE IMPROVED PLEASE DO NOT HESITATE TO OPEN AN ISSUE OR DROP US A LINE ON THE MAILING LIST.
{{% /warning %}}

A client can be a device connected to Hono directly (using an AMQP connection) or via a *Protocol Adapter* (for example via MQTT,
LWM2M, etc). Devices and *Protocol Adapters* can also poll Hono periodically in order check for new command & control messages without
maintaining a constant connection to Hono infrastructure.

The Command & Control API is defined by means of AMQP 1.0 message exchanges, i.e. a client needs to connect to Hono using an AMQP 1.0 client in 
order to invoke operations of the API as described in the following sections.

# Southbound Operations

The following operations can be used by *Devices* and/or *Protocol Adapters* to receive command & control messages sent by *Business Applications*.

## Receive Control Message

**Preconditions**

1. Client has established an AMQP connection with Hono.
2. Client has established an AMQP link in role *receiver* with Hono using target address `control/${tenant_id}/${device_id}`. Where `${device_id}` is the ID of the device to receive control messages for and `${tenant_id}` is the ID of the tenant the device belongs to.

**Message Flow**

*TODO* add sequence diagram

**Message Format**

The following table provides an overview of the properties a client needs to set on a *Control* message.

| Name | Mandatory | Location | Type      | Description |
| :--- | :-------: | :------- | :-------- | :---------- |
| *message-id* | yes | *properties* | UTF-8 *string* | MUST contain the message ID used to match incoming control message with the response message containing result of execution. |
| *device_id* | yes | *application-properties* | UTF-8 *string* | MUST contain the ID of the device the data in the payload has been reported by. |
| *tenant_id* | yes | *application-properties* | UTF-8 *string* | MAY be set by the client to indicate which tenant the the device belongs to. |
| *command* | no | *application-properties* | UTF-8 *string* | MUST contain the command to be executed by a device. |
|*ttl* | no | *properties* | *int* | Indicates the timeout value (in milliseconds) indicating when server can assume that command has not been executed because of the lack of the response from the client. Default timeout is *60000* (1 minute). |
|*durable* | no | *properties* | *boolean* | Indicates if the message should be persisted (*store and forward*) by the underlying message transport middleware. By default command&control messages are durable.  |

If a control message requires additional data to be properly executed (like command parameters), the body of the message MUST consist of a single AMQP *Data* section containing a control 
message data. The format and encoding of the data is indicated by the *command*. It is up to the device to interpret the incoming body by taking `command` into 
consideration.

## Sending back control message execution result

After execution of a control message on a device, device MUST send a response message indicating a status of that execution.

**Preconditions**

1. Client has established an AMQP connection with Hono.
2. Client has established an AMQP link in role *sender* with Hono using target address `control-reply/${tenant_id}/{$device_id}`. Where `${device_id}` is an ID of the
device that received the control message and `${tenant_id}` is the ID of the tenant the device belongs to.

**Message Flow**

*TODO* add sequence diagram

**Message Format**

The following table provides an overview of the properties a client needs to set on a *Control* message.

| Name | Mandatory | Location | Type | Description |
| :--- | :-------: | :------- | :-------- | :---------- |
| *correlation-id* | yes | *properties* | UTF-8 *string* | MUST contain the correlation ID used to match incoming control message with the response message containing result of execution. |
| *status* | yes | *application-properties* | *integer* | MUST indicates the status of the execution. See table below for possible values. |

The status property must contain a valid HTTP status code:

| Code | Description |
| :--- | :---------- |
| *200* | The command was successfully processed and the response message contains a result. |
| *202* | The command was accepted and will be processed. A response will be sent once the command is processed. |
| *204* | The command was successfully processed with an empty result. |
| *400* | The command could not be processed due to a malformed message. |

For status codes indicating an error (codes in the `400 - 499` range) the message body MAY contain a detailed description of the error that occurred.

If a control message response contains some additional diagnostic information, the body of the message MUST consist of a single AMQP *Data* section containing a control 
message data. The body must be a descriptive message indicating what happened during the control message execution. It is primarily used for monitoring and debugging purposes.
The format and encoding of the data MUST be indicated by the *content-type* and (optional) *content-encoding* properties of the message.

# Northbound Operations

### Send control message

**Preconditions**

1. Client has established an AMQP connection with Hono.
2. Client has established an AMQP link in role *sender* with Hono using target address `control/${tenant_id}/${device_id}` where `${device_id}` is the ID of the device the message should be sent to and `${tenant_id}` is the ID of the tenant the device belongs to.

**Message Flow**

*TODO* add sequence diagram

**Message Format**

The format of the messages containing the control message is the same as for the *Receiving control message* operation.

*TODO* specify delivery states used to signal successful transmission of data.
