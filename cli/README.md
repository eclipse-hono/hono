This module contains CLI tools for communication with Hono. This includes:

1. a command-line client to interact with Hono's northbound APIs
2. a command-line client to interact with
   Hono's [AMQP protocol adapter](https://www.eclipse.org/hono/docs/user-guide/amqp-adapter/).

The component is implemented as a Spring Boot application and can be configured with its
[configuration mechanisms](https://docs.spring.io/spring-boot/docs/current/reference/html/spring-boot-features.html#boot-features-external-config)
and [profiles](https://docs.spring.io/spring-boot/docs/current/reference/html/spring-boot-features.html#boot-features-profiles).

For the available configuration options see
the [Hono Client Configuration](https://www.eclipse.org/hono/docs/admin-guide/hono-client-configuration/).


## Usage of the northbound CLI application

Build the CLI: 

```bash 
# in directory hono/cli/
mvn clean install
cd target/
```
Receiving *events* and *telemetry data* from devices is enabled with the profile `receiver`.
To send *commands* to a device, use the profile `command`.
To enable Kafka based messaging, add the profile `kafka`.

For instructions on how to get the addresses of the services and register devices please refer to
the [Getting Started Guide](https://www.eclipse.org/hono/getting-started/).


### Examples with AMQP based messaging

Receive *events* and *telemetry data* sent to the configured AMQP messaging network for a tenant:

```bash 
# in directory hono/cli/target/
java -jar hono-cli-*-exec.jar --hono.client.host=$AMQP_NETWORK_IP --hono.client.port=15672 --hono.client.username=consumer@HONO --hono.client.password=verysecret --tenant.id=$MY_TENANT --spring.profiles.active=receiver 
```

Send a *command* to a device over the configured AMQP messaging network:

```bash 
# in directory hono/cli/target/
java -jar hono-cli-*-exec.jar --hono.client.host=$AMQP_NETWORK_IP --hono.client.port=15672 --hono.client.username=consumer@HONO --hono.client.password=verysecret --tenant.id=$MY_TENANT --device.id=$MY_DEVICE --spring.profiles.active=command
```


### Examples with Kafka based messaging 

Receive *events* and *telemetry data* sent to the configured Kafka cluster for a tenant:

```bash 
# in directory hono/cli/target/
java -jar hono-cli-*-exec.jar --hono.kafka.commonClientConfig.bootstrap.servers=$KAFKA_SERVERS --tenant.id=$MY_TENANT --spring.profiles.active=receiver,kafka
```

Send a *command* to a device over the configured Kafka cluster:

```bash 
# in directory hono/cli/target/
java -jar hono-cli-*-exec.jar --hono.kafka.commonClientConfig.bootstrap.servers=$KAFKA_SERVERS --tenant.id=$MY_TENANT --device.id=$MY_DEVICE --spring.profiles.active=command,kafka 
```


## Usage of the AMQP Adapter CLI client

Please refer to the documentation of the 
[AMQP Command-line Client](https://www.eclipse.org/hono/docs/user-guide/amqp-adapter/#amqp-command-line-client)
for the usage of the CLI that can be used to connect to the AMQP protocol adapter.
