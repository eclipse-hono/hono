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
* Apache Kafka
* Hono Command Router service
* Hono HTTP adapter
* Hono MQTT adapter
* Hono AMQP adapter
* Hono CoAP adapter
* Hono Lora adapter

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

### Running the Tests with the Native Executable Container Images

The tests can be run using the native executable based images using the `hono.components.type` Maven property:

```sh
mvn verify -Prun-tests -Dhono.components.type=quarkus-native
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

### Running the Tests for a particular Protocol Adapter only

By default, all protocol adapter listed in the overview above are started as part of running the integration tests.
However, when working on a particular protocol adapter, only the integration tests verifying that adapter's behavior
will be of interest and thus starting all other adapters seems unnecessary.

The following Maven profiles can be used to run the integration tests with a single protocol adapter only:

* `amqp-only`
* `coap-only`
* `http-only`
* `lora-only`
* `mqtt-only`

For example, the following command will run all MQTT related test cases and skip the tests for the other adapter types:

```sh
mvn verify -Prun-tests,mqtt-only
```

The `no-adapters` Maven profile can be used to run no adapter (and corresponding tests) at all. This might be useful when
working on the device registry and/or command router components.

It is also possible to selectively disable one or more protocol adapters and skip the corresponding test cases
by means of setting one or more of the following Maven properties to `true`:

* `hono.amqp-adapter.disabled`
* `hono.coap-adapter.disabled`
* `hono.http-adapter.disabled`
* `hono.lora-adapter.disabled`
* `hono.mqtt-adapter.disabled`

For example, the following command will not start the MQTT adapter and will run all tests except for the MQTT related ones:

```sh
mvn verify -Prun-tests -Dhono.mqtt-adapter.disabled=true
```

### Running the Tests with local services

The integration tests are usually run against Hono components that have been started as
containers using Docker. However, the tests can also be executed against protocol adapters and/or
service components that have been started as normal Java applications.
This is particularly helpful when developing a feature in one of the components and then running
integration tests to verify the component's behavior. For example, when working on the HTTP protocol
adapter, all of Hono's service components can be run as Docker containers while the HTTP adapter can
be run as a Java application.

1. Start Hono services as Docker containers:

   ``` sh
   mvn verify -Dstart -Pno-adapters,jaeger
   ```

   The command line above starts the infrastructure services like MongoDB, the Kafka messaging
   infrastructure and the Jaeger tracing back end. Switching the type of messaging infrastructure is
   supported by the Maven properties/profiles described above.

   The service container ports are all mapped to fixed host ports. This allows easy monitoring via external
   tools (e.g. database viewers).

2. Start relevant protocol adapters using Quarkus IDE integration or using the `quarkus:dev` Maven goal.

   Started this way, the protocol adapters are configured to connect to the services running as Docker
   containers. By default, the components started as Java applications do not report tracing information
   to the Jaeger back end. However, if Jaeger has been started as a Docker container (as in the command
   used in step 1 above), then reporting of tracing information can be enabled by means of setting
   the *otel.traces.sampler* Maven property to value `always_on`:

   ```sh
   mvn quarkus:dev -Dotel.traces.sampler=always_on
   ```

3. Run integration tests against local adapter. The command below will run all MQTT integration tests
   (assuming that the MQTT adapter has been started as a Java application in step 2):

   ```sh
   mvn verify -Plocal,fix-ports,useRunningContainers,run-tests -Dit.test=mqtt/*IT
   ```

4. Finaly all Hono related containers (e.g. dependencies) could be stopped as described above:

   ```sh
   mvn verify -PstopContainers
   ```

