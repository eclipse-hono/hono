+++
title = "Kubernetes"
weight = 475
+++

All the Eclipse Hono&trade; components can be deployed on Kubernetes, thanks to the resources YAML files that are provided through the repository.
These files describe such components in terms of _deployments_ and _services_ in order to have the right pods running in the Kubernetes cluster so that they are able
to communicate with each other.
<!--more-->

## Prerequisites

The main prerequisite for this kind of deployment is to have an available Kubernetes cluster. For a local development, it's pretty simple having such cluster using Minikube 
which is a tool that helps you run Kubernetes locally by running a single-node Kubernetes cluster inside a VM. Follow [this guide](https://kubernetes.io/docs/getting-started-guides/minikube/)
for installing and having Minikube up and running.

The other prerequisite is to have the Kubectl command line tool for interacting with the Kubernetes cluster. Follow [this guide](https://kubernetes.io/docs/tasks/tools/install-kubectl/) 
for installing and configuring such a tool.

After launching Minikube and before building the Eclipse Hono images, it's necessary to execute the following command :

~~~sh
$ eval $(minikube docker-env)
~~~

In this way, the `DOCKER_HOST` environment variable is set to the Docker daemon running inside the Minikube VM. Launching the following command for building the Eclipse Hono images,
such daemon will be used and the final images will be available inside the Minikube VM, ready for the deployment.

~~~sh
~/hono$ mvn clean install -Pbuild-docker-image
~~~

## One _script_ deployment

In order to deploy Eclipse Hono on Kubernetes, a bunch of steps are needed as explained in the next chapter. If you want to avoid to do them, a _one click_ deployment
script is available in the repository.
After having the Kubernetes cluster up and running and the Kubectl command line tool in the PATH, the deployment can be executed launching the following bash script
(from the "example/target/deploy/kubernetes/" directory)

~~~sh
$ ./kubernetes_deploy.sh
~~~

When you want to shutdown the Eclipse Hono instance, there is the following useful script:

~~~sh
$ ./kubernetes_undeploy.sh
~~~

## Step by step deployment

### Creating a namespace

First, create a new namespace using the Kubectl command line tool in the following way :

~~~sh
$ kubectl create namespace hono
~~~

### Preparing persistent volume

In order to handle the Device Registry and preserve the related file when pods go down for any reason (i.e. manual scale down to zero instances, crash, ...),
a persistent volume is needed so that can be used, through a _claim_, by the Device Registry. In general, the persistent volume is deployed by the cluster
administrator but for development purposes, a local `/tmp/hono` directory can be used on your _local_ host but it needs to be created with read/write permissions in the following way :

~~~sh
$ mkdir /tmp/hono
$ chmod 777 /tmp/hono
~~~

After that, it's possible to provision the persistent volume with the following command.

~~~sh
kubectl create -f <path-to-repo>/example/target/classes/META-INF/fabric8/kubernetes/hono-pv.yml --namespace hono
~~~

### Deploying Eclipse Hono components

It is now possible to deploy all the other Kubernetes resources related to:

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
$ kubectl create -f <path-to-repo>/metrics/target/classes/META-INF/fabric8/kubernetes.yml --namespace hono
~~~

Then the Artemis Broker:

~~~sh
$ kubectl create -f <path-to-repo>/broker/target/classes/META-INF/fabric8/kubernetes.yml --namespace hono
~~~

Then the Qpid Dispatch Router:

~~~sh
$ kubectl create -f <path-to-repo>/dispatchrouter/target/classes/META-INF/fabric8/kubernetes.yml --namespace hono
~~~

Then the Auth Server:

~~~sh
$ kubectl create -f <path-to-repo>/services/auth/target/classes/META-INF/fabric8/kubernetes.yml --namespace hono
~~~

Then the Device Registry:

~~~sh
$ kubectl create -f <path-to-repo>/services/device-registry/target/classes/META-INF/fabric8/kubernetes.yml --namespace hono
~~~

Then the Hono Messaging component:

~~~sh
$ kubectl create -f <path-to-repo>/services/messaging/target/classes/META-INF/fabric8/kubernetes.yml --namespace hono
~~~

Finally, both the adapters (HTTP REST and MQTT).

~~~sh
$ kubectl create -f <path-to-repo>/adapters/rest-vertx/target/classes/META-INF/fabric8/kubernetes.yml --namespace hono
$ kubectl create -f <path-to-repo>/adapters/mqtt-vertx/target/classes/META-INF/fabric8/kubernetes.yml --namespace hono
~~~

In this way, all the components are accessible inside the cluster using the _service_ addresses from a client's point of view.

In order to see the deployed components, you can use the Kubernetes UI which is accessible launching the following command : 

~~~sh
$ minikube dashboard
~~~

In the following pictures an Eclipse Hono deployment on Kubernetes is running with all the provided components.

![Eclipse Hono on Kubernets](../kubernetes_hono.png)

## Access to Hono services

The Kubernetes deployment provides access to Eclipse Hono by meaning of "services" and the main ones are :

* hono-dispatch-router-ext : router network for the business application in order to receive data
* hono-adapter-mqtt-vertx : MQTT protocol adapter for sending data
* hono-adapter-rest-vertx : HTTP protocol adapter for sending data

You can check these services through the `kubectl get services --namespace hono` command having the following output :

~~~sh
NAME                           CLUSTER-IP   EXTERNAL-IP   PORT(S)                                      AGE
grafana                        10.0.0.115   <nodes>       3000:31000/TCP                               15m
hono-adapter-mqtt-vertx        10.0.0.155   <nodes>       1883:31883/TCP,8883:30883/TCP                2m
hono-adapter-rest-vertx        10.0.0.184   <nodes>       8080:30080/TCP,8443:30443/TCP                3m
hono-artemis                   10.0.0.11    <none>        5672/TCP                                     6m
hono-dispatch-router           10.0.0.175   <none>        5673/TCP                                     5m
hono-dispatch-router-ext       10.0.0.124   <nodes>       5671:30671/TCP,5672:30672/TCP                5m
hono-service-auth              10.0.0.116   <none>        5671/TCP                                     5m
hono-service-device-registry   10.0.0.248   <none>        5671:31671/TCP,8080:31080/TCP,8443:31443/TCP 4m
hono-service-messaging         10.0.0.223   <none>        5671/TCP                                     3m
influxdb                       10.0.0.217   <none>        2003/TCP,8083/TCP,8086/TCP                   15m
~~~

These services are accessible using the Minikube VM IP address (that you can get with the `minikube ip` command) and the so called *node ports* (i.e. 30080, 30671, ...).

### Starting a Consumer

As described in the "Getting Started" guide, data produced by devices is usually consumed by downstream applications which connect directly to the router network service.
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
