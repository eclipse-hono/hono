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
helm install --dep-up --name eclipse-hono --namespace hono helm/
~~~

This will create namespace `hono` in the cluster and install all the components to that namespace. The name of the Helm release will be `eclipse-hono`.

The status of the deployment can be checked using any of the following commands:

~~~sh
helm list
helm status eclipse-hono
helm get eclipse-hono
~~~

### Deploying Hono using kubectl

In cases where installation of Helm's Tiller service into the cluster is not an option, the Kubernetes resource descriptors created by Helm can be deployed manually using the `kubectl` command line tool.

The following commands generate the resource descriptors:

~~~sh
# in directory: eclipse-hono-$VERSION/deploy/
helm dep update helm/
helm template --name eclipse-hono --namespace hono --output-dir . helm/
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
The following command lists all services and their endpoints:

~~~sh
kubectl get service -n hono

NAME                                    TYPE           CLUSTER-IP       EXTERNAL-IP     PORT(S)
eclipse-hono-grafana                    ClusterIP      10.98.116.21     <none>          3000/TCP
eclipse-hono-kube-state-metrics         ClusterIP      10.111.143.45    <none>          8080/TCP
eclipse-hono-prometheus-node-exporter   ClusterIP      10.99.123.105    <none>          9100/TCP
eclipse-hono-prometheus-op-operator     ClusterIP      10.107.136.47    <none>          8080/TCP
hono-adapter-amqp-vertx                 LoadBalancer   10.97.243.98     10.97.243.98    5672:32672/TCP,5671:32671/TCP
hono-adapter-http-vertx                 LoadBalancer   10.104.162.33    10.104.162.33   8080:30080/TCP,8443:30443/TCP
hono-adapter-kura                       LoadBalancer   10.110.31.68     10.110.31.68    1883:31884/TCP,8883:30884/TCP
hono-adapter-mqtt-vertx                 LoadBalancer   10.103.97.233    10.103.97.233   1883:31883/TCP,8883:30883/TCP
hono-artemis                            ClusterIP      10.105.129.3     <none>          5671/TCP
hono-dispatch-router                    ClusterIP      10.107.34.77     <none>          5673/TCP
hono-dispatch-router-ext                LoadBalancer   10.109.64.70     10.109.64.70    15671:30671/TCP,15672:30672/TCP
hono-service-auth                       ClusterIP      10.103.101.106   <none>          5671/TCP
hono-service-device-registry            ClusterIP      10.107.138.95    <none>          5671/TCP
hono-service-device-registry-ext        LoadBalancer   10.107.214.87    10.107.214.87   28080:31080/TCP,28443:31443/TCP
prometheus-operated                     ClusterIP      None             <none>          9090/TCP
~~~

The listing above has been retrieved from a Minikube cluster that emulates a load balancer via the `minikube tunnel` command (refer to the [Minikube Networking docs](https://github.com/kubernetes/minikube/blob/master/docs/networking.md#loadbalancer-emulation-minikube-tunnel) for details).
The service endpoints can be accessed at the *EXTERNAL-IP* addresses and corresponding *PORT(S)*, e.g. 8080 for the HTTP adapter (*hono-adapter-http-vertx*) and 28080 for the device registry (*hono-service-device-registry*).

The following command assigns the IP address of the device registry service to the `REGISTRY_IP` environment variable so that they can easily be used from the command line:

~~~sh
export REGISTRY_IP=$(kubectl get service hono-service-device-registry-ext --output='jsonpath={.status.loadBalancer.ingress[0].ip}' -n hono)
~~~

The following command can then be used to check for the existence of the *DEFAULT_TENANT* which is created as part of the installation:

~~~sh
curl -sIX GET http://$REGISTRY_IP:28080/tenant/DEFAULT_TENANT

HTTP/1.1 200 OK
content-type: application/json; charset=utf-8
content-length: 289
~~~

<a name="dashboard"></a>

## Accessing the Grafana Dashboard

Hono comes with an example Grafana dashboard which provides some insight into the messages flowing through the protocol adapters.
The following command needs to be run first in order to forward the Grafana service's endpoint to the local host:

~~~sh
kubectl port-forward service/eclipse-hono-grafana 3000 -n hono
~~~

Then the dashboard can be opened by pointing a browser to http://localhost:3000 using credentials `admin:admin`.

## Undeploying Hono

A Hono instance that has been deployed using Helm's Tiller service can be undeployed by running

~~~sh
helm delete --purge eclipse-hono
kubectl delete crd prometheuses.monitoring.coreos.com prometheusrules.monitoring.coreos.com servicemonitors.monitoring.coreos.com alertmanagers.monitoring.coreos.com
~~~

The additional `kubectl delete` command is necessary to remove [Prometheus operator CRDs](https://github.com/helm/charts/tree/master/stable/prometheus-operator#uninstalling-the-chart).

If the Jaeger tracing component has been deployed, additional resources have to be deleted manually:
~~~sh
kubectl delete crd jaegers.jaegertracing.io
kubectl delete svc -n hono eclipse-hono-jaeger-operator
~~~

A Hono instance that has been deployed manually using the resource files can be undeployed by running

~~~sh
# in directory: eclipse-hono-$VERSION/deploy/
kubectl delete -f ./eclipse-hono -R
~~~

## Deploying custom Container Images

The sections above describe how Hono's pre-built container images can be deployed using Helm. In some cases it might be desirable to build Hono from source, e.g. in order to use a different metrics back end or to [enable Jaeger tracing](#deploying-jaeger). In these cases, the Helm templates contained in the source tree can be used instead of the Helm chart from the download page.

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
helm install --dep-up --name eclipse-hono --namespace hono --set honoContainerRegistry=my.registry.io target/deploy/helm/
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
helm install --dep-up --name eclipse-hono --namespace hono target/deploy/helm/
~~~

## Deploying Jaeger

In order to deploy the Jaeger tracing component along with Hono, first make sure that the Hono container images have been built with the Jaeger client included.
This is done by activating the `jaeger` Maven profile, see [Monitoring & Tracing]({{< ref "/admin-guide/monitoring-tracing-config.md#configuring-usage-of-jaeger-tracing-included-in-docker-images" >}}).
The deployment can then be done using the `jaeger.enabled=true` option when running Helm:

~~~sh
# in Hono working tree directory: hono/deploy
helm install --dep-up --name eclipse-hono --set jaeger.enabled=true --namespace hono target/deploy/helm/
~~~

For more information on how to access the Jaeger UI, see the [Jaeger Operator documentation](https://github.com/jaegertracing/jaeger-operator#accessing-the-ui).

