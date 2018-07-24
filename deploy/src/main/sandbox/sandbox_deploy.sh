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

# This script deploys Hono to the Eclipse Foundation's sandbox at hono.eclipse.org
# Some of the services require a valid certificate and private key to be
# created as secrets hono.eclipse.org-cert.pem and hono.eclipse.org-key.pem
# respectively.

# Absolute path this script is in
SCRIPTPATH="$(cd "$(dirname "$0")" && pwd -P)"
CONFIG=$SCRIPTPATH/../config
CERTS=$CONFIG/hono-demo-certs-jar
NS=hono
CREATE_OPTIONS="-l project=$NS --network $NS --detach=false"
HONO_VERSION="${project.version}"

if [ "" != ""$1 ]
then
  HONO_VERSION=$1
fi

echo "DEPLOYING ECLIPSE HONO ($HONO_VERSION) SANDBOX TO DOCKER SWARM"
echo

# creating Hono network
docker network create --label project=$NS --driver overlay $NS

docker secret create -l project=$NS trusted-certs.pem $CERTS/trusted-certs.pem

echo
echo Deploying Influx DB and Grafana ...
docker secret create -l project=$NS influxdb.conf $SCRIPTPATH/../deploy/influxdb.conf
docker service create $CREATE_OPTIONS --name influxdb \
  --secret influxdb.conf \
  --limit-memory 128m \
  influxdb:${influxdb.version} -config /run/secrets/influxdb.conf

docker config create -l project=$NS filesystem-provisioner.yaml $SCRIPTPATH/grafana/provisioning/dashboards/filesystem-provisioner.yaml
docker config create -l project=$NS grafana_dashboard.json $SCRIPTPATH/grafana/dashboard-definitions/grafana_dashboard.json
docker config create -l project=$NS influxdb.yaml $SCRIPTPATH/grafana/provisioning/datasources/influxdb.yaml
docker service create $CREATE_OPTIONS --name grafana -p 3001:3000 \
  --config source=filesystem-provisioner.yaml,target=/etc/grafana/provisioning/dashboards/filesystem-provisioner.yaml \
  --config source=grafana_dashboard.json,target=/etc/grafana/dashboard-definitions/grafana_dashboard.json \
  --config source=influxdb.yaml,target=/etc/grafana/provisioning/datasources/influxdb.yaml \
  --limit-memory 64m \
  grafana/grafana:${grafana.version}
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
docker service create $CREATE_OPTIONS --name hono-artemis \
  --env ARTEMIS_CONFIGURATION=/run/secrets \
  --secret artemis-broker.xml \
  --secret artemis-bootstrap.xml \
  --secret artemis-users.properties \
  --secret artemis-roles.properties \
  --secret login.config \
  --secret logging.properties \
  --secret artemis.profile \
  --limit-memory 512m \
  --entrypoint "/opt/artemis/bin/artemis run xml:/run/secrets/artemis-bootstrap.xml" \
  ${artemis.image.name}
echo ... done

echo
echo Deploying Qpid Dispatch Router ...
docker secret create -l project=$NS qdrouterd.json $SCRIPTPATH/qpid/sandbox-qdrouterd.json
docker service create $CREATE_OPTIONS --name hono-dispatch-router -p 15671:5671 -p 15672:5672 \
  --secret hono.eclipse.org-key.pem \
  --secret hono.eclipse.org-cert.pem \
  --secret trusted-certs.pem \
  --secret qdrouterd.json \
  --limit-memory 256m \
  ${dispatch-router.image.name} /sbin/qdrouterd -c /run/secrets/qdrouterd.json
echo ... done

echo
echo Deploying Authentication Server ...
docker secret create -l project=$NS auth-server-key.pem $CERTS/auth-server-key.pem
docker secret create -l project=$NS auth-server-cert.pem $CERTS/auth-server-cert.pem
docker secret create -l project=$NS hono-service-auth-config.yml $SCRIPTPATH/hono-service-auth-config.yml
docker secret create -l project=$NS sandbox-permissions.json $SCRIPTPATH/sandbox-permissions.json
docker service create $CREATE_OPTIONS --name hono-service-auth \
  --secret auth-server-key.pem \
  --secret auth-server-cert.pem \
  --secret trusted-certs.pem \
  --secret sandbox-permissions.json \
  --secret hono-service-auth-config.yml \
  --limit-memory 196m \
  --env _JAVA_OPTIONS=-Xmx150m \
  --env SPRING_CONFIG_LOCATION=file:///run/secrets/hono-service-auth-config.yml \
  --env SPRING_PROFILES_ACTIVE=authentication-impl,prod \
  --env LOGGING_CONFIG=classpath:logback-spring.xml \
  --mount type=volume,source=hono-extensions,target=/opt/hono/extensions,readonly \
  ${docker.image.org-name}/hono-service-auth:$HONO_VERSION
echo ... done

echo
echo Deploying Device Registry ...
docker volume inspect -f '{{ .Name }}' device-registry 1> /dev/null 2> /dev/null
if [ $? -eq 1 ]
then
  echo "Creating and initializing Docker Volume for Device Registry..."
  # create volume for persisting Device Registry data
  docker volume create --label project=$NS device-registry

  # initialize Device Registry volume with default credentials
  docker secret create -l project=$NS sandbox-credentials.json $SCRIPTPATH/sandbox-credentials.json
  docker secret create -l project=$NS sandbox-tenants.json $SCRIPTPATH/sandbox-tenants.json
  docker service create --detach=true --name init-device-registry-data \
    --secret sandbox-credentials.json \
    --secret sandbox-tenants.json \
    --mount type=volume,source=device-registry,target=/var/lib/hono/device-registry \
    --restart-condition=none \
    busybox sh -c 'cp -u /run/secrets/sandbox-credentials.json /var/lib/hono/device-registry/credentials.json; cp -u /run/secrets/sandbox-tenants.json /var/lib/hono/device-registry/tenants.json'
fi
docker secret create -l project=$NS device-registry-key.pem $CERTS/device-registry-key.pem
docker secret create -l project=$NS device-registry-cert.pem $CERTS/device-registry-cert.pem
docker secret create -l project=$NS hono-service-device-registry-config.yml $SCRIPTPATH/hono-service-device-registry-config.yml
docker service create $CREATE_OPTIONS --name hono-service-device-registry -p 25671:5671 -p 28080:8080 -p 28443:8443 \
  --secret device-registry-key.pem \
  --secret device-registry-cert.pem \
  --secret hono.eclipse.org-key.pem \
  --secret hono.eclipse.org-cert.pem \
  --secret auth-server-cert.pem \
  --secret trusted-certs.pem \
  --secret hono-service-device-registry-config.yml \
  --limit-memory 256m \
  --env _JAVA_OPTIONS=-Xmx180m \
  --env SPRING_CONFIG_LOCATION=file:///run/secrets/hono-service-device-registry-config.yml \
  --env LOGGING_CONFIG=classpath:logback-spring.xml \
  --env SPRING_PROFILES_ACTIVE=dev \
  --mount type=volume,source=device-registry,target=/var/lib/hono/device-registry \
  --mount type=volume,source=hono-extensions,target=/opt/hono/extensions,readonly \
  ${docker.image.org-name}/hono-service-device-registry:$HONO_VERSION
echo ... done

# For the time being, we do not deploy Hono Messaging anymore because
# all protocol adapters connect to Dispatch Router directly.
# However, once we support custom adapters with the sandbox, we might
# want to deploy Hono Messaging again.

#echo
#echo Deploying Hono Messaging ...
#docker secret create -l project=$NS hono-messaging-key.pem $CERTS/hono-messaging-key.pem
#docker secret create -l project=$NS hono-messaging-cert.pem $CERTS/hono-messaging-cert.pem
#docker secret create -l project=$NS hono-service-messaging-config.yml $SCRIPTPATH/hono-service-messaging-config.yml
#docker service create $CREATE_OPTIONS --name hono-service-messaging \
#  --secret hono-messaging-key.pem \
#  --secret hono-messaging-cert.pem \
#  --secret auth-server-cert.pem \
#  --secret trusted-certs.pem \
#  --secret hono-service-messaging-config.yml \
#  --env SPRING_CONFIG_LOCATION=file:///run/secrets/hono-service-messaging-config.yml \
#  --env LOGGING_CONFIG=classpath:logback-spring.xml \
#  --env SPRING_PROFILES_ACTIVE=prod \
#  --env _JAVA_OPTIONS=-Xmx196m \
#  ${docker.image.org-name}/hono-service-messaging:$HONO_VERSION
#echo ... done

echo
echo Deploying HTTP adapter ...
docker secret create -l project=$NS http-adapter.credentials $SCRIPTPATH/../deploy/http-adapter.credentials
docker secret create -l project=$NS hono-adapter-http-vertx-config.yml $SCRIPTPATH/hono-adapter-http-vertx-config.yml
docker service create $CREATE_OPTIONS --name hono-adapter-http-vertx -p 8080:8080 -p 8443:8443 \
  --secret hono.eclipse.org-key.pem \
  --secret hono.eclipse.org-cert.pem \
  --secret http-adapter.credentials \
  --secret hono-adapter-http-vertx-config.yml \
  --limit-memory 384m \
  --env _JAVA_OPTIONS=-Xmx256m \
  --env SPRING_CONFIG_LOCATION=file:///run/secrets/hono-adapter-http-vertx-config.yml \
  --env SPRING_PROFILES_ACTIVE=trace \
  --env LOGGING_CONFIG=classpath:logback-spring.xml \
  --mount type=volume,source=hono-extensions,target=/opt/hono/extensions,readonly \
  ${docker.image.org-name}/hono-adapter-http-vertx:$HONO_VERSION
echo ... done

echo
echo Deploying MQTT adapter ...
docker secret create -l project=$NS mqtt-adapter.credentials $SCRIPTPATH/../deploy/mqtt-adapter.credentials
docker secret create -l project=$NS hono-adapter-mqtt-vertx-config.yml $SCRIPTPATH/hono-adapter-mqtt-vertx-config.yml
docker service create $CREATE_OPTIONS --name hono-adapter-mqtt-vertx -p 1883:1883 -p 8883:8883 \
  --secret hono.eclipse.org-key.pem \
  --secret hono.eclipse.org-cert.pem \
  --secret mqtt-adapter.credentials \
  --secret hono-adapter-mqtt-vertx-config.yml \
  --limit-memory 384m \
  --env _JAVA_OPTIONS=-Xmx256m \
  --env SPRING_CONFIG_LOCATION=file:///run/secrets/hono-adapter-mqtt-vertx-config.yml \
  --env SPRING_PROFILES_ACTIVE=dev \
  --env LOGGING_CONFIG=classpath:logback-spring.xml \
  --mount type=volume,source=hono-extensions,target=/opt/hono/extensions,readonly \
  ${docker.image.org-name}/hono-adapter-mqtt-vertx:$HONO_VERSION
echo ... done

echo
echo Deploying Kura adapter ...
docker secret create -l project=$NS kura-adapter.credentials $SCRIPTPATH/../deploy/kura-adapter.credentials
docker secret create -l project=$NS hono-adapter-kura-config.yml $SCRIPTPATH/hono-adapter-kura-config.yml
docker service create $CREATE_OPTIONS --name hono-adapter-kura -p 1884:1883 -p 8884:8883 \
  --secret hono.eclipse.org-key.pem \
  --secret hono.eclipse.org-cert.pem \
  --secret kura-adapter.credentials \
  --secret hono-adapter-kura-config.yml \
  --limit-memory 384m \
  --env _JAVA_OPTIONS=-Xmx256m \
  --env SPRING_CONFIG_LOCATION=file:///run/secrets/hono-adapter-kura-config.yml \
  --env SPRING_PROFILES_ACTIVE=prod \
  --env LOGGING_CONFIG=classpath:logback-spring.xml \
  --mount type=volume,source=hono-extensions,target=/opt/hono/extensions,readonly \
  ${docker.image.org-name}/hono-adapter-kura:$HONO_VERSION
echo ... done

echo
echo "Deploying NGINX for redirecting to Hono web site"
docker config create -l project=$NS site.conf $SCRIPTPATH/nginx.conf
# we bind mount the directory that is used by Certbot to
# get/update the Let's Encrypt certificate
docker service create --detach=false --name hono-nginx -p 80:80 \
  --limit-memory 32m \
  --config source=site.conf,target=/etc/nginx/conf.d/site.conf,mode=0440 \
  --mount type=bind,source=/var/www/certbot,target=/var/www/letsencrypt \
  nginx:1.13
echo ... done

echo ECLIPSE HONO SANDBOX DEPLOYED TO DOCKER SWARM
echo
