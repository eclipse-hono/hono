# Hono Integration Tests

This module contains integration tests for Hono. The tests are executed against Docker images of *Hono Server*, *Apache Qpid Dispatch Router* and *Apache ActiveMQ Artemis*.

## Prerequisites

In order to run the tests you will need the following:

* a working *Docker Engine* installation (either local or on a separate host)
* the *Hono Server* Docker image (see [Hono Example](../example/readme.md) on how to build the image)
* access to Docker Hub for pulling in required third party images (*Apache Qpid Dispatch Router* and *ActiveMQ Artemis*)

## Running the Tests

Run the tests by executing the following command from the `tests` directory (add `-Ddocker.host=tcp://${host}:${port}` if Docker is not installed locally)

    $ mvn verify -Prun-tests

This starts the following Docker containers and runs the test cases against them

* hono-server
* hono-device-registry
* Qpid Dispatch Router
* ActiveMQ Artemis Broker
* test-config (a volume image that contains configuration files for the Hono server)
