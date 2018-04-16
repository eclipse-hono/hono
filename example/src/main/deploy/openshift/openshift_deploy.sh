#!/bin/bash

# Copyright (c) 2017, 2018 Red Hat and others.
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
OPENSHIFT_MASTER=${1:-"https://$(minishift ip):8443"}

source $SCRIPTPATH/common.sh

echo DEPLOYING ECLIPSE HONO ON OPENSHIFT

# use hono project
oc project hono

echo "Waiting for EnMasse..."
wait_for_enmasse 7 hono

# create addresses
curl -X PUT -T addresses.json -H "content-type: application/json" http://$(oc get route restapi -o jsonpath='{.spec.host}')/v1/addresses/default

# starting to deploy Eclipse Hono (developer user)
echo
echo "Deploying influxDB & Grafana ..."

oc create secret generic influxdb-conf --from-file=$SCRIPTPATH/../influxdb.conf
oc create -f $SCRIPTPATH/../influxdb-deployment.yml
oc create -f $SCRIPTPATH/../influxdb-svc.yml
oc create -f $SCRIPTPATH/../grafana-deployment.yml
oc create -f $SCRIPTPATH/../grafana-svc.yml
oc create -f $SCRIPTPATH/grafana-route.yml
echo ... done

echo "Deploying Authentication Server ..."
oc create secret generic hono-service-auth-conf \
  --from-file=$CERTS/auth-server-key.pem \
  --from-file=$CERTS/auth-server-cert.pem \
  --from-file=$CERTS/trusted-certs.pem \
  --from-file=permissions.json=$SCRIPTPATH/../example-permissions.json \
  --from-file=application.yml=$SCRIPTPATH/hono-service-auth-config.yml
oc create -f $CONFIG/hono-service-auth-jar/META-INF/fabric8/openshift.yml
echo ... done

echo "Deploying Device Registry ..."
oc create secret generic hono-service-device-registry-conf \
  --from-file=$CERTS/device-registry-key.pem \
  --from-file=$CERTS/device-registry-cert.pem \
  --from-file=$CERTS/auth-server-cert.pem \
  --from-file=$CERTS/trusted-certs.pem \
  --from-file=$SCRIPTPATH/../example-credentials.json \
  --from-file=$SCRIPTPATH/../example-tenants.json \
  --from-file=application.yml=$SCRIPTPATH/hono-service-device-registry-config.yml
oc create -f $CONFIG/hono-service-device-registry-jar/META-INF/fabric8/openshift.yml
echo ... done

echo "Deploying Hono Messaging ..."
oc create secret generic hono-service-messaging-conf \
  --from-file=$CERTS/hono-messaging-key.pem \
  --from-file=$CERTS/hono-messaging-cert.pem \
  --from-file=$CERTS/auth-server-cert.pem \
  --from-file=$CERTS/trusted-certs.pem \
  --from-file=application.yml=$SCRIPTPATH/hono-service-messaging-config-enmasse.yml
oc create -f $CONFIG/hono-service-messaging-jar/META-INF/fabric8/openshift.yml
echo ... done

echo "Deploying HTTP adapter ..."
oc create secret generic hono-adapter-http-vertx-conf \
  --from-file=$CERTS/http-adapter-key.pem \
  --from-file=$CERTS/http-adapter-cert.pem \
  --from-file=$CERTS/trusted-certs.pem \
  --from-file=$SCRIPTPATH/../http-adapter.credentials \
  --from-file=application.yml=$SCRIPTPATH/hono-adapter-http-vertx-config.yml
oc create -f $CONFIG/hono-adapter-http-vertx-jar/META-INF/fabric8/openshift.yml
echo ... done

echo "Deploying MQTT adapter ..."
oc create secret generic hono-adapter-mqtt-vertx-conf \
  --from-file=$CERTS/mqtt-adapter-key.pem \
  --from-file=$CERTS/mqtt-adapter-cert.pem \
  --from-file=$CERTS/trusted-certs.pem \
  --from-file=$SCRIPTPATH/../mqtt-adapter.credentials \
  --from-file=application.yml=$SCRIPTPATH/hono-adapter-mqtt-vertx-config.yml
oc create -f $CONFIG/hono-adapter-mqtt-vertx-jar/META-INF/fabric8/openshift.yml
echo ... done

echo "Deploying Kura adapter ..."
oc create secret generic hono-adapter-kura-conf \
  --from-file=$CERTS/kura-adapter-key.pem \
  --from-file=$CERTS/kura-adapter-cert.pem \
  --from-file=$CERTS/trusted-certs.pem \
  --from-file=$SCRIPTPATH/../kura-adapter.credentials \
  --from-file=application.yml=$SCRIPTPATH/hono-adapter-kura-config.yml
oc create -f $CONFIG/hono-adapter-kura-jar/META-INF/fabric8/openshift.yml
echo ... done

echo
echo "Configuring Grafana with data source & dashboard ..."

chmod +x $SCRIPTPATH/../configure_grafana.sh
HOST=$(oc get nodes --output=jsonpath='{range .items[*]}{.status.addresses[?(@.type=="InternalIP")].address} {.spec.podCIDR} {"\n"}{end}' --as system:admin)
GRAFANA_PORT='NaN'
until [ "$GRAFANA_PORT" -eq "$GRAFANA_PORT" ] 2>/dev/null; do
  GRAFANA_PORT=$(oc get service grafana --output='jsonpath={.spec.ports[0].nodePort}' --as system:admin); sleep 1;
done
$SCRIPTPATH/../configure_grafana.sh $HOST $GRAFANA_PORT
echo ... done

echo ECLIPSE HONO DEPLOYED ON OPENSHIFT