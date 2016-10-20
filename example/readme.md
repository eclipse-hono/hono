# Hono Example

This module contains sample code illustrating how clients can use Hono to send and receive messages.

### Running the Example

In order to run the example you will need the following

* The compiled *Hono* artifacts
* An *Apache Qpid Dispatch Router* instance
* An AMQP 1.0 compatible message broker e.g. [*Apache Artemis*](https://activemq.apache.org/artemis/)

You can build all required artifacts and docker images by running the following command in the project's root folder:

    $ mvn clean install -Pbuild-docker-image

##### Prerequisites

The easiest way to run the example is by using [Docker Compose](https://docs.docker.com/compose) to start up *Dispatch Router* and *Hono Server* *Docker* images. However, it is also possible to run these images individually using plain *Docker* (see next section).

1. If you do not already have *Docker* and *Docker Compose* installed on your system follow these instructions to
  * [install Docker](https://docs.docker.com/engine/installation/) and
  * **optionally** [install Docker Compose](https://docs.docker.com/compose/install/)
1. In order to build all required Docker images run the following from the project's root folder
    `$ mvn clean install -Ddocker.host=tcp://${host}:${port} -Pbuild-docker-image`

with `${host}` and `${port}` reflecting the name/IP address and port of the host where Docker is running on. If you are running on Linux and Docker is installed locally or you specified the `DOCKER_HOST` environment variable, you can omit the `docker.host` property.
 
### Start Server Components using Docker Compose

The easiest way to start the server components is by using *Docker Compose*. Simply run the following from the `example/target/hono` directory

    $ docker-compose up -d

This will start up the following components:

* a *Dispatch Router* instance that downstream clients connect to in order to consume telemetry data.
* an *Apache Artemis* instance wired up with the Dispatch Router.
* a *Hono Server* instance wired up with the dispatch router.
* a Docker *volume* container for the *Example Configuration* which contains a `permissions.json` file that defines permissions required to run the example. You can edit these permissions in the file `example/config/permissions.json`. Don't forget to rebuild and restart the Docker image for the changes to take effect.
* a Docker *volume* containing the *Dispatch Router* configuration
* a *Hono REST Adapter* instance that exposes Hono's Telemetry API as RESTful resources.

Use the following to stop the server components

    $ docker-compose stop

If you want to restart the services later use

    $ docker-compose start

### Start Server Components using plain Docker

If you do not want to install *Docker Compose* or simply want to have more fine grained control over the process
you can also use plain *Docker* to run and wire up the images manually from the command line.

##### Start Dispatch Router

In order to start a router using [Gordon Sim's Qpid Dispatch Router image](https://hub.docker.com/r/gordons/qpid-dispatch/) with the required configuration run the following from the
command line

    $ docker run -d --name qdrouter-config eclipsehono/qpid-default-config:latest
    $ docker run -d --name qdrouter-sasldb eclipsehono/qpid-sasldb:latest
    $ docker run -d --name qdrouter -p 15672:5672 -h qdrouter --volumes-from="qdrouter-config" --volumes-from="qdrouter-sasldb" gordons/qpid-dispatch:0.6.0
 
##### Start Apache Artemis

In order to start an Apache Artemis instance with the required configuration run the following from the command line

    $ docker run -d --name artemis-config eclipsehono/artemis-default-config:latest
    $ docker run -d --name broker -h broker --volumes-from="artemis-config" vromero/activemq-artemis:1.4.0
 
##### Example Configuration

Start volume container to provide required configuration
    
    $ docker run -d --name example-config eclipsehono/hono-default-config:latest

##### Start Hono Server

Once the *Dispatch Router* Docker image has been started using the command above the *Hono Server* image can be run as follows

    $ docker run -d --name hono --link qdrouter -p 5672:5672 --volumes-from="example-config" eclipsehono/hono-server:latest

### Run Client

Now that the required server components have been started you can run the example client.

The example client can be used to send telemetry messages or event messages typed on the console to the *Hono Server*. It can also be used to log uploaded messages to the console.

In order to receive and log telemetry messages uploaded to Hono, run the client from the `example` folder as follows:

    $ java -jar target/hono-example-0.5-M3-SNAPSHOT.jar --hono.server.host=localhost
    
In order to receive and log event messages uploaded to Hono, run the client from the `example` folder as follows:

    $ java -jar target/hono-example-0.5-M3-SNAPSHOT.jar --spring.profiles.active=receiver,event --hono.server.host=localhost

 **NOTE**: Replace `localhost` with the name or IP address of the host that the *Dispatch Router* is running on. If the *Dispatch Router* is running on `localhost` you can omit the option.

In order to upload telemetry messages entered on the command line, run the client from the `example` folder as follows:

    $ java -jar target/hono-example-0.5-M3-SNAPSHOT.jar --spring.profiles.active=sender --hono.server.host=localhost
    
In order to upload event messages entered on the command line, run the client from the `example` folder as follows:

    $ java -jar target/hono-example-0.5-M3-SNAPSHOT.jar --spring.profiles.active=sender,event --hono.server.host=localhost

 **NOTE**: Replace `localhost` with the name or IP address of the host that the *Hono Server* is running on. If the *Hono Server* is running on `localhost` you can omit the option.

### Start Hono Server using Maven

You may want to start the *Hono Server* from within your IDE or from the command line, e.g. in order to more easily attach a debugger or take advantage of hot code replacement with incremental compilation.

1. Start up the *Dispatch Router* Docker image as described above.
1. Run the following Maven command from the `server` folder


    $ mvn spring-boot:run -Dhono.telemetry.downstream.host=localhost -Dhono.telemetry.downstream.port=15672 -Dhono.permissions.path=../example/config/permissions.json

  **NOTE**: Replace `localhost` with the name or IP address of the host that the *Dispatch Router* is running on.

### Registering a device using the REST adapter

The following command registers a device with ID `4711`.

    $ curl -X POST -i -d 'device_id=4711' http://127.0.0.1:8080/registration/DEFAULT_TENANT

The result will contain a `Location` header containing the resource path created for the device. In this example it will look
like this:

    HTTP/1.1 201 Created
    Location: /registration/DEFAULT_TENANT/4711
    Content-Length: 0

You can then retrieve registration data for the device using

    $ curl -i http://127.0.0.1:8080/registration/DEFAULT_TENANT/4711

which will result in something like this:

    HTTP/1.1 200 OK
    Content-Type: application/json; charset=utf-8
    Content-Length: 35
    
    {
      "data" : { },
      "id" : "4711"
    }

### Uploading Telemetry Data using the REST adapter

    $ curl -X PUT -i -H 'Content-Type: application/json' --data-binary '{"temp": 5}' \
    $ http://127.0.0.1:8080/telemetry/DEFAULT_TENANT/4711
