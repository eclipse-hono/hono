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

source $SCRIPTPATH/common.sh

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

# use hono project
oc project hono

echo "Waiting for EnMasse..."
wait_for_enmasse 7 hono

# create addresses
curl -X PUT -T addresses.json -H "content-type: application/json" http://$(oc get route restapi -o jsonpath='{.spec.host}')/v1/addresses/default

# starting to deploy Eclipse Hono (developer user)
echo
echo "Deploying influxDB & Grafana ..."
oc create secret generic influxdb-conf --from-file=$CONFIG/influxdb.conf
oc create -f $CONFIG/hono-metrics-jar/META-INF/fabric8/openshift.yml
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
  --from-file=application.yml=$CONFIG/hono-service-messaging-config-enmasse.yml
oc create -f $CONFIG/hono-service-messaging-jar/META-INF/fabric8/openshift.yml
echo ... done

echo Deploying HTTP adapter ...
oc create secret generic hono-adapter-http-vertx-conf \
  --from-file=$CERTS/http-adapter-key.pem \
  --from-file=$CERTS/http-adapter-cert.pem \
  --from-file=$CERTS/trusted-certs.pem \
  --from-file=application.yml=$CONFIG/hono-adapter-http-vertx-config.yml
oc create -f $CONFIG/hono-adapter-http-vertx-jar/META-INF/fabric8/openshift.yml
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