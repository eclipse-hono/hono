#!/bin/sh

NS=hono

echo UNDEPLOYING ECLIPSE HONO FROM KUBERNETES

kubectl delete deploy,service,pvc -l app=hono --namespace $NS

# deleting Hono Server persistent volume
kubectl delete pv hono-pv

# deleting Hono namespace
kubectl delete namespace $NS

# deleting the directory for hosting persistent volume
rm -rf /tmp/hono

echo ECLIPSE HONO UNDEPLOYED FROM KUBERNETES
