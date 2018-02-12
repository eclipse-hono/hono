+++
title = "Consuming Messages from Java"
weight = 385
+++

To illustrate how Eclipse Hono&trade; can be integrated with Java code, a simple program is provided that consumes telemetry or event data 
from the default tenant from a device. 
It shall serve as a blueprint to integrate your existing java source code with Hono. 

The code is found in the [example](https://github.com/eclipse/hono/tree/master/example) module in the package [org.eclipse.hono.vertx.example](https://github.com/eclipse/hono/tree/master/example/src/main/java/org/eclipse/hono/vertx/example).

The provided classes are kept as simple as possible (in the tradition of a classical "Hello World" implementation).
This means that they make use of simple constant definitions, deal with exceptions as rarely as possible and use a few `System.out`'s.

Please refer to the javadoc of the classes for details.
 
{{% note %}}
Note that production ready code likely has to think more about error handling and logging than this simple blueprint. 
{{% /note %}}

## Configure the example

For simplicity, all configurations are defined as Java constants inside the class [HonoExampleConstants](https://github.com/eclipse/hono/blob/master/example/src/main/java/org/eclipse/hono/vertx/example/base/HonoExampleConstants.java).

If you have Hono running in Docker under `localhost`, the example should work out of the box.
Otherwise, please check and change the values to your needs (they are documented inside the class) and recompile them.
   

## Run the example

The application waits for messages until you press any key or kill it.

### Telemetry Data

`$ mvn exec:java -Dexec.mainClass=org.eclipse.hono.vertx.example.HonoTelemetryConsumer`

All received telemetry data are printed to the console.

### Event Data
  
`$ mvn exec:java -Dexec.mainClass=org.eclipse.hono.vertx.example.HonoEventConsumer`

All received event data are printed to the console.


Please note that consumers do not connect with Hono directly, but rather with an AMQP router network. 
In the standard setup of Hono this is the [qdrouter](https://qpid.apache.org/components/dispatch-router/index.html) from the Apache Qpid project.
In production scenarios this might be a large setup of AMQP routers, brokers, etc.

### Encryption of communication 
  
For the encrypted communication with Hono, the necessary truststore is already installed and used by the Hono client.

If you want to integrate the code with your own software, please copy the provided truststore from the Hono project, e.g. like

    $ mkdir -p ${yourProjectDir}/src/main/resources/certs
    $ cp ${honoProjectDir}/demo-certs/certs/trusted-certs.pem ${yourProjectDir}/src/main/resources/certs

and adopt the code pointing to the file location.

