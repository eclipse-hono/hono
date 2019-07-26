+++
title = "Connection Events"
weight = 200
+++

Hono's protocol adapters can use *connection events* to indicate the connection
status of a device. In particular, an adapter can notify downstream components
about a newly established connection with a device or about a device having
disconnected.
<!-- more -->

The connection status of devices using stateful protocols like MQTT and AMQP can usually
be determined quite easily because these protocols often require peers to explicitly open
or close a connection and often also support a kind of heart beat which can be used to
determine if a connection is still alive. However, for stateless protocols like HTTP
or CoAP, there is no clear definition of what it actually means that a device is
*connected*. It is obvious that a device is connected when it is sending an HTTP request.
However, the adapter has no way of knowing if the device has gone to sleep after it has
received the adapter's response to its request.

That said, connection events indicating an established connection with a device can
usually be taken as face value. However, connection events indicating the disconnection
of a device may only represent the protocol adapters view of the device's connection
status. For example, the HTTP adapter might consider a device *disconnected* because
it hasn't received any requests from the device for some time. However, the device itself
might as well be up and running (i.e. not sleeping) and simply have no data worth publishing.

The mechanism of firing connection events is pluggable with the default implementation
simply forwarding connection status information to the logging framework.
Hono also comes with an alternative implementation which forwards connection status
information by means of [Connection Events]({{< relref "/api/event#connection-event" >}})
via the *Events* API.
