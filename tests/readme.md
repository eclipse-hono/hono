# Hono Tests

This module contains integration tests against Hono run in a docker based environment.

## Prerequisites

In order to run the tests you will need the following (see [Hono Example](../example/readme.md) on how to build the required images).

* *Docker*
* The *Hono* docker image
* An *Apache Qpid Dispatch Router* docker image
* The *test-config* docker image (contains configuration for the Hono server, build with command `mvn docker:build` from `tests` directory)

## Running the Tests

After these prerequisites are met you can run the tests by executing the following command from the `tests` directory (you may add `-Ddocker.host=tcp://${host}:${port}` if required)

    $ mvn verify -Prun-tests

The test starts the following containers

* hono-server
* Qpid Dispatch Router
* test-config (volume image, contains configuration for hono server)

and executes all existing `*IT` tests against this environment.