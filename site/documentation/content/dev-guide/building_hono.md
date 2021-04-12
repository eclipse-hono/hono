+++
title = "Building from Source"
weight = 385
+++

Hono can be deployed using the pre-built Docker images available from our [Docker Hub repositories](https://hub.docker.com/u/eclipse/). However, customizing and/or extending Hono's functionality requires building the images from source code.

This page provides step by step instructions for getting the source code and building the Hono's Docker images from it.

## Prerequisites for building Hono

#### Docker

Creating Hono's container images using the Hono build process requires a [Docker](https://www.docker.com/) daemon
running either locally or on another host you have access to.
Please follow the instructions on the [Docker web site](https://www.docker.com/) to install Docker on your platform.

#### Java

Hono is written in Java and therefore requires a Java Development Kit (JDK) version 11 or higher installed on your computer. Please follow the JDK vendor's instructions for installing Java on your operating system.

#### Maven

Hono's build process is based on [Apache Maven](https://maven.apache.org). You need at least Maven 3.5 in order to build Hono.
Please follow the [installation instructions on the Maven home page](https://maven.apache.org/).

#### Git

A Git client is required if you want to contribute changes/improvements to the Hono project. It is not necessary for simply building Hono locally.
Please refer to the [Git Downloads page](https://git-scm.com/downloads) for installation instructions.

## Getting the Hono Source Code

Either

* download the latest [release archive](https://github.com/eclipse/hono/releases) and extract the archive to a local folder or
* clone the Hono source code repository from GitHub:
  ```
  git clone https://github.com/eclipse/hono.git
  ```
  This will create a `hono` folder in the current working directory and clone the whole repository into that folder.


## Starting the Hono Build Process

Run the following from the source folder:

```sh
# in the "hono" folder containing the source code
mvn clean install -Ddocker.host=tcp://${host}:${port} -Pbuild-docker-image,metrics-prometheus,jaeger
```

with `${host}` and `${port}` reflecting the name/IP address and port of the host where Docker is running on. This will build all libraries, Docker images and example code. If you are running on Linux and Docker is installed locally or you have set the `DOCKER_HOST` environment variable, you can omit the `-Ddocker.host` property definition.

If you plan to build the Docker images more frequently, e.g. because you want to extend or improve the Hono code, then you should define the `docker.host` property in your Maven `settings.xml` file containing the very same value as you would use on the command line as indicated above. The file is usually located in the `.m2` folder in your user's home directory. This way you can simply do a `mvn clean install` later on and the Docker images will be built automatically as well because the `build-docker-image` profile is activated automatically if the Maven property `docker.host` is set.

{{% note title="Be patient" %}}
The first build might take several minutes because Docker will need to download all the base images that Hono is relying on. However, most of these will be cached by Docker so that subsequent builds will be running much faster.
{{% /note %}}

#### Using custom Image Names

The container images being created will have names based on the following pattern:
`${docker.registry-name}/${docker.image.org-name}/${project.artifactId}:${project.version}`.

The variables in the name are standard Maven properties. The default value for the *docker.registry-name* property is `index.docker.io`.
The default value for *docker.image.org-name* is `eclipse`. The following build command creates Hono's images using the `quay.io` registry
and the `custom` repository name:

```sh
mvn clean install -Ddocker.host=tcp://${host}:${port} -Pbuild-docker-image,metrics-prometheus,jaeger -Ddocker.registry-name=quay.io -Ddocker.image.org-name=custom
```

#### Building native Images

The build process supports building *native* Docker images using the GraalVM for some of Hono's components.
In order to do so, the `build-native-image` Maven profile needs to be activated:

```sh
# in the "hono" folder containing the source code
mvn clean install -Ddocker.host=tcp://${host}:${port} -Pbuild-native-image,metrics-prometheus
```

{{% note title="Experimental" %}}
Support for *native* images is an experimental feature. The `build-native-image` and the `build-docker-image` profiles are mutually exclusive.
Using Jaeger tracing with native images doesn't work yet, i.e. the `jaeger` Maven profile must not be activated when building
native images.
{{% /note %}}

#### Pushing Images

The container images that are created as part of the build process can be automatically pushed to a container registry using the `docker-push-image` Maven profile:

```sh
mvn clean install -Ddocker.host=tcp://${host}:${port} -Pbuild-docker-image,metrics-prometheus,jaeger,docker-push-image
```

Note that the container registry might require authentication in order to push images. The build uses the Docker Maven Plugin for creating and pushing images.
Please refer to the [plugin documentation](http://dmp.fabric8.io/#authentication) for details regarding how to configure credentials for the registry.

## Perform the integration tests

The source code for Hono comes with a test suite for integration testing. To trigger these tests, change to the `tests` folder and execute:

```
# in the "hono/tests" folder containing the test suite
mvn verify -Prun-tests
```

The tests are executed against the Docker images of the Hono components. Because of that, it is necessary to build the respective images as
described above before the execution of the tests. The respective `Readme.md` file in the folder `hono/tests` contains more information regarding the test suite.
