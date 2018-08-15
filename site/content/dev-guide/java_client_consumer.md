+++
title = "Consuming Messages from Java"
weight = 385
+++

To illustrate how Eclipse Hono&trade; can be integrated with Java code, a simple application is provided that consumes telemetry and event data 
for devices belonging to the default tenant. 

It also includes support for Command and Control:

if indicated by a received downstream message that contains a `ttd` value (refer to [Device notifications]({{< relref "concepts/device-notifications.md" >}}) for details) it tries to send a command to the device.
If the value of `ttd` indicates that the device stays connected for an unlimited time (`ttd == -1`), the application will periodically repeat to send a command until
notified the device is disconnected again (`ttd == 0`).

This application shall serve as a blueprint to integrate your existing java source code with Hono. 
Its code is found in the [example](https://github.com/eclipse/hono/tree/master/example) module in the package [org.eclipse.hono.vertx.example](https://github.com/eclipse/hono/tree/master/example/src/main/java/org/eclipse/hono/vertx/example).

The provided classes are kept as simple as possible (in the tradition of a classical "Hello World" implementation) while still
covering the most relevant messaging patterns (downstream and upstream messages).
For this purpose they make use of simple constant definitions and deal with exceptions as rarely as possible.
You may want to change the level of detail that should be logged to the console by editing the contained `resources/logback.xml` file.

Please refer to the javadoc of the classes for details.
 
{{% note %}}
Note that production ready code likely has to think more about error handling and logging than this simple blueprint. 
{{% /note %}}

## Configure the example

For simplicity, all configurations are defined as Java constants inside the class [HonoExampleConstants](https://github.com/eclipse/hono/blob/master/example/src/main/java/org/eclipse/hono/vertx/example/base/HonoExampleConstants.java).

If you have Hono running in Docker under `localhost`, the example should work out of the box.

Some configuration values can be overridden by providing them as property to the application.

This includes the `host` and the `port` of the AMQP network.
In the standard setup of Hono they should be configured to the [qdrouter](https://qpid.apache.org/components/dispatch-router/index.html) from the Apache Qpid project.
In production scenarios this might be a large setup of AMQP routers, brokers, etc.

Please refer to the class `HonoExampleConstants` to find out which part of the application can be configured by properties.

## Run the example

The application waits for messages until you press any key or kill it.

It is started by

`$ mvn exec:java -Dexec.mainClass=org.eclipse.hono.vertx.example.HonoConsumer`

or - if e.g. the host of the AMQP network should be changed - 
 
`$ mvn exec:java -Dexec.mainClass=org.eclipse.hono.vertx.example.HonoConsumer -Dconsumer.host=192.168.99.100`


### Telemetry and Event messages

Depending on the logger configuration, all received downstream messages are printed to the console.

Please note that consumers do not connect with Hono directly, but rather with an AMQP router network. 

### Command and Control

By using a helper class provided by Hono, a callback in the application code is invoked when a downstream message was received
that signals the device will stay connected to the protocol adapter for some time (see [Device notifications]({{< relref "concepts/device-notifications.md" >}}) for details).

Inside this callback an arbitrary simple command is sent down to the device (once or periodically) and the response is logged to the console.

### Encryption of communication 
  
For the encrypted communication with Hono, the necessary truststore is already installed and used by the Hono client.

If you want to integrate the code with your own software, please copy the provided truststore from the Hono project, e.g. like

    $ mkdir -p ${yourProjectDir}/src/main/resources/certs
    $ cp ${honoProjectDir}/demo-certs/certs/trusted-certs.pem ${yourProjectDir}/src/main/resources/certs

and adopt the code pointing to the file location.

