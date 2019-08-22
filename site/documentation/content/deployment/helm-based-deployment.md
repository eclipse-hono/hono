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
After the archive has been extracted, the chart can be found in the `eclipse-hono-$VERSION/deploy/helm/eclipse-hono` folder.

#### Hono Command Line Client

The Hono command line client is available from the [download page]({{% homelink "/downloads/" %}}).
The command line client requires a Java 11 runtime environment to run.

## Deploying Hono

The recommended way of deploying Hono is by means of using Helm's *Tiller* service running on the Kubernetes cluster:

~~~sh
# in directory: eclipse-hono-$VERSION/deploy/helm
helm install --dep-up --name hono --namespace hono eclipse-hono/
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

Then the dashboard can be opened by pointing your browser to `http://localhost:3000` using credentials `admin:admin`.

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
helm install --dep-up --name hono --namespace hono --set deviceRegistry.imageName=my.registry.io/eclipse/hono-service-device-registry:1.0-CUSTOM,authServer.imageName=my.registry.io/eclipse/hono-service-auth:1.0-CUSTOM,deviceConnectionService.imageName=my.registry.io/eclipse/hono-service-device-connection:1.0-CUSTOM,adapters.amqp.imageName=my.registry.io/eclipse/hono-adapter-amqp-vertx:1.0-CUSTOM,adapters.mqtt.imageName=my.registry.io/eclipse/hono-adapter-mqtt-vertx:1.0-CUSTOM,adapters.http.imageName=my.registry.io/eclipse/hono-adapter-http-vertx:1.0-CUSTOM target/deploy/helm/eclipse-hono/
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
helm install --dep-up --name hono --namespace hono target/deploy/helm/eclipse-hono/
~~~

## Using a production grade Device Registry and AMQP Messaging Network

The Helm chart by default deploys the example Device Registry that comes with Hono. The example registry provides implementations
of the Tenant, Device Registration, Credentials and Device Connection APIs which can be used for example/demo purposes.

The chart also deploys an example AMQP Messaging Network consisting of a single Apache Qpid Dispatch Router and a single
Apache ActiveMQ Artemis broker.

The protocol adapters are configured to connect to the example messaging network and registry by default.

In a production environment, though, usage of the example registry and messaging network is strongly discouraged and more
sophisticated (custom) implementations of the service APIs should be used.

The Helm chart supports configuration of the protocol adapters to connect to other service implementations than the example registry
and messaging network as described in the following sections.

### Integrating with an existing AMQP Messaging Network

The Helm chart can be configured to use an existing AMQP Messaging Network implementation instead of the example implementation.
In order to do so, the protocol adapters need to be configured with information about the AMQP Messaging Network's endpoint address
and connection parameters.

The easiest way to set these properties is by means of putting them into a YAML file with content like this:

```
# do not deploy example AMQP Messaging Network
amqpMessagingNetworkDeployExample: false

adapters:

  # mount (existing) Kubernetes secret which contains
  # credentials for connecting to AMQP network
  extraSecretMounts:
  - amqpNetwork:
      secretName: "my-secret"
      mountPath: "/etc/custom"

  # provide connection params
  # assuming that "my-secret" contains an "amqp-credenials.properties" file
  amqpMessagingNetworkSpec:
    host: my-custom.amqp-network.org
    port: 5672
    credentialsPath: "/etc/custom/amqp-credentials.properties"
  commandAndControlSpec:
    host: my-custom.amqp-network.org
    port: 5672
    credentialsPath: "/etc/custom/amqp-credentials.properties"
```

Both the *amqpMessagingNetworkSpec* and the *commandAndControlSpec* need to contain Hono client configuration properties
as described in the [client admin guide]({{< relref "/admin-guide/hono-client-configuration" >}}).
Make sure to adapt/add properties as required by the AMQP Messaging Network.

Note that *my-secret* is expected to already exist in the namespace that Hono gets deployed to, i.e. the Helm chart will **not**
create this secret.

Assuming that the file is named `customAmqpNetwork.yaml`, the values can then be passed in to the `helm install` command
as follows:

```sh
# in directory: eclipse-hono-$VERSION/deploy/
helm install --dep-up --name hono --namespace hono -f customAmqpNetwork.yaml helm/
```

### Integrating with a custom Device Registry

The Helm chart can be configured to use existing implementations of the Tenant, Device Registration, Credentials and Device Connection APIs
instead of the example device registry.
In order to do so, the protocol adapters need to be configured with information about the service endpoints and connection parameters.

The easiest way to set these properties is by means of putting them into a YAML file with the following content:

```
# do not deploy example Device Registry
deviceRegistryDeployExample: false

adapters:

  # mount (existing) Kubernetes secret which contains
  # credentials for connecting to services
  extraSecretMounts:
  - customRegistry:
      secretName: "my-secret"
      mountPath: "/etc/custom"

  # provide connection params
  # assuming that "my-secret" contains credentials files
  tenantSpec:
    host: my-custom.registry.org
    port: 5672
    credentialsPath: "/etc/custom/tenant-service-credentials.properties"
  deviceRegistrationSpec:
    host: my-custom.registry.org
    port: 5672
    credentialsPath: "/etc/custom/registration-service-credentials.properties"
  credentialsSpec:
    host: my-custom.registry.org
    port: 5672
    credentialsPath: "/etc/custom/credentials-service-credentials.properties"
  deviceConnectionSpec:
    host: my-custom.registry.org
    port: 5672
    credentialsPath: "/etc/custom/device-connection-service-credentials.properties"
```

All of the *specs* need to contain Hono client configuration properties
as described in the [client admin guide]({{< relref "/admin-guide/hono-client-configuration" >}}).
Make sure to adapt/add properties as required by the custom service implementations.
The information cotnained in the *specs* will then be used by all protocol adapters that get deployed.
As a consequence, it is not possible to use credentials for the services which are specific to the
individual protocol adapters.

Note that *my-secret* is expected to already exist in the namespace that Hono gets deployed to, i.e. the Helm chart will **not**
create this secret.

Assuming that the file is named `customRegistry.yaml`, the values can then be passed in to the `helm install` command
as follows:

```sh
# in directory: eclipse-hono-$VERSION/deploy/
helm install --dep-up --name hono --namespace hono -f customRegistry.yaml helm/
```

## Using the Device Connection Service

Hono's example Device Registry component contains a simple in-memory implementation of the [Device Connection API]({{< relref "/api/device-connection" >}}).
This example implementation is used by default when the example registry is deployed.

Hono also contains a production ready, data grid based implementation of the Device Connection API which can be deployed and used instead of
the example implementation. The component can be deployed by means of setting the *deviceConnectionService.enabled* property to `true` when
running Helm:

~~~sh
# in directory: eclipse-hono-$VERSION/deploy/
helm install --dep-up --name hono --namespace hono --set deviceConnectionService.enabled=true helm/eclipse-hono/
~~~

This will deploy the Device Connection service and configure all protocol adapters to use it instead of the example Device Registry implementation.
However, the service requires a connection to a data grid in order to store the device connection data.
The Helm chart supports deployment of a simple data grid which can be used for experimenting by means of setting the
*dataGridDeployExample* property to `true` when running Helm:

~~~sh
# in directory: eclipse-hono-$VERSION/deploy/
helm install --dep-up --name hono --namespace hono --set deviceConnectionService.enabled=true --set dataGridDeployExample=true helm/eclipse-hono/
~~~

This will deploy the data grid and configure the Device Connection service to use it for storing the connection data.

The Device Connection service can also be configured to connect to an already existing data grid. Please refer to the
[admin guide]({{< relref "/admin-guide/device-connection-config.md" >}}) for details regarding the corresponding configuration properties.

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
helm install --dep-up --name hono --namespace hono --set jaegerBackendDeployExample=true target/deploy/helm/eclipse-hono/
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
helm install --dep-up --name hono --namespace hono --set jaegerAgentConf.REPORTER_TYPE=tchannel --set jaegerAgentConf.REPORTER_TCHANNEL_HOST_PORT=my-jaeger-collector:14267 target/deploy/helm/eclipse-hono/
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
helm install --dep-up --name hono --namespace hono --set adapters.kura.enabled=true helm/eclipse-hono/
~~~
