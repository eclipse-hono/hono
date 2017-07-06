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

echo UNDEPLOYING ECLIPSE HONO FROM DOCKER SWARM

docker service rm \
  hono-adapter-mqtt-vertx \
  hono-adapter-rest-vertx \
  hono-service-messaging \
  hono-service-device-registry

docker service rm hono-service-auth
docker secret rm \
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

docker service rm \
  hono-artemis \
  grafana \
  influxdb

docker secret rm trusted-certs.pem

docker network rm $NS

echo ECLIPSE HONO UNDEPLOYED FROM DOCKER SWARM
