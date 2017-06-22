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

# creating Hono persistent volume (admin needed)
oc login -u system:admin
oc create -f ../target/fabric8/hono-pv.yml

# starting to deploy Eclipse Hono (developer user)
oc login -u developer

echo Deploying Authentication Server ...
oc create -f ../../services/auth/target/fabric8/hono-auth-svc.yml
oc create -f ../../services/auth/target/fabric8/hono-auth-dc.yml
oc create -f ../../services/auth/target/fabric8/hono-auth-route.yml
echo ... done

echo Deploying Device Registry ...
oc create -f ../../services/device-registry/target/fabric8/hono-device-registry-svc.yml
oc create -f ../../services/device-registry/target/fabric8/hono-device-registry-pvc.yml
oc create -f ../../services/device-registry/target/fabric8/hono-device-registry-dc.yml
oc create -f ../../services/device-registry/target/fabric8/hono-device-registry-route.yml
echo ... done

echo Deploying Qpid Dispatch Router ...
oc create -f ../../dispatchrouter/target/fabric8/dispatch-router-svc.yml
oc create -f ../../dispatchrouter/target/fabric8/dispatch-router-external-svc.yml
oc create -f ../../dispatchrouter/target/fabric8/dispatch-router-dc.yml
oc create -f ../../dispatchrouter/target/fabric8/dispatch-router-route.yml
echo ... done

echo Deploying Apache ActiveMQ Artemis Broker ...
oc create -f ../../broker/target/fabric8/artemis-svc.yml
oc create -f ../../broker/target/fabric8/artemis-dc.yml
echo ... done

echo Deploying Hono Messaging ...
oc create -f ../../services/messaging/target/fabric8/hono-messaging-svc.yml
oc create -f ../../services/messaging/target/fabric8/hono-messaging-dc.yml
oc create -f ../../services/messaging/target/fabric8/hono-messaging-route.yml
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