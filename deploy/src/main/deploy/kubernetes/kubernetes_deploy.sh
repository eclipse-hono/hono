#!/bin/sh
#*******************************************************************************
# Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
#
# See the NOTICE file(s) distributed with this work for additional
# information regarding copyright ownership.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License 2.0 which is available at
# http://www.eclipse.org/legal/epl-2.0
#
# SPDX-License-Identifier: EPL-2.0
#*******************************************************************************

# Absolute path this script is in
SCRIPTPATH="$(cd "$(dirname "$0")" && pwd -P)"
HONO_HOME=$SCRIPTPATH/../../../..
CONFIG=$SCRIPTPATH/../../config
RESOURCES=$SCRIPTPATH/../resource-descriptors
CERTS=$CONFIG/hono-demo-certs-jar
NS=hono

echo DEPLOYING ECLIPSE HONO TO KUBERNETES

# creating Hono namespace
kubectl create namespace $NS

echo
echo "Deploying influxDB & Grafana ..."

kubectl create secret generic influxdb-conf \
  --from-file=$SCRIPTPATH/../influxdb.conf \
  --namespace $NS
kubectl create -f $RESOURCES/influx --namespace $NS
kubectl create configmap grafana-provisioning-datasources \
  --from-file=$SCRIPTPATH/grafana/provisioning/datasources \
  --namespace $NS
kubectl create configmap grafana-provisioning-dashboards \
  --from-file=$SCRIPTPATH/grafana/provisioning/dashboards \
  --namespace $NS
kubectl create configmap grafana-dashboard-defs \
--from-file=$SCRIPTPATH/grafana/dashboard-definitions \
  --namespace $NS
kubectl create -f $RESOURCES/grafana --namespace $NS
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
kubectl create -f $RESOURCES/artemis --namespace $NS
echo ... done

echo
echo "Deploying Qpid Dispatch Router ..."
kubectl create secret generic hono-dispatch-router-conf \
  --from-file=$CERTS/qdrouter-key.pem \
  --from-file=$CERTS/qdrouter-cert.pem \
  --from-file=$CERTS/trusted-certs.pem \
  --from-file=$SCRIPTPATH/qpid/qdrouterd-with-broker.json \
  --namespace $NS
kubectl create -f $RESOURCES/dispatch-router --namespace $NS
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
kubectl create -f $RESOURCES/hono-service-auth --namespace $NS
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
kubectl create -f $RESOURCES/hono-service-device-registry --namespace $NS
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
kubectl create -f $RESOURCES/hono-service-messaging --namespace $NS
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
kubectl create -f $RESOURCES/hono-adapter-http --namespace $NS
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
kubectl create -f $RESOURCES/hono-adapter-mqtt --namespace $NS
echo ... done

echo
echo "Deploying AMQP adapter ..."
kubectl create secret generic hono-adapter-amqp-vertx-conf \
  --from-file=$CERTS/amqp-adapter-key.pem \
  --from-file=$CERTS/amqp-adapter-cert.pem \
  --from-file=$CERTS/trusted-certs.pem \
  --from-file=$SCRIPTPATH/../amqp-adapter.credentials \
  --from-file=application.yml=$SCRIPTPATH/hono-adapter-amqp-vertx-config.yml \
  --namespace $NS
kubectl create -f $RESOURCES/hono-adapter-amqp --namespace $NS
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
kubectl create -f $RESOURCES/hono-adapter-kura --namespace $NS
echo ... done

echo ECLIPSE HONO DEPLOYED TO KUBERNETES
