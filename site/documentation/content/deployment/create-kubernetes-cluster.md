+++
title = "Setting up a Kubernetes Cluster"
weight = 480
+++

This guide describes how to set up a Kubernetes cluster which can be used to run Eclipse Hono.
<!--more-->

Hono can be deployed to any Kubernetes cluster running version 1.11 or newer. This includes [OpenShift (Origin)](https://www.okd.io/) which is built on top of Kubernetes.

The [Kubernetes web site](https://kubernetes.io/docs/setup/) provides instructions for setting up a (local) cluster on bare metal and/or virtual infrastructure from scratch or for provisioning a managed Kubernetes cluster from one of the popular cloud providers.

<a name="Local Development"></a>
## Setting up a local Development Environment

The easiest option is to set up a single-node cluster running on a local VM using the [Minikube](https://github.com/kubernetes/minikube) project.
This kind of setup is sufficient for evaluation and development purposes.
Please refer to Kubernetes' [Minikube setup guide](https://kubernetes.io/docs/setup/minikube/) for instructions on how to set up a cluster locally.

The recommended settings for a Minikube VM used for running Hono's example setup are as follows:

* **cpus**: 2
* **memory**: 8192 (MB)

The command to start the VM will look something like this:
```sh
minikube start --cpus 2 --memory 8192 -p hono
```

After the Minikube VM has started successfully, the `minikube tunnel` command should be run in order to support Hono's services being deployed using the *LoadBalancer* type. Please refer to the [Minikube Networking docs](https://github.com/kubernetes/minikube/blob/master/docs/networking.md#access-to-loadbalancer-services-using-minikube-tunnel) for details.

{{% note title="Supported Kubernetes Versions" %}}
Minikube will use the most recent Kubernetes version that was available when it has been compiled by default. Hono *should* run on any version of Kubernetes starting with 1.13.6. However, it has been tested with several specific versions only so if you experience any issues with running Hono on a more recent version, please try to deploy to 1.13.6 before
raising an issue. You can use Minikube's `--kubernetes-version` command line switch to set a particular version.
{{% /note %}}

## Setting up a Production Environment

Setting up a multi-node Kubernetes cluster is a more advanced topic. Please follow the corresponding links provided in the [Kubernetes documentation](https://kubernetes.io/docs/setup/#production-environment).

## Setting up a Cluster using Azure Kubernetes Service

The following sections provide step-by-step instructions for setting up a Kubernetes cluster on Microsoft Azure that can be used to run Hono.

### Prepare Environment

At this step we expect you have clean Ubuntu VM or PC (tested on Ubuntu 18.04.2 LTS (GNU/Linux 4.18.0-1013-azure x86_64))

#### Install azure cli

```sh
sudo apt-get update
sudo apt-get install curl apt-transport-https lsb-release gpg

curl -sL https://packages.microsoft.com/keys/microsoft.asc | \
    gpg --dearmor | \
    sudo tee /etc/apt/trusted.gpg.d/microsoft.asc.gpg > /dev/null

AZ_REPO=$(lsb_release -cs)
echo "deb [arch=amd64] https://packages.microsoft.com/repos/azure-cli/ $AZ_REPO main" | \
    sudo tee /etc/apt/sources.list.d/azure-cli.list

sudo apt-get update
sudo apt-get install azure-cli
```

#### Install Kubectl

The kubectl tool is used to manage a Kubernetes cluster from the command line.
Follow the [installation guide](https://kubernetes.io/docs/tasks/tools/install-kubectl/) in order to set up `kubetcl` on your local machine.

#### Log in to Azure

Type

```sh
az login
```
and follow instructions on screen.

A result of successful log in will be a JSON output with subscription details. Run the following command, using the subscription *id* from the JSON structure:

```sh
az account set -s "{YOUR SUBSCRIPTION ID}"
```

Before we continue, let's setup some variables, that will be used to create resources on Azure:

```sh
# Name of your Resource Group, where all services will be deployed
AKS_RESOURCE_GROUP={YOUR-RG-NAME}
# Name of your AKS cluster
AKS_CLUSTER_NAME={YOUR-AKS-NAME}
# Name of your private container registry (should match ^[a-zA-Z0-9]*$)
ACR_NAME={YOUR-ACR-NAME}
```

#### Create Resource Group for Hono Deployment

```sh
az group create --name $AKS_RESOURCE_GROUP --location westeurope
```

#### Create private Container Registry on Azure

```sh
az acr create --resource-group $AKS_RESOURCE_GROUP --name $ACR_NAME  --sku Basic
```

The full name of the private container registry will be `$ACR_NAME.azurecr.io`. This name needs to be used with the `docker push` command when pushing container images to the registry.

#### Log in to Container Registry

```sh
sudo az acr login --name $ACR_NAME
```

### Create Azure Kubernetes Service Cluster

Now it's time to create the AKS cluster.

#### Create Service Principal

```sh
az ad sp create-for-rbac --skip-assignment
```
If successfull, *appId* and *password* will be displayed.

#### Create Cluster

```sh
# It is recommended to use kubernetes 1.12.5 or higher
# To list available versions run:
# az aks get-versions --location westeurope --output table

az aks create --name $AKS_CLUSTER_NAME --resource-group $AKS_RESOURCE_GROUP --node-count 3 --generate-ssh-keys --service-principal "{appId}" --client-secret "{password}" --enable-addons monitoring --kubernetes-version 1.13.5
```

#### Set Cluster as current Context for kubectl

```sh
az aks get-credentials --resource-group $AKS_RESOURCE_GROUP --name $AKS_CLUSTER_NAME
```

#### Create ClusterRoleBinding for Kubernetes dashboard

```sh
# By default, AKS is deployed with RBAC enabled. Refer to this doc: https://docs.microsoft.com/en-us/azure/aks/kubernetes-dashboard

kubectl create clusterrolebinding kubernetes-dashboard --clusterrole=cluster-admin --serviceaccount=kube-system:kubernetes-dashboard
```

#### Deploy Helm's Tiller Service

```sh
kubectl create serviceaccount --namespace kube-system tiller
kubectl create clusterrolebinding tiller-cluster-rule --clusterrole=cluster-admin --serviceaccount=kube-system:tiller

helm init --service-account tiller
```

#### Grant AKS Cluster access to Container Registry

```sh
# Get the id of the service principal configured for AKS
CLIENT_ID=$(az aks show --resource-group $AKS_RESOURCE_GROUP --name $AKS_CLUSTER_NAME --query "servicePrincipalProfile.clientId" --output tsv)

# Get the ACR registry resource id
ACR_ID=$(az acr show --name $ACR_NAME --resource-group $AKS_RESOURCE_GROUP --query "id" --output tsv)

# Create role assignment
az role assignment create --assignee $CLIENT_ID --role acrpull --scope $ACR_ID
```

### Monitoring

You can monitor your cluster using Azure Monitor Insights.

Navigate to [https://portal.azure.com](https://portal.azure.com) -> your resource group -> your kubernetes cluster

On an overview tab you fill find an information about your cluster (status, location, version, etc.). Also, here you will find a "Monitor Containers" link. Navigate to "Monitor Containers" and explore metrics and statuses of your Cluster, Nodes, Controllers and Containers.

Another option is to use the kubernetes dashboard. To do this, run:

```sh
az aks browse --resource-group $AKS_RESOURCE_GROUP --name $AKS_CLUSTER_NAME
```

This will create a port forward for Kubernetes' dashboard on your local port 8001.
Open [http://127.0.0.1:8001](http://127.0.0.1:8001) in your browser and select *Namespace* `hono`, check status of your services, pods, etc.

### Cleaning up

Use the following command to delete all created resources (incl. the ubernetes cluster and the private container registry)
once they are no longer needed:

```sh
az group delete --name $AKS_RESOURCE_GROUP --yes --no-wait
```

### Links

- [Azure CLI](https://docs.microsoft.com/en-us/cli/azure/?view=azure-cli-latest)
- [Azure Kubernetes Service](https://docs.microsoft.com/en-us/azure/aks/)
- [Azure Container Registry](https://docs.microsoft.com/en-us/azure/container-registry/)
