+++
title = "Helm based Deployment"
weight = 471
+++

Eclipse Hono&trade;'s components are provided as container images which can be run on arbitrary container orchestration platforms.
This page describes the steps necessary to deploy Hono to a [Kubernetes](https://kubernetes.io) cluster using the [Helm package manager](https://helm.sh).
<!--more-->

{{% note %}}
The example configuration that comes with Hono and which is used in this guide is supposed to be used for evaluation and development purposes only.
{{% /note %}}

## Prerequisites

#### Kubernetes Cluster

The most basic requirement is, of course, a Kubernetes cluster to deploy to.
The [Kubernetes setup guide]({{< relref "create-kubernetes-cluster.md" >}}) describes options available for setting up a cluster.

#### Helm

Helm is a tool for managing (complex) Kubernetes applications. In this guide it is used to deploy Hono to the cluster.
[Helm's installation instructions](https://docs.helm.sh/install/) provide more details.

#### Kubectl

The kubectl tool is used to manage a Kubernetes cluster from the command line. In this guide it is used to retrieve information about Hono's service endpoints from the cluster.
The [installation guide](https://kubernetes.io/docs/tasks/tools/install-kubectl/) provides instructions for setting up `kubectl` locally.

#### Hono Helm Chart

The Helm chart is contained in the Hono archive that is available from [Hono's download page]({{% homelink "/downloads/" %}}).
After the archive has been extracted, the chart can be found in the `eclipse-hono-$VERSION/deploy/helm` folder.

#### Hono Command Line Client

The Hono command line client is available from the [download page]({{% homelink "/downloads/" %}}).
The command line client requires a Java 11 runtime environment to run.

## Deploying Hono

The recommended way of deploying Hono is by means of using Helm's *Tiller* service running on the Kubernetes cluster:

~~~sh
# in directory: eclipse-hono-$VERSION/deploy/
helm install --dep-up --name hono --namespace hono helm/
~~~

This will create namespace `hono` in the cluster and install all the components to that namespace. The name of the Helm release will be `hono`.

The status of the deployment can be checked using any of the following commands:

~~~sh
helm list
helm status hono
helm get hono
~~~

### Deploying Hono using kubectl

In cases where installation of Helm's Tiller service into the cluster is not an option, the Kubernetes resource descriptors created by Helm can be deployed manually using the `kubectl` command line tool.

The following commands generate the resource descriptors:

~~~sh
# in directory: eclipse-hono-$VERSION/deploy/
helm dep update helm/
helm template --name hono --namespace hono --output-dir . helm/
~~~

This will create an `eclipse-hono` folder containing all the resource descriptors which can then be deployed to the cluster using `kubectl`:

~~~sh
# in directory: eclipse-hono-$VERSION/deploy/
kubectl create namespace hono
kubectl config set-context $(kubectl config current-context) --namespace=hono
find . -path "./eclipse-hono/*" -name crd*.yaml -exec kubectl apply -f {} \;
kubectl apply -f ./eclipse-hono -R
~~~

## Verifying the Installation

Once deployment has completed, Hono's external API endpoints are exposed via corresponding Kubernetes *Services*.
The following command lists all services and their endpoints (replace `hono` with the namespace that you have deployed to):

~~~sh
kubectl get service -n hono

NAME                                    TYPE           CLUSTER-IP       EXTERNAL-IP      PORT(S)
hono-adapter-amqp-vertx                 LoadBalancer   10.109.123.153   10.109.123.153   5672:32672/TCP,5671:32671/TCP
hono-adapter-amqp-vertx-headless        ClusterIP      None             <none>           <none>
hono-adapter-http-vertx                 LoadBalancer   10.99.180.137    10.99.180.137    8080:30080/TCP,8443:30443/TCP
hono-adapter-http-vertx-headless        ClusterIP      None             <none>           <none>
hono-adapter-mqtt-vertx                 LoadBalancer   10.102.204.69    10.102.204.69    1883:31883/TCP,8883:30883/TCP
hono-adapter-mqtt-vertx-headless        ClusterIP      None             <none>           <none>
hono-artemis                            ClusterIP      10.97.31.154     <none>           5671/TCP
hono-dispatch-router                    ClusterIP      10.98.111.236    <none>           5673/TCP
hono-dispatch-router-ext                LoadBalancer   10.109.220.100   10.109.220.100   15671:30671/TCP,15672:30672/TCP
hono-grafana                            ClusterIP      10.110.61.181    <none>           3000/TCP
hono-prometheus-server                  ClusterIP      10.96.70.135     <none>           9090/TCP
hono-service-auth                       ClusterIP      10.109.97.44     <none>           5671/TCP
hono-service-auth-headless              ClusterIP      None             <none>           <none>
hono-service-device-registry            ClusterIP      10.105.190.233   <none>           5671/TCP
hono-service-device-registry-ext        LoadBalancer   10.101.42.99     10.101.42.99     28080:31080/TCP,28443:31443/TCP
hono-service-device-registry-headless   ClusterIP      None             <none>           <none>
~~~

The listing above has been retrieved from a Minikube cluster that emulates a load balancer via the `minikube tunnel` command (refer to the [Minikube Networking docs](https://github.com/kubernetes/minikube/blob/master/docs/networking.md#loadbalancer-emulation-minikube-tunnel) for details).
The service endpoints can be accessed at the *EXTERNAL-IP* addresses and corresponding *PORT(S)*, e.g. 8080 for the HTTP adapter (*hono-adapter-http-vertx*) and 28080 for the device registry (*hono-service-device-registry*).

The following command assigns the IP address of the device registry service to the `REGISTRY_IP` environment variable so that they can easily be used from the command line:

~~~sh
export REGISTRY_IP=$(kubectl get service hono-service-device-registry-ext --output='jsonpath={.status.loadBalancer.ingress[0].ip}' -n hono)
~~~

The following command can then be used to check for the existence of the *DEFAULT_TENANT* which is created as part of the installation:

~~~sh
curl -sIX GET http://$REGISTRY_IP:28080/v1/tenants/DEFAULT_TENANT

HTTP/1.1 200 OK
etag: 89d40d26-5956-4cc6-b978-b15fda5d1823
content-type: application/json; charset=utf-8
content-length: 260
~~~

<a name="dashboard"></a>

## Accessing the Grafana Dashboard

Hono comes with an example Grafana dashboard which provides some insight into the messages flowing through the protocol adapters.
The following command needs to be run first in order to forward the Grafana service's endpoint to the local host:

~~~sh
kubectl port-forward service/hono-grafana 3000 -n hono
~~~

Then the dashboard can be opened by pointing a browser to http://localhost:3000 using credentials `admin:admin`.

## Undeploying Hono

A Hono instance that has been deployed using Helm's Tiller service can be undeployed by running

~~~sh
helm delete --purge hono
~~~

A Hono instance that has been deployed manually using the resource files can be undeployed by running

~~~sh
# in directory: eclipse-hono-$VERSION/deploy/
kubectl delete -f ./eclipse-hono -R
~~~

## Deploying custom Container Images

The sections above describe how Hono's pre-built container images can be deployed using Helm. In some cases it might be desirable to build Hono from source, e.g. in order to use a different metrics back end or to [use Jaeger tracing](#using-jaeger-tracing). In these cases, the Helm templates contained in the source tree can be used instead of the Helm chart from the download page.

The container images created as part of the build process need to be made available to the Kubernetes cluster that Hono should be deployed to. This usually requires the images to be pushed to a (private) container registry that the cluster has pull access to. Please refer to the documentation of the employed Kubernetes service provider for details regarding the setup and configuration of a private container registry.

### Deploying via a private Registry

The first step is getting the source code of Hono. Please refer to [Building from Source]({{< relref "building_hono.md" >}}) for details.
Once the source code has been retrieved, the build process can be started using the following command:

~~~sh
# in base directory of Hono working tree:
mvn clean install -Pbuild-docker-image,metrics-prometheus
~~~

After the build process has finished, the custom container images need to be pushed to the registry so that the Kubernetes cluster can pull them from there during deployment.
Assuming that the images should be tagged with `1.0-CUSTOM` and the container registry name is `my.registry.io`, the following command can be used to tag the locally built images and push them to the registry:

~~~sh
# in base directory of Hono working tree:
./push_hono_images.sh 1.0-CUSTOM my.registry.io
~~~

{{% note %}}
You may need to log in to the (private) container registry before pushing the images.
{{% /note %}}

Once the images have been pushed, the deployment can be done using Helm:

~~~sh
# in Hono working tree directory: hono/deploy
helm install --dep-up --name hono --namespace hono --set honoContainerRegistry=my.registry.io target/deploy/helm/
~~~

### Deploying to Minikube

When using Minikube as the deployment target, things are a little easier. Minikube comes with an embedded Docker daemon which can be used to build the container images instead of using a local Docker daemon, thus eliminating the need to push the images to a registry altogether.
In order to use Minikube's Docker daemon, the following command needs to be run:

~~~sh
eval $(minikube docker-env)
~~~

This will set the Docker environment variables to point to Minikube's Docker daemon which can then be used for building the container images and storing them locally in the Minikube VM.

In any case the build process can be started using the following command:

~~~sh
# in base directory of Hono working tree:
mvn clean install -Pbuild-docker-image,metrics-prometheus
~~~
The newly built images can then be deployed using Helm:

~~~sh
# in Hono working tree directory: hono/deploy
helm install --dep-up --name hono --namespace hono target/deploy/helm/
~~~

## Using Jaeger Tracing

Hono's components are instrumented using OpenTracing to allow tracking of the distributed processing of messages flowing through the system.
The Hono chart can be configured to report tracing information to the [Jaeger tracing system](https://jaegertracing.io). The *Spans* reported
by the components can then be viewed in a web browser.

In order for Hono's components to use the Jaeger client for reporting tracing information, the container images need to be built
with the `jaeger` Maven profile. Please refer to [Monitoring & Tracing]
({{< relref "/admin-guide/monitoring-tracing-config.md#configuring-usage-of-jaeger-tracing-included-in-docker-images" >}}) for details.

The chart can be configured to deploy and use an example Jaeger back end by means of setting the *jaegerBackendDeployExample* property
to `true` when running Helm:

~~~sh
# in Hono working tree directory: hono/deploy
helm install --dep-up --name hono --namespace hono --set jaegerBackendDeployExample=true target/deploy/helm/
~~~

This will create a Jaeger back end instance suitable for testing purposes and will configure all deployed Hono components to use the
Jaeger back end.

The following command can then be used to return the IP address with which the Jaeger UI can be accessed in a browser (ensure `minikube tunnel` is running when using minikube):
~~~sh
kubectl get service hono-jaeger-query --output='jsonpath={.status.loadBalancer.ingress[0].ip}' -n hono
~~~

If no example Jaeger back end should be deployed but instead an existing Jaeger installation should be used,
the chart's *jaegerAgentConf* property can be set to environment variables which are passed in to
the Jaeger Agent that is deployed with each of Hono's components.

~~~sh
# in Hono working tree directory: hono/deploy
helm install --dep-up --name hono --namespace hono --set jaegerAgentConf.REPORTER_TYPE=tchannel --set jaegerAgentConf.REPORTER_TCHANNEL_HOST_PORT=my-jaeger-collector:14267 target/deploy/helm/
~~~

## Deploying optional Adapters

The Helm chart supports deployment of additional protocol adapters which are still considered experimental or have been deprecated.
The following table provides an overview of the corresponding configuration properties that need to be set on deployment.

| Property                     | Default  | Description                              |
| :--------------------------- | :------- | :--------------------------------------- |
| *adapters.kura.enabled*      | `false` | Indicates if the deprecated Kura protocol adapter should be deployed. |

The following command will deploy the Kura adapter along with Hono's standard adapters:

~~~sh
# in directory: eclipse-hono-$VERSION/deploy/
helm install --dep-up --name hono --namespace hono --set adapters.kura.enabled=true helm/
~~~
