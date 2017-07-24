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
docker service create -l $NS --detach --name influxdb --network $NS -p 2003:2003 -p 8083:8083 -p 8086:8086 \
  --env INFLUXDB_GRAPHITE_ENABLED=true \
  --env INFLUXDB_ADMIN_ENABLED=true \
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
docker service create -l $NS --detach --name hono-service-auth --network $NS \
  --env HONO_AUTH_AMQP_BIND_ADDRESS=0.0.0.0 \
  --env HONO_AUTH_AMQP_KEY_PATH=/etc/hono/certs/auth-server-key.pem \
  --env HONO_AUTH_AMQP_CERT_PATH=/etc/hono/certs/auth-server-cert.pem \
  --env HONO_AUTH_AMQP_MAX_INSTANCES=1 \
  --env LOGGING_CONFIG=classpath:logback-spring.xml \
  --env SPRING_PROFILES_ACTIVE=authentication-impl,dev \
  eclipsehono/hono-service-auth:${project.version}
echo ... done

echo
echo Deploying Device Registry ...
docker service create -l $NS --detach --name hono-service-device-registry --network $NS \
  --secret trusted-certs.pem \
  --env HONO_AUTH_HOST=hono-service-auth.hono \
  --env HONO_AUTH_TRUST_STORE_PATH=/run/secrets/trusted-certs.pem \
  --env HONO_AUTH_NAME='Hono Device Registry' \
  --env HONO_AUTH_VALIDATION_CERT_PATH=/etc/hono/certs/auth-server-cert.pem \
  --env HONO_REGISTRY_AMQP_BIND_ADDRESS=0.0.0.0 \
  --env HONO_REGISTRY_AMQP_KEY_PATH=/etc/hono/certs/device-registry-key.pem \
  --env HONO_REGISTRY_AMQP_CERT_PATH=/etc/hono/certs/device-registry-cert.pem \
  --env HONO_REGISTRY_AMQP_INSECURE_PORT_ENABLED=false \
  --env HONO_REGISTRY_AMQP_MAX_INSTANCES=1 \
  --env HONO_REGISTRY_SVC_SIGNING_SHARED_SECRET=g#aWO!BUm7aj*#%X*VGXKFhxkhNrMNj0 \
  --env LOGGING_CONFIG=classpath:logback-spring.xml \
  --env SPRING_PROFILES_ACTIVE=default,dev \
  eclipsehono/hono-service-device-registry:${project.version}
echo ... done

echo
echo Deploying Hono Messaging ...
docker service create -l $NS --detach --name hono-service-messaging --network $NS \
  --secret trusted-certs.pem \
  --env HONO_AUTH_HOST=hono-service-auth.hono \
  --env HONO_AUTH_TRUST_STORE_PATH=/run/secrets/trusted-certs.pem \
  --env HONO_AUTH_NAME='Hono Messaging' \
  --env HONO_AUTH_VALIDATION_CERT_PATH=/etc/hono/certs/auth-server-cert.pem \
  --env HONO_DOWNSTREAM_HOST=hono-dispatch-router.hono \
  --env HONO_DOWNSTREAM_PORT=5673 \
  --env HONO_DOWNSTREAM_KEY_PATH=/etc/hono/certs/hono-messaging-key.pem \
  --env HONO_DOWNSTREAM_CERT_PATH=/etc/hono/certs/hono-messaging-cert.pem \
  --env HONO_DOWNSTREAM_TRUST_STORE_PATH=/run/secrets/trusted-certs.pem \
  --env HONO_MESSAGING_BIND_ADDRESS=0.0.0.0 \
  --env HONO_MESSAGING_KEY_PATH=/etc/hono/certs/hono-messaging-key.pem \
  --env HONO_MESSAGING_CERT_PATH=/etc/hono/certs/hono-messaging-cert.pem \
  --env HONO_MESSAGING_INSECURE_PORT_ENABLED=false \
  --env HONO_MESSAGING_MAX_INSTANCES=1 \
  --env HONO_MESSAGING_VALIDATION_SHARED_SECRET=g#aWO!BUm7aj*#%X*VGXKFhxkhNrMNj0 \
  --env HONO_METRIC_REPORTER_GRAPHITE_ACTIVE=true \
  --env HONO_METRIC_REPORTER_GRAPHITE_HOST=influxdb.hono \
  --env HONO_METRIC_REPORTER_GRAPHITE_PORT=2003 \
  --env LOGGING_CONFIG=classpath:logback-spring.xml \
  --env SPRING_PROFILES_ACTIVE=default,dev \
  eclipsehono/hono-service-messaging:${project.version}
echo ... done

echo
echo Deploying HTTP REST adapter ...
docker service create -l $NS --detach --name hono-adapter-rest-vertx --network $NS -p 8080:8080 -p 8443:8443 \
  --secret trusted-certs.pem \
  --env HONO_MESSAGING_NAME='Hono REST Adapter' \
  --env HONO_MESSAGING_HOST=hono-service-messaging.hono \
  --env HONO_MESSAGING_PORT=5671 \
  --env HONO_MESSAGING_USERNAME=rest-adapter@HONO \
  --env HONO_MESSAGING_PASSWORD=rest-secret \
  --env HONO_MESSAGING_TRUST_STORE_PATH=/run/secrets/trusted-certs.pem \
  --env HONO_REGISTRATION_NAME='Hono REST Adapter' \
  --env HONO_REGISTRATION_HOST=hono-service-device-registry.hono \
  --env HONO_REGISTRATION_PORT=5671 \
  --env HONO_REGISTRATION_USERNAME=rest-adapter@HONO \
  --env HONO_REGISTRATION_PASSWORD=rest-secret \
  --env HONO_REGISTRATION_TRUST_STORE_PATH=/run/secrets/trusted-certs.pem \
  --env HONO_HTTP_BIND_ADDRESS=0.0.0.0 \
  --env HONO_HTTP_INSECURE_PORT_BIND_ADDRESS=0.0.0.0 \
  --env HONO_HTTP_INSECURE_PORT_ENABLED=true \
  --env HONO_HTTP_KEY_PATH=/etc/hono/certs/rest-adapter-key.pem \
  --env HONO_HTTP_CERT_PATH=/etc/hono/certs/rest-adapter-cert.pem \
  --env HONO_HTTP_MAX_INSTANCES=1 \
  --env SPRING_PROFILES_ACTIVE=dev \
  --env LOGGING_CONFIG=classpath:logback-spring.xml \
  eclipsehono/hono-adapter-rest-vertx:${project.version}
echo ... done

echo
echo Deploying MQTT adapter ...
docker service create -l $NS --detach --name hono-adapter-mqtt-vertx --network $NS -p 1883:1883 -p 8883:8883 \
  --secret trusted-certs.pem \
  --env HONO_MESSAGING_NAME='Hono MQTT Adapter' \
  --env HONO_MESSAGING_HOST=hono-service-messaging.hono \
  --env HONO_MESSAGING_PORT=5671 \
  --env HONO_MESSAGING_USERNAME=mqtt-adapter@HONO \
  --env HONO_MESSAGING_PASSWORD=mqtt-secret \
  --env HONO_MESSAGING_TRUST_STORE_PATH=/run/secrets/trusted-certs.pem \
  --env HONO_REGISTRATION_NAME='Hono MQTT Adapter' \
  --env HONO_REGISTRATION_HOST=hono-service-device-registry.hono \
  --env HONO_REGISTRATION_PORT=5671 \
  --env HONO_REGISTRATION_USERNAME=mqtt-adapter@HONO \
  --env HONO_REGISTRATION_PASSWORD=mqtt-secret \
  --env HONO_REGISTRATION_TRUST_STORE_PATH=/run/secrets/trusted-certs.pem \
  --env HONO_MQTT_BIND_ADDRESS=0.0.0.0 \
  --env HONO_MQTT_INSECURE_PORT_BIND_ADDRESS=0.0.0.0 \
  --env HONO_MQTT_INSECURE_PORT_ENABLED=true \
  --env HONO_MQTT_KEY_PATH=/etc/hono/certs/mqtt-adapter-key.pem \
  --env HONO_MQTT_CERT_PATH=/etc/hono/certs/mqtt-adapter-cert.pem \
  --env HONO_MQTT_MAX_INSTANCES=1 \
  --env SPRING_PROFILES_ACTIVE=dev \
  --env LOGGING_CONFIG=classpath:logback-spring.xml \
  eclipsehono/hono-adapter-mqtt-vertx:${project.version}
echo ... done

echo ECLIPSE HONO DEPLOYED TO DOCKER SWARM
