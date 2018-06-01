+++
title = "Connection Events API"
weight = 450
+++

The *Connection Events* API allows protocol adapters to handle event of
devices establishing (or loosing) a connection with the protocol adapter. It
is up to a specific protocol adaper implementations if it makes use of this
API or not.

<!-- more -->

{{% note %}}
This API has been added in Hono 0.6. Previous versions do not support nor
implement the Connection Events API.
{{% /note %}}

The mechanism of handling such internal events is pluggable and the default
implementation simply logs this to the logging framework. 

# Via Hono Events API

However there is second default implementation in Hono which allows sending out
events using the [*Event* API]({{< relref "Event-API.md" >}}).

The internal connect/disconnect event will be translated into a Hono Event with
a well known message format and type. And it will be injected into the normal
event message stream.

{{% note title="Authenticated devices only" %}}
As the *Event* API requires a tenant in order to allow sending events, an
authenticated device is required. Internal events for unauthenticated devices
will be dropped by this implementation and not be delivered via the *Event* API.
{{% /note %}}

**Message format**

The following table provides an overview of the message structure:

| Name | Mandatory | Location | Type      | Description |
| :--- | :-------: | :------- | :-------- | :---------- |
| *content-type* | yes | *properties* | *symbol* | Must be set to  *application/vnd.eclipse-hono-dc-notification+json* |
| *device_id* | yes | *application-properties* | *string* | The ID of the authenticated device |

The payload is a JSON object with the following fields:

| Name | Mandatory | Type      | Description |
| :--- | :-------: | :-------- | :---------- |
| *cause* | yes | *string* | The cause of the connection event. Must be either *connected* or *disconnected*. |
| *remote-id* | yes | *string* | The ID of the remote endpoint which connected (e.g. a remote address, port, client id, ...). This is specific to the protocol adapter. |
| *source* | yes | *string* | The name of the protocol adapter. e.g. *hono-mqtt*. |
| *data* | no | *object* | An arbitrary JSON object which may contain additional information from the protocol adapter. |

Below is an example for a connection event:

~~~json
{
  "cause": "connect",
  "remote-id": "mqtt-client-id-1",
  "source": "hono-mqtt",
  "data": {
    "foo": "bar"
  }
}
~~~
