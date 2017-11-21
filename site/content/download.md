+++
title = "Downloads"
menu = "main"
weight = 150
+++

## Binaries

The preferred way of deploying Eclipse Hono&trade; is by means of using the pre-built Docker images provided
on [Docker Hub](https://hub.docker.com/u/eclipsehono/).

The most convenient way to both pull the images and start corresponding containers is by means of
[deploying a *stack* using Docker's *Swarm Mode*](https://docs.docker.com/engine/reference/commandline/stack_deploy/). You can find an example stack definition using the published Hono images in the release archive:

* [Eclipse Hono 0.5-M10 Archive](https://www.eclipse.org/downloads/download.php?file=/hono/eclipse-hono-example-0.5-M10.tar.gz)

After downloading the archive, extract it to a local folder, change into that folder and run the following from the command line (assuming that you have Docker Engine running in Swarm mode):

~~~sh
eclipse-hono-example-0.5-M10$ deploy/docker/swarm_deploy.sh
~~~

You may also want to consider other deployment options:

* [Kubernetes]({{< relref "deployment/kubernetes.md" >}})
* [OpenShift]({{< relref "deployment/openshift.md" >}})
* [Docker Swarm]({{< relref "deployment/docker-swarm.md" >}})

### Older Versions

These artifacts are available for reference only. Please always use the latest milestone since this is the one we can provide the best support for.

* [0.5-M9 Archive](https://www.eclipse.org/downloads/download.php?file=/hono/eclipse-hono-example-0.5-M9.tar.gz)
* [0.5-M8 Archive](https://www.eclipse.org/downloads/download.php?file=/hono/eclipse-hono-example-0.5-M8.tar.gz)
* [0.5-M7 Archive](https://www.eclipse.org/downloads/download.php?file=/hono/eclipse-hono-example-0.5-M7.tar.gz)
* [0.5-M6 Archive](eclipse-hono-example-0.5-M6.tar.gz)
* [0.5-M5 Docker Compose file](docker-compose-0.5-M5.yml)
* [0.5-M4 Docker Compose file](docker-compose-0.5-M4.yml)
* [0.5-M3 Docker Compose file](docker-compose-0.5-M3.yml)
* [0.5-M2 Docker Compose file](docker-compose-0.5-M2.yml)

## Source Code

The source code can be cloned (using [Git](https://git-scm.com/)) or downloaded from the [Eclipse Hono GitHub repository](https://github.com/eclipse/hono)