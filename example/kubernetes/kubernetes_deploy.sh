#!/bin/sh

# Absolute path to this script, e.g. /home/user/bin/foo.sh
SCRIPT=$(readlink -f "$0")
# Absolute path this script is in, thus /home/user/bin
SCRIPTPATH=$(dirname "$SCRIPT")
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
kubectl create -f $HONO_HOME/example/target/classes/META-INF/fabric8/kubernetes/hono-pv.yml --namespace $NS

echo Deploying Artemis broker ...
kubectl create -f $HONO_HOME/broker/target/classes/META-INF/fabric8/kubernetes.yml --namespace $NS

echo Deploying Qpid Dispatch Router ...
kubectl create -f $HONO_HOME/dispatchrouter/target/classes/META-INF/fabric8/kubernetes.yml --namespace $NS
echo ... done

echo Deploying Authentication Server ...
kubectl create -f $HONO_HOME/services/auth/target/classes/META-INF/fabric8/kubernetes.yml --namespace $NS
echo ... done

echo Deploying Device Registry ...
kubectl create -f $HONO_HOME/services/device-registry/target/classes/META-INF/fabric8/kubernetes.yml --namespace $NS
echo ... done

echo Deploying Hono Messaging ...
kubectl create -f $HONO_HOME/services/messaging/target/classes/META-INF/fabric8/kubernetes.yml --namespace $NS
echo ... done

echo Deploying HTTP REST adapter ...
kubectl create -f $HONO_HOME/adapters/rest-vertx/target/classes/META-INF/fabric8/kubernetes.yml --namespace $NS
echo ... done

echo Deploying MQTT adapter ...
kubectl create -f $HONO_HOME/adapters/mqtt-vertx/target/classes/META-INF/fabric8/kubernetes.yml --namespace $NS
echo ... done

echo ECLIPSE HONO DEPLOYED TO KUBERNETES
