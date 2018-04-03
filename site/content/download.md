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

### Latest Milestone

The newest features and bugfixes are available in *milestones* that are published every 4 -6 weeks. Please note that these milestones represent the latest *state of development* which also means that APIs or features may change from one milestone to the other. Installation works in the same way as for the latest stable release.

* [Eclipse Hono 0.6-M2 Archive](https://www.eclipse.org/downloads/download.php?file=/hono/eclipse-hono-example-0.6-M2.tar.gz)

A Java based command line client for consuming telemetry data and events from Hono is available for download as well:

* [Eclipse Hono 0.6-M2 Command Line Client](https://www.eclipse.org/downloads/download.php?file=/hono/hono-example-0.6-M2-exec.jar)

The client can be run from the command line like this:

~~~sh
$ java -jar hono-example-0.6-M2-exec.jar --hono.client.host=hono.eclipse.org --hono.client.port=15672 --hono.client.username=consumer@HONO --hono.client.password=verysecret --spring.profiles.active=receiver --tenant.id=DEFAULT_TENANT
~~~

Please refer to the [Admin Guide]({{< relref "admin-guide/hono-client-configuration.md" >}}) for details regarding the command line options that the client supports.

### Older Versions

These artifacts are available for reference only. Please always use the latest version since this is the one we can provide the best support for.

* [Eclipse Hono 0.6-M1 Archive](https://www.eclipse.org/downloads/download.php?file=/hono/eclipse-hono-example-0.6-M1.tar.gz)
* [Eclipse Hono 0.5-M10 Archive](https://www.eclipse.org/downloads/download.php?file=/hono/eclipse-hono-example-0.5-M10.tar.gz)

## Source Code

The source code can be cloned (using [Git](https://git-scm.com/)) or downloaded from the [Eclipse Hono GitHub repository](https://github.com/eclipse/hono)

## Release Notes

For a list of the changes in this release, take a look at the [release notes]({{< relref "release-notes.md" >}}).
