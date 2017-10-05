#!/bin/sh

# Copyright (c) 2017 Red Hat and others.
#
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the Eclipse Public License v1.0
# which accompanies this distribution, and is available at
# http://www.eclipse.org/legal/epl-v10.html
#
# Contributors:
#    Red Hat - initial creation
#    Bosch Software Innovations GmbH

# Absolute path this script is in
SCRIPTPATH="$(cd "$(dirname "$0")" && pwd -P)"
CONFIG=$SCRIPTPATH/../../config
CERTS=$CONFIG/hono-demo-certs-jar
HONO_HOME=$SCRIPTPATH/../../../..

echo DEPLOYING ECLIPSE HONO ON OPENSHIFT

# creating the directory for Hono Server persistent volume
if [ ! -d /tmp/hono ]; then
    mkdir /tmp/hono
    chmod 777 /tmp/hono
else
    echo /tmp/hono already exists!
fi

# creating Hono persistent volume (admin needed)
oc login -u system:admin
oc create -f $SCRIPTPATH/hono-pv.yml

oc login -u developer

# creating new project
oc new-project hono --description="Open source IoT connectivity" --display-name="Eclipse Hono"

# starting to deploy Eclipse Hono (developer user)
echo
echo "Deploying influxDB & Grafana ..."
oc create secret generic influxdb-conf --from-file=$CONFIG/influxdb.conf
oc create -f $CONFIG/hono-metrics-jar/META-INF/fabric8/openshift.yml
echo ... done

echo Deploying Apache ActiveMQ Artemis Broker ...
oc create secret generic hono-artemis-conf \
  --from-file=$CONFIG/hono-artemis-jar/etc/artemis-broker.xml \
  --from-file=$CONFIG/hono-artemis-jar/etc/artemis-bootstrap.xml \
  --from-file=$CONFIG/hono-artemis-jar/etc/artemis-users.properties \
  --from-file=$CONFIG/hono-artemis-jar/etc/artemis-roles.properties \
  --from-file=$CONFIG/hono-artemis-jar/etc/login.config \
  --from-file=$CONFIG/hono-artemis-jar/etc/logging.properties \
  --from-file=$CONFIG/hono-artemis-jar/etc/artemis.profile \
  --from-file=$CERTS/artemisKeyStore.p12 \
  --from-file=$CERTS/trustStore.jks
oc create -f $CONFIG/hono-artemis-jar/META-INF/fabric8/openshift.yml
echo ... done

echo Deploying Qpid Dispatch Router ...
oc create secret generic hono-dispatch-router-conf \
  --from-file=$CERTS/qdrouter-key.pem \
  --from-file=$CERTS/qdrouter-cert.pem \
  --from-file=$CERTS/trusted-certs.pem \
  --from-file=$CONFIG/hono-dispatch-router-jar/qpid/qdrouterd-with-broker.json \
  --from-file=$CONFIG/hono-dispatch-router-jar/sasl/qdrouter-sasl.conf \
  --from-file=$CONFIG/hono-dispatch-router-jar/sasl/qdrouterd.sasldb
oc create -f $CONFIG/hono-dispatch-router-jar/META-INF/fabric8/openshift.yml
echo ... done

echo Deploying Authentication Server ...
oc create secret generic hono-service-auth-conf \
  --from-file=$CERTS/auth-server-key.pem \
  --from-file=$CERTS/auth-server-cert.pem \
  --from-file=$CERTS/trusted-certs.pem \
  --from-file=application.yml=$CONFIG/hono-service-auth-config.yml
oc create -f $CONFIG/hono-service-auth-jar/META-INF/fabric8/openshift.yml
echo ... done

echo Deploying Device Registry ...
oc create secret generic hono-service-device-registry-conf \
  --from-file=$CERTS/device-registry-key.pem \
  --from-file=$CERTS/device-registry-cert.pem \
  --from-file=$CERTS/auth-server-cert.pem \
  --from-file=$CERTS/trusted-certs.pem \
  --from-file=$CONFIG/example-credentials.json \
  --from-file=application.yml=$CONFIG/hono-service-device-registry-config.yml
oc create -f $CONFIG/hono-service-device-registry-jar/META-INF/fabric8/openshift.yml
echo ... done

echo Deploying Hono Messaging ...
oc create secret generic hono-service-messaging-conf \
  --from-file=$CERTS/hono-messaging-key.pem \
  --from-file=$CERTS/hono-messaging-cert.pem \
  --from-file=$CERTS/auth-server-cert.pem \
  --from-file=$CERTS/trusted-certs.pem \
  --from-file=application.yml=$CONFIG/hono-service-messaging-config.yml
oc create -f $CONFIG/hono-service-messaging-jar/META-INF/fabric8/openshift.yml
echo ... done

echo Deploying HTTP REST adapter ...
oc create secret generic hono-adapter-rest-vertx-conf \
  --from-file=$CERTS/rest-adapter-key.pem \
  --from-file=$CERTS/rest-adapter-cert.pem \
  --from-file=$CERTS/trusted-certs.pem \
  --from-file=application.yml=$CONFIG/hono-adapter-rest-vertx-config.yml
oc create -f $CONFIG/hono-adapter-rest-vertx-jar/META-INF/fabric8/openshift.yml
echo ... done

echo Deploying MQTT adapter ...
oc create secret generic hono-adapter-mqtt-vertx-conf \
  --from-file=$CERTS/mqtt-adapter-key.pem \
  --from-file=$CERTS/mqtt-adapter-cert.pem \
  --from-file=$CERTS/trusted-certs.pem \
  --from-file=application.yml=$CONFIG/hono-adapter-mqtt-vertx-config.yml
oc create -f $CONFIG/hono-adapter-mqtt-vertx-jar/META-INF/fabric8/openshift.yml
echo ... done

echo ECLIPSE HONO DEPLOYED ON OPENSHIFT