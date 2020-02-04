+++
title = "Helm based Deployment"
weight = 471
+++

Eclipse Hono&trade;'s components are provided as container images which can be run on arbitrary container orchestration platforms.
This page describes the steps necessary to deploy Hono to a [Kubernetes](https://kubernetes.io) cluster using the
[Helm package manager](https://helm.sh).
<!--more-->

## Installing Hono

Hono's Helm chart is available from the [Eclipse IoT Packages chart repository](https://www.eclipse.org/packages/repository/).
Please refer to the [chart's README](https://github.com/eclipse/packages/blob/master/charts/hono/README.md) for
instructions regarding installation and configuration.

## Deploying custom Container Images

The chart by default installs Hono's pre-built container images. In some cases it might be desirable to build Hono
from source, e.g. in order to use a different metrics back end or to [use Jaeger tracing]({{< relref "#using-jaeger-tracing" >}}).

The container images created as part of the build process need to be made available to the Kubernetes cluster that
Hono should be installed to. This usually requires the images to be pushed to a (private) container registry that
the cluster can pull them from. Please refer to the documentation of the employed Kubernetes service provider for
details regarding the setup and configuration of a private container registry.

### Deploying via a private Registry

The first step is getting the source code of Hono. Please refer to [Building from Source]({{< relref "building_hono.md" >}}) for details.
Once the source code has been retrieved, the build process can be started using the following command:

~~~sh
# in base directory of Hono working tree:
mvn clean install -Pbuild-docker-image,metrics-prometheus
~~~

After the build process has finished, the custom container images need to be pushed to the registry so that the
Kubernetes cluster can pull them from there during deployment.
Assuming that the images should be tagged with `1.0.3-CUSTOM` and the container registry name is `my.registry.io`,
the following command can be used to tag the locally built images and push them to the registry:

~~~sh
# in base directory of Hono working tree:
./push_hono_images.sh 1.0.3-CUSTOM my.registry.io
~~~

{{% note %}}
You may need to log in to the (private) container registry before pushing the images.
{{% /note %}}

The image names that Hono should use for starting up containers can be configured in a YAML file:

```yaml
deviceRegistryExample:
  imageName: "my.registry.io/eclipse/hono-service-device-registry:1.0.3-CUSTOM"
authServer:
  imageName: "my.registry.io/eclipse/hono-service-auth:1.0.3-CUSTOM"
deviceConnectionService:
  imageName: "my.registry.io/eclipse/hono-service-device-connection:1.0.3-CUSTOM"
adapters:
  amqp:
    imageName: "my.registry.io/eclipse/hono-adapter-amqp-vertx:1.0.3-CUSTOM"
  mqtt:
    imageName: "my.registry.io/eclipse/hono-adapter-mqtt-vertx:1.0.3-CUSTOM"
  http:
    imageName: "my.registry.io/eclipse/hono-adapter-http-vertx:1.0.3-CUSTOM"
```

Assuming that the YAML file is called `imageNames.yaml`, installation can then be done using:

~~~sh
helm install --dependency-update -n hono -f imageNames.yaml eclipse-hono eclipse-iot/hono
~~~

### Deploying to Minikube

When using Minikube as the deployment target, things are a little easier. Minikube comes with an
embedded Docker daemon which can be used to build the container images instead of using a local
Docker daemon, thus eliminating the need to push the images to a registry altogether.
In order to use Minikube's Docker daemon, the following command needs to be run:

~~~sh
eval $(minikube docker-env)
~~~

This will set the Docker environment variables to point to Minikube's Docker daemon which can then be
used for building the container images and storing them locally in the Minikube VM.

In any case the build process can be started using the following command:

~~~sh
# in base directory of Hono working tree:
mvn clean install -Pbuild-docker-image,metrics-prometheus
~~~
The newly built images can then be deployed using Helm:

~~~sh
helm install --dependency-update -n hono eclipse-hono eclipse-iot/hono
~~~


### Using Jaeger Tracing

Hono's components are instrumented using OpenTracing to allow tracking of the distributed processing of messages flowing through the system.
The Hono chart can be configured to report tracing information to the [Jaeger tracing system](https://www.jaegertracing.io/). The *Spans* reported
by the components can then be viewed in a web browser.

In order for Hono's components to use the Jaeger client for reporting tracing information, the container images need to be built
with the `jaeger` Maven profile. Please refer to [Monitoring & Tracing]
({{< relref "/admin-guide/monitoring-tracing-config.md#configuring-usage-of-jaeger-tracing-included-in-docker-images" >}}) for details.
The newly built images also need to be made available to the target Kubernetes cluster as described in the two previous sections.

The chart can be configured to deploy and use an example Jaeger back end by means of setting the *jaegerBackendDeployExample* property
to `true` when running Helm:

~~~sh
helm install --dependency-update -n hono --set jaegerBackendExample.enabled=true eclipse-hono eclipse-iot/hono
~~~

This will create a Jaeger back end instance suitable for testing purposes and will configure all deployed Hono components to use the
Jaeger back end.

The following command can then be used to return the IP address with which the Jaeger UI can be accessed in a
browser (ensure `minikube tunnel` is running when using minikube):

~~~sh
kubectl get service hono-jaeger-query --output='jsonpath={.status.loadBalancer.ingress[0].ip}' -n hono
~~~

If no example Jaeger back end should be deployed but instead an existing Jaeger installation should be used,
the chart's *jaegerAgentConf* property can be set to environment variables which are passed in to
the Jaeger Agent that is deployed with each of Hono's components.

~~~sh
helm install --dependency-update -n hono --set jaegerAgentConf.REPORTER_TYPE=tchannel --set jaegerAgentConf.REPORTER_TCHANNEL_HOST_PORT=my-jaeger-collector:14267 eclipse-hono eclipse-iot/hono
~~~

### Deploying to Azure Kubernetes Service (AKS)

The following chapter describes how to use Azure Kubernetes Service (AKS) as a deployment target that has
been set up as described in the [Setting up a Kubernetes Cluster guide]({{< relref "create-kubernetes-cluster.md" >}}).

First we build the docker images and push them into the ACR. Note that if you define a custom image tag
you have to provide the helm with the image tags as described in the chapters above.

```bash
# Resource group where the ACR is deployed.
acr_resourcegroupname={YOUR_ACR_RG}
# Name of your ACR.
acr_registry_name={YOUR_ACR_NAME}
# Full name of the ACR.
acr_login_server=$acr_registry_name.azurecr.io
# Authenticate your docker daemon with the ACR.
az acr login --name $ACR_NAME
# Build images.
cd hono
mvn install -Pbuild-docker-image -Ddocker.registry=$acr_login_server
# Push images to ACR.
./push_hono_images.sh 1.0.0-SNAPSHOT $acr_login_server
```

Now we can retrieve settings from the deployment for the following steps:

```bash
# Resource group of the AKS deployment
resourcegroup_name=hono

aks_cluster_name=`az group deployment show --name HonoBasicInfrastructure --resource-group $resourcegroup_name --query properties.outputs.aksClusterName.value -o tsv`
http_ip_address=`az group deployment show --name HonoBasicInfrastructure --resource-group $resourcegroup_name --query properties.outputs.httpPublicIPAddress.value -o tsv`
amqp_ip_address=`az group deployment show --name HonoBasicInfrastructure --resource-group $resourcegroup_name --query properties.outputs.amqpPublicIPAddress.value -o tsv`
mqtt_ip_address=`az group deployment show --name HonoBasicInfrastructure --resource-group $resourcegroup_name --query properties.outputs.mqttPublicIPAddress.value -o tsv`
registry_ip_address=`az group deployment show --name HonoBasicInfrastructure --resource-group $resourcegroup_name --query properties.outputs.registryPublicIPAddress.value -o tsv`
network_ip_address=`az group deployment show --name HonoBasicInfrastructure --resource-group $resourcegroup_name --query properties.outputs.networkPublicIPAddress.value -o tsv`
```

Note: add the following lines in case you opted for the Azure Service Bus variant:

```bash
service_bus_namespace=`az group deployment show --name HonoBasicInfrastructure --resource-group $resourcegroup_name --query properties.outputs.serviceBusNamespaceName.value -o tsv`
service_bus_key_name=`az group deployment show --name HonoBasicInfrastructure --resource-group $resourcegroup_name --query properties.outputs.serviceBusKeyName.value -o tsv`
service_bus_key=`az group deployment show --name HonoBasicInfrastructure --resource-group $resourcegroup_name --query properties.outputs.serviceBusKey.value -o tsv`
```

Next we prepare the k8s environment:

```bash
k8s_namespace=honons
kubectl create namespace $k8s_namespace
```

Finally install Hono. Leveraging the _managed-premium-retain_ storage in combination with _deviceRegistry.resetFiles=false_ parameter is optional but ensures that Device registry storage will retain future update deployments.

```bash
# in Hono working tree directory: hono/deploy
helm install target/deploy/helm/eclipse-hono/ \
    --dep-up \
    --name hono \
    --namespace $k8s_namespace \
    --set adapters.mqtt.svc.annotations."service\.beta\.kubernetes\.io/azure-load-balancer-resource-group"=$resourcegroup_name \
    --set adapters.http.svc.annotations."service\.beta\.kubernetes\.io/azure-load-balancer-resource-group"=$resourcegroup_name \
    --set adapters.amqp.svc.annotations."service\.beta\.kubernetes\.io/azure-load-balancer-resource-group"=$resourcegroup_name \
    --set deviceRegistryExample.svc.annotations."service\.beta\.kubernetes\.io/azure-load-balancer-resource-group"=$resourcegroup_name \
    --set amqpMessagingNetworkExample.dispatchRouter.svc.annotations."service\.beta\.kubernetes\.io/azure-load-balancer-resource-group"=$resourcegroup_name \
    --set deviceRegistryExample.storageClass=managed-premium-retain \
    --set deviceRegistryExample.resetFiles=false \
    --set adapters.mqtt.svc.loadBalancerIP=$mqtt_ip_address \
    --set adapters.http.svc.loadBalancerIP=$http_ip_address \
    --set adapters.amqp.svc.loadBalancerIP=$amqp_ip_address \
    --set deviceRegistryExample.svc.loadBalancerIP=$registry_ip_address \
    --set amqpMessagingNetworkExample.dispatchRouter.svc.loadBalancerIP=$network_ip_address
```

Note: add the following lines in case you opted for the Azure Service Bus variant:

```bash
    # Router update required to work together with Azure Service Bus
    --set amqpMessagingNetworkExample.dispatchRouter.imageName=quay.io/enmasse/qdrouterd-base:1.8.0 \
    --set amqpMessagingNetworkExample.broker.type=servicebus \
    --set amqpMessagingNetworkExample.broker.servicebus.saslUsername=$service_bus_key_name \
    --set amqpMessagingNetworkExample.broker.servicebus.saslPassword=$service_bus_key \
    --set amqpMessagingNetworkExample.broker.servicebus.host=$service_bus_namespace.servicebus.windows.net \
```

Have fun with Hono on Microsoft Azure!

Next steps:

You can follow the steps as described in the [Getting Started]({{% homelink "getting-started/" %}}) guide with the following differences:

Compared to a plain k8s deployment Azure provides us DNS names with static IPs for the Hono endpoints. To retrieve them:

```bash
HTTP_ADAPTER_IP=`az group deployment show --name HonoBasicInfrastructure --resource-group $resourcegroup_name --query properties.outputs.httpPublicIPFQDN.value -o tsv`
AMQP_ADAPTER_IP=`az group deployment show --name HonoBasicInfrastructure --resource-group $resourcegroup_name --query properties.outputs.amqpPublicIPFQDN.value -o tsv`
MQTT_ADAPTER_IP=`az group deployment show --name HonoBasicInfrastructure --resource-group $resourcegroup_name --query properties.outputs.mqttPublicIPFQDN.value -o tsv`
REGISTRY_IP=`az group deployment show --name HonoBasicInfrastructure --resource-group $resourcegroup_name --query properties.outputs.registryPublicIPFQDN.value -o tsv`
AMQP_NETWORK_IP=`az group deployment show --name HonoBasicInfrastructure --resource-group $resourcegroup_name --query properties.outputs.networkPublicIPFQDN.value -o tsv`
```

As Azure Service Bus does not support auto creation of queues you have to create a queue per tenant (ID), e.g. after you have created your tenant run:

```bash
az servicebus queue create --resource-group $resourcegroup_name \
    --namespace-name $service_bus_namespace \
    --name $MY_TENANT
```
