+++
title = "Setting up a Kubernetes Cluster"
weight = 480
+++

This guide describes how to set up a Kubernetes cluster which can be used to run Eclipse Hono&trade;.

<!--more-->

Hono can be deployed to any Kubernetes cluster running version 1.11 or newer. This includes [OpenShift (Origin)](https://www.okd.io/) which is built on top of Kubernetes.

The [Kubernetes web site](https://kubernetes.io/docs/setup/) provides instructions for setting up a (local) cluster on bare metal and/or virtual infrastructure from scratch or for provisioning a managed Kubernetes cluster from one of the popular cloud providers.

<a name="Local Development"></a>

## Setting up a local Development Environment

The easiest option is to set up a single-node cluster running on a local VM using the [Minikube](https://minikube.sigs.k8s.io/) project.
This kind of setup is sufficient for evaluation and development purposes.
Please refer to Minikube's [getting started guide](https://minikube.sigs.k8s.io/docs/start/) for instructions on how to set up a cluster locally.

The recommended settings for a Minikube VM used for running Hono's example setup are as follows:

- **cpus**: 2
- **memory**: 8192 (MB)

The command to start the VM will look something like this:

```sh
minikube start --cpus 2 --memory 8192
```

After the Minikube VM has started successfully, the `minikube tunnel` command should be run in order to support Hono's services being deployed using the *LoadBalancer* type. Please refer to the [Minikube Loadbalancer docs](https://minikube.sigs.k8s.io/docs/tasks/loadbalancer/) for details.

{{% note title="Setting Kubernetes Version" %}}
Minikube will use the most recent Kubernetes version that was available when it has been compiled by default.
Hono *should* run on any version of Kubernetes starting with 1.13.6. However, it has been tested with several
specific versions only. The most recent version known to work is 1.15.4 so if you experience any issues with
running Hono on another version, please try to deploy to 1.15.4 before raising an issue.
You can use Minikube's `--kubernetes-version` command line switch to set a particular version.
{{% /note %}}

## Setting up a Production Environment

Setting up a multi-node Kubernetes cluster is a more advanced topic. Please follow the corresponding links provided in the [Kubernetes documentation](https://kubernetes.io/docs/setup/#production-environment).

## Setting up an Environment on Microsoft Azure

This chapter describes how Hono can be deployed on Microsoft Azure. It includes:

- [Azure Resource Manager (ARM)](https://docs.microsoft.com/en-us/azure/azure-resource-manager/resource-group-overview) templates for an automated infrastructure deployment.
- Hono deployment into [Azure Kubernetes Service (AKS)](https://docs.microsoft.com/en-us/azure/aks/intro-kubernetes).
- Push Hono docker images to an [Azure Container Registry (ACR)](https://azure.microsoft.com/en-us/services/container-registry/).
- Optional [Azure Service Bus](https://docs.microsoft.com/en-us/azure/service-bus-messaging/service-bus-messaging-overview) as broker for the [Hono AMQP 1.0 Messaging Network]({{< relref "/architecture/component-view#amqp-1-0-messaging-network" >}}) instead of a self hosted ActiveMQ Artemis.
- [Virtual Network (VNet) service endpoints](https://docs.microsoft.com/en-us/azure/virtual-network/virtual-network-service-endpoints-overview) ensure protected communication between AKS and Azure Service Bus.

<!--more-->

{{% warning title="Use for demos only" %}}
This deployment model is not meant for productive use but rather for evaluation as well as demonstration purposes or as a baseline to evolve a production grade [Application architecture](https://docs.microsoft.com/en-us/azure/architecture/guide/) out of it which includes Hono.
{{% /warning %}}

### Prerequisites

- An [Azure subscription](https://azure.microsoft.com/en-us/get-started/).
- [Azure CLI](https://docs.microsoft.com/en-us/cli/azure/install-azure-cli) installed to setup the infrastructure.
- [kubectl](https://kubernetes.io/docs/tasks/tools/install-kubectl/) and [helm](https://helm.sh/docs/using_helm/#installing-helm) installed to deploy Hono into [Azure Kubernetes Service (AKS)](https://docs.microsoft.com/en-us/azure/aks/intro-kubernetes).

### Setup

As described [here](https://docs.microsoft.com/en-gb/azure/aks/kubernetes-service-principal) we will create an explicit service principal. Later we add roles to this principal to access the [Azure Container Registry (ACR)](https://docs.microsoft.com/en-us/azure/container-registry/container-registry-intro).

```bash
# Resource group where the ACR is deployed.
acr_resourcegroupname={YOUR_ACR_RG}
# Name of your ACR.
acr_registry_name={YOUR_ACR_NAME}
# Full name of the ACR.
acr_login_server=$acr_registry_name.azurecr.io

# Create service principal
service_principal=`az ad sp create-for-rbac --name http://honoServicePrincipal --skip-assignment --output tsv`
app_id_principal=`echo $service_principal|cut -f1`
password_principal=`echo $service_principal|cut -f4`
object_id_principal=`az ad sp show --id $app_id_principal --query objectId --output tsv`
acr_id_access_registry=`az acr show --resource-group $acr_resourcegroupname --name $acr_registry_name --query "id" --output tsv`
```

Note: it might take a few seconds until the principal is available for the next steps.

```bash
az role assignment create --assignee $app_id_principal --scope $acr_id_access_registry --role Reader

resourcegroup_name=hono
az group create --name $resourcegroup_name --location "westeurope"
```

With the next command we will use the provided [Azure Resource Manager (ARM)](https://docs.microsoft.com/en-us/azure/azure-resource-manager/resource-group-overview) templates to setup the AKS cluster. This might take a while.

```bash
unique_solution_prefix=myprefix
cd deploy/src/main/deploy/azure/
az group deployment create --name HonoBasicInfrastructure --resource-group $resourcegroup_name --template-file arm/honoInfrastructureDeployment.json --parameters uniqueSolutionPrefix=$unique_solution_prefix servicePrincipalObjectId=$object_id_principal servicePrincipalClientId=$app_id_principal servicePrincipalClientSecret=$password_principal
```

Notes:

- add the following parameter in case you want to opt for the Azure Service Bus as broker in the [Hono AMQP 1.0 Messaging Network]({{< relref "/architecture/component-view#amqp-1-0-messaging-network" >}}) instead of deploying a (self-hosted) ActiveMQ Artemis into AKS: _serviceBus=true_
- add the following parameter to define the k8s version of the AKS cluster. The default as defined in the template [might not be supported](https://docs.microsoft.com/en-us/azure/aks/supported-kubernetes-versions) in your target Azure region, e.g. _kubernetesVersion=1.14.6_

After the deployment is complete you can set your cluster in _kubectl_.

```bash
az aks get-credentials --resource-group $resourcegroup_name --name $aks_cluster_name
```

Next deploy helm on to the AKS cluster as well create retain storage for the device registry. It will take a moment until tiller is booted up.

```bash
kubectl apply -f helm-rbac.yaml
helm init --service-account tiller
kubectl apply -f managed-premium-retain.yaml
```

Now wait until the Azure deployment is complete and then continue with the [Helm deployment]({{< relref "helm-based-deployment.md" >}}) of Hono itself.

### Monitoring

You can monitor your cluster using [Azure Monitor for containers](https://docs.microsoft.com/en-us/azure/azure-monitor/insights/container-insights-overview).

Navigate to [https://portal.azure.com](https://portal.azure.com) -> your resource group -> your kubernetes cluster

On an overview tab you fill find an information about your cluster (status, location, version, etc.). Also, here you will find a "Monitor Containers" link. Navigate to "Monitor Containers" and explore metrics and statuses of your Cluster, Nodes, Controllers and Containers.

### Cleaning up

Use the following command to delete all created resources once they are no longer needed:

```sh
az group delete --name $resourcegroup_name --yes --no-wait
```
