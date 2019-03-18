# Hono Integration Tests

This module contains integration tests for Hono. The tests are executed against Docker images of the Hono components.

## Prerequisites

In order to run the tests you will need the following:

* a working *Docker Engine* installation (either local or on a separate host)
* the Docker images of the Hono components. See the [Getting Started guide on the project web site](https://www.eclipse.org/hono/getting-started/) for instructions on building the Docker images.

## Running the Tests

Run the tests by executing the following command from the `tests` directory (add `-Ddocker.host=tcp://${host}:${port}` if Docker is not installed locally)

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
