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

If you do not already have *Docker* and *Docker Compose* installed on your system follow these instructions to

1. [install Docker](https://docs.docker.com/engine/installation/) and
2. [install Docker Compose](https://docs.docker.com/compose/install/)

##### Building Docker Images

In order to build all required Docker images run the following from the project's root folder

    `~/hono$ mvn clean install -Ddocker.host=tcp://${host}:${port} -Pbuild-docker-image`

with `${host}` and `${port}` reflecting the name/IP address and port of the host where Docker is running on. If you are running on Linux and Docker is installed locally or you specified the `DOCKER_HOST` environment variable, you can omit the `docker.host` property.

If you plan to build the Docker images more frequently, e.g. because you want to extend or improve the Hono code, then you should define the `docker.host` property in your Maven `settings.xml` file containing the very same value as you would use on the command line as indicated above. This way you can simply do a `mvn clean install` later on and the Docker images will be built automatically as well because the `build-docker-image` profile is activated automatically if the Maven property `docker.host` is set.
 
### Start Server Components using Docker Compose

The easiest way to start the server components is by using *Docker Compose*. As part of the build process a Docker Compose file is generated that you can use to start up a Hono instance on your Docker host. Simply run the following from the `example/target/hono` directory

    ~/hono/example/target/hono$ docker-compose up -d

This will start up the following components:

* a *Dispatch Router* instance that downstream clients connect to in order to consume telemetry data.
* an *Apache Artemis* instance wired up with the Dispatch Router.
* a *Hono Server* instance wired up with the dispatch router.
* a Docker *volume* container for the *Default Configuration* which contains a `permissions.json` file that defines permissions required to run the example. You can edit these permissions in the file `config/src/main/resources/permissions.json`. Don't forget to rebuild and restart the Docker image for the changes to take effect.
* a Docker *volume* containing the *Dispatch Router* configuration
* a *Hono REST Adapter* instance that exposes Hono's Telemetry API as RESTful resources.
* a *Hono MQTT Adapter* instance that exposes Hono's Telemetry API as an MQTT topic hierarchy.

Use the following to stop the server components

    ~/hono/example/target/hono$ docker-compose stop

If you want to restart the services later use

    ~/hono/example/target/hono$ docker-compose start

### Start Server Components individually using plain Docker

If you do not want to install *Docker Compose* or simply want to have more fine grained control over the process
you can also use plain *Docker* to run and wire up the images manually from the command line.

##### Start Dispatch Router

In order to start a router using [Gordon Sim's Qpid Dispatch Router image](https://hub.docker.com/r/gordons/qpid-dispatch/) with the required configuration run the following from the command line

    $ docker run -d --name qdrouter-config eclipsehono/qpid-default-config:latest
    $ docker run -d --name qdrouter-sasldb eclipsehono/qpid-sasldb:latest
    $ docker run -d --name qdrouter -p 15671:5671 -p 15672:5672 -p 15673:5673 -h qdrouter \
    $ --volumes-from="qdrouter-config" --volumes-from="qdrouter-sasldb" gordons/qpid-dispatch:0.7.0
 
##### Start Apache Artemis

In order to start an Apache Artemis instance with the required configuration run the following from the command line

    $ docker run -d --name artemis-config eclipsehono/artemis-default-config:latest
    $ docker run -d --name broker -h broker --volumes-from="artemis-config" vromero/activemq-artemis:1.4.0
 
##### Example Configuration

Start volume container to provide required configuration
    
    $ docker run -d --name hono-config eclipsehono/hono-default-config:latest

##### Start Hono Server

Once the *Dispatch Router* Docker image has been started using the command above the *Hono Server* image can be run as follows

    $ docker run -d --name hono --link qdrouter -p 5672:5672 --volumes-from="hono-config" \
    $ -e "SPRING_CONFIG_LOCATION=file:///etc/hono/" eclipsehono/hono-server:latest

### Run Client

Now that the required server components have been started you can run the example client.

The example client can be used to send telemetry messages or event messages typed on the console to the *Hono Server*. It can also be used to log uploaded messages to the console.

In order to receive and log telemetry messages uploaded to Hono, run the client from the `example` folder as follows:

    $ java -jar target/hono-example-0.5-M3-SNAPSHOT.jar --hono.client.host=localhost
    
In order to receive and log event messages uploaded to Hono, run the client from the `example` folder as follows:

    $ java -jar target/hono-example-0.5-M3-SNAPSHOT.jar --spring.profiles.active=receiver,event --hono.client.host=localhost

 **NOTE**: Replace `localhost` with the name or IP address of the host that the *Dispatch Router* is running on. If the *Dispatch Router* is running on `localhost` you can omit the option.

In order to upload telemetry messages entered on the command line, run the client from the `example` folder as follows:

    $ java -jar target/hono-example-0.5-M3-SNAPSHOT.jar --spring.profiles.active=sender --hono.client.host=localhost
    
In order to upload event messages entered on the command line, run the client from the `example` folder as follows:

    $ java -jar target/hono-example-0.5-M3-SNAPSHOT.jar --spring.profiles.active=sender,event --hono.client.host=localhost

 **NOTE**: Replace `localhost` with the name or IP address of the host that the *Hono Server* is running on. If the *Hono Server* is running on `localhost` you can omit the option.

### Start Hono Server using Maven

You may want to start the *Hono Server* from within your IDE or from the command line, e.g. in order to more easily attach a debugger or take advantage of hot code replacement with incremental compilation.

1. Start up the *Dispatch Router* Docker image as described above.
1. Run the following Maven command from the `application` folder

    ~/hono/application$ mvn spring-boot:run -Drun.arguments=--hono.downstream.host=localhost,--hono.downstream.port=15673,--hono.downstream.keyStorePath=../config/demo-certs/certs/honoKeyStore.p12,--hono.downstream.keyStorePassword=honokeys,--hono.downstream.trustStorePath=../config/demo-certs/certs/trusted-certs.pem,--hono.downstream.hostnameVerificationRequired=false,--logging.config=classpath:logback-spring.xml

**NOTE**: Replace `localhost` with the name or IP address of the host that the *Dispatch Router* is running on. The `hono.downstream.keyStorePath` parameter is required because the Dispatch Router requires the Hono server to authenticate by means of a client certificate during connection establishment. The `hono.downstream.hostnameVerificationRequired` parameter is necessary to prevent Hono from validating the Dispatch Router's hostname by means of comparing it to the *common name* of the server's certificate's subject.
You may want to make logging of the Hono server a little more verbose by enabling the `dev` Spring profile. To do so, append `,--spring.profiles.active=dev` to the command line.

# Accessing the Hono Instance via REST

Now that the Hono instance is up and running you can use Hono's protocol adapters and Hono's AMQP 1.0 based APIs to interact with it.

The following sections will use the REST adapter to interact with Hono because it is more convenient to access using a standard HTTP client like `curl` or `HTTPie` from the command line.

Please refer to the `REST adapter` documentation for additional information on how to access Hono's functionality via REST.

NOTE: The following sections assume that the REST adapter runs on the local machine. However, if you started the REST adapter on another host or VM then make sure to replace `localhost` with the name or IP address of that host.

### Registering a device using the REST adapter

The first thing to do is registering a device identity with Hono. Hono uses this information to authorize access to the device's telemetry data and functionality.

The following command registers a device with ID `4711`.

    $ curl -X POST -i -d 'device_id=4711' http://localhost:8080/registration/DEFAULT_TENANT

The result will contain a `Location` header containing the resource path created for the device. In this example it will look like this:

    HTTP/1.1 201 Created
    Location: /registration/DEFAULT_TENANT/4711
    Content-Length: 0

You can then retrieve registration data for the device using

    $ curl -i http://localhost:8080/registration/DEFAULT_TENANT/4711

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
    $ http://localhost:8080/telemetry/DEFAULT_TENANT/4711
