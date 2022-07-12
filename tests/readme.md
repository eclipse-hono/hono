# Hono Integration Tests

This module contains integration tests for Hono. The tests are executed against Docker images of the Hono components.

## Prerequisites

In order to run the tests you will need the following:

* a working *Docker Engine* installation (either local or on a separate host)
* the Docker images of the Hono components. See the
  [Developer Guide on the project web site](https://www.eclipse.org/hono/docs/dev-guide/building_hono/) for
  instructions on building the Docker images.

## Running the Tests

Run the tests by executing the following command from the `tests` directory (add `-Ddocker.host=tcp://${host}:${port}`
if Docker is not installed locally)

```sh
# in directory: hono/tests/
mvn verify -Prun-tests
```

This starts the following Docker containers and runs the test cases against them

* Infinispan server
* MongoDB server
* Hono Authentication service
* Hono Device Registration service
* Apache Zookeeper
* Apache Kafka
* Hono Command Router service
* Hono HTTP adapter
* Hono MQTT adapter
* Hono AMQP adapter
* Hono CoAP adapter

To run all tests of a certain class, or to run an individual test, set the `it.test` property:

```sh
mvn verify -Prun-tests -Dit.test=TelemetryHttpIT
mvn verify -Prun-tests -Dit.test=TelemetryHttpIT#testUploadUsingQoS1
```

The `logging.profile` property with a value of either `prod`, `dev` or `trace` can be used to set the log level in
the Hono Docker containers and the integration tests:

```sh
mvn verify -Prun-tests -Dlogging.profile=trace
```

### Running the Tests without starting/stopping the containers

When running the tests with the `docker.keepRunning` property, the Docker containers will not be stopped and removed
once the tests are complete:

```sh
mvn verify -Prun-tests -Ddocker.keepRunning
```

Subsequent test runs can use the running containers and will thereby finish much faster by adding the
`useRunningContainers` profile to the Maven command:

```sh
mvn verify -Prun-tests,useRunningContainers
```

With that profile, the Docker containers will be kept running as well.

In order to stop and remove the Docker containers started by a test run, use:

```sh
mvn verify -PstopContainers
```

### Running the Tests with the JDBC based device registry using Postgres

By default, the integration tests are run using the Mongo DB based device registry.
In order to use the JDBC based device registry with a dedicated Postgres server,
the `hono.deviceregistry.type` Maven property needs to be set to value `jdbc`:

```sh
mvn verify -Prun-tests -Dhono.deviceregistry.type=jdbc
```

### Running the Tests with the JDBC based device registry using embedded H2

By default, the integration tests are run using the Mongo DB based device registry.
In order to use the JDBC based device registry with an embedded H2 database,
the `hono.deviceregistry.type` Maven property needs to be set to value `file`:

```sh
mvn verify -Prun-tests -Dhono.deviceregistry.type=file
```

### Running the Tests with the Jaeger tracing component

The tests can be run in such a way, that the OpenTelemetry trace spans created in the Hono components
as part of a test run can be inspected later on. The tracing component used for this is Jaeger.

Run the tests using the `jaeger` Maven profile:

```sh
# in directory: hono/tests/
mvn verify -Prun-tests,jaeger -Ddocker.keepRunning
```

To run a single test instead, use e.g.:

```sh
mvn verify -Prun-tests,jaeger -Ddocker.keepRunning -Dit.test=TelemetryHttpIT#testUploadUsingQoS1
```

Note that in order to be able to view the traces after a test run, the `docker.keepRunning` property has
to be used as well, as shown above.  

The Jaeger UI, showing the traces of the test run, can be accessed at `http://localhost:18080`

if the Docker engine is running on `localhost`, otherwise the appropriate Docker host has to be used in the
above URL. To choose a different port for the Jaeger UI, set the `jaeger.query.port` Maven property to the
desired port when running the tests. 

Start subsequent test runs using the running containers with the `useRunningContainers` Maven profile, e.g.:

```sh
mvn verify -Prun-tests,jaeger,useRunningContainers -Dit.test=TelemetryHttpIT#testUploadUsingQoS1
```

In order to stop and remove the Docker containers started by a test run, use:

```sh
mvn verify -PstopContainers
```

### Running the Tests with the Command Router using an embedded Cache

The Command Router component by default stores routing information in a dedicated Infinispan server.
The router can also be configured to instead store the data in an embedded cache by means of
setting the `hono.commandrouting.cache` Maven property to value `embedded`:

```sh
mvn verify -Prun-tests -Dhono.commandrouting.cache=embedded
```

### Running the Tests with AMQP 1.0 based Messaging Infrastructure

By default, the integration tests are run using the Kafka based messaging infrastructure. In order to use
AMQP 1.0 based messaging infrastructure instead, the `hono.messaging-infra.type` Maven property needs to be set to
value `amqp`:

```sh
mvn verify -Prun-tests -Dhono.messaging-infra.type=amqp
```
