+++
title = "Artifacts for installing and using Hono"
description = "Links to binary and source code artifacts required for installing and using Hono."
menu = "main"
linkTitle = "Download"
weight = 150
+++

## Binaries

Eclipse Hono's service components are provided by means of pre-built Docker images available from
[Docker Hub](https://hub.docker.com/u/eclipse/). These container images can be deployed to popular
container orchestration platforms like Kubernetes and OpenShift.

The [Eclipse IoT Packages](https://www.eclipse.org/packages/) project hosts the
[Hono Helm chart](https://github.com/eclipse/packages/tree/master/charts/hono)
which can be used to install the most recent release of Hono to a Kubernetes cluster
using the [Helm package manager](https://helm.sh).
Please refer to the [deployment guide]({{% doclink "/deployment/helm-based-deployment/" %}})
for installation instructions.

A Java based command line client for consuming telemetry data and events from Hono is available for download from

* [Eclipse Hono 1.10.1 Command Line Client](https://www.eclipse.org/downloads/download.php?file=/hono/hono-cli-1.10.1-exec.jar)

The client requires a Java 11 runtime environment to run.

Please refer to the [Admin Guide]({{% doclink "/admin-guide/hono-client-configuration/" %}}) for details regarding the command
line options that the client supports.

## Source Code

The source code can be cloned or downloaded from [Hono's GitHub repository](https://github.com/eclipse/hono).
The [Building from Source]({{% doclink "/dev-guide/building_hono/" %}}) guide provides instructions on how to build Hono locally.

## Release Notes

For a list of the changes in this release, take a look at the [release notes]({{< relref "release-notes" >}}).

## Previous versions

* [Eclipse Hono 1.10.0 Command Line Client](https://www.eclipse.org/downloads/download.php?file=/hono/hono-cli-1.10.0-exec.jar)
* [Eclipse Hono 1.9.1 Command Line Client](https://www.eclipse.org/downloads/download.php?file=/hono/hono-cli-1.9.1-exec.jar)
* [Eclipse Hono 1.9.0 Command Line Client](https://www.eclipse.org/downloads/download.php?file=/hono/hono-cli-1.9.0-exec.jar)
