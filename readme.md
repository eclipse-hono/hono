# Eclipse Hono

[Eclipse Hono](https://projects.eclipse.org/projects/iot.hono) provides uniform (remote) service interfaces for connecting large numbers of IoT devices to a (cloud) back end. It specifically supports scalable and secure data ingestion (*telemetry* data) as well as *command & control* type message exchange patterns and provides interfaces for provisioning & managing device identity and access control rules.

### Getting started

##### Compile from Source

If you do not already have a working Maven installation on your system please follow the [installation instructions on the Maven home page](https://maven.apache.org/). Then simply run the following from the command line.

    $ mvn clean install

This will build all libraries and example code.

##### Run the Example

Please see [Hono Example](example/readme.md) for details on how to run the demo application.

### Using Hono

Please take a look at the [example clients](example) which illustrate how client code can interact with Hono to send and receive data.

### Modules

* `client`: simple java client based on vertx-proton 
* `adapters`: implementation of core protocol adapters 
* `example`: simple example that uses the hono-client to send and receive messages via the hono-server
* `server`: the hono server component exposing the Hono API via AMQP 1.0

### Get in Touch

Please check out the [Hono project home page](https://projects.eclipse.org/projects/iot.hono) for details.

### Build status

- Travis CI ![Build status](https://travis-ci.org/eclipse/hono.svg)
- [Hudson](https://hudson.eclipse.org/hono/)
