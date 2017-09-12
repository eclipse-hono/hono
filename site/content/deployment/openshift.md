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

## Script based Deployment

In order to deploy Eclipse Hono on OpenShift, a bunch of steps are needed as explained in the next chapter. If you want to avoid to do them manually, a _one click_ deployment
script is available in the repository.
After having the OpenShift cluster up and running and the client tools in the PATH, the deployment can be done by running the following bash script
(from the `example/target/deploy/openshift` directory)

~~~sh
~hono/example/target/deploy/openshift$ chmod +x *.sh
~hono/example/target/deploy/openshift$ ./openshift_deploy.sh
~~~

In order to see the deployed components, you can launch OpenShift's web UI in a browser by issuing:

~~~sh
$ minishift dashboard
~~~

You can login with username `developer` and password `developer`.
Be sure to switch to the `Hono` project in the UI in order to see the components deployed as part of Hono.

The screen shots below show Hono's components deployed to OpenShift.

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


### Undeploying Hono

There also is a script for shutting down and undeploying Hono:

~~~sh
~hono/example/target/deploy/openshift$ ./openshift_undeploy.sh
~~~

## Deploying individual Components

You may also deploy each of Hono's components separately by copying the relevant commands from the deploy script to your own script or entering them directly on the command line.

## Access to Hono services

The OpenShift deployment provides access to Eclipse Hono by means of *services* and the main ones are:

* **hono-dispatch-router-ext**: router network for the business application in order to consume data
* **hono-adapter-mqtt-vertx**: protocol adapter for publishing telemetry data and events using the MQTT protocol
* **hono-adapter-rest-vertx**: protocol adapter for publishing telemetry data and events using the HTTP protocol
* **hono-service-device-registry**: component for registering and managing devices

You can check these services through the `oc get services` command having the following output :

~~~sh
NAME                           CLUSTER-IP       EXTERNAL-IP   PORT(S)                                      AGE
grafana                        172.30.104.165   <nodes>       3000:31000/TCP                               2m
hono-adapter-mqtt-vertx        172.30.3.63      <nodes>       1883:31883/TCP,8883:30883/TCP                2m
hono-adapter-rest-vertx        172.30.205.239   <nodes>       8080:30080/TCP,8443:30443/TCP                2m
hono-artemis                   172.30.21.155    <none>        5672/TCP                                     2m
hono-dispatch-router           172.30.140.127   <none>        5673/TCP                                     2m
hono-dispatch-router-ext       172.30.12.86     <nodes>       5671:30671/TCP,5672:30672/TCP                2m
hono-service-auth              172.30.155.8     <none>        5671/TCP                                     2m
hono-service-device-registry   172.30.177.150   <none>        5671:31671/TCP,8080:31080/TCP,8443:31443/TCP 2m
hono-service-messaging         172.30.114.146   <none>        5671/TCP                                     2m
influxdb                       172.30.203.114   <none>        2003/TCP,8083/TCP,8086/TCP                   2m
~~~

Using the *OpenShift Origin client tools* way, these services are accessible using the related `CLUSTER-IP` and the *internal* ports (i.e. 8080, 5671, ...).

Using the *Minishift* way, you have to use the Minishift VM's IP address (that you can get with the `minishift ip` command) and the so called *node ports* (i.e. 30080, 30671, ...).
In the following sections `<IP_ADDRESS>` needs to be replaced with the IP address of the Minishift VM.

### Starting a Consumer

As described in the [Getting Started]({{< relref "getting-started.md" >}}) guide, data produced by devices is usually consumed by downstream applications which connect directly to the router network service.
You can start the client from the `example` folder as follows:

~~~sh
~/hono/example$ mvn spring-boot:run -Drun.arguments=--hono.client.host=<IP_ADDRESS>,--hono.client.port=30671,--hono.client.username=consumer@HONO,--hono.client.password=verysecret
~~~

### Uploading Telemetry

In order to upload telemetry data to Hono, the device needs to be registered with the system. You can register the device using the
*Device Registry* by running the following command (i.e. for a device with ID `4711`):

~~~sh
$ curl -X POST -i -d 'device-id=4711' http://<IP_ADDRESS>:31080/registration/DEFAULT_TENANT
~~~

After having the device registered, uploading telemetry is just a simple HTTP PUT command to the *REST Adapter*:

~~~sh
$ curl -X PUT -i -H 'Content-Type: application/json' --data-binary '{"temp": 5}' \
> http://<IP_ADDRESS>:30080/telemetry/DEFAULT_TENANT/4711
~~~

Other than using the *REST Adapter*, it's possible to upload telemetry data using the *MQTT Adapter* as well:

~~~sh
mosquitto_pub -h <IP_ADDRESS> -p 31883 -i 4711 -t telemetry/DEFAULT_TENANT/4711 -m '{"temp": 5}'
~~~
