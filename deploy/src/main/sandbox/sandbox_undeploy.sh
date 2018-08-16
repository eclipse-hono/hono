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
NS=hono

echo UNDEPLOYING ECLIPSE HONO SANDBOX FROM DOCKER SWARM

docker service rm hono-nginx
docker config rm site.conf

docker service rm hono-adapter-kura
docker secret rm \
  kura-adapter.credentials \
  hono-adapter-kura-config.yml

docker service rm hono-adapter-amqp-vertx
docker secret rm \
  hono-adapter-amqp-vertx-config.yml \
  amqp-adapter.credentials

docker service rm hono-adapter-http-vertx
docker secret rm \
  http-adapter.credentials \
  hono-adapter-http-vertx-config.yml

docker service rm hono-adapter-mqtt-vertx
docker secret rm \
  mqtt-adapter.credentials \
  hono-adapter-mqtt-vertx-config.yml

#docker service rm hono-service-messaging
#docker secret rm \
#  hono-messaging-key.pem \
#  hono-messaging-cert.pem \
#  hono-service-messaging-config.yml

docker service rm hono-service-device-registry
docker secret rm \
  device-registry-key.pem \
  device-registry-cert.pem \
  hono-service-device-registry-config.yml

docker service rm init-device-registry-data
docker secret rm \
  sandbox-credentials.json \
  sandbox-tenants.json

docker service rm hono-service-auth
docker secret rm \
  sandbox-permissions.json \
  auth-server-key.pem \
  auth-server-cert.pem \
  hono-service-auth-config.yml

docker service rm hono-dispatch-router
docker secret rm qdrouterd.json

docker service rm hono-artemis
docker secret rm \
  artemis-broker.xml \
  artemis-bootstrap.xml \
  artemis-users.properties \
  artemis-roles.properties \
  login.config \
  logging.properties \
  artemis.profile

docker service rm grafana
docker config rm \
  filesystem-provisioner.yaml \
  grafana_dashboard.json \
  influxdb.yaml

docker service rm influxdb
docker secret rm influxdb.conf

docker secret rm trusted-certs.pem

docker network rm $NS

echo ECLIPSE HONO SANDBOX UNDEPLOYED FROM DOCKER SWARM
