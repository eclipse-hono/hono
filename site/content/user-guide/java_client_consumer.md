+++
title = "Java - integrate Hono consumer"
weight = 290
+++

Simple example of a Java main program that consumes telemetry data that was sent for the default tenant (usually devices). 
It shall serve as a blueprint to integrate your existing java source code with Hono. 
<!--more-->



The goal of the following description is to extend an arbitrary Java program to **consume** telemetry data that was sent *downstream* to Hono.

For that the following example is kept as simple as possible (in the tradition of a classical "Hello World" implementation).
This means that we will make use of simple constant definitions, deal with exceptions as rarely as possible and use a few `System.out`'s.

The application waits for messages until you press CTRL+C or kill it.

Please note that consumers do not connect with Hono directly, but rather with an AMQP router network. 
In the standard setup of Hono this is the [qdrouter](https://qpid.apache.org/components/dispatch-router/index.html) from the Apache Qpid project.
In production scenarios this might be a large setup of AMQP routers, brokers, etc.

{{% warning %}}
Note that production ready code likely has to think more about error handling and logging than this simple blueprint. 
{{% /warning %}}

## Setup your project

To have a common base, we start with a simple maven project that generates a java main application by using the maven generate archetype like that:

```shell
$ mvn archetype:generate -B -DarchetypeGroupId=org.apache.maven.archetypes \
-DarchetypeArtifactId=maven-archetype-quickstart -DarchetypeVersion=1.1 \
-DgroupId=org.eclipse.hono -DartifactId=honoTelemetryDownstreamConsumer -Dversion=0.1-SNAPSHOT -Dpackage=org.eclipse.hono
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

For the encrypted communication with qdrouter (which forwards to Hono), please copy the provided truststore from the Hono project, e.g. like

    $ mkdir -p ${yourProjectDir}/src/main/resources/certs
    $ cp ${honoProjectDir}/demo-certs/certs/trusted-certs.pem ${yourProjectDir}/src/main/resources/certs

The truststore is referenced in the code below and is given to the Hono client.

## Implementation
Instead of a step by step guide to expand the maven generated simple Java class, we prefer to show the full class implementation and then explain the several aspects. 

This allows experienced developers to just read (and understand) the source code and copy it to their own implementation to get Hono connected as quickly as possible.

Here is the code:

```java
package org.eclipse.hono;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.proton.ProtonClientOptions;
import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.amqp.messaging.Section;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.HonoClient;
import org.eclipse.hono.client.impl.HonoClientImpl;
import org.eclipse.hono.client.MessageConsumer;
import org.eclipse.hono.connection.ConnectionFactoryImpl;
import org.eclipse.hono.util.MessageHelper;

import java.util.concurrent.CountDownLatch;

public class App {
    public static final String QPID_ROUTER_HOST = "192.168.99.100";
    public static final short  QPID_ROUTER_PORT = 15671;

    public static final String TENANT_ID = "DEFAULT_TENANT";

    private final Vertx vertx = Vertx.vertx();
    private final HonoClient honoClient;

    private final CountDownLatch latch;

    public App() {
        honoClient = new HonoClientImpl(vertx,
                ConnectionFactoryImpl.ConnectionFactoryBuilder.newBuilder()
                        .vertx(vertx)
                        .host(QPID_ROUTER_HOST)
                        .port(QPID_ROUTER_PORT)
                        .user("user1@HONO")
                        .password("pw")
                        .trustStorePath("certs/trusted-certs.pem")
                        .disableHostnameVerification()
                        .build());
        latch = new CountDownLatch(1);
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Starting downstream consumer...");
        App app = new App();
        app.consumeTelemetryData();
        System.out.println("Finishing downstream consumer.");
    }

    private void consumeTelemetryData() throws Exception {
        final Future<MessageConsumer> consumerFuture = Future.future();

        consumerFuture.setHandler(result -> {
            if (!result.succeeded()) {
                System.err.println("honoClient could not create telemetry consumer : " + result.cause());
            }
            latch.countDown();
        });

        final Future<HonoClient> connectionTracker = Future.future();

        honoClient.connect(new ProtonClientOptions(), connectionTracker.completer());

        connectionTracker.compose(honoClient -> {
                    honoClient.createTelemetryConsumer(TENANT_ID,
                            msg -> handleTelemetryMessage(msg), consumerFuture.completer());
                },
                consumerFuture);

        latch.await();

        if (consumerFuture.succeeded())
            System.in.read();
        vertx.close();
    }

    private void handleTelemetryMessage(final Message msg) {
        final Section body = msg.getBody();
        String content = null;
        if (!(body instanceof Data))
            return;

        content = ((Data) msg.getBody()).getValue().toString();

        final String deviceId = MessageHelper.getDeviceId(msg);

        StringBuilder sb = new StringBuilder("received message [device: ").
                append(deviceId).append(", content-type: ").append(msg.getContentType()).append(" ]: ").append(content);

        if (msg.getApplicationProperties() != null) {
            sb.append(" with application properties: ").append(msg.getApplicationProperties().getValue());
        }

        System.out.println(sb.toString());
    }
}
```

## Detailed description of the code

The code consists of several parts that are discussed now:
  
### Define connection details
We need the host address and port of qdrouter and define them for simplicity as constants in the main class (QPID_ROUTER_HOST, QPID_ROUTER_PORT).

{{% warning %}}
Please replace them with the values corresponding to your qdrouter installation.
{{% /warning %}}
                
For downstream data we will need the tenant from which we plan to consume data.
We define it as constant here (TENANT_ID) as well and use the default that Hono is using ("DEFAULT_TENANT").

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
The shown code configures the client to use an encrypted communication channel to qdrouter (due to the presence of **trustStorePath**).

If you want to use unencrypted communication, please:

- use the qdrouter port that is used for unencrypted AMQP (in the standard installation 15672)

- remove the lines for encrypted communication in the shown code (**trustStorePath(...)** and **disableHostnameVerification()**)
{{% /warning %}}

### Main method

Initiates the control flow in the method consumeTelemetryData.


### Consume downstream telemetry data

This is the most important part of the example and consists of:


#### Buildup of a telemetry consumer from Hono

The buildup of a Hono telemetry consumer is a typical piece of asynchronous vertx code.

Since we want to consume messages from Hono until we are stopped, it is important to not let the main thread end.

First, the `latch.await()` code is blocking the main thread until the `HonoClient` was successfully connected.

Then we use `System.read.in()` to block the main thread. As soon as you press the Return-key, the consumer shuts down.


#### Consume messages from Hono
The telemetry consumer handles incoming messages in a callback which directly delegates processing to the method `handleTelemetryMessage`.

Since the AMQP message delivered is based on Apache Qpid Proton please refer to the [Proton documentation](https://qpid.apache.org/proton/) for details.
We only deal with messages that contain a body of type `Data` here.

The code prints out the received message and continues to listen.

#### Close the connection
For simplicity, the Hono client is not shutdown in this example - instead only vertx is closed.

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

When you start your application, there might be no messages that are currently sent to the tenant. This means your application will just wait for incoming messages.

To start sending messages to the tenant, you can use the example sender of the Hono project (see [Getting started](https://www.eclipse.org/hono/getting-started/) or a self-written sender 
(see [Java Hono sender](https://www.eclipse.org/hono/user-guide/java_client_sender/)) for that.

As soon as you start to send telemetry data to the tenant, you will receive messages. Note that the code easily retrieves the `deviceId` from which the message was sent.


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
