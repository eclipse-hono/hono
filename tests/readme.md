# Hono Integration Tests

This module contains integration tests for Hono. The tests are executed against Docker images of the Hono components.

## Prerequisites

In order to run the tests you will need the following:

* a working *Docker Engine* installation (either local or on a separate host)
* the Docker images of the Hono components. See the [Developer Guide on the project web site](https://www.eclipse.org/hono/docs/dev-guide/building_hono/) for instructions on building the Docker images.

## Running the Tests

Run the tests by executing the following command from the `tests` directory (add `-Ddocker.host=tcp://${host}:${port}` if Docker is not installed locally)

    # in directory: hono/tests/
    mvn verify -Prun-tests

This starts the following Docker containers and runs the test cases against them

* Hono Authentication service
* Hono Device Registration service
* ActiveMQ Artemis message broker
* Qpid Dispatch Router
* Hono HTTP adapter
* Hono MQTT adapter
* Hono AMQP adapter
* Hono CoAP adapter

To run a single test, set the `it.test` property:

    $ mvn verify -Prun-tests -Dit.test=TelemetryHttpIT

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
