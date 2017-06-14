+++
title = "Java - integrate Hono sender"
weight = 280
+++

Simple example of a Java main program that sends telemetry data to the default tenant from the registered default device. 
It shall serve as a blueprint to integrate your existing java source code with Hono. 
<!--more-->



The goal of the following description is to extend an arbitrary Java program to send telemetry data *downstream* to Hono.

For that the following example is kept as simple as possible (in the tradition of a classical "Hello World" implementation).
This means that we will make use of simple constant definitions, deal with exceptions as rarely as possible and use a few `System.out`'s.

The only exception to pure simplicity is that we will send several messages in sequence to show the necessary programming patterns.

{{% warning %}}
Note that production ready code likely has to think more about error handling and logging than this simple blueprint.
{{% /warning %}}

## Setup your project

To have a common base, we start with a simple maven project that generates a java main application by using the maven generate archetype like that:

```shell
$ mvn archetype:generate -B -DarchetypeGroupId=org.apache.maven.archetypes \
-DarchetypeArtifactId=maven-archetype-quickstart -DarchetypeVersion=1.1 \
-DgroupId=org.eclipse.hono -DartifactId=honoTelemetryDownstreamSender -Dversion=0.1-SNAPSHOT -Dpackage=org.eclipse.hono
```

If you wish to use your own java project you can skip this step. The integration of the following steps into your existing source code will be straightforward.

## Configure your project

Next we need to extend the maven configuration :
 
### Enable Java 1.8
Add to your pom.xml:
```xml
      <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.3</version>
                <configuration>
                    <source>1.8</source>
                    <target>1.8</target>
                </configuration>
            </plugin>
        </plugins>
    </build>

```
### Add dependency to Hono client
Add to your pom.xml:

```xml
<dependency>
      <groupId>org.eclipse.hono</groupId>
      <artifactId>hono-client</artifactId>
      <version>0.5-M5</version>
</dependency>
```
  
### Copy truststore

For the encrypted communication with Hono, please copy the provided truststore from the Hono project, e.g. like

    $ mkdir -p ${yourProjectDir}/src/main/resources/certs
    $ cp ${honoProjectDir}/demo-certs/certs/trusted-certs.pem ${yourProjectDir}/src/main/resources/certs

The truststore is referenced in the code below and is given to the Hono client.

## Implementation
Instead of a step by step guide to expand the maven generated simple Java class, we prefer to show the full class implementation and then explain the several aspects. 

This allows experienced developers to just read (and understand) the source code and copy it to their own implementation to get Hono connected as quickly as possible.

Here is the code:

```java
package org.eclipse.hono;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.proton.ProtonClientOptions;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.impl.HonoClientImpl;
import org.eclipse.hono.client.MessageSender;
import org.eclipse.hono.connection.ConnectionFactoryImpl;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.stream.IntStream;


public class App {
    public static final String HONO_HOST = "localhost";
    public static final int    HONO_PORT = 5671;

    public static final String TENANT_ID = "DEFAULT_TENANT";
    public static final String DEVICE_ID = "4711";

    public static final int COUNT        = 50;

    private final Vertx vertx = Vertx.vertx();
    private final HonoClient honoClient;
    
    private final CountDownLatch latch;

    public App() {
        honoClient = new HonoClientImpl(vertx,
                ConnectionFactoryImpl.ConnectionFactoryBuilder.newBuilder()
                        .vertx(vertx)
                        .host(HONO_HOST)
                        .port(HONO_PORT)
                        .user("hono-client")
                        .password("secret")
                        .trustStorePath("certs/trusted-certs.pem")
                        .disableHostnameVerification()
                        .build());
        latch = new CountDownLatch(1);
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Starting downstream sender...");
        App app = new App();
        app.sendTelemetryData();
        System.out.println("Finishing downstream sender.");
    }


    private void sendTelemetryData() throws Exception {
        final Future<MessageSender> senderFuture = Future.future();

        senderFuture.setHandler(result -> {
            if (!result.succeeded()) {
                System.err.println("honoClient could not create telemetry sender : " + result.cause().getMessage());
            }
            latch.countDown();
        });

        final Future<HonoClient> connectionTracker = Future.future();
        honoClient.connect(new ProtonClientOptions(), connectionTracker.completer());

        connectionTracker.compose(honoClient -> {
                    honoClient.getOrCreateTelemetrySender(TENANT_ID, senderFuture.completer());
                },
                senderFuture);

        latch.await();

        if (senderFuture.succeeded()) {
            MessageSender ms = senderFuture.result();

            IntStream.range(0, COUNT).forEach(value -> {
                sendSingleMessage(ms, value);
            });
        }

        vertx.close();
    }

    private void sendSingleMessage(MessageSender ms, int value) {
        CountDownLatch messageSenderLatch = new CountDownLatch(1);
        System.out.println("Sending message... #" + value);

        final Map<String, Object> properties = new HashMap<>();
        properties.put("my_prop_string", "I'm a string");
        properties.put("my_prop_int", 10);

        ms.send(DEVICE_ID, properties, "myMessage" + value, "text/plain",
                v -> {
                    messageSenderLatch.countDown();
                });
        try {
            messageSenderLatch.await();
        } catch (InterruptedException e) {
        }
    }
}

```

## Detailed description of the code

The code consists of several parts that are discussed now:
  
### Define connection details

We need the host address and port of Hono and define them for simplicity as constants in the main class (HONO_HOST, HONO_PORT).

{{% warning %}}
Please replace them with the values corresponding to your Hono server.
{{% /warning %}}
                
For downstream data we will need the tenant and the deviceId for which we plan to send data from.
We define them as constants here  (TENANT_ID, DEVICE_ID) as well and use the defaults that Hono is using ("DEFAULT_TENANT", "4711").

COUNT defines the number of the sequentially sent messages.

### Fields

| Field                                     | Description                                                      |
| :---------------------------------------- | :--------------------------------------------------------------- |
| `vertx`  | The Hono client is based on vertx, so for a java implementation vertx is the technology to use. Please refer to the [vertx documentation](http://vertx.io/docs/) for any introduction to that. |
| `honoClient`  | The instance of the Hono client (that is using vertx itself in the implementation)
| `latch`  | The `CountDownLatch` is explained below with the code using it.


### Constructor

The `HonoClient` is constructed by a factory builder and is filled with the contact details in a fluent api way.
The `CountDownLatch` is initialized for one count down that is used in callbacks to signal the main thread to continue.

{{% warning %}}
\\
The shown code configures the client to use an encrypted communication channel to Hono (due to the presence of **trustStorePath**).
If you want to use unencrypted communication, please:
- configure the Hono server by not setting any **hono.server.keyStorePath** (restart necessary)
- remove the lines for encrypted communication in the shown code (**trustStorePath(...)** and **disableHostnameVerification()**)
{{% /warning %}}

### Main method

Initiates the control flow in the method sendTelemetryData.


### Send downstream telemetry data

This is the most important part of the example and consists of:


#### Buildup of a telemetry sender to Hono

The buildup of a Hono telemetry sender is a typical piece of asynchronous vertx code.

Therefore it is important to not let the main thread end before all AMQP messages were sent and acknowledged - this is were the CountDownLatch is used to block the main thread at the appropriate lines.
The `latch.await()` code is blocking the main thread until the `HonoClient` was successfully connected and then starts to send the first message.


#### Use the sender to send several messages in sequence to Hono
The message sender instance is constructed in the senderFuture and can be accessed via the `result()` method.

Here the flow control of the AMQP protocol is relevant: the `HonoClient` considers the available credits and invokes the callback once as soon as there are enough credits to send. 
At the time the callback is invoked the message was sent already.

The callback itself counts down a latch `messageSenderLatch` which has blocked the main thread. If the latch was counted down, the main thread continues and sends the next message.

#### Close the connection
For simplicity, the Hono client is not shutdown in this example. Instead only vertx is closed.

The integration of shutdown code is straightforward and thus not shown here.

### Insert logger

You probably noticed the error message at startup that complains about a missing logger binding. You can overcome this by adding a logger to your pom.xml, e.g. logback:

```xml
<dependency>
      <groupId>ch.qos.logback</groupId>
      <artifactId>logback-classic</artifactId>
      <version>1.1.3</version>
</dependency>
```

You should get several logging messages during startup then.

## Start and test your implementation

Before you start your application, you normally want to start a consumer for the tenant first (please see *Flow control* why). 
You can use the example receiver of the Hono project for that (please see the [Getting started](https://www.eclipse.org/hono/getting-started/) page for instructions) 
or write an own consumer (see [Java Hono consumer](https://www.eclipse.org/hono/user-guide/java_client_consumer/)).


## Flow control
If you do not have a consumer registered with Hono and start the sender, no credits at all will be available and the sender of this example will wait for being allowed to send the very first message.
If you start the consumer now, the sender soon will get credits and starts to send it's messages until the configured number of messages was sent.

## Use a nightly build of Hono
Sometimes you may want to use the most current build of Hono that is available (usually nightly snapshots).

For this purpose, please add the Eclipse snapshot repository to your Maven `settings.xml`:

```xml
	<repository>
	  <id>eclipse-snapshots</id>
	  <url>https://repo.eclipse.org/content/repositories/snapshots</url>
	  <releases>
	    <enabled>false</enabled>
	  </releases>
	  <snapshots>
	    <enabled>true</enabled>
	  </snapshots>
	</repository>
```
After that, you can reference a snapshot build in your `pom.xml`:

```xml
 <dependency>
       <groupId>org.eclipse.hono</groupId>
       <artifactId>hono-client</artifactId>
       <version>0.5-M5-SNAPSHOT</version>
 </dependency>
```
