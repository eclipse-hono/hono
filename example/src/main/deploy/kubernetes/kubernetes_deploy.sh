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
HONO_HOME=$SCRIPTPATH/../../../..
CONFIG=$SCRIPTPATH/../../config
CERTS=$CONFIG/hono-demo-certs-jar
NS=hono

echo DEPLOYING ECLIPSE HONO TO KUBERNETES

# creating Hono namespace
kubectl create namespace $NS

echo
echo "Deploying influxDB & Grafana ..."

kubectl create serviceaccount useroot --namespace $NS

kubectl create secret generic influxdb-conf \
  --from-file=$SCRIPTPATH/../influxdb.conf \
  --namespace $NS
kubectl create -f $SCRIPTPATH/../influxdb-deployment.yml --namespace $NS
kubectl create -f $SCRIPTPATH/../influxdb-svc.yml --namespace $NS
kubectl create -f $SCRIPTPATH/../grafana-deployment.yml --namespace $NS
kubectl create -f $SCRIPTPATH/../grafana-svc.yml --namespace $NS
echo ... done

echo
echo "Deploying Artemis broker ..."
kubectl create secret generic hono-artemis-conf \
  --from-file=$SCRIPTPATH/artemis/artemis-broker.xml \
  --from-file=$SCRIPTPATH/artemis/artemis-bootstrap.xml \
  --from-file=$SCRIPTPATH/artemis/artemis-users.properties \
  --from-file=$SCRIPTPATH/artemis/artemis-roles.properties \
  --from-file=$SCRIPTPATH/artemis/login.config \
  --from-file=$SCRIPTPATH/artemis/logging.properties \
  --from-file=$SCRIPTPATH/artemis/artemis.profile \
  --from-file=$CERTS/artemisKeyStore.p12 \
  --from-file=$CERTS/trustStore.jks \
  --namespace $NS
kubectl create -f $SCRIPTPATH/../artemis-deployment.yml --namespace $NS
kubectl create -f $SCRIPTPATH/../artemis-svc.yml --namespace $NS
echo ... done

echo
echo "Deploying Qpid Dispatch Router ..."
kubectl create secret generic hono-dispatch-router-conf \
  --from-file=$CERTS/qdrouter-key.pem \
  --from-file=$CERTS/qdrouter-cert.pem \
  --from-file=$CERTS/trusted-certs.pem \
  --from-file=$SCRIPTPATH/qpid/qdrouterd-with-broker.json \
  --from-file=$SCRIPTPATH/qpid/qdrouter-sasl.conf \
  --from-file=$SCRIPTPATH/qpid/qdrouterd.sasldb \
  --namespace $NS
kubectl create -f $SCRIPTPATH/../dispatch-router-deployment.yml --namespace $NS
kubectl create -f $SCRIPTPATH/../dispatch-router-svc.yml --namespace $NS
kubectl create -f $SCRIPTPATH/../dispatch-router-ext-svc.yml --namespace $NS
echo ... done

echo
echo "Deploying Authentication Server ..."
kubectl create secret generic hono-service-auth-conf \
  --from-file=$CERTS/auth-server-key.pem \
  --from-file=$CERTS/auth-server-cert.pem \
  --from-file=$CERTS/trusted-certs.pem \
  --from-file=permissions.json=$SCRIPTPATH/../example-permissions.json \
  --from-file=application.yml=$SCRIPTPATH/hono-service-auth-config.yml \
  --namespace $NS
kubectl create -f $CONFIG/hono-service-auth-jar/META-INF/fabric8/kubernetes.yml --namespace $NS
echo ... done

echo
echo "Deploying Device Registry ..."
kubectl create secret generic hono-service-device-registry-conf \
  --from-file=$CERTS/device-registry-key.pem \
  --from-file=$CERTS/device-registry-cert.pem \
  --from-file=$CERTS/auth-server-cert.pem \
  --from-file=$CERTS/trusted-certs.pem \
  --from-file=$SCRIPTPATH/../example-credentials.json \
  --from-file=$SCRIPTPATH/../example-tenants.json \
  --from-file=application.yml=$SCRIPTPATH/hono-service-device-registry-config.yml \
  --namespace $NS
kubectl create -f $CONFIG/hono-service-device-registry-jar/META-INF/fabric8/kubernetes.yml --namespace $NS
echo ... done

echo
echo "Deploying Hono Messaging ..."
kubectl create secret generic hono-service-messaging-conf \
  --from-file=$CERTS/hono-messaging-key.pem \
  --from-file=$CERTS/hono-messaging-cert.pem \
  --from-file=$CERTS/auth-server-cert.pem \
  --from-file=$CERTS/trusted-certs.pem \
  --from-file=application.yml=$SCRIPTPATH/hono-service-messaging-config.yml \
  --namespace $NS
kubectl create -f $CONFIG/hono-service-messaging-jar/META-INF/fabric8/kubernetes.yml --namespace $NS
echo ... done

echo
echo "Deploying HTTP adapter ..."
kubectl create secret generic hono-adapter-http-vertx-conf \
  --from-file=$CERTS/http-adapter-key.pem \
  --from-file=$CERTS/http-adapter-cert.pem \
  --from-file=$CERTS/trusted-certs.pem \
  --from-file=$SCRIPTPATH/../http-adapter.credentials \
  --from-file=application.yml=$SCRIPTPATH/hono-adapter-http-vertx-config.yml \
  --namespace $NS
kubectl create -f $CONFIG/hono-adapter-http-vertx-jar/META-INF/fabric8/kubernetes.yml --namespace $NS
echo ... done

echo
echo "Deploying MQTT adapter ..."
kubectl create secret generic hono-adapter-mqtt-vertx-conf \
  --from-file=$CERTS/mqtt-adapter-key.pem \
  --from-file=$CERTS/mqtt-adapter-cert.pem \
  --from-file=$CERTS/trusted-certs.pem \
  --from-file=$SCRIPTPATH/../mqtt-adapter.credentials \
  --from-file=application.yml=$SCRIPTPATH/hono-adapter-mqtt-vertx-config.yml \
  --namespace $NS
kubectl create -f $CONFIG/hono-adapter-mqtt-vertx-jar/META-INF/fabric8/kubernetes.yml --namespace $NS
echo ... done

echo
echo "Deploying Kura adapter ..."
kubectl create secret generic hono-adapter-kura-conf \
  --from-file=$CERTS/kura-adapter-key.pem \
  --from-file=$CERTS/kura-adapter-cert.pem \
  --from-file=$CERTS/trusted-certs.pem \
  --from-file=$SCRIPTPATH/../kura-adapter.credentials \
  --from-file=application.yml=$SCRIPTPATH/hono-adapter-kura-config.yml \
  --namespace $NS
kubectl create -f $CONFIG/hono-adapter-kura-jar/META-INF/fabric8/kubernetes.yml --namespace $NS
echo ... done

echo
echo "Configuring Grafana with data source & dashboard ..."

chmod +x $SCRIPTPATH/../configure_grafana.sh
HOST=$(kubectl get nodes --output=jsonpath='{range .items[*]}{.status.addresses[?(@.type=="InternalIP")].address} {.spec.podCIDR} {"\n"}{end}')
GRAFANA_PORT='NaN'
until [ "$GRAFANA_PORT" -eq "$GRAFANA_PORT" ] 2>/dev/null; do
  GRAFANA_PORT=$(kubectl get service grafana -n hono --output='jsonpath={.spec.ports[0].nodePort}'); sleep 1;
done
$SCRIPTPATH/../configure_grafana.sh $HOST $GRAFANA_PORT
echo ... done

echo ECLIPSE HONO DEPLOYED TO KUBERNETES
