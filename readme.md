# Hono

[Hono](https://projects.eclipse.org/projects/iot.hono) provides uniform (remote) service interfaces for connecting large numbers of IoT devices to a (cloud) back end. It specifically supports scalable and secure data ingestion (*telemetry* data) as well as *command & control* type message exchange patterns and provides interfaces for provisioning & managing device identity and access control rules.

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

* `api`: defines the hono client api interfaces
* `client`: implementation of the hono api, based on amqp-client
* `dispatcher`: the hono message dispatcher
* `docker`: ready to use docker compose file to start the hono server with all requirement runtime components.
* `example`: simple example that uses the hono-client to send and receive messages via the hono-dispatcher

### Get in Touch

Please check out the [Hono project home page](https://projects.eclipse.org/projects/iot.hono) for details.
