+++
title = "Building from Source"
weight = 385
+++

Hono can be deployed using the pre-built Docker images available from our [Docker Hub repositories](https://hub.docker.com/u/eclipse/). However, customizing and/or extending Hono's functionality requires building the images from source code.

This page provides step by step instructions for getting the source code and building the Hono's Docker images from it.

## Prerequisites for building Hono

#### Docker

Creating Hono's container images using the Hono build process requires a [Docker](http://www.docker.com) daemon running either locally or on another host you have access to. Please follow the instructions on the [Docker web site](http://www.docker.com) to install Docker on your platform.

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
mvn clean install -Ddocker.host=tcp://${host}:${port} -Pbuild-docker-image,metrics-prometheus
```

with `${host}` and `${port}` reflecting the name/IP address and port of the host where Docker is running on. This will build all libraries, Docker images and example code. If you are running on Linux and Docker is installed locally or you have set the `DOCKER_HOST` environment variable, you can omit the `-Ddocker.host` property definition.

If you plan to build the Docker images more frequently, e.g. because you want to extend or improve the Hono code, then you should define the `docker.host` property in your Maven `settings.xml` file containing the very same value as you would use on the command line as indicated above. The file is usually located in the `.m2` folder in your user's home directory. This way you can simply do a `mvn clean install` later on and the Docker images will be built automatically as well because the `build-docker-image` profile is activated automatically if the Maven property `docker.host` is set.

{{% note title="Be patient" %}}
The first build might take several minutes because Docker will need to download all the base images that Hono is relying on. However, most of these will be cached by Docker so that subsequent builds will be running much faster.
{{% /note %}}
