#!/bin/bash

# Absolute path this script is in
SCRIPTPATH="$(cd "$(dirname "$0")" && pwd -P)"
RESOURCES=$SCRIPTPATH/../resource-descriptors
NS=hono

echo DEPLOYING ECLIPSE HONO TO AZURE AKS
set +e
source $SCRIPTPATH/olm_deploy.sh
source $SCRIPTPATH/kubernetes_deploy.sh
set -e

echo CREATE AZURE EXTERNAL SERVICES
kubectl delete service hono-dispatch-router-ext -n hono || true

kubectl create -f $RESOURCES/azure --namespace $NS

echo DONE!
