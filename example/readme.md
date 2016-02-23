# Hono Example

This module contains sample code illustrating how clients can use Hono to send and receive messages.

### Running the Example

In order to run the example you will need the following

* The compiled *Hono* artifacts
* A *RabbitMQ* message broker

##### Prerequisites

The easiest way to run the example is by using [Docker Compose](https://docs.docker.com/compose) to start up *RabbitMQ* and *Hono Dispatcher* *Docker* images. However, it is also possible to run these images individually using plain *Docker* (see next section).

1. If you do not already have *Docker* and *Docker Compose* installed on your system follow these instructions to
  * [install Docker](https://docs.docker.com/engine/installation/) and
  * **optionally** [install Docker Compose](https://docs.docker.com/compose/install/)
1. In order to build the *Hono Dispatcher* Docker image run the following from the `dispatcher` folder
   `$ mvn install -Pbuild-docker-image`

### Start Server Components using Docker Compose

The easiest way to start the server components is by using *Docker Compose*. Simply run the following from the `docker` directory

    $ docker-compose up -d

This will start up both a *RabbitMQ* broker instance as well as the *Hono Dispatcher* wired up with each other automatically.

Use the following to stop the server components

    $ docker-compose stop

If you want to restart the services later use

    $ docker-compose start

### Start Server Components using plain Docker

If you do not want to install *Docker Compose* or simply want to have more fine grained control over the process
you can also use plain *Docker* to run and wire up the images manually from the command line.

##### Start RabbitMQ

In order to start a broker using [the official RabbitMQ image](https://hub.docker.com/_/rabbitmq/) run the following from the
command line

    $ docker run -d --name rabbitmq -p 5672:5672 -p 15672:15672 -h rabbitmq rabbitmq:3-management

##### Start Hono Dispatcher

Once the *RabbitMQ* Docker image has been started using the command above the *Hono Dispatcher* image can be run as follows

    $ docker run -d --name hono --link rabbitmq hono/dispatcher:0.1-SNAPSHOT

### Run Clients

Now that the required server components have been started you can run the example clients.
* `Client1_Sender` can send messages typed on the console to the *Hono Dispatcher* and also listens to messages received from the dispatcher and logs them to the console.
* `Client2_Receiver` listens to messages sent to the *Hono Dispatcher*, e.g. using `Client1_Sender`, and prints them to the console.

Set environment variable on Linux (**NOTE**: replace *localhost* with the IP address of the machine running the *RabbitMQ* broker)

    $ export AMQP_URI=amqp://localhost:5672

or on Windows

    $ set AMQP_URI=amqp://localhost:5672

then run either `Client1_Sender` or `Client2_Receiver` from the `example/target` folder.

    $ java -cp hono-example-0.1-SNAPSHOT-jar-with-dependencies.jar org.eclipse.hono.example.Client1_Sender
    $ java -cp hono-example-0.1-SNAPSHOT-jar-with-dependencies.jar org.eclipse.hono.example.Client2_Receiver

### Start Hono Dispatcher using Maven

If you want to start the *Hono Dispatcher* service from within your IDE or from the command line, e.g. in order to more easily attach a debugger or take advantage of hot code replacement with incremental compilation, you can follow the instructions below.

Set the following environment variables in order to make the *RabbitMQ* broker's host and port available to the *Hono Dispatcher* (make sure to replace the host and port with the host and port your broker is running on). On Linux use

    $ export AMQP_URI=amqp://localhost:5672

or on Windows use

    $ set AMQP_URI=amqp://localhost:5672

Then run the following from the `dispatcher` folder using Maven

    $ mvn exec:java -Dexec.mainClass=org.eclipse.hono.dispatcher.EventDispatcherApplication

