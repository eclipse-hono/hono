+++
title = "AMQP Adapter Client for Java"
weight = 390
+++

Eclipse Hono&trade; comes with a Java client for the AMQP adapter. It is intended for the implementation of
(prototype) devices, (protocol) gateways and/or (end-to-end) tests. The client is based on Eclipse Vert.x.

The client is represented by the [AmqpAdapterClient](https://github.com/eclipse-hono/hono/blob/master/clients/device-amqp/src/main/java/org/eclipse/hono/client/device/amqp/AmqpAdapterClient.java)
class in the *hono-client-device-amqp* module.
Instances can be created using the `create(HonoConnection)` factory method.

The client provides methods for each of the following operations:

 * Getting a receiver for receiving command & control messages
   <br>(via the [AmqpAdapterClient](https://github.com/eclipse-hono/hono/blob/master/clients/device-amqp/src/main/java/org/eclipse/hono/client/device/amqp/AmqpAdapterClient.java)
   `createCommandConsumer` / `createDeviceSpecificCommandConsumer` methods)
 * Sending a telemetry message
   <br>(via the inherited [TelemetrySender](https://github.com/eclipse-hono/hono/blob/master/clients/device-amqp/src/main/java/org/eclipse/hono/client/device/amqp/TelemetrySender.java)
   `sendTelemetry` method)
 * Sending an event message
   <br>(via the inherited [EventSender](https://github.com/eclipse-hono/hono/blob/master/clients/device-amqp/src/main/java/org/eclipse/hono/client/device/amqp/EventSender.java)
  `sendEvent` method)
 * Sending a response to a previously received command
   <br>(via the inherited [CommandResponder](https://github.com/eclipse-hono/hono/blob/master/clients/device-amqp/src/main/java/org/eclipse/hono/client/device/amqp/CommandResponder.java)
   `sendCommandResponse` method)

Please refer to the javadoc of the classes for details.

For examples of how to use the client, see the example implementation in the [AmqpExampleDevice](https://github.com/eclipse-hono/hono/blob/master/examples/hono-client-examples/src/main/java/org/eclipse/hono/devices/AmqpExampleDevice.java) class.

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
