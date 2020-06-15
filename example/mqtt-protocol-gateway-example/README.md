# "Azure IoT Hub" Protocol Gateway

This Protocol Gateway shows how to use Hono's Protocol Gateway SDK to implement a production-ready protocol gateway. 
The MQTT-API of "Azure IoT Hub" serves as a working example. Parts of its API are mapped to Hono's communication patterns.

Full compatibility with the Azure IoT Hub is not a design goal of this example. It is supposed to behave similarly for 
the "happy path", but cannot treat all errors or misuse in the same way as the former.  

Supported are the following types of messages:

## Mapping of Azure IoT Hub messages to Hono messages

**Device-to-cloud communication**

| Azure IoT Hub message | Hono message | Limitations |
|---|---|---|
| [Device-to-cloud](https://docs.microsoft.com/en-us/azure/iot-hub/iot-hub-devguide-d2c-guidance) with QoS 0 (*AT MOST ONCE*) | [Telemetry](https://www.eclipse.org/hono/docs/api/telemetry/#forward-telemetry-data) | Messages are not brokered | 
| [Device-to-cloud](https://docs.microsoft.com/en-us/azure/iot-hub/iot-hub-devguide-d2c-guidance) with QoS 1 (*AT LEAST ONCE*) | [Event](https://www.eclipse.org/hono/docs/api/event/#forward-event) | Messages are not brokered | 


**Cloud-to-device communication**

| Hono message | Azure IoT Hub message | Limitations |
|---|---|---|
| [One-way Command](https://www.eclipse.org/hono/docs/api/command-and-control/#send-a-one-way-command) | [Cloud-to-device](https://docs.microsoft.com/en-us/azure/iot-hub/iot-hub-devguide-messages-c2d) | Messages are not brokered (ignores CleanSession flag) | 
| [Request/Response Command](https://www.eclipse.org/hono/docs/api/command-and-control/#send-a-request-response-command) | [Direct method](https://docs.microsoft.com/en-us/azure/iot-hub/iot-hub-devguide-direct-methods) | |

## Limitations

Not supported are the following features of Azure IoT Hub:
 
 * "device twins"
 * file uploads 
 * message brokering 
 * "jobs"
 * the back-end application API 
 * device authentication with client certificates

## Device Authentication

A Hono protocol gateway is responsible for the authentication of the devices.
This example implementation does not provide or require data storage for device credentials. 
Instead, it can only be configured to use a single demo device, which must already be present in Hono's device registry (see below).
Client certificate based authentication is not implemented.

Since there is only one device in this example implementation anyway, the credentials for the tenant's gateway client are not looked up dynamically, but are taken from the configuration.
 

## Prerequisites

### Configuration

The protocol gateway needs the configuration of:

1. the AMQP adapter of a running Hono instance to connect to
2. the MQTT server 
3. the Demo-Device to use.

By default, the gateway will connect to the AMQP adapter of the [Hono Sandbox](https://www.eclipse.org/hono/sandbox/).
However, it can also be configured to connect to a local instance.
The default configuration can be found in the file `example/mqtt-protocol-gateway-example/src/main/resources/application.yml` 
and can be customized using [Spring Boot Configuration](https://docs.spring.io/spring-boot/docs/current/reference/html/spring-boot-features.html#boot-features-external-config). 


### Registering Devices

The demo device to be used needs to be registered in Hono's device registry. 
The [Getting started](https://www.eclipse.org/hono/getting-started/#registering-devices) guide shows how to register a device.
 
 
### Starting a Receiver

Telemetry and event messages need an application that consumes the messages. 
The [Getting started](https://www.eclipse.org/hono/getting-started/#starting-the-example-application) guide shows how to start the example application that receives the messages.
 
 
## Starting the Protocol Gateway

~~~sh
# in directory: hono/example/mqtt-protocol-gateway-example/
mvn spring-boot:run
~~~

 
## Enable TLS 

Azure IoT Hub only provides connections with TLS and only offers port 8883. To start the protocol gateway listening
on this port with TLS enabled and a demo certificate, run:

~~~sh
# in base directory of Hono repository:
mvn clean install
cd example/mqtt-protocol-gateway-example/
java -jar target/mqtt-protocol-gateway-example*-exec.jar --spring.profiles.active=ssl
~~~

With the [Eclipse Mosquitto](https://mosquitto.org/) command line client, for example, sending an event message would then look like this:

~~~sh
# in directory: hono/example/mqtt-protocol-gateway-example/
mosquitto_pub -d -h localhost -p 8883 -i '4712' -u 'demo1' -P 'demo-secret'  -t "devices/4712/messages/events/" -m "hello world" -V mqttv311 --cafile target/config/hono-demo-certs-jar/trusted-certs.pem -q 1
~~~

Existing hardware devices might need to be configured to accept the used certificate. 

## Example Requests

With the [Eclipse Mosquitto](https://mosquitto.org/) command line client the requests look like the following.
 
**Telemetry**
 
~~~sh
mosquitto_pub -d -h localhost -i '4712' -u 'demo1' -P 'demo-secret' -t 'devices/4712/messages/events/?foo%20bar=b%5Fa%5Fz' -m "hello world" -V mqttv311 -q 0
~~~
 
**Events**
 
~~~sh
mosquitto_pub -d -h localhost -i '4712' -u 'demo1' -P 'demo-secret' -t 'devices/4712/messages/events/?foo%20bar=b%5Fa%5Fz' -m '{"alarm": 1}' -V mqttv311 -q 1
~~~
 
### Commands 

The example application can be used to send commands. 
The [Getting started](https://www.eclipse.org/hono/getting-started/#advanced-sending-commands-to-a-device) shows a walk-through example.

**Subscribe for one-way commands**
 
~~~sh
mosquitto_sub -v -h localhost -u "demo1" -P "demo-secret" -t 'devices/4712/messages/devicebound/#' -q 1
~~~
 
**Subscribe for request-response commands**
 
~~~sh
mosquitto_sub -v -h localhost -u "demo1" -P "demo-secret" -t '$iothub/methods/POST/#' -q 1
~~~

When Mosquitto receives the command, in the terminal should appear something like this:
~~~sh
$iothub/methods/POST/setBrightness/?$rid=0100bba05d61-7027-4131-9a9d-30238b9ec9bb {"brightness": 87}
~~~

**Respond to a command**
 
To send a response the ID after `rid=` can be copied and pasted into a new terminal to send a response like this:
~~~sh
export RID=0100bba05d61-7027-4131-9a9d-30238b9ec9bb
mosquitto_pub -d -h localhost -u 'demo1' -P 'demo-secret' -t "\$iothub/methods/res/200/?\$rid=$RID" -m ''{"success": true}' -q 1
~~~
Note that the actual identifier from the command must be used.
