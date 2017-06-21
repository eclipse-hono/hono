+++
title = "OpenShift"
weight = 475
+++

All the Eclipse Hono&trade; components can be deployed on OpenShift, thanks to the resources YAML files that are provided through the repository.
These files describe such components in terms of _deployments_ and _services_ in order to have the right pods running in the OpenShift cluster so that they are able
to communicate with each other.
<!--more-->

## Prerequisites

The main prerequisite for this kind of deployment is to have an available OpenShift cluster. For a local development, it's pretty simple having such cluster just
using the OpenShift client tools that can be downloaded from the [OpenShift Origin](https://github.com/openshift/origin/releases) project repository.
Follow [this guide](https://github.com/openshift/origin/blob/master/docs/cluster_up_down.md) for setting up a local developer instance of OpenShift,
for having an accessible registry for Docker and starting the cluster locally.

## One _script_ deployment

In order to deploy Eclipse Hono on OpenShift, a bunch of steps are needed as explained in the next chapter. If you want to avoid to do them, a _one click_ deployment
script is available in the repository.
After having the OpenShift cluster up and running and the client tools in the PATH, the deployment can be executed launching the following bash script
(from the "example/openshift" directory)

~~~sh
$ bash openshift_deploy.sh
~~~

When you want to shutdown the Eclipse Hono instance, there is the following useful script:

~~~sh
$ bash openshift_undeploy.sh
~~~

## Step by step deployment

### Creating a project

First, create a new project using the OpenShift client tools in the following way :

~~~sh
$ oc new-project hono
~~~

### Preparing persistent volume

In order to handle the device registry and preserve the related file when pods go down for any reason (i.e. manual scale down to zero instances, crash, ...),
a persistent volume is needed so that can be used, through a _claim_, by the Hono Server component. In general, the persistent volume is deployed by the cluster
administrator but for development purposes, a local `/tmp/hono` directory can be used on your _local_ host but it needs to be created with read/write permissions in the following way :

~~~sh
$ mkdir /tmp/hono
$ chmod 777 /tmp/hono
~~~

After that, it's needed to log into the cluster as a system administrator in order to provision such persistent volume.

~~~sh
$ oc login -u system:admin
$ oc create -f <path-to-repo>/hono/application/target/fabric8/hono-app-pv.yml
~~~

When the persistent volume is provisioned, come back to use the default `developer` user.

~~~sh
$ oc login -u developer
~~~

### Deploying Eclipse Hono components

Using the `developer` user, it's now possible to deploy all the other OpenShift resources related to :

* Qpid Dispatch Router (service and deployment)
* Auth Server (service and deployment)
* Device Registry (service and deployment)
* Hono Server (persistent volume claim, service and deployment)
* HTTP REST adapter (service and deployment)
* MQTT adapter (service and deployment)

In order to start deploy the Qpid Dispatch Router, the following resources needs to be created.

~~~sh
$ oc create -f <path-to-repo>/hono/dispatchrouter/target/fabric8/dispatch-router-svc.yml
$ oc create -f <path-to-repo>/hono/dispatchrouter/target/fabric8/dispatch-router-dc.yml
~~~

Then the Auth Server.

~~~sh
$ oc create -f <path-to-repo>/hono/services/auth/target/fabric8/hono-auth-svc.yml
$ oc create -f <path-to-repo>/hono/services/auth/target/fabric8/hono-auth-dc.yml
~~~

Then the Device Registry.

~~~sh
$ oc create -f <path-to-repo>/hono/services/device-registry/target/fabric8/hono-device-registry-svc.yml
$ oc create -f <path-to-repo>/hono/services/device-registry/target/fabric8/hono-device-registry-dc.yml
~~~

Then the Hono Server, which needs a _claim_ on the persistent volume already provisioned other than a _deployment_ and _service_.

~~~sh
$ oc create -f <path-to-repo>/hono/application/target/fabric8/hono-app-pvc.yml
$ oc create -f <path-to-repo>/hono/application/target/fabric8/hono-app-svc.yml
$ oc create -f <path-to-repo>/hono/application/target/fabric8/hono-app-dc.yml
~~~

Finally, both the adapters (HTTP REST and MQTT).

~~~sh
$ oc create -f <path-to-repo>/hono/adapters/rest-vertx/target/fabric8/hono-adapter-rest-vertx-svc.yml
$ oc create -f <path-to-repo>/hono/adapters/rest-vertx/target/fabric8/hono-adapter-rest-vertx-dc.yml
$ oc create -f <path-to-repo>/hono/adapters/mqtt-vertx/target/fabric8/hono-adapter-mqtt-vertx-svc.yml
$ oc create -f <path-to-repo>/hono/adapters/mqtt-vertx/target/fabric8/hono-adapter-mqtt-vertx-dc.yml
~~~

In this way, all the components are accessible inside the cluster using the _service_ addresses from the clients point of view.

In order to see the deployed components, you can use the OpenShift Web console that is accessible at https://localhost:8443/ using your preferred browser.

In the following pictures an Eclipse Hono deployment on OpenShift is running with all the provided components.

![Eclipse Hono on Openshift](../openshift_01.png)

![Eclipse Hono on Openshift](../openshift_02.png)
