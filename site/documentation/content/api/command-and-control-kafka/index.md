---
title: "Command & Control API for Kafka Specification"
linkTitle: "Command & Control API for Kafka"
weight: 419
---

The *Command & Control* API of Eclipse Hono&trade; is used by *Business Applications* to send commands to connected devices.

Commands can be used to trigger actions on devices. Examples include updating a configuration property, installing a software component or switching the state of an actuator.
<!--more-->

Hono distinguishes two types of commands. The first type is *one-way* only. In this case the sender of the command does not expect the device to send back a message in response to the command. This type of command is referred to as a *one-way command* in the remainder of this page. One-way commands may be used to e.g. *notify* a device about a change of state.

The second type of commands expects a *response* to be sent back from the device as a result of processing the command. In this case the response contains a *status* code which indicates whether the command could be processed successfully. If so, the response may also include data representing the result of processing the command. This type of command is plainly referred to as a *command* because it represents the default case.

The Command & Control API for Kafka is an alternative to the [Command & Control API for AMQP]({{< relref "/api/command-and-control" >}}).
With this API, clients send command messages to an Apache Kafka&reg; cluster instead of an AMQP Messaging Network.

See [Kafka-based APIs]({{< relref "/api/kafka-api" >}}) for fundamental information about Hono's Kafka-based APIs.
The statements there apply to this specification.

{{% note title="Tech preview" %}}
The support of Kafka as a messaging system is currently a preview and not yet ready for production. The APIs are subject to change without prior notice.
{{% /note %}}

## Send a One-Way Command

Business Applications use this operation to send a command to a device for which they do not expect to receive a response from the device.
For that, the Business Application connects to the *Kafka Cluster* and writes a message to the tenant-specific topic `hono.command.${tenant_id}` where `${tenant_id}` is the ID of the tenant that the client wants to send the command for.

**Preconditions**

1. Either the topic `hono.command.${tenant_id}` exists, or the broker is configured to automatically create topics on demand.
1. The *Business Application* is authorized to write to the topic.

**Message Flow**

1. The *Business Application* writes a command message to the topic `hono.command.${tenant_id}` on the *Kafka Cluster*.
1. Hono consumes the message from the *Kafka Cluster* and forwards it to the device, provided that the target device is connected and is accepting commands.

**Message Format**

The key of the message MUST be the ID of the device that the command is targeted at.

Metadata MUST be set as Kafka headers on a message.
The following table provides an overview of the headers the *Business Application* needs to set on a one-way command message.

| Name               | Mandatory | Type      | Description |
| :----------------- | :-------: | :-------- | :---------- |
| *device_id*        | yes       | *string*  | The identifier of the device that the command is targeted at. |
| *subject*          | yes       | *string*  | The name of the command to be executed by the device. |
| *content-type*     | no        | *string*  | If present, MUST contain a *Media Type* as defined by [RFC 2046](https://tools.ietf.org/html/rfc2046) which describes the semantics and format of the command's input data contained in the message payload. However, not all protocol adapters will support this property as not all transport protocols provide means to convey this information, e.g. MQTT 3.1.1 has no notion of message headers. |

The command message MAY contain arbitrary payload, set as message value, to be sent to the device. The value of the message's *subject* header may provide a hint to the device regarding the format, encoding and semantics of the payload data.

## Send a (Request/Response) Command

*Business Applications* use this operation to send a command to a device for which they expect the device to send back a response.
For that, the Business Application connects to the *Kafka Cluster* and writes a message to the tenant-specific topic `hono.command.${tenant_id}` where `${tenant_id}` is the ID of the tenant that the client wants to send the command for.
The Business Application can consume the corresponding command response from the `hono.command_response.${tenant_id}` topic.

In contrast to a one-way command, a request/response command contains a *response-expected* header with value `true` and a *correlation-id* header, providing the identifier that is used to correlate a response message to the original request.

**Preconditions**

1. Either the topics `hono.command.${tenant_id}` and `hono.command_response.${tenant_id}` exist, or the broker is configured to automatically create topics on demand.
1. The *Business Application* is authorized to write to the `hono.command.${tenant_id}` topic and read from the `hono.command_response.${tenant_id}` topic.
1. The *Business Application* is subscribed to the `hono.command_response.${tenant_id}` topic with a Kafka consumer.

**Message Flow**

1. The *Business Application* writes a command message to the topic `hono.command.${tenant_id}` on the *Kafka Cluster*.
1. Hono consumes the message from the *Kafka Cluster* and forwards it to the device, provided that the target device is connected and is accepting commands.
1. The device sends a command response message. Hono writes that message to the `hono.command_response.${tenant_id}` topic on the *Kafka Cluster*.
1. The *Business Application* consumes the command response message from the `hono.command_response.${tenant_id}` topic.

**Command Message Format**

The key of the message MUST be the ID of the device that the command is targeted at.

Metadata MUST be set as Kafka headers on a message.
The following table provides an overview of the headers the *Business Application* needs to set on a request/response command message.

| Name                | Mandatory | Type      | Description |
| :------------------ | :-------: | :-------- | :---------- |
| *correlation-id*    | yes       | *string*  | The identifier used to correlate a response message to the original request. It is used as the *correlation-id* header in the response. |
| *device_id*         | yes       | *string*  | The identifier of the device that the command is targeted at. |
| *response-expected* | yes       | *boolean* | MUST be set with a value of `true`, meaning that a response from the device is expected for the command. |
| *subject*           | yes       | *string*  | The name of the command to be executed by the device. |
| *content-type*      | no        | *string*  | If present, MUST contain a *Media Type* as defined by [RFC 2046](https://tools.ietf.org/html/rfc2046) which describes the semantics and format of the command's input data contained in the message payload. However, not all protocol adapters will support this property as not all transport protocols provide means to convey this information, e.g. MQTT 3.1.1 has no notion of message headers. |

The command message MAY contain arbitrary payload, set as message value, to be sent to the device. The value of the message's *subject* header may provide a hint to the device regarding the format, encoding and semantics of the payload data.

An application can determine the overall outcome of the operation by means of the response to the command that is sent back by the device. An application should consider execution of a command to have failed, if it does not receive a response within a reasonable amount of time.

**Response Message Format**

The key of the message MUST be the ID of the device that the command response is from.

Metadata MUST be set as Kafka headers on a message.
The following table provides an overview of the headers set on a message sent in response to a command.

| Name               | Mandatory | Type      | Description |
| :----------------- | :-------: | :-------- | :---------- |
| *correlation-id*   | yes       | *string*  | MUST contain the value of the *correlation-id* header of the request message that this is the response for. |
| *device_id*        | yes       | *string*  | The identifier of the device that sent the response. |
| *status*           | yes       | *integer* | MUST indicate the status of the execution. See table below for possible values. |
| *content-type*     | no        | *string*  | If present, MUST contain a *Media Type* as defined by [RFC 2046](https://tools.ietf.org/html/rfc2046) which describes the semantics and format of the command's input data contained in the message payload. However, not all protocol adapters will support this property as not all transport protocols provide means to convey this information, e.g. MQTT 3.1.1 has no notion of message headers. |

The *status* header must contain an [HTTP 1.1 response status code](https://tools.ietf.org/html/rfc7231#section-6):

| Code  | Description |
| :---- | :---------- |
| *2xx* | The command has been processed successfully. |
| *4xx* | The command could not be processed due to a client error, e.g. malformed message payload. |
| *5xx* | The command could not be processed due to an internal problem at the device side. |

The semantics of the individual codes are specific to the device and command. For status codes indicating an error (codes in the `400 - 599` range) the message body MAY contain a detailed description of the error that occurred.

The command response message MAY contain arbitrary payload, set as message value, to be sent to the business application.
