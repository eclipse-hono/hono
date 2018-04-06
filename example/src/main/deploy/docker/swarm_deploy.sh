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
CREATE_OPTIONS="-l project=$NS --network $NS --detach=false"
DOCKER_IP=$(docker node inspect self --format '{{ .Status.Addr }}')

echo DEPLOYING ECLIPSE HONO TO DOCKER SWARM

# creating Hono network
docker network create --label project=$NS --driver overlay $NS

docker secret create -l project=$NS trusted-certs.pem $CERTS/trusted-certs.pem

echo
echo Deploying Influx DB and Grafana ...
docker secret create -l project=$NS influxdb.conf $SCRIPTPATH/../influxdb.conf
docker service create $CREATE_OPTIONS --name influxdb -p 8086:8086 \
  --secret influxdb.conf \
  influxdb:${influxdb.version} -config /run/secrets/influxdb.conf
docker service create $CREATE_OPTIONS --name grafana -p 3000:3000 grafana/grafana:${grafana.version}
echo ... done

echo
echo Deploying Artemis broker ...
docker secret create -l $NS artemis-broker.xml $SCRIPTPATH/artemis/artemis-broker.xml
docker secret create -l $NS artemis-bootstrap.xml $SCRIPTPATH/artemis/artemis-bootstrap.xml
docker secret create -l $NS artemis-users.properties $SCRIPTPATH/artemis/artemis-users.properties
docker secret create -l $NS artemis-roles.properties $SCRIPTPATH/artemis/artemis-roles.properties
docker secret create -l $NS login.config $SCRIPTPATH/artemis/login.config
docker secret create -l $NS logging.properties $SCRIPTPATH/artemis/logging.properties
docker secret create -l $NS artemis.profile $SCRIPTPATH/artemis/artemis.profile
docker secret create -l $NS artemisKeyStore.p12 $CERTS/artemisKeyStore.p12
docker secret create -l $NS trustStore.jks $CERTS/trustStore.jks
docker service create $CREATE_OPTIONS --name hono-artemis \
  --env ARTEMIS_CONFIGURATION=/run/secrets \
  --secret artemis-broker.xml \
  --secret artemis-bootstrap.xml \
  --secret artemis-users.properties \
  --secret artemis-roles.properties \
  --secret login.config \
  --secret logging.properties \
  --secret artemis.profile \
  --secret artemisKeyStore.p12 \
  --secret trustStore.jks \
  --entrypoint "/opt/artemis/bin/artemis run xml:/run/secrets/artemis-bootstrap.xml" \
  ${artemis.image.name}
echo ... done

echo
echo Deploying Qpid Dispatch Router ...
docker secret create -l project=$NS qdrouter-key.pem $CERTS/qdrouter-key.pem
docker secret create -l project=$NS qdrouter-cert.pem $CERTS/qdrouter-cert.pem
docker secret create -l project=$NS qdrouterd.json $SCRIPTPATH/qpid/qdrouterd-with-broker.json
docker secret create -l project=$NS qdrouter-sasl.conf $SCRIPTPATH/qpid/qdrouter-sasl.conf
docker secret create -l project=$NS qdrouterd.sasldb $SCRIPTPATH/qpid/qdrouterd.sasldb
docker service create $CREATE_OPTIONS --name hono-dispatch-router -p 15671:5671 -p 15672:5672 \
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
docker secret create -l project=$NS auth-server-key.pem $CERTS/auth-server-key.pem
docker secret create -l project=$NS auth-server-cert.pem $CERTS/auth-server-cert.pem
docker secret create -l project=$NS permissions.json $SCRIPTPATH/../example-permissions.json
docker secret create -l project=$NS hono-service-auth-config.yml $SCRIPTPATH/hono-service-auth-config.yml
docker service create $CREATE_OPTIONS --name hono-service-auth \
  --secret auth-server-key.pem \
  --secret auth-server-cert.pem \
  --secret trusted-certs.pem \
  --secret permissions.json \
  --secret hono-service-auth-config.yml \
  --env SPRING_CONFIG_LOCATION=file:///run/secrets/hono-service-auth-config.yml \
  --env SPRING_PROFILES_ACTIVE=authentication-impl,dev \
  --env LOGGING_CONFIG=classpath:logback-spring.xml \
  --env _JAVA_OPTIONS=-Xmx32m \
  ${docker.image.org-name}/hono-service-auth:${project.version}
echo ... done

echo
echo Deploying Device Registry ...
docker volume inspect -f '{{ .Name }}' device-registry 1> /dev/null 2> /dev/null
if [ $? -eq 1 ]
then
  echo "Creating and initializing Docker Volume for Device Registry..."
  docker volume create --label project=$NS device-registry
  docker secret create -l project=$NS example-credentials.json $SCRIPTPATH/../example-credentials.json
  docker secret create -l project=$NS example-tenants.json $SCRIPTPATH/../example-tenants.json
  docker service create --detach=true --name init-device-registry-data \
    --secret example-credentials.json \
    --secret example-tenants.json \
    --mount type=volume,source=device-registry,target=/var/lib/hono/device-registry \
    --restart-condition=none \
    busybox sh -c 'cp -u /run/secrets/example-credentials.json /var/lib/hono/device-registry/credentials.json;cp -u /run/secrets/example-tenants.json /var/lib/hono/device-registry/tenants.json'
fi
docker secret create -l project=$NS device-registry-key.pem $CERTS/device-registry-key.pem
docker secret create -l project=$NS device-registry-cert.pem $CERTS/device-registry-cert.pem
docker secret create -l project=$NS hono-service-device-registry-config.yml $SCRIPTPATH/hono-service-device-registry-config.yml
docker service create $CREATE_OPTIONS --name hono-service-device-registry -p 25671:5671 -p 28080:8080 -p 28443:8443 \
  --secret device-registry-key.pem \
  --secret device-registry-cert.pem \
  --secret auth-server-cert.pem \
  --secret trusted-certs.pem \
  --secret hono-service-device-registry-config.yml \
  --env SPRING_CONFIG_LOCATION=file:///run/secrets/hono-service-device-registry-config.yml \
  --env LOGGING_CONFIG=classpath:logback-spring.xml \
  --env SPRING_PROFILES_ACTIVE=dev \
  --env _JAVA_OPTIONS=-Xmx64m \
  --mount type=volume,source=device-registry,target=/var/lib/hono/device-registry \
  ${docker.image.org-name}/hono-service-device-registry:${project.version}

echo
echo Deploying Hono Messaging ...
docker secret create -l project=$NS hono-messaging-key.pem $CERTS/hono-messaging-key.pem
docker secret create -l project=$NS hono-messaging-cert.pem $CERTS/hono-messaging-cert.pem
docker secret create -l project=$NS hono-service-messaging-config.yml $SCRIPTPATH/hono-service-messaging-config.yml
docker service create $CREATE_OPTIONS --name hono-service-messaging -p 5671:5671 \
  --secret hono-messaging-key.pem \
  --secret hono-messaging-cert.pem \
  --secret auth-server-cert.pem \
  --secret trusted-certs.pem \
  --secret hono-service-messaging-config.yml \
  --env SPRING_CONFIG_LOCATION=file:///run/secrets/hono-service-messaging-config.yml \
  --env LOGGING_CONFIG=classpath:logback-spring.xml \
  --env SPRING_PROFILES_ACTIVE=dev \
  --env _JAVA_OPTIONS=-Xmx196m \
  ${docker.image.org-name}/hono-service-messaging:${project.version}
echo ... done

echo
echo Deploying HTTP adapter ...
docker secret create -l project=$NS http-adapter-key.pem $CERTS/http-adapter-key.pem
docker secret create -l project=$NS http-adapter-cert.pem $CERTS/http-adapter-cert.pem
docker secret create -l project=$NS http-adapter.credentials $SCRIPTPATH/../http-adapter.credentials
docker secret create -l project=$NS hono-adapter-http-vertx-config.yml $SCRIPTPATH/hono-adapter-http-vertx-config.yml
docker service create $CREATE_OPTIONS --name hono-adapter-http-vertx -p 8080:8080 -p 8443:8443 \
  --secret http-adapter-key.pem \
  --secret http-adapter-cert.pem \
  --secret trusted-certs.pem \
  --secret http-adapter.credentials \
  --secret hono-adapter-http-vertx-config.yml \
  --env SPRING_CONFIG_LOCATION=file:///run/secrets/hono-adapter-http-vertx-config.yml \
  --env SPRING_PROFILES_ACTIVE=dev \
  --env LOGGING_CONFIG=classpath:logback-spring.xml \
  --env _JAVA_OPTIONS=-Xmx128m \
  ${docker.image.org-name}/hono-adapter-http-vertx:${project.version}
echo ... done

echo
echo Deploying MQTT adapter ...
docker secret create -l project=$NS mqtt-adapter-key.pem $CERTS/mqtt-adapter-key.pem
docker secret create -l project=$NS mqtt-adapter-cert.pem $CERTS/mqtt-adapter-cert.pem
docker secret create -l project=$NS mqtt-adapter.credentials $SCRIPTPATH/../mqtt-adapter.credentials
docker secret create -l project=$NS hono-adapter-mqtt-vertx-config.yml $SCRIPTPATH/hono-adapter-mqtt-vertx-config.yml
docker service create $CREATE_OPTIONS --name hono-adapter-mqtt-vertx -p 1883:1883 -p 8883:8883 \
  --secret mqtt-adapter-key.pem \
  --secret mqtt-adapter-cert.pem \
  --secret trusted-certs.pem \
  --secret mqtt-adapter.credentials \
  --secret hono-adapter-mqtt-vertx-config.yml \
  --env SPRING_CONFIG_LOCATION=file:///run/secrets/hono-adapter-mqtt-vertx-config.yml \
  --env SPRING_PROFILES_ACTIVE=dev \
  --env LOGGING_CONFIG=classpath:logback-spring.xml \
  --env _JAVA_OPTIONS=-Xmx128m \
  ${docker.image.org-name}/hono-adapter-mqtt-vertx:${project.version}
echo ... done

echo
echo Deploying Kura adapter ...
docker secret create -l project=$NS kura-adapter-key.pem $CERTS/kura-adapter-key.pem
docker secret create -l project=$NS kura-adapter-cert.pem $CERTS/kura-adapter-cert.pem
docker secret create -l project=$NS kura-adapter.credentials $SCRIPTPATH/../kura-adapter.credentials
docker secret create -l project=$NS hono-adapter-kura-config.yml $SCRIPTPATH/hono-adapter-kura-config.yml
docker service create $CREATE_OPTIONS --name hono-adapter-kura -p 1884:1883 -p 8884:8883 \
  --secret kura-adapter-key.pem \
  --secret kura-adapter-cert.pem \
  --secret trusted-certs.pem \
  --secret kura-adapter.credentials \
  --secret hono-adapter-kura-config.yml \
  --env SPRING_CONFIG_LOCATION=file:///run/secrets/hono-adapter-kura-config.yml \
  --env SPRING_PROFILES_ACTIVE=prod \
  --env LOGGING_CONFIG=classpath:logback-spring.xml \
  --env _JAVA_OPTIONS=-Xmx128m \
  ${docker.image.org-name}/hono-adapter-kura:${project.version}
echo ... done

echo
echo Configuring Grafana ...
chmod +x $SCRIPTPATH/../configure_grafana.sh
$SCRIPTPATH/../configure_grafana.sh ${DOCKER_IP}
echo ... done

echo ECLIPSE HONO DEPLOYED TO DOCKER SWARM
