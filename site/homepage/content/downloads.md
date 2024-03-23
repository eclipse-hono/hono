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

A command line client for interacting with Hono's north bound APIs and its AMQP adapter is available for download from

* [Eclipse Hono 2.5.0 Command Line Client (Java Archive)](https://www.eclipse.org/downloads/download.php?file=/hono/hono-cli-2.5.0-exec.jar)
* [Eclipse Hono 2.5.0 Command Line Client (Linux x86_64)](https://www.eclipse.org/downloads/download.php?file=/hono/hono-cli-2.5.0)

The Java client requires a Java 17 runtime environment to run. The Linux binary should run on most (modern) Linux distributions.

Please refer to the [Getting Started Guide]({{% doclink "/getting-started/" %}}) for details regarding the usage of the command
line client.

## Source Code

The source code can be cloned or downloaded from [Hono's GitHub repository](https://github.com/eclipse-hono/hono).
The [Building from Source]({{% doclink "/dev-guide/building_hono/" %}}) guide provides instructions on how to build Hono locally.

## Release Notes

For a list of the changes in this release, take a look at the [release notes]({{< relref "release-notes" >}}).

## Previous versions

* [Eclipse Hono 2.4.1 Command Line Client (Java Archive)](https://www.eclipse.org/downloads/download.php?file=/hono/hono-cli-2.4.1-exec.jar)
* [Eclipse Hono 2.4.1 Command Line Client (Linux x86_64)](https://www.eclipse.org/downloads/download.php?file=/hono/hono-cli-2.4.1)
* [Eclipse Hono 2.4.0 Command Line Client (Java Archive)](https://www.eclipse.org/downloads/download.php?file=/hono/hono-cli-2.4.0-exec.jar)
* [Eclipse Hono 2.4.0 Command Line Client (Linux x86_64)](https://www.eclipse.org/downloads/download.php?file=/hono/hono-cli-2.4.0)
