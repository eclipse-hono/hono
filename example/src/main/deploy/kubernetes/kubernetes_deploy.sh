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
kubectl create secret generic influxdb-conf \
  --from-file=$CONFIG/influxdb.conf \
  --namespace $NS
kubectl create -f $CONFIG/hono-metrics-jar/META-INF/fabric8/kubernetes.yml --namespace $NS
echo ... done

echo
echo Deploying Artemis broker ...
kubectl create secret generic hono-artemis-conf \
  --from-file=$CONFIG/hono-artemis-jar/etc/artemis-broker.xml \
  --from-file=$CONFIG/hono-artemis-jar/etc/artemis-bootstrap.xml \
  --from-file=$CONFIG/hono-artemis-jar/etc/artemis-users.properties \
  --from-file=$CONFIG/hono-artemis-jar/etc/artemis-roles.properties \
  --from-file=$CONFIG/hono-artemis-jar/etc/login.config \
  --from-file=$CONFIG/hono-artemis-jar/etc/logging.properties \
  --from-file=$CONFIG/hono-artemis-jar/etc/artemis.profile \
  --from-file=$CERTS/artemisKeyStore.p12 \
  --from-file=$CERTS/trustStore.jks \
  --namespace $NS
kubectl create -f $CONFIG/hono-artemis-jar/META-INF/fabric8/kubernetes.yml --namespace $NS
echo ... done

echo
echo Deploying Qpid Dispatch Router ...
kubectl create secret generic hono-dispatch-router-conf \
  --from-file=$CERTS/qdrouter-key.pem \
  --from-file=$CERTS/qdrouter-cert.pem \
  --from-file=$CERTS/trusted-certs.pem \
  --from-file=$CONFIG/hono-dispatch-router-jar/qpid/qdrouterd-with-broker.json \
  --from-file=$CONFIG/hono-dispatch-router-jar/sasl/qdrouter-sasl.conf \
  --from-file=$CONFIG/hono-dispatch-router-jar/sasl/qdrouterd.sasldb \
  --namespace $NS
kubectl create -f $CONFIG/hono-dispatch-router-jar/META-INF/fabric8/kubernetes.yml --namespace $NS
echo ... done

echo
echo Deploying Authentication Server ...
kubectl create secret generic hono-service-auth-conf \
  --from-file=$CERTS/auth-server-key.pem \
  --from-file=$CERTS/auth-server-cert.pem \
  --from-file=$CERTS/trusted-certs.pem \
  --from-file=application.yml=$CONFIG/hono-service-auth-config.yml \
  --namespace $NS
kubectl create -f $CONFIG/hono-service-auth-jar/META-INF/fabric8/kubernetes.yml --namespace $NS
echo ... done

echo
echo Deploying Device Registry ...
kubectl create secret generic hono-service-device-registry-conf \
  --from-file=$CERTS/device-registry-key.pem \
  --from-file=$CERTS/device-registry-cert.pem \
  --from-file=$CERTS/auth-server-cert.pem \
  --from-file=$CERTS/trusted-certs.pem \
  --from-file=$CONFIG/example-credentials.json \
  --from-file=application.yml=$CONFIG/hono-service-device-registry-config.yml \
  --namespace $NS
kubectl create -f $CONFIG/hono-service-device-registry-jar/META-INF/fabric8/kubernetes.yml --namespace $NS
echo ... done

echo
echo Deploying Hono Messaging ...
kubectl create secret generic hono-service-messaging-conf \
  --from-file=$CERTS/hono-messaging-key.pem \
  --from-file=$CERTS/hono-messaging-cert.pem \
  --from-file=$CERTS/auth-server-cert.pem \
  --from-file=$CERTS/trusted-certs.pem \
  --from-file=application.yml=$CONFIG/hono-service-messaging-config.yml \
  --namespace $NS
kubectl create -f $CONFIG/hono-service-messaging-jar/META-INF/fabric8/kubernetes.yml --namespace $NS
echo ... done

echo
echo Deploying HTTP REST adapter ...
kubectl create secret generic hono-adapter-rest-vertx-conf \
  --from-file=$CERTS/rest-adapter-key.pem \
  --from-file=$CERTS/rest-adapter-cert.pem \
  --from-file=$CERTS/trusted-certs.pem \
  --from-file=application.yml=$CONFIG/hono-adapter-rest-vertx-config.yml \
  --namespace $NS
kubectl create -f $CONFIG/hono-adapter-rest-vertx-jar/META-INF/fabric8/kubernetes.yml --namespace $NS
echo ... done

echo
echo Deploying MQTT adapter ...
kubectl create secret generic hono-adapter-mqtt-vertx-conf \
  --from-file=$CERTS/mqtt-adapter-key.pem \
  --from-file=$CERTS/mqtt-adapter-cert.pem \
  --from-file=$CERTS/trusted-certs.pem \
  --from-file=application.yml=$CONFIG/hono-adapter-mqtt-vertx-config.yml \
  --namespace $NS
kubectl create -f $CONFIG/hono-adapter-mqtt-vertx-jar/META-INF/fabric8/kubernetes.yml --namespace $NS
echo ... done

echo ECLIPSE HONO DEPLOYED TO KUBERNETES
