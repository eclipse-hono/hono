---
title: "Command & Control API Specification for Google Pub/Sub"
linkTitle: "Command & Control API (Pub/Sub)"
weight: 422
---

The *Command & Control* API of Eclipse Hono&trade; is used by *Business Applications* to send commands to connected
devices.

Commands can be used to trigger actions on devices. Examples include updating a configuration property, installing a
software component or switching the state of an actuator.

Hono distinguishes two types of commands. The first type is *one-way* only. In this case the sender of the command does
not expect the device to send back a message in response to the command. This type of command is referred to as a
*one-way command* in the remainder of this page. One-way commands may be used to e.g. *notify* a device about a change
of state.

The second type of commands expects a *response* to be sent back from the device as a result of processing the command.
In this case the response contains a *status* code which indicates whether the command could be processed successfully.
If so, the response may also include data representing the result of processing the command. This type of command is
plainly referred to as a *command* because it represents the default case.

The Command & Control API is defined by means of Google Pub/Sub message exchanges, i.e. a client needs to connect to
Hono and configure Hono for Google Pub/Sub messaging infrastructure in order to use it and invoke operations of the API
as described in the following sections. Throughout the remainder of this page we will simply use *Pub/Sub* when
referring to Google Pub/Sub.

The Command & Control API for Pub/Sub is an alternative to the
[Command & Control API for Kafka]({{< relref "/api/command-and-control-kafka" >}}) for applications that want to
use Pub/Sub instead of an Apache Kafka&trade; broker to send command messages to devices.

{{% notice warning %}}
Support for the Google Pub/Sub based Command & Control API is considered **experimental** and may change without
further notice.
{{% /notice %}}

## Send a One-Way Command

Business Applications use this operation to send a command to a device for which they do not expect to receive a
response from the device.
For that, the Business Application connects to *Pub/Sub* and writes a message to the tenant-specific topic
`projects/${google_project_id}/topics/${tenant_id}.command` where `${google_project_id}` is the ID of the Google Cloud
project and `${tenant_id}` is the ID of the tenant that the client wants to send the command for.

**Preconditions**

1. A Google Cloud Project is set up with the Pub/Sub API enabled.
2. The ID of the Google Cloud Project is declared as mentioned in the [Publisher and Subscriber Configuration]({{<
   relref "/admin-guide/pubsub-config#publisher-and-subscriber-configuration" >}}).
3. The *Business Application* is authorized to write to the topic.
4. The topic `projects/${google_project_id}/topics/${tenant_id}.command` exists.
5. The subscription `projects/${google_project_id}/subscriptions/${tenant_id}.command` exists.

**Message Flow**

1. The *Business Application* writes a command message to the
   topic `projects/${google_project_id}/topics/${tenant_id}.command` on *Pub/Sub*.
2. Hono consumes the message through the subscription `projects/${google_project_id}/subscriptions/${tenant_id}.command`
   from *Pub/Sub* and forwards it to the device, provided that the target device is connected and is accepting commands.

**Message Format**

Metadata MUST be set as Pub/Sub attributes on a message. The following table provides an overview of the attributes the
*Business Application* needs to set on a one-way command message.

| Name           | Mandatory | Type     | Description                                                   |
|:---------------|:---------:|:---------|:--------------------------------------------------------------|
| *device_id*    |    yes    | *string* | The identifier of the device that the command is targeted at. |
| *subject*      |    yes    | *string* | The name of the command to be executed by the device.         |
| *content-type* |    no     | *string* | If present, MUST contain a *Media Type* as defined by [RFC 2046](https://tools.ietf.org/html/rfc2046) which describes the semantics and format of the command's input data contained in the message payload. However, not all protocol adapters will support this property as not all transport protocols provide means to convey this information, e.g. MQTT 3.1.1 has no notion of message headers. |

The command message MAY contain arbitrary payload, set as message value, to be sent to the device. The value of the
message's *subject* attribute may provide a hint to the device regarding the format, encoding and semantics of the
payload
data.

## Send a (Request/Response) Command

*Business Applications* use this operation to send a command to a device for which they expect the device to send back
a response.
For that, the Business Application connects to *Pub/Sub* and writes a message to the tenant-specific topic
`projects/${google_project_id}/topics/${tenant_id}.command` where `${google_project_id}` is the ID of the Google Cloud
project and `${tenant_id}` is the ID of the tenant that the client wants to send the command for.

The Business Application can consume the corresponding command response by creating a subscription from the
`projects/${google_project_id}/topics/${tenant_id}.command_response` topic.

In contrast to a one-way command, a request/response command contains a *response-required* attribute with value `true`
and a *correlation-id* attribute, providing the identifier that is used to correlate a response message to the original
request.

**Preconditions**

1. The topics `projects/${google_project_id}/topics/${tenant_id}.command`
   and `projects/${google_project_id}/topics/${tenant_id}.command_response` exist
2. The subscription `projects/${google_project_id}/subscriptions/${tenant_id}.command` to the
   topic `projects/${google_project_id}/topics/${tenant_id}.command` exists.
3. A subscription to the topic `projects/${google_project_id}/topics/${tenant_id}.command_response` exists.
4. The *Business Application* is authorized to write to the `projects/${google_project_id}/topics/${tenant_id}.command`
   topic and read from the subscriptions.

**Message Flow**

1. The *Business Application* writes a command message to the
   `projects/${google_project_id}/topics/${tenant_id}.command` topic on *Pub/Sub*.
2. Hono consumes the message from *Pub/Sub* and forwards it to the device, provided that the target device is
   connected and is accepting commands.
3. The device sends a command response message. Hono writes that message to
   the `projects/${google_project_id}/topics/${tenant_id}.command_response` topic
   on *Pub/Sub*.
4. The *Business Application* consumes the command response message from an independently created subscription to
   the `projects/${google_project_id}/topics/${tenant_id}.command_response` topic.

**Command Message Format**

Metadata MUST be set as Pub/Sub attributes on a message.
The following table provides an overview of the attributes the *Business Application* needs to set on a request/response
command message.

| Name                                         | Mandatory | Type      | Description                                                                                                                                                                                                                                                                                                                                                                                           |
|:---------------------------------------------|:---------:|:----------|:------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| *correlation-id*                             |    yes    | *string*  | The identifier used to correlate a response message to the original request. It is used as the *correlation-id* attribute in the response.                                                                                                                                                                                                                                                            |
| *device_id*                                  |    yes    | *string*  | The identifier of the device that the command is targeted at.                                                                                                                                                                                                                                                                                                                                         |
| *response-required*                          |    yes    | *boolean* | MUST be set with a value of `true`, meaning that the device is required to send a response for the command.                                                                                                                                                                                                                                                                                           |
| *subject*                                    |    yes    | *string*  | The name of the command to be executed by the device.                                                                                                                                                                                                                                                                                                                                                 |
| *content-type*                               |    no     | *string*  | If present, MUST contain a *Media Type* as defined by [RFC 2046](https://tools.ietf.org/html/rfc2046) which describes the semantics and format of the command's input data contained in the message payload. However, not all protocol adapters will support this property as not all transport protocols provide means to convey this information, e.g. MQTT 3.1.1 has no notion of message headers. |
| *delivery-failure-notification-metadata[\*]* |    no     | *string*  | Attributes with the *delivery-failure-notification-metadata* prefix are adopted for the error command response that is sent in case delivering the command to the device failed. In case of a successful command delivery, these attributes are ignored.                                                                                                                                              |

The command message MAY contain arbitrary payload, set as message value, to be sent to the device. The value of the
message's *subject* attribute may provide a hint to the device regarding the format, encoding and semantics of the payload
data.

An application can determine the overall outcome of the operation by means of the response to the command. The response
is either sent back by the device, or in case the command could not be successfully forwarded to the device, an error
command response message is sent by the Hono protocol adapter or Command Router component.

**Response Message Format**

Metadata MUST be set as Pub/Sub attributes on a message.
The following table provides an overview of the attributes set on a message sent in response to a command.

| Name                                         | Mandatory | Type      | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
|:---------------------------------------------|:---------:|:----------|:--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| *correlation-id*                             |    yes    | *string*  | MUST contain the value of the *correlation-id* attribute of the request message that this is the response for.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          |
| *creation-time*                              |    yes    | *long*    | The instant in time (milliseconds since the Unix epoch) when the message has been created.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              |
| *device_id*                                  |    yes    | *string*  | The identifier of the device that sent the response.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    |
| *status*                                     |    yes    | *integer* | MUST indicate the status of the execution. See table below for possible values.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         |
| *tenant_id*                                  |    yes    | *string*  | The identifier of the tenant that the device belongs to.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |
| *content-type*                               |    no     | *string*  | If present, MUST contain a *Media Type* as defined by [RFC 2046](https://tools.ietf.org/html/rfc2046) which describes the semantics and format of the command's input data contained in the message payload. However, not all protocol adapters will support this property as not all transport protocols provide means to convey this information, e.g. MQTT 3.1.1 has no notion of message headers.<br>If the response is an error message sent by the Hono protocol adapter or Command Router component, the content type MUST be *application/vnd.eclipse-hono-delivery-failure-notification+json*. |
| *delivery-failure-notification-metadata[\*]* |    no     | *string*  | MUST be adopted from the command message attributes with the same names *if* the response is an error message sent by the Hono protocol adapter or Command Router component.                                                                                                                                                                                                                                                                                                                                                                                                                            |

The *status* attribute must contain an [HTTP 1.1 response status code](https://tools.ietf.org/html/rfc7231#section-6).

The command response message MAY contain arbitrary payload, set as message value, to be sent to the business
application.

**Response Message sent from Device**

In a response from a device, the semantics of the *status* code ranges are as follows:

| Code  | Description                                                                               |
|:------|:------------------------------------------------------------------------------------------|
| *2xx* | The command has been processed successfully.                                              |
| *4xx* | The command could not be processed due to a client error, e.g. malformed message payload. |
| *5xx* | The command could not be processed due to an internal problem at the device side.         |

The semantics of the individual codes are specific to the device and command. For status codes indicating an error
(codes in the `400 - 599` range) the message body MAY contain a detailed description of the error that occurred.

**Response Message sent from Hono Component**

If the command response message represents an error message sent by the Hono protocol adapter or Command Router
component, with the *content-type* attribute set to *application/vnd.eclipse-hono-delivery-failure-notification+json*,
the possible status codes are:

| Code  | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     |
|:------|:------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| *4xx* | The command has not been delivered to or processed by the device because the command message does not contain all required information. Another reason may be that a message limit has been exceeded.                                                                                                                                                                                                                                                                                           |
| *5xx* | The command has not been delivered to the device or can not be processed by the device due to reasons that are not the responsibility of the sender of the command. Possible reasons are:<ul><li>The device is (currently) not connected.</li><li>In case the transport protocol supports acknowledgements: The device hasn't sent an acknowledgement in time or has indicated that the message cannot be processed.</li><li>There was an error forwarding the command to the device.</li></ul> |

The payload MUST contain a UTF-8 encoded string representation of a single JSON object with the following fields:

| Name    | Mandatory | Type     | Description                                                                      |
|:--------|:---------:|:---------|:---------------------------------------------------------------------------------|
| *error* |    yes    | *string* | The error message describing the cause for the command message delivery failure. |
