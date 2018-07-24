#!/bin/bash
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
CONFIG=$SCRIPTPATH/../../config
CERTS=$CONFIG/hono-demo-certs-jar
RESOURCES=$SCRIPTPATH/../resource-descriptors
HONO_HOME=$SCRIPTPATH/../../../..
OPENSHIFT_MASTER=${1:-"https://$(minishift ip):8443"}

set -e

source $SCRIPTPATH/common.sh

echo DEPLOYING ECLIPSE HONO ON OPENSHIFT

# use hono project
oc project hono

echo "Waiting for EnMasse..."
wait_for_enmasse 8 hono

# create addresses
curl -X POST --insecure -T "$SCRIPTPATH/addresses.json" -H "content-type: application/json" https://$(oc get route restapi -o jsonpath='{.spec.host}')/apis/enmasse.io/v1alpha1/namespaces/hono/addressspaces/default/addresses

# starting to deploy Eclipse Hono (developer user)
echo
echo "Deploying influxDB & Grafana ..."

oc create secret generic influxdb-conf --from-file=$SCRIPTPATH/../influxdb.conf
oc create -f $RESOURCES/influx

oc create configmap grafana-provisioning-datasources --from-file=$SCRIPTPATH/grafana/provisioning/datasources
oc create configmap grafana-provisioning-dashboards --from-file=$SCRIPTPATH/grafana/provisioning/dashboards
oc create configmap grafana-dashboard-defs --from-file=$SCRIPTPATH/grafana/dashboard-definitions
oc create -f $RESOURCES/grafana
echo ... done

echo "Deploying Authentication Server ..."
oc create secret generic hono-service-auth-conf \
  --from-file=$CERTS/auth-server-key.pem \
  --from-file=$CERTS/auth-server-cert.pem \
  --from-file=$CERTS/trusted-certs.pem \
  --from-file=permissions.json=$SCRIPTPATH/../example-permissions.json \
  --from-file=application.yml=$SCRIPTPATH/hono-service-auth-config.yml
oc create -f $RESOURCES/hono-service-auth
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
oc create -f $RESOURCES/hono-service-device-registry
echo ... done

echo "Deploying Hono Messaging ..."
oc create secret generic hono-service-messaging-conf \
  --from-file=$CERTS/hono-messaging-key.pem \
  --from-file=$CERTS/hono-messaging-cert.pem \
  --from-file=$CERTS/auth-server-cert.pem \
  --from-file=$CERTS/trusted-certs.pem \
  --from-file=application.yml=$SCRIPTPATH/hono-service-messaging-config-enmasse.yml
oc create -f $RESOURCES/hono-service-messaging
echo ... done

echo "Deploying HTTP adapter ..."
oc create secret generic hono-adapter-http-vertx-conf \
  --from-file=$CERTS/http-adapter-key.pem \
  --from-file=$CERTS/http-adapter-cert.pem \
  --from-file=$CERTS/trusted-certs.pem \
  --from-file=$SCRIPTPATH/../http-adapter.credentials \
  --from-file=application.yml=$SCRIPTPATH/hono-adapter-http-vertx-config-enmasse.yml
oc create -f $RESOURCES/hono-adapter-http
echo ... done

echo "Deploying MQTT adapter ..."
oc create secret generic hono-adapter-mqtt-vertx-conf \
  --from-file=$CERTS/mqtt-adapter-key.pem \
  --from-file=$CERTS/mqtt-adapter-cert.pem \
  --from-file=$CERTS/trusted-certs.pem \
  --from-file=$SCRIPTPATH/../mqtt-adapter.credentials \
  --from-file=application.yml=$SCRIPTPATH/hono-adapter-mqtt-vertx-config-enmasse.yml
oc create -f $RESOURCES/hono-adapter-mqtt
echo ... done

echo "Deploying Kura adapter ..."
oc create secret generic hono-adapter-kura-conf \
  --from-file=$CERTS/kura-adapter-key.pem \
  --from-file=$CERTS/kura-adapter-cert.pem \
  --from-file=$CERTS/trusted-certs.pem \
  --from-file=$SCRIPTPATH/../kura-adapter.credentials \
  --from-file=application.yml=$SCRIPTPATH/hono-adapter-kura-config.yml
oc create -f $RESOURCES/hono-adapter-kura
echo ... done

echo ECLIPSE HONO DEPLOYED ON OPENSHIFT