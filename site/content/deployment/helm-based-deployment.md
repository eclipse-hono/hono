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

### Kubernetes Cluster

The most basic requirement is, of course, a Kubernetes cluster to deploy to.
Please refer to the [Kubernetes setup guide]({{< ref "/deployment/create-kubernetes-cluster.md" >}}) for options regarding the setup of a Kubernetes cluster.

### Helm

Helm is a tool for managing (complex) Kubernetes applications. In this guide it is used to deploy Hono to the cluster.
Please refer to [Helm's installation instructions](https://docs.helm.sh/install/) for details.

### Kubectl

The kubectl tool is used to manage a Kubernetes cluster from the command line. In this guide it is used to retrieve information about Hono's service endpoints from the cluster.
Follow the [installation guide](https://kubernetes.io/docs/tasks/tools/install-kubectl/) in order to set up `kubetcl` on your local machine.

### Hono Helm Chart

The Helm chart is contained in the Hono archive that is available from the [download page]({{< ref "/download.md" >}}).
After the archive has been extracted, the chart can be found in the `eclipse-hono-$VERSION/deploy/helm` folder.

### Hono Command Line Client

The Hono command line client is available from the [download page]({{< ref "/download.md" >}}).
Note that running the command line client requires a Java 11 runtime environment being installed locally.

## Deploying Hono

The recommended way of deploying Hono is by means of using Helm's *Tiller* service running on the Kubernetes cluster:

~~~sh
# in directory: eclipse-hono-$VERSION/deploy/
helm install --dep-up --name eclipse-hono --namespace hono helm/
~~~

This will create namespace `hono` in the cluster and install all the components to that namespace. The name of the Helm release will be `eclipse-hono`.

You can check the status of the deployment using one of the following commands:

~~~sh
helm list
helm status eclipse-hono
helm get eclipse-hono
~~~

### Deploying Hono using kubectl

In cases where installation of Helm's Tiller service into the cluster is not an option, the Kubernetes resource descriptors created by Helm can be deployed manually using the `kubectl` command line tool.

Run the following commands to generate the resource descriptors using Helm:

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

## Accessing Hono's API

Once deployment has completed, Hono's external API endpoints are exposed via corresponding Kubernetes *Services*.
The following command lists all services and their endpoints:

~~~sh
kubectl get service -n hono

NAME                                    TYPE           CLUSTER-IP       EXTERNAL-IP     PORT(S)
eclipse-hono-grafana                    ClusterIP      10.98.116.21     <none>          80/TCP
eclipse-hono-kube-state-metrics         ClusterIP      10.111.143.45    <none>          8080/TCP
eclipse-hono-prometheus-node-exporter   ClusterIP      10.99.123.105    <none>          9100/TCP
eclipse-hono-prometheus-op-operator     ClusterIP      10.107.136.47    <none>          8080/TCP
grafana                                 NodePort       10.100.132.96    <none>          3000:31000/TCP
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
The service endpoints are accessible on the *EXTERNAL-IP* addresses and the corresponding *PORT(S)*, e.g. 8080 for the HTTP adapter and 28080 for the device registry.

### Starting a Consumer

Data published by devices is usually consumed by downstream applications which connect directly to the AMQP messaging network (represented by the `hono-dispatch-router-ext` service.
The following command starts a consumer from the command line:

~~~sh
# in directory: eclipse-hono-$VERSION/deploy/
eval $(services.sh)

# in the directory where the Hono command line client has been downloaded to:
java -jar hono-cli-*-exec.jar --hono.client.host=$AMQP_NETWORK_IP --hono.client.port=15672 --hono.client.username=consumer@HONO --hono.client.password=verysecret --spring.profiles.active=receiver
~~~

This will start a new consumer that logs all telemetry and event messages to the console.

### Uploading Telemetry

In another console, run the following command in order to put the IP addresses of Hono's service endpoints to
some environment variables:

~~~sh
# in directory: eclipse-hono-$VERSION/deploy/
eval $(services.sh)
~~~

In order to upload telemetry data to Hono, a device needs to be registered with the system first.
The following command registers a device with ID `4711` in the example device registry that is deployed with Hono:

~~~sh
curl -X POST -i -H 'Content-Type: application/json' --data-binary '{"device-id": "4711"}' http://$REGISTRY_IP:28080/registration/DEFAULT_TENANT
~~~

After having the device registered, uploading telemetry is just a simple HTTP POST command to the *HTTP Adapter*:

~~~sh
curl -i -u sensor1@DEFAULT_TENANT:hono-secret -H 'Content-Type: application/json' --data-binary '{"temp": 5}' http://$HTTP_ADAPTER_IP:8080/telemetry
~~~

Other than using the *HTTP Adapter*, it's possible to upload telemetry data using the *MQTT Adapter* as well:

~~~sh
mosquitto_pub -h $MQTT_ADAPTER_IP -u 'sensor1@DEFAULT_TENANT' -P hono-secret -t telemetry -m '{"temp": 5}'
~~~

The username and password used above for device `4711` are part of the example configuration that comes with Hono.
See [Device Identity]({{< ref "/concepts/device-identity.md" >}}) for an explanation of how devices are identified in Hono and how device identity is related to authentication.

## Undeploying Hono

To undeploy a Hono instance that has been deployed using Helm's Tiller service, run:

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

To undeploy a Hono instance that has been deployed manually from the resource files, run:

~~~sh
# in directory: eclipse-hono-$VERSION/deploy/
kubectl delete -f ./eclipse-hono -R
~~~

## Deploying from Source

The sections above describe how a released version of Hono can be deployed using Helm. In some cases it might be desirable to build Hono from source, e.g. in order to use a different metrics back end or to [enable Jaeger tracing]({{< relref "#deploying-jaeger" >}}). In these cases, the Helm templates contained in the source tree can be used instead of the Helm chart from the download page.

The container images created as part of the build process need to be made available to the Kubernetes cluster that Hono should be deployed to. This usually requires the images to be pushed to a (private) container registry that the cluster has pull access to. Please refer to the documentation of the employed Kubernetes service provider for details regarding the setup and configuration of a private container registry.

### Deploying via a private Registry

The build process can be started using the following command:

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

When using Minikube as the deployment target, things are a little easier. Minikube comes with an embedded Docker daemon which can be used to build the container images instead of using a local daemon, thus eliminating the need to push the images to a registry altogether. In order to use Minikube's Docker daemon, run the following command:

~~~sh
eval $(minikube docker-env)
~~~

This will set the Docker environment variables to point to Minikube's Docker daemon which will then be used for building the container images and storing them locally in the Minikube VM.

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

