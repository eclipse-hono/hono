+++
title = "OpenShift"
weight = 475
+++

All the Eclipse Hono&trade; components can be deployed on OpenShift, thanks to the resources YAML files that are provided through the repository.
These files describe such components in terms of _deployments_ and _services_ in order to have the right pods running in the OpenShift cluster so that they are able
to communicate with each other.
<!--more-->

## Prerequisites

The main prerequisite for this kind of deployment is to have an available OpenShift cluster. For a local development, it's pretty simple having such cluster choosing
between using :

* OpenShift Origin client tools
* Minishift

### OpenShift Origin client tools

In this case, the cluster can be deployed just using the OpenShift Origin client tools that can be downloaded from the [OpenShift Origin](https://github.com/openshift/origin/releases) project repository.
Follow [this guide](https://github.com/openshift/origin/blob/master/docs/cluster_up_down.md) for setting up a local developer instance of OpenShift,
for having an accessible registry for Docker and starting the cluster locally. The deployed cluster is made by only one node (the host) running inside a Docker image and
the host Docker registry will be used for getting built images.
 
### Minishift

Minishift is a tool that helps you run OpenShift locally by running a single-node OpenShift cluster inside a VM. Follow [this guide](https://docs.openshift.org/latest/minishift/getting-started/index.html)
for installing and having Minishift up and running.
After launching Minishift and before building the Eclipse Hono images, it's necessary to execute the following command :

~~~sh
$ eval $(minishift docker-env)
~~~

In this way, the `DOCKER_HOST` environment variable is set to the Docker daemon running inside the Minishift VM. Launching the following command for building the Eclipse Hono images,
such daemon will be used and the final images will be available inside the Minishift VM, ready for the deployment.

~~~sh
~/hono$ mvn clean install -Pbuild-docker-image
~~~

## One _script_ deployment

In order to deploy Eclipse Hono on OpenShift, a bunch of steps are needed as explained in the next chapter. If you want to avoid to do them, a _one click_ deployment
script is available in the repository.
After having the OpenShift cluster up and running and the client tools in the PATH, the deployment can be executed launching the following bash script
(from the "example/target/deploy/openshift/" directory)

~~~sh
$ ./openshift_deploy.sh
~~~

When you want to shutdown the Eclipse Hono instance, there is the following useful script:

~~~sh
$ ./openshift_undeploy.sh
~~~

## Step by step deployment

### Creating a project

First, create a new project using the OpenShift client tools in the following way :

~~~sh
$ oc new-project hono
~~~

### Preparing persistent volume

In order to handle the Device Registry and preserve the related file when pods go down for any reason (i.e. manual scale down to zero instances, crash, ...),
a persistent volume is needed so that can be used, through a _claim_, by the Device Registry. In general, the persistent volume is deployed by the cluster
administrator but for development purposes, a local `/tmp/hono` directory can be used on your _local_ host but it needs to be created with read/write permissions in the following way :

~~~sh
$ mkdir /tmp/hono
$ chmod 777 /tmp/hono
~~~

After that, it's needed to log into the cluster as a system administrator in order to provision such persistent volume.

~~~sh
$ oc login -u system:admin
$ oc create -f <path-to-repo>/example/target/deploy/openshift/hono-pv.yml
~~~

When the persistent volume is provisioned, come back to use the default `developer` user.

~~~sh
$ oc login -u developer
~~~

### Deploying Eclipse Hono components

Using the `developer` user, it is now possible to deploy all the other OpenShift resources related to:

1. Grafana
1. Artemis Broker
1. Qpid Dispatch Router
1. Auth Server
1. Device Registry
1. Hono Messaging
1. HTTP REST adapter
1. MQTT adapter

Deploy Grafana (for metrics support):

~~~sh
$ oc create -f <path-to-repo>/hono/metrics/target/classes/META-INF/fabric8/openshift.yml
~~~

Then the Artemis Broker:

~~~sh
$ oc create -f <path-to-repo>/hono/broker/target/classes/META-INF/fabric8/openshift.yml
~~~

Then the Qpid Dispatch Router:

~~~sh
$ oc create -f <path-to-repo>/hono/dispatchrouter/target/classes/META-INF/fabric8/openshift.yml
~~~

Then the Auth Server:

~~~sh
$ oc create -f <path-to-repo>/hono/services/auth/target/classes/META-INF/fabric8/openshift.yml
~~~

Then the Device Registry:

~~~sh
$ oc create -f <path-to-repo>/hono/services/device-registry/target/classes/META-INF/fabric8/openshift.yml
~~~

Then the Hono Messaging component:

~~~sh
$ oc create -f <path-to-repo>/hono/services/messaging/target/classes/META-INF/fabric8/openshift.yml
~~~

Finally, both the adapters (HTTP REST and MQTT).

~~~sh
$ oc create -f <path-to-repo>/hono/adapters/rest-vertx/target/classes/META-INF/fabric8/openshift.yml
$ oc create -f <path-to-repo>/hono/adapters/mqtt-vertx/target/classes/META-INF/fabric8/openshift.yml
~~~

In this way, all the components are accessible inside the cluster using the _service_ addresses from a client's point of view.

In order to see the deployed components, you can use the [OpenShift Web console](https://localhost:8443/) using your preferred browser.

In the following pictures an Eclipse Hono deployment on OpenShift is running with all the provided components.

![Eclipse Hono on Openshift](../openshift_01.png)

![Eclipse Hono on Openshift](../openshift_02.png)

![Eclipse Hono on Openshift](../openshift_03.png)

![Eclipse Hono on Openshift](../openshift_04.png)

![Eclipse Hono on Openshift](../openshift_05.png)

![Eclipse Hono on Openshift](../openshift_06.png)

![Eclipse Hono on Openshift](../openshift_07.png)

![Eclipse Hono on Openshift](../openshift_08.png)

![Eclipse Hono on Openshift](../openshift_grafana.png)

![Eclipse Hono on Openshift](../openshift_influxdb.png)

## Access to Hono services

The OpenShift deployment provides access to Eclipse Hono by meaning of "services" and the main ones are :

* hono-dispatch-router-ext : router network for the business application in order to receive data
* hono-adapter-mqtt-vertx : MQTT protocol adapter for sending data
* hono-adapter-rest-vertx : HTTP protocol adapter for sending data

You can check these services through the `oc get services` command having the following output :

~~~sh
NAME                           CLUSTER-IP       EXTERNAL-IP   PORT(S)                         AGE
grafana                        172.30.104.165   <nodes>       3000:31000/TCP                  2m
hono-adapter-mqtt-vertx        172.30.3.63      <nodes>       1883:31883/TCP,8883:30883/TCP   2m
hono-adapter-rest-vertx        172.30.205.239   <nodes>       8080:30080/TCP,8443:30443/TCP   2m
hono-artemis                   172.30.21.155    <none>        5672/TCP                        2m
hono-dispatch-router           172.30.140.127   <none>        5673/TCP                        2m
hono-dispatch-router-ext       172.30.12.86     <nodes>       5671:30671/TCP,5672:30672/TCP   2m
hono-service-auth              172.30.155.8     <none>        5671/TCP                        2m
hono-service-device-registry   172.30.177.150   <none>        5671/TCP                        2m
hono-service-messaging         172.30.114.146   <none>        5671/TCP                        2m
influxdb                       172.30.203.114   <none>        2003/TCP,8083/TCP,8086/TCP      2m
~~~

Using the "OpenShift Origin client tools" way, these services are accessible using the related `CLUSTER-IP` and the "internal" ports (i.e. 8080, 5671, ...).

Using the "Minishift" way, you have to use the Minishift VM IP address (that you can get with the `minishift ip` command) and the so called *node ports* (i.e. 30080, 30671, ...).

### Starting a Consumer

Ad described in the "Getting Started" guide, data produced by devices is usually consumed by downstream applications which connect directly to the router network service.
You can start the client from the `example` folder as follows:

~~~sh
~/hono/example$ mvn spring-boot:run -Drun.arguments=--hono.client.host=<IP_ADDRESS>,--hono.client.port=<PORT>,--hono.client.username=consumer@HONO,--hono.client.password=verysecret
~~~

where `<IP_ADDRESS>` and `<PORT>` are the IP address and the related port of the router network service as described before.

### Uploading Telemetry

In order to upload telemetry to Hono, the device needs to be already registered with the system. For doing that, you can use the HTTP REST endpoint provided by
the Device Registration service and running the following command (i.e. for a device with ID `4711`) :

~~~sh
$ curl -X POST -i -d 'device_id=4711' http://<IP_ADDRESS>:<PORT>/registration/DEFAULT_TENANT
~~~

where `<IP_ADDRESS>` and `<PORT>` are the IP address and the related port of the HTTP REST endpoint as described before.
After having the device registered, uploading telemetry is just a simple HTTP PUT command.

~~~sh
$ curl -X PUT -i -H 'Content-Type: application/json' --data-binary '{"temp": 5}' \
> http://<IP_ADDRESS>:<PORT>/telemetry/DEFAULT_TENANT/4711
~~~

where in this case `<IP_ADDRESS>` and `<PORT>` are the IP address and the related port of the HTTP REST protocol adapter.
Other than using the HTTP REST protocol adapter, it's possible to upload telemetry data using the MQTT protocol adapter.

~~~sh
mosquitto_pub -h <IP_ADDRESS> -p <PORT> -i 4711 -t telemetry/DEFAULT_TENANT/4711 -m '{"temp": 5}'
~~~

where `<IP_ADDRESS>` and `<PORT>` are the IP address and the related port of the MQTT service as described before.
