# Hono Integration Tests

This module contains integration tests for Hono. The tests are executed against Docker images of the Hono components.

## Prerequisites

In order to run the tests you will need the following:

* a working *Docker Engine* installation (either local or on a separate host)
* the Docker images of the Hono components. See the [Developer Guide on the project web site](https://www.eclipse.org/hono/docs/dev-guide/building_hono/) for instructions on building the Docker images.

## Running the Tests

Run the tests by executing the following command from the `tests` directory (add `-Ddocker.host=tcp://${host}:${port}` if Docker is not installed locally)

```sh
# in directory: hono/tests/
mvn verify -Prun-tests
```

This starts the following Docker containers and runs the test cases against them

* Hono Authentication service
* Hono Device Registration service
* ActiveMQ Artemis message broker
* Qpid Dispatch Router
* Hono HTTP adapter
* Hono MQTT adapter
* Hono AMQP adapter
* Hono CoAP adapter

To run all tests of a certain class, or to run an individual test, set the `it.test` property:

```sh
mvn verify -Prun-tests -Dit.test=TelemetryHttpIT
mvn verify -Prun-tests -Dit.test=TelemetryHttpIT#testUploadUsingQoS1
```

The `logging.profile` property with a value of either `prod`, `dev` or `trace` can be used to set the log level in the Hono Docker containers and the integration tests:

```sh
mvn verify -Prun-tests -Dlogging.profile=trace
```

### Running the Tests without starting/stopping the containers

When running the tests with the `docker.keepRunning` property, the Docker containers will not be stopped and removed once the tests are complete:

```sh
mvn verify -Prun-tests -Ddocker.keepRunning
```

Subsequent test runs can use the running containers and will thereby finish much faster by adding the `useRunningContainers` profile to the maven command:

```sh
mvn verify -Prun-tests,useRunningContainers
```

With that profile, the Docker containers will be kept running as well.

In order to stop and remove the Docker containers started by a test run, use:

```sh
mvn verify -PstopContainers
```

### Running the Tests with the MongoDB based device registry

By default, the integration tests include the file based device registry. In order to include the MongoDB based device registry instead of the file based counterpart, use the `device-registry-mongodb` maven profile:

```sh
mvn verify -Prun-tests,device-registry-mongodb
```

### Running the Tests with the JDBC based device registry

By default, the integration tests include the file based device registry. In order to include the JDBC based device registry instead of the file based counterpart, use the `device-registry-jdbc` maven profile:

```sh
mvn verify -Prun-tests,device-registry-jdbc
```

### Running the Tests with the Jaeger tracing component

The tests can be run in such a way, that the OpenTracing trace spans created in the Hono components as part of a test run can be inspected later on. The OpenTracing component used for this is Jaeger.
 
To include the Jaeger client, build the Hono Docker images using the `jaeger` maven profile:

```sh
# in the "hono" folder containing the source code
mvn clean install -Pbuild-docker-image,metrics-prometheus,jaeger
```

(Add a `-Ddocker.host` property definition if needed, as described in the [Developer Guide](https://www.eclipse.org/hono/docs/dev-guide/building_hono/).)

Then run the tests using the `jaeger` maven profile:

```sh
# in directory: hono/tests/
mvn verify -Prun-tests,jaeger -Ddocker.keepRunning
```

To run a single test instead, use e.g.:

```sh
mvn verify -Prun-tests,jaeger -Ddocker.keepRunning -Dit.test=TelemetryHttpIT#testUploadUsingQoS1
```

Note that in order to be able to view the traces after a test run, the `docker.keepRunning` property has to be used as well, as shown above.  

The Jaeger UI, showing the traces of the test run, can be accessed at `http://localhost:18080`

if the Docker engine is running on `localhost`, otherwise the appropriate Docker host has to be used in the above URL. To choose a different port for the Jaeger UI, set the `jaeger.query.port` maven property to the desired port when running the tests. 

Start subsequent test runs using the running containers with the `useRunningContainers` maven profile, e.g.:

```sh
mvn verify -Prun-tests,jaeger,useRunningContainers -Dit.test=TelemetryHttpIT#testUploadUsingQoS1
```

In order to stop and remove the Docker containers started by a test run, use:

```sh
mvn verify -PstopContainers
```

### Running the Tests with the Quarkus based Components

By default, the integration tests are run using the Spring Boot based Hono components. For some components there are
Quarkus based alternative implementations. The tests can be run using these Quarkus based components by means of activating
the `components-quarkus-jvm` maven profile:

```sh
mvn verify -Prun-tests,components-quarkus-jvm
```

Note: the profile can also be activated by setting the Maven property *hono.components.type* to value `quarkus-jvm`.

```sh
mvn verify -Prun-tests -Dhono.components.type=quarkus-jvm
```

### Running the Tests with the Command Router using an embedded Cache

The Command Router component by default stores routing information in a dedicated Infinispan server.
The router can also be configured to instead store the data in an embedded cache by means of activating the `embedded_cache`
profile:

```sh
mvn verify -Prun-tests -Dhono.commandrouting.cache=embedded
```

### Running the Tests with the Device Connection service component

By default, the integration tests are run using the Command Router service component. In order to use the Device
Connection service component instead, the `device-connection-service` maven profile can be activated:

```sh
mvn verify -Prun-tests -Dhono.commandrouting.mode=dev-con-service
```
