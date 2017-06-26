#!/bin/sh

# Absolute path to this script, e.g. /home/user/bin/foo.sh
SCRIPT=$(readlink -f "$0")
# Absolute path this script is in, thus /home/user/bin
SCRIPTPATH=$(dirname "$SCRIPT")
HONO_HOME=$SCRIPTPATH/../..

echo DEPLOYING ECLIPSE HONO ON OPENSHIFT

# creating new project
oc new-project hono --description="Open source IoT connectivity" --display-name="Eclipse Hono"

# creating the directory for Hono Server persistent volume
if [ ! -d /tmp/hono ]; then
    mkdir /tmp/hono
    chmod 777 /tmp/hono
else
    echo /tmp/hono already exists!
fi

# creating Hono persistent volume (admin needed)
oc login -u system:admin -n hono
oc create -f $HONO_HOME/example/target/classes/META-INF/fabric8/openshift/hono-pv.yml

# starting to deploy Eclipse Hono (developer user)
oc login -u developer -n hono

echo
echo Deploying Grafana ...
oc create -f $HONO_HOME/metrics/target/classes/META-INF/fabric8/openshift.yml
echo ... done

echo Deploying Apache ActiveMQ Artemis Broker ...
oc create -f $HONO_HOME/broker/target/classes/META-INF/fabric8/openshift.yml
echo ... done

echo Deploying Qpid Dispatch Router ...
oc create -f $HONO_HOME/dispatchrouter/target/classes/META-INF/fabric8/openshift.yml
echo ... done

echo Deploying Authentication Server ...
oc create -f $HONO_HOME/services/auth/target/classes/META-INF/fabric8/openshift.yml
echo ... done

echo Deploying Device Registry ...
oc create -f $HONO_HOME/services/device-registry/target/classes/META-INF/fabric8/openshift.yml
echo ... done

echo Deploying Hono Messaging ...
oc create -f $HONO_HOME/services/messaging/target/classes/META-INF/fabric8/openshift.yml
echo ... done

echo Deploying HTTP REST adapter ...
oc create -f $HONO_HOME/adapters/rest-vertx/target/classes/META-INF/fabric8/openshift.yml
echo ... done

echo Deploying MQTT adapter ...
oc create -f $HONO_HOME/adapters/mqtt-vertx/target/classes/META-INF/fabric8/openshift.yml
echo ... done

echo ECLIPSE HONO DEPLOYED ON OPENSHIFT