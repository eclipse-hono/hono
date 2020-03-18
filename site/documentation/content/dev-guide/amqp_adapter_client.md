+++
title = "AMQP Adapter Client for Java"
weight = 390
+++

Eclipse Hono&trade; comes with a Java client for the AMQP adapter. It is intended to be used to implement (prototypical)
devices, (protocol) gateways or to be used for (end-to-end) testing. It is based on Eclipse Vert.x.

The starting point is the class `AmqpAdapterClientFactory`.

It provides methods to get sender or receivers for the communication with the AMQP adapter. A sender is available for 
each of the following actions:

 * send telemetry message
 * send event message
 * send a response to a previously  received command
 

{{% note title="Do not re-use sender instances" %}}
The senders manage the underlying AMQP sender links, which are restored after a temporary interruption of the connection. 
('HonoConnection' handles automatic reconnection). To achieve this, a caching mechanism is used, so that 
 defective links are replaced by new ones. It is important that the sender is always retrieved from the factory before sending.
 Do not keep links to sender objects and reuse them for subsequent send operations!
{{% /note %}}


## Create a Client Instance 

```
String tenantId = ...;
HonoConnection connection = ...;
connection.connect();

AmqpAdapterClientFactory factory = AmqpAdapterClientFactory.create(connection, tenantId);
```


## Send Event

```
final String deviceId = ...;
final byte[] payload = ...;
final String contentType = ...;
final Map<String, ?> customApplicationProperties = ...;

factory.getOrCreateEventSender()
        .compose(eventSender -> eventSender.send(deviceId, payload, contentType, customApplicationProperties))
        .setHandler(asyncResult -> {
            if (asyncResult.succeeded()){
                System.out.println("Message has been successfully send. Remote state is: " + asyncResult.result().getRemoteState());
            } else {
                System.out.println("Message send failed" + asyncResult.cause());
            }
        });
```

## Send Telemetry message

Send a telemetry message with QoS *AT_MOST_ONCE*: 
```
final String deviceId = ...;
final byte[] payload = ...;
final String contentType = ...;
final Map<String, ?> customApplicationProperties = ...;

factory.getOrCreateTelemetrySender()
            .compose(telemetrySender -> telemetrySender.send(deviceId, payload, contentType, customApplicationProperties))
            .setHandler(asyncResult -> {
                if (asyncResult.succeeded()){
                    System.out.println("Message has been successfully send. Remote state is: " + asyncResult.result().getRemoteState());
                } else {
                    System.out.println("Message send failed" + asyncResult.cause());
                }
            });
```


Send a telemetry message with QoS *AT_LEAST_ONCE*: 
```
final String deviceId = ...;
final byte[] payload = ...;
final String contentType = ...;
final Map<String, ?> customApplicationProperties = ...;

factory.getOrCreateTelemetrySender()
            .compose(telemetrySender -> telemetrySender.sendAndWaitForOutcome(deviceId, payload, contentType, customApplicationProperties))
            .setHandler(asyncResult -> {
                if (asyncResult.succeeded()){
                    System.out.println("Message has been successfully send. Remote state is: " + asyncResult.result().getRemoteState());
                } else {
                    System.out.println("Message send failed" + asyncResult.cause());
                }
            });
```

## Receive a command

```
factory.createCommandConsumer( msg -> {
        System.out.println("Received message: " + MessageHelper.getPayloadAsString(msg);
    });
```

*NB:* The returned `MessageConsumer` should be closed when no commands should be received anymore.


Gateways can subscribe to commands for devices individually with the following code:
  
```
factory.createDeviceSpecificCommandConsumer(deviceIdOfTheDevice, msg -> {
            System.out.println("Received message: " + MessageHelper.getPayloadAsString(msg);
        });
```
