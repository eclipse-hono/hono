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
OPENSHIFT_MASTER=${1:-"https://$(minishift ip):8443"}


source $SCRIPTPATH/common.sh

echo DEPLOYING ECLIPSE HONO ON OPENSHIFT

prepare_openshift

# creating new project
oc new-project hono --description="Open source IoT connectivity" --display-name="Eclipse Hono"

# starting to deploy Eclipse Hono (developer user)
echo
echo "Deploying influxDB & Grafana ..."

# add a service account allowing Grafana to run as root
oc create serviceaccount useroot --as='system:admin'
oc adm policy add-scc-to-user anyuid -z useroot --as='system:admin'

oc create secret generic influxdb-conf --from-file=$CONFIG/influxdb.conf
oc create -f $SCRIPTPATH/../kubernetes/influxdb-deployment.yml
oc create -f $SCRIPTPATH/../kubernetes/influxdb-svc.yml
oc create -f $SCRIPTPATH/../kubernetes/grafana-deployment.yml
oc create -f $SCRIPTPATH/../kubernetes/grafana-svc.yml
oc create -f $SCRIPTPATH/grafana-route.yml
echo ... done

echo "Deploying Apache ActiveMQ Artemis Broker ..."
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

echo "Deploying Qpid Dispatch Router ..."
oc create secret generic hono-dispatch-router-conf \
  --from-file=$CERTS/qdrouter-key.pem \
  --from-file=$CERTS/qdrouter-cert.pem \
  --from-file=$CERTS/trusted-certs.pem \
  --from-file=$CONFIG/hono-dispatch-router-jar/qpid/qdrouterd-with-broker.json \
  --from-file=$CONFIG/hono-dispatch-router-jar/sasl/qdrouter-sasl.conf \
  --from-file=$CONFIG/hono-dispatch-router-jar/sasl/qdrouterd.sasldb
oc create -f $CONFIG/hono-dispatch-router-jar/META-INF/fabric8/openshift.yml
echo ... done

echo "Deploying Authentication Server ..."
oc create secret generic hono-service-auth-conf \
  --from-file=$CERTS/auth-server-key.pem \
  --from-file=$CERTS/auth-server-cert.pem \
  --from-file=$CERTS/trusted-certs.pem \
  --from-file=application.yml=$CONFIG/hono-service-auth-config.yml
oc create -f $CONFIG/hono-service-auth-jar/META-INF/fabric8/openshift.yml
echo ... done

echo "Deploying Device Registry ..."
oc create secret generic hono-service-device-registry-conf \
  --from-file=$CERTS/device-registry-key.pem \
  --from-file=$CERTS/device-registry-cert.pem \
  --from-file=$CERTS/auth-server-cert.pem \
  --from-file=$CERTS/trusted-certs.pem \
  --from-file=$CONFIG/example-credentials.json \
  --from-file=application.yml=$CONFIG/hono-service-device-registry-config.yml
oc create -f $CONFIG/hono-service-device-registry-jar/META-INF/fabric8/openshift.yml
echo ... done

echo "Deploying Hono Messaging ..."
oc create secret generic hono-service-messaging-conf \
  --from-file=$CERTS/hono-messaging-key.pem \
  --from-file=$CERTS/hono-messaging-cert.pem \
  --from-file=$CERTS/auth-server-cert.pem \
  --from-file=$CERTS/trusted-certs.pem \
  --from-file=application.yml=$CONFIG/hono-service-messaging-config.yml
oc create -f $CONFIG/hono-service-messaging-jar/META-INF/fabric8/openshift.yml
echo ... done

echo "Deploying HTTP adapter ..."
oc create secret generic hono-adapter-http-vertx-conf \
  --from-file=$CERTS/http-adapter-key.pem \
  --from-file=$CERTS/http-adapter-cert.pem \
  --from-file=$CERTS/trusted-certs.pem \
  --from-file=application.yml=$CONFIG/hono-adapter-http-vertx-config.yml
oc create -f $CONFIG/hono-adapter-http-vertx-jar/META-INF/fabric8/openshift.yml
echo ... done

echo "Deploying MQTT adapter ..."
oc create secret generic hono-adapter-mqtt-vertx-conf \
  --from-file=$CERTS/mqtt-adapter-key.pem \
  --from-file=$CERTS/mqtt-adapter-cert.pem \
  --from-file=$CERTS/trusted-certs.pem \
  --from-file=application.yml=$CONFIG/hono-adapter-mqtt-vertx-config.yml
oc create -f $CONFIG/hono-adapter-mqtt-vertx-jar/META-INF/fabric8/openshift.yml
echo ... done

echo "Deploying Kura adapter ..."
oc create secret generic hono-adapter-kura-conf \
  --from-file=$CERTS/kura-adapter-key.pem \
  --from-file=$CERTS/kura-adapter-cert.pem \
  --from-file=$CERTS/trusted-certs.pem \
  --from-file=application.yml=$CONFIG/hono-adapter-kura-config.yml
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