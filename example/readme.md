# Hono Example

This module contains sample code illustrating how clients can use Hono to send and receive messages.

### Running the Example

In order to run the example you will need the following

* The compiled *Hono* artifacts
* An *Apache Qpid Dispatch Router* instance

##### Prerequisites

The easiest way to run the example is by using [Docker Compose](https://docs.docker.com/compose) to start up *Dispatch Router* and *Hono Server* *Docker* images. However, it is also possible to run these images individually using plain *Docker* (see next section).

1. If you do not already have *Docker* and *Docker Compose* installed on your system follow these instructions to
  * [install Docker](https://docs.docker.com/engine/installation/) and
  * **optionally** [install Docker Compose](https://docs.docker.com/compose/install/)
1. In order to build the *Hono Server* Docker image run the following from the `server` folder
    `$ mvn install -Ddocker.host=tcp://${host}:${port}`
1. In order to build the *Configuration* Docker image run the following command in the `config` folder
    `$ mvn install -Ddocker.host=tcp://${host}:${port} -Pbuild-docker-image` (repeat this step whenever you want to change your example configuration)

with `${host}` and `${port}` reflecting the name/IP address and port of the host where Docker is running on. If you are running on Linux and Docker is installed locally, you can omit the `docker.host` property.
 
### Start Server Components using Docker Compose

The easiest way to start the server components is by using *Docker Compose*. Simply run the following from the `example/docker` directory

    $ docker-compose up -d

This will start up both a *Dispatch Router* instance as well as the *Hono Server* wired up with each other automatically. Additionally a volume container for the *Example Configuration* is started containing a `permissions.json` file that defines permissions required to run the example. You can edit these permissions in the file `example/config/permissions.json`. Don't forget to rebuild and restart the Docker image to make the changes take effect.

Use the following to stop the server components

    $ docker-compose stop

If you want to restart the services later use

    $ docker-compose start

### Start Server Components using plain Docker

If you do not want to install *Docker Compose* or simply want to have more fine grained control over the process
you can also use plain *Docker* to run and wire up the images manually from the command line.

##### Start Dispatch Router

In order to start a broker using [Gordon Sim's Qpid Dispatch Router image](https://hub.docker.com/r/gordons/qpid-dispatch/) run the following from the
command line

    $ docker run -d --name qdrouter -p 15672:5672 -h qdrouter gordons/qpid-dispatch:0.6.0-rc4

##### Example Configuration

Start volume container to provide required configuration
    
    $ docker run -d --name example-config eclipsehono/hono-default-config:0.1-SNAPSHOT

##### Start Hono Server

Once the *Dispatch Router* Docker image has been started using the command above the *Hono Server* image can be run as follows

    $ docker run -d --name hono --link qdrouter -p 5672:5672 --volumes-from="example-config" eclipsehono/server:0.1-SNAPSHOT

### Run Client

Now that the required server components have been started you can run the example client.

The example client can be used to send telemetry messages typed on the console to the *Hono Server*. It can also be used to log uploaded telemetry messages to the console.

In order to receive and log telemetry messages uploaded to Hono, run the client from the `example` folder as follows:

    $ java -jar target/hono-example-0.1-SNAPSHOT.jar --hono.server.host=localhost

 **NOTE**: Replace `localhost` with the name or IP address of the host that the *Dispatch Router* is running on. If the *Dispatch Router* is running on `localhost` you can omit the option.

In order to upload telemetry messages entered on the command line, run the client from the `example` folder as follows:

    $ java -jar target/hono-example-0.1-SNAPSHOT.jar --spring.profiles.active=sender --hono.server.host=localhost

 **NOTE**: Replace `localhost` with the name or IP address of the host that the *Hono Server* is running on. If the *Dispatch Router* is running on `localhost` you can omit the option.

### Start Hono Server using Maven

You may want to start the *Hono Server* from within your IDE or from the command line, e.g. in order to more easily attach a debugger or take advantage of hot code replacement with incremental compilation.

1. Start up the *Dispatch Router* Docker image as described above.
1. Run the following Maven command from the `server` folder


    $ mvn spring-boot:run -Dhono.telemetry.downstream.host=localhost -Dhono.telemetry.downstream.port=15672 -Dhono.permissions.path=../example/config/permissions.json

  **NOTE**: Replace `localhost` with the name or IP address of the host that the *Dispatch Router* is running on.