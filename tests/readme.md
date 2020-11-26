# Hono Integration Tests

This module contains integration tests for Hono. The tests are executed against Docker images of the Hono components.

## Prerequisites

In order to run the tests you will need the following:

* a working *Docker Engine* installation (either local or on a separate host)
* the Docker images of the Hono components. See the [Developer Guide on the project web site](https://www.eclipse.org/hono/docs/dev-guide/building_hono/) for instructions on building the Docker images.

## Running the Tests

Run the tests by executing the following command from the `tests` directory (add `-Ddocker.host=tcp://${host}:${port}` if Docker is not installed locally)

    # in directory: hono/tests/
    $ mvn verify -Prun-tests

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

    $ mvn verify -Prun-tests -Dit.test=TelemetryHttpIT
    $ mvn verify -Prun-tests -Dit.test=TelemetryHttpIT#testUploadUsingQoS1

The `logging.profile` property with a value of either `prod`, `dev` or `trace` can be used to set the log level in the Hono Docker containers:

    $ mvn verify -Prun-tests -Dlogging.profile=trace

### Running the Tests without starting/stopping the containers

When running the tests with the `docker.keepRunning` property, the Docker containers will not be stopped and removed once the tests are complete:

    $ mvn verify -Prun-tests -Ddocker.keepRunning

Subsequent test runs can use the running containers and will thereby finish much faster by adding the `useRunningContainers` profile to the maven command:

    $ mvn verify -Prun-tests,useRunningContainers

With that profile, the Docker containers will be kept running as well.

In order to stop and remove the Docker containers started by a test run, use:

    $ mvn verify -PstopContainers

### Running the Tests with the MongoDB based device registry

By default, the integration tests include the file based device registry. In order to include the MongoDB based device registry instead of the file based counterpart, use the `device-registry-mongodb` maven profile:

    $ mvn verify -Pdevice-registry-mongodb,run-tests

### Running the Tests with the Jaeger tracing component

The tests can be run in such a way, that the OpenTracing trace spans created in the Hono components as part of a test run can be inspected later on. The OpenTracing component used for this is Jaeger.
 
To include the Jaeger client, build the Hono Docker images using the `jaeger` maven profile:

    # in the "hono" folder containing the source code
    $ mvn clean install -Pbuild-docker-image,metrics-prometheus,jaeger

(Add a `-Ddocker.host` property definition if needed, as described in the [Developer Guide](https://www.eclipse.org/hono/docs/dev-guide/building_hono/).)

Then run the tests using the `jaeger` maven profile:

    # in directory: hono/tests/
    $ mvn verify -Prun-tests,jaeger -Ddocker.keepRunning

To run a single test instead, use e.g.:

    $ mvn verify -Prun-tests,jaeger -Ddocker.keepRunning -Dit.test=TelemetryHttpIT#testUploadUsingQoS1

Note that in order to be able to view the traces after a test run, the `docker.keepRunning` property has to be used as well, as shown above.  

The Jaeger UI, showing the traces of the test run, can be accessed at

    http://localhost:18080

if the Docker engine is running on `localhost`, otherwise the appropriate Docker host has to be used in the above URL. To choose a different port for the Jaeger UI, set the `jaeger.query.port` maven property to the desired port when running the tests. 

Start subsequent test runs using the running containers with the `useRunningContainers` maven profile, e.g.:

    $ mvn verify -Prun-tests,jaeger,useRunningContainers -Dit.test=TelemetryHttpIT#testUploadUsingQoS1

In order to stop and remove the Docker containers started by a test run, use:

    $ mvn verify -PstopContainers

### Running the Tests with the Quarkus based Protocol Adapters

By default, the integration tests are run using the Spring Boot based protocol adapters. For some protocol adapters there are
Quarkus based alternative implementations. The tests can be run using these Quarkus based adapters by means of activating
the `protocol-adapters-quarkus` maven profile:

    $ mvn verify -Prun-tests,protocol-adapters-quarkus

### Running the Tests with the Command Router component

By default, the integration tests are run using the Device Connection service component. In order to use the Command
Router service component instead, the `command-router` maven profile can be set:

    $ mvn verify -Prun-tests,command-router
