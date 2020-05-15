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

A Hono Protocol Gateway is responsible for the authentication of the devices because the devices are not necessarily registered in Hono's device registry.
This example implementation does not provide or require data storage for device credentials. 
Instead, it can only be configured to use a single demo device, which must already be present in Hono's device registry 
 (the [Getting started](https://www.eclipse.org/hono/getting-started/#registering-devices) guide shows how to register a device).
Client certificate based authentication is not implemented.

Since there is only one device in this example implementation anyway, the credentials for the tenant's gateway client are not looked up dynamically, but are taken from the configuration.
 
 
## Enable TLS 

~~~sh
# in base directory of Hono repository:
mvn clean install
cd example/mqtt-protocol-gateway-example/
java -jar target/mqtt-protocol-gateway-example*-exec.jar --spring.profiles.active=ssl
~~~

Azure IoT Hub only provides connections with TLS and only offers port 8883. 
This protocol gateway can of course not provide an Azure Server certificate, 
but if the hostname verification can be disabled on the device, it should connect to TLS. 

With the [Eclipse Mosquitto](https://mosquitto.org/) command line client, for example, sending a message would then look like this:

~~~sh
# in directory: hono/example/mqtt-protocol-gateway-example/
mosquitto_pub -h localhost -u 'demo1' -P 'demo-secret' -t 'devices/4712/messages/events' -m '5 degrees' -p 8883 --insecure --cafile target/config/hono-demo-certs-jar/trusted-certs.pem
~~~
 