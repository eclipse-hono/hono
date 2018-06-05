+++
title = "Connection Events"
weight = 200
+++

Hono internally allows protocol adapters to emit connection events. Indicating
if a connection was established between a device and the protocol adapter.

<!-- more -->

This functionality is intended for connection oriented protocols (like MQTT and
AMQP) and it works on a "best effort" basis to create such events. It depends
on each protocol adapter implementation if it emits such events or not.

{{% note title="Since 0.6.0" %}}
This feature has been added in Hono 0.6. Previous versions do not support nor
implement connection events.
{{% /note %}}

The mechanism of handling such internal events is pluggable and the default
implementation simply logs this to the logging framework.

There is a second implementation which uses the *Hono Events* API to send
events to the events channel. It sends a well known message as described
in [Connection Events]({{< relref "api/Event-API.md#connection-events" >}}).

