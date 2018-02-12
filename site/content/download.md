+++
title = "Downloads"
menu = "main"
weight = 150
+++

## Binaries

The preferred way of deploying Eclipse Hono&trade; is by means of using the pre-built Docker images provided
on [Docker Hub](https://hub.docker.com/u/eclipsehono/).

The most convenient way to both pull the images and start corresponding containers is by means of running the deployment script contained in the release archive.

* [Eclipse Hono 0.5 Archive](https://www.eclipse.org/downloads/download.php?file=/hono/eclipse-hono-example-0.5.tar.gz)

After downloading the archive, extract it to a local folder, change into that folder and run the following from the command line (assuming that your local Docker client is configured to connect to a Docker Swarm manager):

~~~sh
eclipse-hono-example-0.5$ deploy/docker/swarm_deploy.sh
~~~

Hono supports deployment to the following container orchestration platforms:

* [Kubernetes]({{< relref "deployment/kubernetes.md" >}})
* [OpenShift]({{< relref "deployment/openshift.md" >}})
* [Docker Swarm]({{< relref "deployment/docker-swarm.md" >}})

### Older Versions

These artifacts are available for reference only. Please always use the latest version since this is the one we can provide the best support for.

* [0.5-M10 Archive](https://www.eclipse.org/downloads/download.php?file=/hono/eclipse-hono-example-0.5-M10.tar.gz)

## Source Code

The source code can be cloned (using [Git](https://git-scm.com/)) or downloaded from the [Eclipse Hono GitHub repository](https://github.com/eclipse/hono)

## Release Notes

For a list of the changes in this release, take a look at the [release notes]({{< relref "release-notes.md" >}}).
