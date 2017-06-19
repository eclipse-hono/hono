#!/bin/sh

echo DEPLOYING ECLIPSE HONO ON OPENSHIFT

# creating new project
oc new-project hono --description="Open source IoT connectivity" --display-name="Eclipse Hono"

# creating the directory for Hono Server persistent volume
if [ ! -d /tmp/hono ]; then
    mkdir /tmp/hono
    chmod 777 /tmp/hono
else
    echo /tmp/hono already exists !
fi

# creating Hono Server persistent volume (admin needed)
oc login -u system:admin
oc create -f ../../application/target/fabric8/hono-app-pv.yml

# starting to deploy Eclipse Hono (developer user)
oc login -u developer
oc create -f ../../application/target/fabric8/hono-app-pvc.yml

echo Deploying Authentication Server ...
oc create -f ../../services/auth/target/fabric8/hono-auth-svc.yml
oc create -f ../../services/auth/target/fabric8/hono-auth-dc.yml
oc create -f ../../services/auth/target/fabric8/hono-auth-route.yml
echo ... done

echo Deploying Qpid Dispatch Router ...
oc create -f ../../dispatchrouter/target/fabric8/dispatch-router-svc.yml
oc create -f ../../dispatchrouter/target/fabric8/dispatch-router-dc.yml
oc create -f ../../dispatchrouter/target/fabric8/dispatch-router-route.yml
echo ... done

echo Deploying Hono Server ...
oc create -f ../../application/target/fabric8/hono-app-svc.yml
oc create -f ../../application/target/fabric8/hono-app-dc.yml
oc create -f ../../application/target/fabric8/hono-app-route.yml
echo ... done

echo Deploying HTTP REST adapter ...
oc create -f ../../adapters/rest-vertx/target/fabric8/hono-adapter-rest-vertx-svc.yml
oc create -f ../../adapters/rest-vertx/target/fabric8/hono-adapter-rest-vertx-dc.yml
oc create -f ../../adapters/rest-vertx/target/fabric8/hono-adapter-rest-vertx-route.yml
oc create -f ../../adapters/rest-vertx/target/fabric8/hono-adapter-rest-vertx-insecure-route.yml
echo ... done

echo Deploying MQTT adapter ...
oc create -f ../../adapters/mqtt-vertx/target/fabric8/hono-adapter-mqtt-vertx-svc.yml
oc create -f ../../adapters/mqtt-vertx/target/fabric8/hono-adapter-mqtt-vertx-dc.yml
oc create -f ../../adapters/mqtt-vertx/target/fabric8/hono-adapter-mqtt-vertx-route.yml
echo ... done

echo ECLIPSE HONO DEPLOYED ON OPENSHIFT