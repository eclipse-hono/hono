+++
title = "Downloads"
menu = "main"
weight = 150
+++

## Binaries

The preferred way of deploying Eclipse Hono&trade; is by means of using the pre-built Docker images provided
on [Docker Hub](https://hub.docker.com/u/eclipse/).

The most convenient way to both pull the images and start corresponding containers is by means of running the deployment script contained in the release archive.

* [Eclipse Hono 0.9 Archive](https://www.eclipse.org/downloads/download.php?file=/hono/eclipse-hono-deploy-0.9.tar.gz)

After downloading the archive, extract it to a local folder, change into that folder and run the following from the command line (assuming that your local Docker client is configured to connect to a Docker Swarm manager):

~~~sh
eclipse-hono-deploy-0.9$ deploy/docker/swarm_deploy.sh
~~~

Hono supports deployment to the following container orchestration platforms:

* [Kubernetes]({{< ref "/deployment/kubernetes.md" >}})
* [OpenShift]({{< ref "/deployment/openshift.md" >}})
* [Docker Swarm]({{< ref "/deployment/docker-swarm.md" >}})

A Java based command line client for consuming telemetry data and events from Hono is available for download as well:

* [Eclipse Hono 0.9 Command Line Client](https://www.eclipse.org/downloads/download.php?file=/hono/hono-cli-0.9-exec.jar)

The client can be run from the command line like this:

~~~sh
$ java -jar hono-cli-0.9-exec.jar --hono.client.host=hono.eclipse.org --hono.client.port=15671 \
--hono.client.tlsEnabled=true --hono.client.username=consumer@HONO --hono.client.password=verysecret \
--spring.profiles.active=receiver --tenant.id=DEFAULT_TENANT
~~~

Please refer to the [Admin Guide]({{< ref "/admin-guide/hono-client-configuration.md" >}}) for details regarding the command line options that the client supports.

### Older Versions

These artifacts are available for reference only. Please always use the latest version since this is the one we can provide the best support for.

* [Eclipse Hono 0.8 Archive](https://www.eclipse.org/downloads/download.php?file=/hono/eclipse-hono-example-0.8.tar.gz)
* [Eclipse Hono 0.7 Archive](https://www.eclipse.org/downloads/download.php?file=/hono/eclipse-hono-example-0.7.tar.gz)
* [Eclipse Hono 0.6 Archive](https://www.eclipse.org/downloads/download.php?file=/hono/eclipse-hono-example-0.6.tar.gz)

## Source Code

The source code can be cloned (using [Git](https://git-scm.com/)) or downloaded from the [Eclipse Hono GitHub repository](https://github.com/eclipse/hono)

## Release Notes

For a list of the changes in this release, take a look at the [release notes]({{< ref "/release-notes.md" >}}).
