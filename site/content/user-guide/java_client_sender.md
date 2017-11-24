+++
title = "Java - integrate Hono sender"
weight = 220
+++

To illustrate how Hono can be integrated with Java code, a simple program is provided that sends telemetry or event data 
to the default tenant from a device. 
It shall serve as a blueprint to integrate your existing java source code with Hono. 

The code is found in the example module in the package `org.eclipse.hono.vertx.example`.

The provided classes are kept as simple as possible (in the tradition of a classical "Hello World" implementation).
This means that they make use of simple constant definitions, deal with exceptions as rarely as possible and use a few `System.out`'s.

This sender example consists of:

 - a base class `AbstractHonoSender` that implements all necessary code to get Hono's client running. It sends data 50 times
   in sequence and shows the necessary programming patterns for that.
 - a base configuration class `HonoExampleConstants` that defines the details where your Hono installation can be found etc.
 - the very short Java main program `HonoTelemetrySender` resp. `HonoEventSender` that derives from `AbstractHonoSender`
   and invokes the code that sends data.


{{% warning %}}
Note that production ready code likely has to think more about error handling and logging than this simple blueprint.
{{% /warning %}}

## Configure the example

For simplicity, all configurations are defined as Java constants inside the class `HonoExampleConstants`.

If you have Hono running in Docker under `localhost`, the example should work out of the box.
Otherwise, please check and change the values to your needs (they are documented inside the class).
   
If you changed values, you need to recompile the examples. Please invoke 

`$ mvn compile`

to activate your changes.


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



## Implementation

Instead of describing the full implementation, here are the rough details of what is happening inside the sender example:

 - first, the sender needs to get a registration assertion (technically a `token`) for the device. This needs one Hono client
   that connects to Hono's Device Registry.
 - next, the sender obviously needs a connection to Hono Messaging. This needs a second Hono client.
 - connection details are all configured in the `HonoExampleConstants` class.
 - since Hono's clients are based on vertx, the instantiation is done asynchronously with vertx Futures.
   Please refer to the [vertx documentation](http://vertx.io/docs/) for any introduction to that.
 - after both Hono clients are connected, the sender sends a payload sequentially to Hono Messaging and finishes
   after repeating that 50 times.
