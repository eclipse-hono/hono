#!/bin/sh

# Absolute path this script is in
SCRIPTPATH="$(cd "$(dirname "$0")" && pwd -P)"
HONO_HOME=$SCRIPTPATH/../..
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
echo Creating persistent volume ...
kubectl create -f $HONO_HOME/example/target/classes/META-INF/fabric8/kubernetes/hono-pv.yml --namespace $NS
echo ... done

echo
echo Deploying Grafana ...
kubectl create -f $HONO_HOME/metrics/target/classes/META-INF/fabric8/kubernetes.yml --namespace $NS
echo ... done

echo
echo Deploying Artemis broker ...
kubectl create -f $HONO_HOME/broker/target/classes/META-INF/fabric8/kubernetes.yml --namespace $NS
echo ... done

echo
echo Deploying Qpid Dispatch Router ...
kubectl create -f $HONO_HOME/dispatchrouter/target/classes/META-INF/fabric8/kubernetes.yml --namespace $NS
echo ... done

echo
echo Deploying Authentication Server ...
kubectl create -f $HONO_HOME/services/auth/target/classes/META-INF/fabric8/kubernetes.yml --namespace $NS
echo ... done

echo
echo Deploying Device Registry ...
kubectl create -f $HONO_HOME/services/device-registry/target/classes/META-INF/fabric8/kubernetes.yml --namespace $NS
echo ... done

echo
echo Deploying Hono Messaging ...
kubectl create -f $HONO_HOME/services/messaging/target/classes/META-INF/fabric8/kubernetes.yml --namespace $NS
echo ... done

echo
echo Deploying HTTP REST adapter ...
kubectl create -f $HONO_HOME/adapters/rest-vertx/target/classes/META-INF/fabric8/kubernetes.yml --namespace $NS
echo ... done

echo
echo Deploying MQTT adapter ...
kubectl create -f $HONO_HOME/adapters/mqtt-vertx/target/classes/META-INF/fabric8/kubernetes.yml --namespace $NS
echo ... done

echo ECLIPSE HONO DEPLOYED TO KUBERNETES
