+++
title = "Consuming Messages from Java"
weight = 397
+++

To illustrate how Eclipse Hono&trade; can be integrated with Java code, a simple application is provided that
consumes telemetry and event data for devices belonging to the default tenant.

It also includes support for Command & Control:

* If indicated by a received downstream message that contains a `ttd` value (refer to
  [Device notifications]({{< relref "concepts/device-notifications/index.md" >}}) for details) it tries to send a command
  to the device.
* If the value of `ttd` indicates that the device stays connected for an unlimited time (`ttd == -1`), the application will
  periodically repeat to send a command until notified the device is disconnected again (`ttd == 0`).

This application shall serve as a blueprint to integrate your existing Java source code with Hono.
Its code is found in the [example](https://github.com/eclipse-hono/hono/tree/master/examples) module in the package
[org.eclipse.hono.vertx.example](https://github.com/eclipse-hono/hono/tree/master/examples/hono-client-examples/src/main/java/org/eclipse/hono/vertx/example).

The provided classes are kept as simple as possible (in the tradition of a classical *Hello World* implementation) while still
covering the most relevant messaging patterns (downstream and upstream messages).
For this purpose they make use of simple constant definitions and deal with exceptions as rarely as possible.
You may want to change the level of detail that should be logged to the console by editing the contained `resources/logback.xml` file.

Please refer to the JavaDoc of the classes for details.
 
{{% notice info %}}
Note that production ready code likely has to think more about error handling and logging than this simple blueprint.
{{% /notice %}}

## Configure the Example

For simplicity, all configurations are defined as Java constants inside the class
[HonoExampleConstants](https://github.com/eclipse-hono/hono/blob/master/examples/hono-client-examples/src/main/java/org/eclipse/hono/vertx/example/base/HonoExampleConstants.java).

If you have Hono running in Docker under `localhost`, the example should work out of the box.

Some configuration values can be overridden by providing them as property to the application.

This includes the `host` and the `port` of the AMQP network.
In the standard setup of Hono they should be configured to the [qdrouter](https://qpid.apache.org/components/dispatch-router/index.html)
from the Apache Qpid project. In production scenarios this might be a large setup of AMQP routers, brokers, etc.

Please refer to the class `HonoExampleConstants` to find out which part of the application can be configured by properties.

## Run the Example

The application waits for messages until you press any key or kill it.

It is started by

~~~sh
# in directory: hono/examples/hono-client-examples/
mvn clean package
mvn exec:java -Dexec.mainClass=org.eclipse.hono.vertx.example.HonoExampleApplication
~~~

or (if e.g. the host of the AMQP network should be changed):

~~~sh
mvn exec:java -Dexec.mainClass=org.eclipse.hono.vertx.example.HonoExampleApplication -Dconsumer.host=192.168.99.100
~~~


### Telemetry and Event messages

Depending on the logger configuration, all received downstream messages are printed to the console.

Please note that consumers do not connect with Hono directly, but rather with an AMQP router network.

### Command and Control

By using a helper class provided by Hono, a callback in the application code is invoked when a downstream
message was received that signals the device will stay connected to the protocol adapter for some time (see
[Device notifications]({{< relref "concepts/device-notifications/index.md" >}}) for details).

Inside this callback an arbitrary simple command is sent down to the device (once or periodically) and the
response is logged to the console.

### Encryption of Communication 
  
For the encrypted communication with Hono, the necessary truststore is already installed and used by the Hono client.

If you want to integrate the code with your own software, please copy the provided truststore
(`hono/demo-certs/certs/trusted-certs.pem`) from the Hono project to the `resources` directory of your project
and adopt the code pointing to the file location.

### Kafka based Messaging

To use Kafka based messaging instead of an AMQP network, set the property `kafka` to `true` provide the host and port
of a bootstrap server:

~~~sh
mvn exec:java -Dexec.mainClass=org.eclipse.hono.vertx.example.HonoExampleApplication -Dconsumer.host=192.168.99.100 -Dconsumer.port=9092 -Dkafka=true
~~~

Additional properties for the Kafka producers and consumers can be added in the code, for example to enable
encrypted communication. For the available configuration options refer to the
[Kafka documentation](https://kafka.apache.org/documentation/#configuration).
