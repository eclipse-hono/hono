#!/bin/sh

NS=hono

echo DEPLOYING ECLIPSE HONO TO KUBERNETES

# creating Hono namespace
kubectl create namespace $NS

# creating the directory for Hono Server persistent volume
if [ ! -d /tmp/hono ]; then
    mkdir /tmp/hono
    chmod 777 /tmp/hono
else
    echo /tmp/hono already exists !
fi

# creating Hono persistent volume (admin needed)
kubectl create -f ../target/fabric8/hono-pv.yml --namespace $NS

echo Deploying Authentication Server ...
kubectl create -f ../../services/auth/target/fabric8/hono-auth-svc.yml --namespace $NS
kubectl create -f ../../services/auth/target/fabric8/hono-auth-dc.yml --namespace $NS
echo ... done

echo Deploying Device Registry ...
kubectl create -f ../../services/device-registry/target/fabric8/hono-device-registry-pvc.yml --namespace $NS
kubectl create -f ../../services/device-registry/target/fabric8/hono-device-registry-svc.yml --namespace $NS
kubectl create -f ../../services/device-registry/target/fabric8/hono-device-registry-dc.yml --namespace $NS
echo ... done

echo Deploying Artemis broker ...
kubectl create -f ../../broker/target/fabric8/artemis-svc.yml --namespace $NS
kubectl create -f ../../broker/target/fabric8/artemis-dc.yml --namespace $NS

echo Deploying Qpid Dispatch Router ...
kubectl create -f ../../dispatchrouter/target/fabric8/dispatch-router-svc.yml --namespace $NS
kubectl create -f ../../dispatchrouter/target/fabric8/dispatch-router-external-svc.yml --namespace $NS
kubectl create -f ../../dispatchrouter/target/fabric8/dispatch-router-dc.yml --namespace $NS
echo ... done

echo Deploying Hono Messaging ...
kubectl create -f ../../services/messaging/target/fabric8/hono-messaging-svc.yml --namespace $NS
kubectl create -f ../../services/messaging/target/fabric8/hono-messaging-dc.yml --namespace $NS
echo ... done

echo Deploying HTTP REST adapter ...
kubectl create -f ../../adapters/rest-vertx/target/fabric8/hono-adapter-rest-vertx-svc.yml --namespace $NS
kubectl create -f ../../adapters/rest-vertx/target/fabric8/hono-adapter-rest-vertx-dc.yml --namespace $NS
echo ... done

echo Deploying MQTT adapter ...
kubectl create -f ../../adapters/mqtt-vertx/target/fabric8/hono-adapter-mqtt-vertx-svc.yml --namespace $NS
kubectl create -f ../../adapters/mqtt-vertx/target/fabric8/hono-adapter-mqtt-vertx-dc.yml --namespace $NS
echo ... done

echo ECLIPSE HONO DEPLOYED TO KUBERNETES
