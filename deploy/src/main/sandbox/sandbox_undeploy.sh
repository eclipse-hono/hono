#!/bin/sh
#*******************************************************************************
# Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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
NS=hono

echo UNDEPLOYING ECLIPSE HONO SANDBOX FROM DOCKER SWARM

docker service rm hono-nginx
docker config rm site.conf

docker service rm ${hono.adapter-coap.service}
docker secret rm \
  coap-adapter.credentials \
  hono-adapter-coap-vertx-config.yml

docker service rm ${hono.adapter-kura.service}
docker secret rm \
  kura-adapter.credentials \
  hono-adapter-kura-config.yml

docker service rm ${hono.adapter-amqp.service}
docker secret rm \
  hono-adapter-amqp-vertx-config.yml \
  amqp-adapter.credentials

docker service rm ${hono.adapter-http.service}
docker secret rm \
  http-adapter.credentials \
  hono-adapter-http-vertx-config.yml

docker service rm ${hono.adapter-mqtt.service}
docker secret rm \
  mqtt-adapter.credentials \
  hono-adapter-mqtt-vertx-config.yml

docker service rm ${hono.registration.service}
docker secret rm \
  device-registry-key.pem \
  device-registry-cert.pem \
  hono-service-device-registry-config.yml

docker service rm init-device-registry-data
docker secret rm \
  sandbox-credentials.json \
  sandbox-tenants.json

docker service rm ${hono.auth.service}
docker secret rm \
  sandbox-permissions.json \
  auth-server-key.pem \
  auth-server-cert.pem \
  hono-service-auth-config.yml

docker service rm ${hono.amqp-network.service}
docker secret rm qdrouterd.json

docker service rm ${hono.artemis.service}
docker secret rm \
  broker.xml \
  artemis-users.properties \
  artemis-roles.properties \
  login.config \
  logging.properties \
  artemisKeyStore.p12

docker service rm grafana
docker config rm \
  filesystem-provisioner.yaml \
  overview.json \
  jvm-details.json \
  message-details.json \
  prometheus.yaml

docker service rm ${prometheus.service}
docker secret rm prometheus.yml

docker secret rm trusted-certs.pem

docker network rm $NS

echo ECLIPSE HONO SANDBOX UNDEPLOYED FROM DOCKER SWARM
