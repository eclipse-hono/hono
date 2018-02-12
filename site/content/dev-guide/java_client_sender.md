+++
title = "Sending Messages from Java"
weight = 390
+++

To illustrate how Eclipse Hono&trade; can be integrated with Java code, a simple program is provided that sends telemetry or event data 
to the default tenant from a device. 
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

First you have to ensure that the device `4711` (which is used inside the example code) is registered with Hono.
This can be done by sending a `http POST` request to Hono's device registry - please refer to the 
[Getting Started]({{< relref "getting-started.md#publishing-data" >}}) page to see how this is done by using `curl`.  

After the device is registered, you start the (correctly configured) example for sending data as:
 
### Telemetry Data

`$ mvn exec:java -Dexec.mainClass=org.eclipse.hono.vertx.example.HonoTelemetrySender`

You need at least one consumer listening to the tenant, otherwise the sender does not have any credits for sending
it's data (which will be reported for each message). 


### Event Data
  
`$ mvn exec:java -Dexec.mainClass=org.eclipse.hono.vertx.example.HonoEventSender`

Events can be sent, even if no consumer is listening to the events. They will be queued and delivered as soon as a consumer
is started.

### Encryption of communication 
  
For the encrypted communication with Hono, the necessary truststore is already installed and used by the Hono client.

If you want to integrate the code with your own software, please copy the provided truststore from the Hono project, e.g. like

    $ mkdir -p ${yourProjectDir}/src/main/resources/certs
    $ cp ${honoProjectDir}/demo-certs/certs/trusted-certs.pem ${yourProjectDir}/src/main/resources/certs

and adopt the code pointing to the file location.
