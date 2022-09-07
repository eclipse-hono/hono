+++
title = "Building from Source"
weight = 385
+++

Hono can be deployed using the pre-built Docker images available from our
[Docker Hub repositories](https://hub.docker.com/u/eclipse/). However, customizing and/or extending Hono's
functionality requires building the images from source code.

This page provides step by step instructions for getting the source code and building the Hono's Docker images from it.

## Prerequisites for building Hono

#### Docker

Creating Hono's container images using the Hono build process requires a [Docker](https://www.docker.com/) daemon
running either locally or on another host you have access to.
Please follow the instructions on the [Docker web site](https://www.docker.com/) to install Docker on your platform.

In case the docker daemon is not running locally, the following environment variable needs to be set in order for
the examples in the remainder of this page to run successfully:

```sh
export DOCKER_HOST=tcp://${host}:${port}
```

with `${host}` and `${port}` reflecting the name/IP address and port of the host where Docker is running on.

#### Java

Hono is written in Java and therefore requires a Java Development Kit (JDK) version 17 or newer installed on your
computer. Please follow the JDK vendor's instructions for installing Java on your operating system.

#### Maven

Hono's build process is based on [Apache Maven](https://maven.apache.org). You need Maven 3.8.1 or newer to
build Hono. Please follow the [installation instructions on the Maven home page](https://maven.apache.org/).

#### Git

A Git client is required if you want to contribute changes/improvements to the Hono project. It is not necessary for
simply building Hono locally. Please refer to the [Git Downloads page](https://git-scm.com/downloads) for installation
instructions.

## Getting the Hono Source Code

Either

* download the latest [release archive](https://github.com/eclipse-hono/hono/releases) and extract the archive to a local
  folder or
* clone the Hono source code repository from GitHub:
  ```sh
  git clone https://github.com/eclipse-hono/hono.git
  ```
  This will create a `hono` folder in the current working directory and clone the whole repository into that folder.


## Starting the Hono Build Process

Run the following from the source folder:

```sh
export DOCKER_HOST
# in the "hono" folder containing the source code
mvn clean install -Pbuild-docker-image,metrics-prometheus
```

This will build all libraries, Docker images and example code.

{{% notice tip %}}
The first build might take several minutes because Docker will need to download all the base images that Hono is
relying on. However, most of these will be cached by Docker so that subsequent builds will be running much faster.
{{% /notice %}}

#### Gathering Code Coverage Information for Unit Tests

Hono's unit tests can be configured to gather code coverage information during execution using the JaCoCo Maven plugin.
The plugin is disabled by default and can be enabled by setting the *jacoco.skip* maven property to `false`:

```sh
# in the "hono" folder containing the source code
mvn clean install -Djacoco.skip=false -Pbuild-docker-image,metrics-prometheus
```

The plugin will produce a `target/jacoco.exec` file in each module which contains the (binary) coverage data.
It will also produce XML data files under `target/site/jacoco` in each module.
Tools like [SonarQube](https://docs.sonarqube.org/latest/analysis/coverage/) can be used to collect and properly format
this data.

#### Using custom Image Names

The container images being created will have names based on the following pattern:
`${docker.registry-name}/${docker.image.org-name}/${project.artifactId}:${project.version}`.

The variables in the name are standard Maven properties. The default value for the *docker.registry-name* property
is `index.docker.io`. The default value for *docker.image.org-name* is `eclipse`. The following build command creates
Hono's images using the `quay.io` registry and the `custom` repository name:

```sh
mvn clean install -Pbuild-docker-image,metrics-prometheus -Ddocker.registry-name=quay.io -Ddocker.image.org-name=custom
```

#### Building native Images

The build process supports building *native* Docker images using the GraalVM for some of Hono's components.
In order to do so, the `build-native-image` Maven profile needs to be activated:

```sh
# in the "hono" folder containing the source code
mvn clean install -Pbuild-native-image,metrics-prometheus
```

{{% notice info %}}
Support for *native* images is an experimental feature. The `build-native-image` and the `build-docker-image` profiles are
mutually exclusive. However, they can be built one after the other.
{{% /notice %}}

#### Pushing Images

The container images that are created as part of the build process can be automatically pushed to a container registry
using the `docker-push-image` Maven profile:

```sh
mvn clean install -Pbuild-docker-image,metrics-prometheus,docker-push-image
```

Note that the container registry might require authentication in order to push images. The build uses the Docker Maven
Plugin for creating and pushing images. Please refer to the [plugin documentation](https://dmp.fabric8.io/#authentication)
for details regarding how to configure credentials for the registry.

#### Building Images for the arm64 Platform

By default, the build process creates container images for the host system platform. For example when running the build
on an `amd64` based system, the container images created will be for the `amd64` platform as well.

Images for the `arm64` platform can, of course, be created by running the build on an `arm64` based system. However, it is also
possible to create images for arbitrary platforms by means of [Docker's buildx command](https://docs.docker.com/build/buildx/).

On Linux based hosts it is possible to use QEMU for building images for other platforms than the host system as described in this
[blog post](https://medium.com/@nshankar_88597/building-and-testing-multi-arch-images-with-docker-buildx-and-qemu-8f72c2f8728b).

Once QEMU and Docker buildx support have been set up, the `docker-multiarch-build` Maven profile can be used to build container
images for both the `amd64` as well as the `arm64` platforms:

```sh
mvn clean install -Pbuild-docker-image,docker-multiarch-build,docker-push-image,metrics-prometheus -Ddocker.registry-name=registry.custom.org -Ddocker.image.org-name=my-repo
```

Note that the `docker-push-image` profile has been activated as well. This is necessary because buildx currently does not support
loading multiple platform build results into the local image cache. Therefore, the created images need to be pushed to a
container registry instead. The `docker.registry-name` and `docker.image.org-name` Maven properties can be used to set
the (host) name of the container registry and the name of the image repository to use for the container image names.

{{% notice style="info" %}}
While creating arm64 specific container images for Hono's components is necessary in order to run Hono on an arm64 based
platform, it may not be sufficient to do so. That is because Hono depends on several other (external) services to be available.
For example, the Mongo DB based registry implementation needs to be configured with a connection to a Mongo DB server and
the Command Router should be configured with access to an Infinispan data grid in a production environment.
Container images for these services may or may not be available for the arm64 platform, so these services might need to be
deployed to an amd64 based platform or hosted externally.
{{% /notice %}}

## Running the Integration Tests

The source code for Hono comes with a test suite for integration testing. To trigger these tests, change to the `tests`
folder and execute:

```
# in the "hono/tests" folder containing the test suite
mvn verify -Prun-tests
```

The tests are executed against the Docker images of the Hono components. Because of that, it is necessary to build the
respective images as described above before the execution of the tests. See the [hono/tests/readme.md](https://github.com/eclipse-hono/hono/blob/master/tests/readme.md)
file for more information regarding the test suite.

## IDE setup

### Checkstyle

Hono uses Checkstyle to ensure its code conforms to a set of defined coding rules. Corresponding checks are done
during the Hono build by means of the Maven Checkstyle plugin.
In order to integrate the coding rules in the IDE, the corresponding plugin (e.g. [Eclipse Checkstyle Plugin](https://checkstyle.org/eclipse-cs) or [Checkstyle-IDEA](https://plugins.jetbrains.com/plugin/1065-checkstyle-idea))
can be configured to use the `legal/src/main/resources/checkstyle/default.xml` configuration file.

The `checkstyle.suppressions.file` Checkstyle configuration property should point to the `legal/src/main/resources/checkstyle/suppressions.xml` file.

### Code Formatter

In the Eclipse IDE, the following files can be used to configure the Java code style formatter according to the style used in Hono:

- `eclipse/hono-code-style.xml`
- `eclipse/hono-clean-up-profile.xml`
- `eclipse/hono.importorder`

In Intellij IDEA, the above code style and import order configuration files can be applied by means of the
[Adapter for Eclipse Code Formatter](https://plugins.jetbrains.com/plugin/6546-adapter-for-eclipse-code-formatter/) IDEA plugin.

### Running tests

When running Hono unit tests in the IDE, the log output can be configured via the corresponding `src/test/resources/application.yml` file.
Note that by default, color output is enabled via the `quarkus.log.console.color` property. To see the colored output
in Eclipse, the [ANSI Escape in Console plugin](https://marketplace.eclipse.org/content/ansi-escape-console) can be used.
The output looks best when using a dark IDE theme.
