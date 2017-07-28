#!/bin/sh

# Copyright (c) 2017 Bosch Software Innovations GmbH and others.
#
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the Eclipse Public License v1.0
# which accompanies this distribution, and is available at
# http://www.eclipse.org/legal/epl-v10.html
#
# Contributors:
#    Bosch Software Innovations GmbH - initial creation

# Absolute path this script is in
SCRIPTPATH="$(cd "$(dirname "$0")" && pwd -P)"
HONO_HOME=$SCRIPTPATH/../../../..
CONFIG=$SCRIPTPATH/../../config
CERTS=$CONFIG/hono-demo-certs-jar
NS=hono

echo DEPLOYING ECLIPSE HONO TO DOCKER SWARM

# creating Hono network
docker network create --driver overlay $NS

docker secret create -l $NS trusted-certs.pem $CERTS/trusted-certs.pem

echo
echo Deploying Influx DB and Grafana ...
docker service create -l $NS --detach --name influxdb --network $NS \
  --env INFLUXDB_ADMIN_ENABLED=true \
  --env INFLUXDB_GRAPHITE_0_ENABLED=true \
  --env INFLUXDB_GRAPHITE_0_TEMPLATES='*.counter.hono.messaging.receivers.upstream.links.* host.measurement.measurement.measurement.measurement.measurement.measurement.type.tenant.measurement*, *.counter.hono.messaging.senders.downstream.* host.measurement.measurement.measurement.measurement.measurement.type.tenant.measurement*, *.gauge.hono.messaging.link.downstream.credits.* host.measurement.measurement.measurement.measurement.measurement.measurement.type.tenant, *.counter.hono.messaging.messages.* host.measurement.measurement.measurement.measurement.type.tenant.measurement*, *.meter.hono.messaging.messages.* host.measurement.measurement.measurement.measurement.type.tenant.measurement*, host.measurement*' \
  influxdb:${influxdb.version}
docker service create -l $NS --detach --name grafana --network $NS -p 3000:3000 eclipsehono/grafana:${project.version}
echo ... done

echo
echo Deploying Artemis broker ...
docker service create -l $NS --detach --name hono-artemis --network $NS eclipsehono/hono-artemis:${project.version}
echo ... done

echo
echo Deploying Qpid Dispatch Router ...
docker secret create -l $NS qdrouter-key.pem $CERTS/qdrouter-key.pem
docker secret create -l $NS qdrouter-cert.pem $CERTS/qdrouter-cert.pem
docker secret create -l $NS qdrouterd.json $CONFIG/hono-dispatch-router-jar/qpid/qdrouterd-with-broker.json
docker secret create -l $NS qdrouter-sasl.conf $CONFIG/hono-dispatch-router-jar/sasl/qdrouter-sasl.conf
docker secret create -l $NS qdrouterd.sasldb $CONFIG/hono-dispatch-router-jar/sasl/qdrouterd.sasldb
docker service create -l $NS --detach --name hono-dispatch-router --network $NS -p 15671:5671 -p 15672:5672 \
  --secret qdrouter-key.pem \
  --secret qdrouter-cert.pem \
  --secret trusted-certs.pem \
  --secret qdrouterd.json \
  --secret qdrouter-sasl.conf \
  --secret qdrouterd.sasldb \
  ${dispatch-router.image.name} /sbin/qdrouterd -c /run/secrets/qdrouterd.json
echo ... done

echo
echo Deploying Authentication Server ...
docker secret create -l $NS auth-server-key.pem $CERTS/auth-server-key.pem
docker secret create -l $NS auth-server-cert.pem $CERTS/auth-server-cert.pem
docker secret create -l $NS hono-service-auth-config.yml $CONFIG/hono-service-auth-config.yml
docker service create -l $NS --detach --name hono-service-auth --network $NS \
  --secret auth-server-key.pem \
  --secret auth-server-cert.pem \
  --secret trusted-certs.pem \
  --secret hono-service-auth-config.yml \
  --env SPRING_CONFIG_LOCATION=file:///run/secrets/hono-service-auth-config.yml \
  --env SPRING_PROFILES_ACTIVE=authentication-impl,dev \
  --env LOGGING_CONFIG=classpath:logback-spring.xml \
  eclipsehono/hono-service-auth:${project.version}
echo ... done

echo
echo Deploying Device Registry ...
docker secret create -l $NS device-registry-key.pem $CERTS/device-registry-key.pem
docker secret create -l $NS device-registry-cert.pem $CERTS/device-registry-cert.pem
docker secret create -l $NS hono-service-device-registry-config.yml $CONFIG/hono-service-device-registry-config.yml
docker service create -l $NS --detach --name hono-service-device-registry --network $NS \
  --secret device-registry-key.pem \
  --secret device-registry-cert.pem \
  --secret auth-server-cert.pem \
  --secret trusted-certs.pem \
  --secret hono-service-device-registry-config.yml \
  --env SPRING_CONFIG_LOCATION=file:///run/secrets/hono-service-device-registry-config.yml \
  --env LOGGING_CONFIG=classpath:logback-spring.xml \
  --env SPRING_PROFILES_ACTIVE=dev \
  eclipsehono/hono-service-device-registry:${project.version}
echo ... done

echo
echo Deploying Hono Messaging ...
docker secret create -l $NS hono-messaging-key.pem $CERTS/hono-messaging-key.pem
docker secret create -l $NS hono-messaging-cert.pem $CERTS/hono-messaging-cert.pem
docker secret create -l $NS hono-service-messaging-config.yml $CONFIG/hono-service-messaging-config.yml
docker service create -l $NS --detach --name hono-service-messaging --network $NS \
  --secret hono-messaging-key.pem \
  --secret hono-messaging-cert.pem \
  --secret auth-server-cert.pem \
  --secret trusted-certs.pem \
  --secret hono-service-messaging-config.yml \
  --env SPRING_CONFIG_LOCATION=file:///run/secrets/hono-service-messaging-config.yml \
  --env LOGGING_CONFIG=classpath:logback-spring.xml \
  --env SPRING_PROFILES_ACTIVE=dev \
  eclipsehono/hono-service-messaging:${project.version}
echo ... done

echo
echo Deploying HTTP REST adapter ...
docker secret create -l $NS rest-adapter-key.pem $CERTS/rest-adapter-key.pem
docker secret create -l $NS rest-adapter-cert.pem $CERTS/rest-adapter-cert.pem
docker secret create -l $NS hono-adapter-rest-vertx-config.yml $CONFIG/hono-adapter-rest-vertx-config.yml
docker service create -l $NS --detach --name hono-adapter-rest-vertx --network $NS -p 8080:8080 -p 8443:8443 \
  --secret rest-adapter-key.pem \
  --secret rest-adapter-cert.pem \
  --secret trusted-certs.pem \
  --secret hono-adapter-rest-vertx-config.yml \
  --env SPRING_CONFIG_LOCATION=file:///run/secrets/hono-adapter-rest-vertx-config.yml \
  --env SPRING_PROFILES_ACTIVE=dev \
  --env LOGGING_CONFIG=classpath:logback-spring.xml \
  eclipsehono/hono-adapter-rest-vertx:${project.version}
echo ... done

echo
echo Deploying MQTT adapter ...
docker secret create -l $NS mqtt-adapter-key.pem $CERTS/mqtt-adapter-key.pem
docker secret create -l $NS mqtt-adapter-cert.pem $CERTS/mqtt-adapter-cert.pem
docker secret create -l $NS hono-adapter-mqtt-vertx-config.yml $CONFIG/hono-adapter-mqtt-vertx-config.yml
docker service create -l $NS --detach --name hono-adapter-mqtt-vertx --network $NS -p 1883:1883 -p 8883:8883 \
  --secret mqtt-adapter-key.pem \
  --secret mqtt-adapter-cert.pem \
  --secret trusted-certs.pem \
  --secret hono-adapter-mqtt-vertx-config.yml \
  --env SPRING_CONFIG_LOCATION=file:///run/secrets/hono-adapter-mqtt-vertx-config.yml \
  --env SPRING_PROFILES_ACTIVE=dev \
  --env LOGGING_CONFIG=classpath:logback-spring.xml \
  eclipsehono/hono-adapter-mqtt-vertx:${project.version}
echo ... done

echo ECLIPSE HONO DEPLOYED TO DOCKER SWARM
