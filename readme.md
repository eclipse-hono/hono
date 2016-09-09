![Hono logo](logo/PNG-150dpi/HONO-Logo_Bild-Wort_quadrat-w-200x180px.png)

### 

[Eclipse Hono](https://projects.eclipse.org/projects/iot.hono) provides uniform (remote) service interfaces for connecting large numbers of IoT devices to a (cloud) back end. It specifically supports scalable and secure data ingestion (*telemetry* data) as well as *command & control* type message exchange patterns and provides interfaces for provisioning & managing device identity and access control rules.

### Getting started

##### Compile from Source

If you do not already have a working Maven installation on your system please follow the [installation instructions on the Maven home page](https://maven.apache.org/). Then simply run the following from the command line.

    $ mvn clean install -Pbuild-docker-image

This will build all libraries, docker images and example code. 

##### Run the Demo Application

Please refer to [Hono Example](example/readme.md) for details on how to run the demo application.

### Using Hono

Please take a look at the [example clients](client) which illustrate how client code can interact with Hono to send and receive data.

##### Remote API

Clients can interact with Hono by means of AMQP 1.0 based message exchanges. Please refer to the corresponding Wiki pages for details:

* [Device Registration API](https://github.com/eclipse/hono/wiki/Device-Registration-API) for registering new devices so that they can connect to Hono.
* [Telemetry API](https://github.com/eclipse/hono/wiki/Telemetry-API) for sending and receiving downstream telemetry data.
* [Command & Control API](https://github.com/eclipse/hono/wiki/Command-And-Control-API) for sending commands to devices and receiving replies.

### Modules

* `client`: simple java client(s) based on vertx-proton 
* `adapters`: implementation of core protocol adapters 
* `example`: simple example that uses the hono-client to send and receive messages via the hono-server
* `server`: the Hono server component exposing the Hono API via AMQP 1.0

### Get in Touch

Please check out the [Hono project home page](https://projects.eclipse.org/projects/iot.hono) for details. We are also available on `#hono` [IRC channel on Freenode](https://webchat.freenode.net/) and [Hono Dev mailing list](https://dev.eclipse.org/mailman/listinfo/hono-dev).

### Build status

- [![Travis Build Status](https://travis-ci.org/eclipse/hono.svg?branch=master)](https://travis-ci.org/eclipse/hono)
- [Hudson Build Status](https://hudson.eclipse.org/hono/)
