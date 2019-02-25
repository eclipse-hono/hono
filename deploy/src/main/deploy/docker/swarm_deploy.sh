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
HONO_HOME=$SCRIPTPATH/../../../..
CONFIG=$SCRIPTPATH/../../config
CERTS=$CONFIG/hono-demo-certs-jar
NS=hono
CREATE_OPTIONS="-l project=$NS --network $NS --detach=false"
DEPLOY_ADAPTERS=YES
COAP_ENABLED=NO

# parse command line switches
for i in "$@"
do
  case $i in
    --no-adapter)
    echo
    echo "Disabling deployment of protocol adapters"
    echo
    DEPLOY_ADAPTERS=NO
    ;;
    --enable-coap)
    if [ $DEPLOY_ADAPTERS = "YES" ] ; then
      COAP_ENABLED=YES
      echo
      echo "Enabling deployment of experimental CoAP protocol adapter"
      echo
    else
      echo
      echo "Cannot enable CoAP adapter if adapters are disabled"
      echo
    fi
    ;;
    *)
      echo "Ignoring unknown option: $i"
    ;;
  esac
done

# --no-adapter deployment, without adapters and optional components (e.g. grafana)
if [ $DEPLOY_ADAPTERS = "NO" ] ; then
  echo DEPLOYING ECLIPSE HONO WITHOUT ADAPTERS TO DOCKER SWARM
else
  echo DEPLOYING ECLIPSE HONO TO DOCKER SWARM
fi

# creating Hono network
docker network create --label project=$NS --driver overlay $NS

docker secret create -l project=$NS trusted-certs.pem $CERTS/trusted-certs.pem

echo
echo Deploying Prometheus ...
docker secret create -l project=$NS prometheus.yml $SCRIPTPATH/prometheus/prometheus.yml
docker service create $CREATE_OPTIONS --name prometheus-operated \
  -p 9090:9090 \
  --limit-memory 512m \
  --secret prometheus.yml \
  --entrypoint "/bin/prometheus" \
  ${prometheus.image.name} \
  --config.file=/run/secrets/prometheus.yml \
  --storage.tsdb.path=/prometheus \
  --storage.tsdb.retention=8h
echo ... done

echo
echo Deploying Grafana ...
docker config create -l project=$NS filesystem-provisioner.yaml $SCRIPTPATH/grafana/provisioning/dashboards/filesystem-provisioner.yaml
docker config create -l project=$NS overview.json $SCRIPTPATH/grafana/dashboard-definitions/overview.json
docker config create -l project=$NS message-details.json $SCRIPTPATH/grafana/dashboard-definitions/message-details.json
docker config create -l project=$NS jvm-details.json $SCRIPTPATH/grafana/dashboard-definitions/jvm-details.json
docker config create -l project=$NS prometheus.yaml $SCRIPTPATH/grafana/provisioning/datasources/prometheus.yaml
docker service create $CREATE_OPTIONS --name grafana -p 3000:3000 \
  --config source=filesystem-provisioner.yaml,target=/etc/grafana/provisioning/dashboards/filesystem-provisioner.yaml \
  --config source=overview.json,target=/etc/grafana/dashboard-definitions/overview.json \
  --config source=jvm-details.json,target=/etc/grafana/dashboard-definitions/jvm-details.json \
  --config source=message-details.json,target=/etc/grafana/dashboard-definitions/message-details.json \
  --config source=prometheus.yaml,target=/etc/grafana/provisioning/datasources/prometheus.yaml \
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
  --limit-memory 512m \
  --entrypoint "/opt/artemis/bin/artemis run xml:/run/secrets/artemis-bootstrap.xml" \
  ${artemis.image.name}
echo ... done

echo
echo Deploying Qpid Dispatch Router ...
if [ $DEPLOY_ADAPTERS = "YES" ]
then
PORT_FORWARDS="-p 15671:5671 -p 15672:5672"
else
PORT_FORWARDS="-p 15671:5671 -p 15672:5672 -p 15673:5673"
fi
docker secret create -l project=$NS qdrouter-key.pem $CERTS/qdrouter-key.pem
docker secret create -l project=$NS qdrouter-cert.pem $CERTS/qdrouter-cert.pem
docker secret create -l project=$NS qdrouterd.json $SCRIPTPATH/qpid/qdrouterd-with-broker.json
docker service create $CREATE_OPTIONS --name hono-dispatch-router ${PORT_FORWARDS} \
  --secret qdrouter-key.pem \
  --secret qdrouter-cert.pem \
  --secret trusted-certs.pem \
  --secret qdrouterd.json \
  --limit-memory 512m \
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
  --limit-memory 196m \
  --env _JAVA_OPTIONS="${default-java-options}" \
  --env SPRING_CONFIG_LOCATION=file:///run/secrets/hono-service-auth-config.yml \
  --env SPRING_PROFILES_ACTIVE=authentication-impl,dev \
  --env LOGGING_CONFIG=classpath:logback-spring.xml \
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
  --limit-memory 256m \
  --env _JAVA_OPTIONS="${default-java-options}" \
  --env SPRING_CONFIG_LOCATION=file:///run/secrets/hono-service-device-registry-config.yml \
  --env LOGGING_CONFIG=classpath:logback-spring.xml \
  --env SPRING_PROFILES_ACTIVE=dev \
  --mount type=volume,source=device-registry,target=/var/lib/hono/device-registry \
  ${docker.image.org-name}/hono-service-device-registry:${project.version}

if [ $DEPLOY_ADAPTERS = "YES" ]
then

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
  --limit-memory 256m \
  --env _JAVA_OPTIONS="${default-java-options}" \
  --env SPRING_CONFIG_LOCATION=file:///run/secrets/hono-adapter-http-vertx-config.yml \
  --env SPRING_PROFILES_ACTIVE=dev \
  --env LOGGING_CONFIG=classpath:logback-spring.xml \
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
  --limit-memory 256m \
  --env _JAVA_OPTIONS="${default-java-options}" \
  --env SPRING_CONFIG_LOCATION=file:///run/secrets/hono-adapter-mqtt-vertx-config.yml \
  --env SPRING_PROFILES_ACTIVE=dev \
  --env LOGGING_CONFIG=classpath:logback-spring.xml \
  ${docker.image.org-name}/hono-adapter-mqtt-vertx:${project.version}
echo ... done

echo
echo Deploying AMQP adapter ...
docker secret create -l project=$NS amqp-adapter-key.pem $CERTS/amqp-adapter-key.pem
docker secret create -l project=$NS amqp-adapter-cert.pem $CERTS/amqp-adapter-cert.pem
docker secret create -l project=$NS amqp-adapter.credentials $SCRIPTPATH/../amqp-adapter.credentials
docker secret create -l project=$NS hono-adapter-amqp-vertx-config.yml $SCRIPTPATH/hono-adapter-amqp-vertx-config.yml
docker service create $CREATE_OPTIONS --name hono-adapter-amqp-vertx -p 5672:5672 -p 5671:5671 \
  --secret amqp-adapter-key.pem \
  --secret amqp-adapter-cert.pem \
  --secret trusted-certs.pem \
  --secret amqp-adapter.credentials \
  --secret hono-adapter-amqp-vertx-config.yml \
  --limit-memory 256m \
  --env _JAVA_OPTIONS="${default-java-options}" \
  --env SPRING_CONFIG_LOCATION=file:///run/secrets/hono-adapter-amqp-vertx-config.yml \
  --env SPRING_PROFILES_ACTIVE=dev \
  --env LOGGING_CONFIG=classpath:logback-spring.xml \
  ${docker.image.org-name}/hono-adapter-amqp-vertx:${project.version}
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
  --limit-memory 256m \
  --env _JAVA_OPTIONS="${default-java-options}" \
  --env SPRING_CONFIG_LOCATION=file:///run/secrets/hono-adapter-kura-config.yml \
  --env SPRING_PROFILES_ACTIVE=dev \
  --env LOGGING_CONFIG=classpath:logback-spring.xml \
  ${docker.image.org-name}/hono-adapter-kura:${project.version}
echo ... done

if [ $COAP_ENABLED = "YES" ]; then
echo
# coap deduplicator requires currently a large heap ;-(
echo Deploying Coap adapter ...
docker secret create -l project=$NS coap-adapter-key.pem $CERTS/coap-adapter-key.pem
docker secret create -l project=$NS coap-adapter-cert.pem $CERTS/coap-adapter-cert.pem
docker secret create -l project=$NS coap-adapter.credentials $SCRIPTPATH/../coap-adapter.credentials
docker secret create -l project=$NS hono-adapter-coap-vertx-config.yml $SCRIPTPATH/hono-adapter-coap-vertx-config.yml
docker service create $CREATE_OPTIONS --name hono-adapter-coap-vertx -p 5683:5683/udp -p 5684:5684/udp \
  --secret coap-adapter-key.pem \
  --secret coap-adapter-cert.pem \
  --secret trusted-certs.pem \
  --secret coap-adapter.credentials \
  --secret hono-adapter-coap-vertx-config.yml \
  --limit-memory 4096m \
  --env _JAVA_OPTIONS="${default-java-options}" \
  --env SPRING_CONFIG_LOCATION=file:///run/secrets/hono-adapter-coap-vertx-config.yml \
  --env SPRING_PROFILES_ACTIVE=dev \
  --env LOGGING_CONFIG=classpath:logback-spring.xml \
  ${docker.image.org-name}/hono-adapter-coap-vertx:${project.version}
echo ... done
fi

fi

echo ECLIPSE HONO DEPLOYED TO DOCKER SWARM
