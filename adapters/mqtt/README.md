# hono-adapter-mqtt project

This module contains Hono MQTT protocol adapter implemented using Vert.x and Quarkus.


## Running the application in dev mode

You can run your application in dev mode that enables live coding using:
```
mvn quarkus:dev
```

## Packaging and running the application

The application can be packaged using `mvn package`.
It produces the `hono-adapter-mqtt-1.0-SNAPSHOT-runner.jar` file in the `/target` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `target/lib` directory.

The application is now runnable using `java -jar target/hono-adapter-mqtt-1.0-SNAPSHOT-runner.jar`.

## Creating a native executable

You can create a native executable using: `mvn package -Pnative`.

You can then execute your native executable with: `./target/hono-adapter-mqtt-1.0-SNAPSHOT-runner`

## Creating Docker images

You can create a docker image by running: `mvn clean install -Pbuild-docker-image`

If you want to create a native image as well, activate the `native` profile as well:

```sh
mvn clean install -Pbuild-docker-image,native
```
