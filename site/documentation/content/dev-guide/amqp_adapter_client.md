+++
title = "AMQP Adapter Client for Java"
weight = 390
+++

Eclipse Hono&trade; comes with a Java client for the AMQP adapter. It is intended for the implementation of 
(prototype) devices, (protocol) gateways or (end-to-end) tests. The client is based on Eclipse Vert.x.

The starting point is the class `AmqpAdapterClientFactory`.

The factory provides methods to get a receiver for receiving commands and a sender for each of the following actions:

 * send a telemetry message
 * send an event message
 * send a response to a previously received command
 

{{% notice tip %}}
The senders manage the underlying AMQP sender links, which are restored after temporary interruption of the connection
(`HonoConnection` manages the automatic reconnection). To achieve this, a caching mechanism is used, so that
defective links are replaced by new ones. The sender must be always retrieved from the factory when sending
because otherwise the link might no longer exist.
So, do not hold references to sender objects and re-use them for subsequent send operations!
{{% /notice %}}


For examples of how to use the client, see the example implementation in the `AmqpExampleDevice` class.

## Usage in a Gateway

There are two ways to subscribe to commands for use in a gateway:

1. Gateway subscribing for commands for a specific device: 
The gateway subscribes individually for each connected device by creating a command consumer for the specific device.
1. Gateway subscribing for commands for all its devices: 
The gateway subscribes for commands sent to all the different devices that the gateway acts on behalf of by 
creating a command consumer that is not scoped on a specific device. 

Subscribing for all devices does not work if multiple instances of the gateway are running at the same time, 
because a command is only sent to one receiver, and this may be an instance that has no connection to the device. 
On the other hand, subscribing for a specific device once it connects to the gateway instance 
(and closing the subscription once it disconnects) may not work for device-oriented protocols that are not connection-based.

## Tracing 

The AMQP Adapter Client supports tracing of the messages with [OpenTracing](https://opentracing.io/). To use tracing, each of the
senders returned by the factory can be cast to an interface with the same name and the prefix "Traceable" 
(e.g. cast `TelemetrySender` to `TraceableTelemetrySender`).
The traceable interfaces provide *send* methods with an additional `SpanContext` parameter. 
