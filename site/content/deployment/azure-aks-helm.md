+++
title = "Eclipse Hono deployment on Microsoft Azure with Helm"
weight = 490
+++

This guide describes the installation of Eclipse Hono on Microsoft Azure. It is not meant for productive use but rather for evaluation as well as demonstration or development purposes or as a baseline to evolve a production grade [Application architecture](https://docs.microsoft.com/en-us/azure/architecture/guide/) out of it which includes Eclipse Hono.
<!--more-->

## Prepare environment

At this step we expect you have clean Ubuntu VM or PC (tested on Ubuntu 18.04.2 LTS (GNU/Linux 4.18.0-1013-azure x86_64))

#### Install azure cli
```bash
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
```bash
sudo apt-get update && sudo apt-get install -y apt-transport-https

curl -s https://packages.cloud.google.com/apt/doc/apt-key.gpg | sudo apt-key add -
echo "deb https://apt.kubernetes.io/ kubernetes-xenial main" | sudo tee -a /etc/apt/sources.list.d/kubernetes.list
sudo apt-get update -y
sudo apt-get install -y kubectl
```
#### Install Helm
```bash
mkdir ~/helmdownload
cd ~/helmdownload
curl https://raw.githubusercontent.com/helm/helm/master/scripts/get > get_helm.sh
chmod 700 get_helm.sh
./get_helm.sh
cd ~/
```
#### Install Java
```bash
sudo add-apt-repository ppa:openjdk-r/ppa && sudo apt-get update -q && sudo apt install -y openjdk-11-jdk
#Make sure you have Java 11 or later installed
java -version
```
#### Install Maven
```bash
sudo apt-get install -y maven

mvn -version
```
#### Install Docker
```bash
sudo apt-get install -y docker.io

sudo systemctl start docker
sudo systemctl enable docker

docker --version
```
#### Clone latest version of HONO
```bash
mkdir ~/repos
cd ~/repos
git clone https://github.com/eclipse/hono.git
```

## Build containers and push to private registry
This block can be skipped if you are planning to deploy hono from prebuild containers at [eclipse docker hub](https://hub.docker.com/u/eclipse/).

Using private container registry allows you to deploy a very latest version of Eclipse Hono, but it required some additional steps:

#### Build HONO
```bash
cd ~/repos/hono
sudo mvn clean install -Pbuild-docker-image,metrics-prometheus
```
Make sure, that everything has been built without errors.

Before we continue, let's setup some variables, that will be used to deploy resources on Azure:
```bash
AKS_RESOURCE_GROUP={YOUR-RG-NAME} # Name of your Resource Group, where all services will be deployed
AKS_CLUSTER_NAME={YOUR-AKS-NAME} # Name of your AKS cluster
ACR_NAME={YOUR-ACR-NAME} # Name of your private container registry
```


#### Login to azure
Type:
```bash
az login
```
and follow instructions on screen...

A result of successful login will be a JSON output with subscription details. Run the following command, using subscription id from JSON:
```bash
az account set -s "{YOUR SUBSCRIPTION ID}"
```
#### Create resource group for HONO deployment
```bash
az group create --name $AKS_RESOURCE_GROUP --location westeurope
```
#### Create your own private container registry on Azure
```bash
#-- name should match ^[a-zA-Z0-9]*$
az acr create --resource-group $AKS_RESOURCE_GROUP --name $ACR_NAME  --sku Basic
```
#### Login to container registry
```bash
sudo az acr login --name $ACR_NAME
```
#### Pushing containers to private repo.
Now let's check a docker images labels, that has been build earlier:
```bash
sudo docker images
```
Now, you should see a list of containers with 2 tags:
- "latest"
- Specific version of containers (ex.: "1.0-M3-SNAPSHOT")

Copy a second one and put as 1st parameter for following script:
```bash
sudo ./push_hono_images.sh 1.0-M3-SNAPSHOT $ACR_NAME.azurecr.io
```
This script will tag and push container to your private repo.

## Create AKS cluster and deploy HONO
---
If you have skipped a previous block, you can use one of the deployment package from [here](https://www.eclipse.org/hono/download/). Make sure you have all required files (They can be found in [Eclipse Hono GitHub](https://github.com/eclipse/hono)):
 - dispatch-router-extlb-svc.yaml in ~/hono/deploy/helm/templates/dispatch-router
 - hono-adapter-amqp-vertx-extlb-svc.yaml in ~/hono/deploy/helm/templates/hono-adapter-amqp
 - hono-adapter-http-vertx-extlb-svc.yaml in ~/hono/deploy/helm/templates/hono-adapter-http
 - hono-adapter-kura-extlb-svc.yaml in ~/hono/deploy/helm/templates/hono-adapter-kura
 - hono-adapter-mqtt-vertx-extlb-svc.yaml in ~/hono/deploy/helm/templates/hono-adapter-mqtt
 - hono-service-device-registry-extlb-svc.yaml in ~/hono/deploy/helm/templates/hono-service-device-registry
---

Now it's time to create Azure Kubernetes Services cluster.

#### Create service principal
```bash
az ad sp create-for-rbac --skip-assignment
```
If success, appId and password will be displayed.
#### Create Azure Kubernetes Service
```bash
#It is recommended to use kubernetes 1.12.5 or higher
#To list avalible versions run:
#az aks get-versions --location westeurope --output table
az aks create --name $AKS_CLUSTER_NAME --resource-group $AKS_RESOURCE_GROUP --node-count 3 --generate-ssh-keys --service-principal "{appId}" --client-secret "{password}" --enable-addons monitoring --kubernetes-version 1.13.5
```
#### Merge cluster as current context for kubectl
```bash
az aks get-credentials --resource-group $AKS_RESOURCE_GROUP --name $AKS_CLUSTER_NAME
```
#### Create ClusterRoleBinding for Kubernetes dashboard
```bash
#By default, AKS deployed with RBAC enabled. Refer to this doc: https://docs.microsoft.com/en-us/azure/aks/kubernetes-dashboard
kubectl create clusterrolebinding kubernetes-dashboard --clusterrole=cluster-admin --serviceaccount=kube-system:kubernetes-dashboard
```
#### Create Serviceaccount, ClusterRoleBinding and deploy tiller
```bash
kubectl create serviceaccount --namespace kube-system tiller
kubectl create clusterrolebinding tiller-cluster-rule --clusterrole=cluster-admin --serviceaccount=kube-system:tiller

helm init

kubectl patch deploy --namespace kube-system tiller-deploy -p '{"spec":{"template":{"spec":{"serviceAccount":"tiller"}}}}'
```
#### Grant Azure Kubernetes Service access to Container Registry
```bash
# Get the id of the service principal configured for AKS
CLIENT_ID=$(az aks show --resource-group $AKS_RESOURCE_GROUP --name $AKS_CLUSTER_NAME --query "servicePrincipalProfile.clientId" --output tsv)

# Get the ACR registry resource id
ACR_ID=$(az acr show --name $ACR_NAME --resource-group $AKS_RESOURCE_GROUP --query "id" --output tsv)

# Create role assignment
az role assignment create --assignee $CLIENT_ID --role acrpull --scope $ACR_ID
```
#### Deploy HONO
Navigate to "kubernetes" directory:
```bash
cd ~/repos/hono/deploy/target/deploy
# if you are using a deployment package, navigate here: ~/hono/deploy/kubernetes
```
#### Run install helm chart
```bash
sudo helm install --dep-up --name eclipse-hono --namespace hono --set useLoadBalancer=true,hono.image.repoPrefix="$ACR_NAME.azurecr.io/eclipse" ./helm/
```
It can take some time to provision pods and services. You can monitor progress with:
```bash
kubectl get services -n hono
kubectl get pods -n hono
```
#### Service ports configuration
|Service|Type|Ports|
|---|---|---|
|hono-adapter-amqp-vertx-extlb|LoadBalancer|5672,5671|
|hono-adapter-http-vertx-extlb|LoadBalancer|8080,8443|
|hono-adapter-kura-extlb|LoadBalancer|11883,18883|
|hono-adapter-mqtt-vertx-extlb|LoadBalancer|1883,8883|
|hono-dispatch-router-extlb|LoadBalancer|15671,15672|
|hono-service-device-registry-extlb|LoadBalancer|28080,28443|
|eclipse-hono-grafana|ClusterIP|80|
|eclipse-hono-kube-state-metrics|ClusterIP|8080|
|eclipse-hono-prometheus-node-exporter|ClusterIP|9100|
|eclipse-hono-prometheus-op-operator|ClusterIP|8080|
|grafana|NodePort|3000|
|hono-adapter-amqp-vertx|NodePort|8088|
|hono-adapter-http-vertx|NodePort|8088|
|hono-adapter-kura|NodePort|8088|
|hono-adapter-mqtt-vertx|NodePort|8088|
|hono-artemis|ClusterIP|5671|
|hono-dispatch-router|ClusterIP|5673|
|hono-service-auth|ClusterIP|5671,8088|
|hono-service-device-registry|NodePort|5671,8088|
|prometheus-operated|ClusterIP|9090|

## Testing

After successful deployment you will have external IP addresses for adapters, device registry and dispatch router. To start testing, install httpie:
```bash
sudo apt-get install httpie
```
Let's have a look at services:
```bash
kubectl get services -n hono
```
As you can see, there is a services, ended with "ext", that has external IP addresses and type "LoadBalancer". Those services are exposed to internet and can be accessed by their external IP addresses.

Now, let's start a consumer.

Open a new terminal (login to your development VM one more time) and navigate to "example" directory:
```bash
cd ~/repos/hono/example
```
Add variable with external IP address of "hono-dispatch-router-ext" service:
```bash
DISPATCH_ROUTER_IP={YOUR IP} # External IP of hono-dispatch-router-ext service
```
Then run:
```bash
mvn exec:java -Dexec.mainClass=org.eclipse.hono.vertx.example.HonoExampleApplication -Dconsumer.host=$DISPATCH_ROUTER_IP
```
If build failed because of not resolved dependencies, try to change a version of project with latest from [here](https://mvnrepository.com/artifact/org.eclipse.hono/hono-demo-certs):
```bash
#change version
cd ~/repos/hono
mvn versions:set -DgroupId=org.eclipse.hono -DartifactId=* -DoldVersion=* -DnewVersion={NEW VERSION}

#re-run an example
cd ~/repos/hono/example
mvn exec:java -Dexec.mainClass=org.eclipse.hono.vertx.example.HonoExampleApplication -Dconsumer.host=$DISPATCH_ROUTER_IP
```

If you see a message "Consumer ready for telemetry and event messages", you are OK and can go forward...

Now, let's switch to another terminal and add some variables:
```bash
HTTP_ADAPTER_IP={YOUR IP} # External IP of hono-adapter-amqp-vertx-ext
DEVICE_REGISTRY_IP={YOUR IP} # External IP of hono-service-device-registry-ext
```
First, a new device should be registered:
```bash
http POST http://$DEVICE_REGISTRY_IP:28080/registration/DEFAULT_TENANT device-id=4711
```
a result should be:
```bash
HTTP/1.1 201 Created
content-length: 0
location: /registration/DEFAULT_TENANT/4711
```
Send a test message:
```bash
http --auth sensor1@DEFAULT_TENANT:hono-secret POST http://$HTTP_ADAPTER_IP:8080/telemetry temp:=5
```
a result should be:
```bash
HTTP/1.1 202 Accepted
content-length: 0
```
On a "consumer" terminal you should see a new message:
```bash
received telemetry [tenant: DEFAULT_TENANT, device: 4711, content-type: application/json ]: [{"temp": 5}]
```
## Monitoring
You can monitor your cluster using Azure Monitor Insights.

Navigate to [https://portal.azure.com](https://portal.azure.com) -> your resource group -> your kubernetes cluster

On an overview tab you fill find an information about your cluster (status, location, version, etc.). Also, here you will find a "Monitor Containers" link. Navigate to "Monitor Containers" and explore metrics and statuses of your Cluster, Nodes, Controllers and Containers.

Other option is to use kubernetes dashboard. To do this, run:
```bash
az aks browse --resource-group $AKS_RESOURCE_GROUP --name $AKS_CLUSTER_NAME
```

This will forward kubernetes dashboard on your local port 8001. Open browser, navigate to [http://127.0.0.1:8001](http://127.0.0.1:8001), choose namespace "hono", check status of your services, pods, etc.

## Clean up
Revert version of hono, if it was changed:
```bash
#revert to original version after testing
cd ~/repos/hono
mvn versions:revert
```
Delete kubernetes cluster if it no longer needed:
```bash
az group delete --name $AKS_RESOURCE_GROUP --yes --no-wait
```
## Links
- [Azure CLI](https://docs.microsoft.com/en-us/cli/azure/?view=azure-cli-latest)
- [Azure Kubernetes Service](https://docs.microsoft.com/en-us/azure/aks/)
- [Azure Container Registry](https://docs.microsoft.com/en-us/azure/container-registry/)
- [Eclipse Hono](https://www.eclipse.org/hono/)
- [Docker docs](https://docs.docker.com/)
