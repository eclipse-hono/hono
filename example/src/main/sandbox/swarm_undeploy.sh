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
NS=hono

echo UNDEPLOYING ECLIPSE HONO SANDBOX FROM DOCKER SWARM

docker service rm hono-nginx
docker config rm site.conf

docker service rm hono-adapter-kura
docker secret rm \
  kura-adapter-key.pem \
  kura-adapter-cert.pem \
  hono-adapter-kura-config.yml

docker service rm hono-adapter-http-vertx
docker secret rm \
  http-adapter-key.pem \
  http-adapter-cert.pem \
  hono-adapter-http-vertx-config.yml

docker service rm hono-adapter-mqtt-vertx
docker secret rm \
  mqtt-adapter-key.pem \
  mqtt-adapter-cert.pem \
  hono-adapter-mqtt-vertx-config.yml

docker service rm hono-service-messaging
docker secret rm \
  hono-messaging-key.pem \
  hono-messaging-cert.pem \
  hono-service-messaging-config.yml

docker service rm hono-service-device-registry
docker secret rm \
  device-registry-key.pem \
  device-registry-cert.pem \
  hono-service-device-registry-config.yml

docker service rm init-device-registry-data
docker secret rm sandbox-credentials.json
docker secret rm sandbox-tenants.json

docker service rm hono-service-auth
docker secret rm \
  sandbox-permissions.json \
  auth-server-key.pem \
  auth-server-cert.pem \
  hono-service-auth-config.yml

docker service rm hono-dispatch-router
docker secret rm \
  qdrouter-key.pem \
  qdrouter-cert.pem \
  qdrouterd.json \
  qdrouter-sasl.conf \
  qdrouterd.sasldb

docker service rm hono-artemis
docker secret rm \
  artemis-broker.xml \
  artemis-bootstrap.xml \
  artemis-users.properties \
  artemis-roles.properties \
  login.config \
  logging.properties \
  artemis.profile \
  artemisKeyStore.p12 \
  trustStore.jks

docker service rm grafana influxdb
docker secret rm influxdb.conf

docker secret rm trusted-certs.pem

docker network rm $NS

echo ECLIPSE HONO SANDBOX UNDEPLOYED FROM DOCKER SWARM
