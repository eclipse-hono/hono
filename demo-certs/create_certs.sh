#!/bin/bash
#*******************************************************************************
# Copyright (c) 2016, 2022 Contributors to the Eclipse Foundation
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

# A simple shell script for generating example certificates to be used with Hono.

DIR=certs
HONO_TRUST_STORE=trustStore.jks
HONO_TRUST_STORE_PWD=honotrust
HONO_TRUST_STORE_PWD_FILE=trust-store-password
AUTH_SERVER_KEY_STORE=authServerKeyStore.p12
AUTH_SERVER_KEY_STORE_PWD=authkeys
CMD_ROUTER_KEY_STORE=commandRouterKeyStore.p12
CMD_ROUTER_KEY_STORE_PWD=commandrouterkeys
DEVREG_SERVER_KEY_STORE=deviceRegistryKeyStore.p12
DEVREG_SERVER_KEY_STORE_PWD=deviceregistrykeys
MQTT_ADAPTER_KEY_STORE=mqttKeyStore.p12
MQTT_ADAPTER_KEY_STORE_PWD=mqttkeys
HTTP_ADAPTER_KEY_STORE=httpKeyStore.p12
HTTP_ADAPTER_KEY_STORE_PWD=httpkeys
LORA_ADAPTER_KEY_STORE=loraKeyStore.p12
LORA_ADAPTER_KEY_STORE_PWD=lorakeys
ARTEMIS_KEY_STORE=artemisKeyStore.p12
ARTEMIS_KEY_STORE_PWD=artemiskeys
COAP_ADAPTER_KEY_STORE=coapKeyStore.p12
COAP_ADAPTER_KEY_STORE_PWD=coapkeys
AMQP_ADAPTER_KEY_STORE=amqpKeyStore.p12
AMQP_ADAPTER_KEY_STORE_PWD=amqpkeys
EXAMPLE_GATEWAY_KEY_STORE=exampleGatewayKeyStore.p12
EXAMPLE_GATEWAY_KEY_STORE_PWD=examplegatewaykeys
# set to either EC or RSA
KEY_ALG=EC

if [ -z "${JAVA_HOME}" ]
then
   JAVA_KEY_TOOL=keytool
else
   JAVA_KEY_TOOL=${JAVA_HOME}/bin/keytool
fi

function create_key { 

  echo ""
  if [ $KEY_ALG == "EC" ]
  then
    openssl ecparam -name secp384r1 -genkey -noout | openssl pkcs8 -topk8 -nocrypt -inform PEM -outform PEM -out "$DIR/$1"
  else
    openssl genrsa 4096 | openssl pkcs8 -topk8 -nocrypt -inform PEM -outform PEM -out "$DIR/$1"
  fi

  if [ $? -ne 0 ]; then
    error=$?
    echo "failed to create keys"
    exit $error
  fi
}

#
# parameters:
# name of key/cert
# key store name (optional)
# key store password (optional)
function create_cert {

  echo ""
  echo "creating $1 key and certificate"
  create_key "$1-key.pem"
  openssl req -config ca_opts -new -key "$DIR/$1-key.pem" -subj "/C=CA/L=Ottawa/O=Eclipse IoT/OU=Hono/CN=$1" | \
    openssl x509 -req -extfile ca_opts -extensions "req_ext_$1" -out "$DIR/$1.pem" -days 365 -CA "$DIR/ca-cert.pem" -CAkey "$DIR/ca-key.pem" -CAcreateserial
  cat "$DIR/$1.pem" "$DIR/ca-cert.pem" > "$DIR/$1-cert.pem" && rm "$DIR/$1.pem"
  if [ "$2" ]
  then
    echo "adding key/cert for $1 to key store $DIR/$2"
    openssl pkcs12 -export -inkey "$DIR/$1-key.pem" -in "$DIR/$1-cert.pem" -out "$DIR/$2" -name "$1" -password "pass:$3"
  fi

  if [ $? -ne 0 ]; then
    error=$?
    echo "failed to create certificate"
    exit $error
  fi
}

function create_client_cert {
  echo ""
  echo "creating client key and certificate for device $1"
  create_key "device-$1-key.pem"
  openssl req -new -key "$DIR/device-$1-key.pem" -subj "/C=CA/L=Ottawa/O=Eclipse IoT/OU=Hono/CN=Device $1" | \
    openssl x509 -req -out "$DIR/device-$1-cert.pem" -days 365 -CA $DIR/default_tenant-cert.pem -CAkey $DIR/default_tenant-key.pem \
      -extfile client_ext -extensions req_ext
  SUBJECT=$(openssl x509 -in "$DIR/device-$1-cert.pem" -noout -subject -nameopt RFC2253)
  echo "cert.device-$1.$SUBJECT" >> $DIR/device-certs.properties

  if [ $? -ne 0 ]; then
    error=$?
    echo "failed to create client certificate"
    exit $error
  fi
}

if [ -d $DIR ]
then
rm $DIR/*.pem
rm $DIR/*.p12
rm $DIR/*.jks
rm $DIR/*.properties
rm $DIR/$HONO_TRUST_STORE_PWD_FILE
else
mkdir $DIR
fi

echo "creating root key and certificate"
create_key root-key.pem
openssl req -x509 -config ca_opts -new -key $DIR/root-key.pem -out $DIR/root-cert.pem -days 365 -subj "/C=CA/L=Ottawa/O=Eclipse IoT/OU=Hono/CN=root"

echo ""
echo "creating CA key and certificate"
create_key ca-key.pem
openssl req -config ca_opts -reqexts intermediate_ext -new -key $DIR/ca-key.pem -days 365 -subj "/C=CA/L=Ottawa/O=Eclipse IoT/OU=Hono/CN=ca" | \
 openssl x509 -req -extfile ca_opts -extensions intermediate_ext -out $DIR/ca-cert.pem -days 365 -CA $DIR/root-cert.pem -CAkey $DIR/root-key.pem -CAcreateserial

echo ""
echo "creating PEM trust store ($DIR/trusted-certs.pem) containing CA certificate"
cat $DIR/ca-cert.pem $DIR/root-cert.pem > $DIR/trusted-certs.pem

echo ""
echo "creating JKS trust store ($DIR/$HONO_TRUST_STORE) containing CA certificate"
${JAVA_KEY_TOOL} -import -trustcacerts -noprompt -alias root -file $DIR/root-cert.pem -keystore $DIR/$HONO_TRUST_STORE -storepass $HONO_TRUST_STORE_PWD
${JAVA_KEY_TOOL} -import -trustcacerts -noprompt -alias ca -file $DIR/ca-cert.pem -keystore $DIR/$HONO_TRUST_STORE -storepass $HONO_TRUST_STORE_PWD
if [ $? -ne 0 ]; then
  error=$?
  echo "failed to create truststore"
  exit $error
fi
echo $HONO_TRUST_STORE_PWD > $DIR/$HONO_TRUST_STORE_PWD_FILE

echo ""
echo "creating CA key and certificate for DEFAULT_TENANT"
create_key default_tenant-key.pem
openssl req -x509 -key $DIR/default_tenant-key.pem -out $DIR/default_tenant-cert.pem -days 365 -subj "/C=CA/L=Ottawa/O=Eclipse IoT/OU=Hono/CN=DEFAULT_TENANT_CA"

echo ""
echo "extracting trust anchor information from tenant CA cert"
CA_SUBJECT=$(openssl x509 -in $DIR/default_tenant-cert.pem -noout -subject -nameopt RFC2253 | sed s/^subject=//)
PK=$(openssl x509 -in $DIR/default_tenant-cert.pem -noout -pubkey | sed /^---/d | sed -z 's/\n//g')
NOT_BEFORE=$(date --date="$(openssl x509 -in $DIR/default_tenant-cert.pem -noout -startdate -nameopt RFC2253 | sed s/^notBefore=//)" --iso-8601=seconds)
NOT_AFTER=$(date --date="$(openssl x509 -in $DIR/default_tenant-cert.pem -noout -enddate -nameopt RFC2253 | sed s/^notAfter=//)" --iso-8601=seconds)
{
  echo "trusted-ca.subject-dn=$CA_SUBJECT"
  echo "trusted-ca.public-key=$PK"
  echo "trusted-ca.algorithm=$KEY_ALG"
  echo "trusted-ca.not-before=$NOT_BEFORE"
  echo "trusted-ca.not-after=$NOT_AFTER"
} > $DIR/trust-anchor.properties

create_cert qdrouter
create_cert auth-server $AUTH_SERVER_KEY_STORE $AUTH_SERVER_KEY_STORE_PWD
create_cert device-registry $DEVREG_SERVER_KEY_STORE $DEVREG_SERVER_KEY_STORE_PWD
create_cert command-router $CMD_ROUTER_KEY_STORE $CMD_ROUTER_KEY_STORE_PWD
create_cert http-adapter $HTTP_ADAPTER_KEY_STORE $HTTP_ADAPTER_KEY_STORE_PWD
create_cert lora-adapter $LORA_ADAPTER_KEY_STORE $LORA_ADAPTER_KEY_STORE_PWD
create_cert mqtt-adapter $MQTT_ADAPTER_KEY_STORE $MQTT_ADAPTER_KEY_STORE_PWD
create_cert artemis $ARTEMIS_KEY_STORE $ARTEMIS_KEY_STORE_PWD
create_cert coap-adapter $COAP_ADAPTER_KEY_STORE $COAP_ADAPTER_KEY_STORE_PWD
create_cert amqp-adapter $AMQP_ADAPTER_KEY_STORE $AMQP_ADAPTER_KEY_STORE_PWD
create_cert example-gateway $EXAMPLE_GATEWAY_KEY_STORE $EXAMPLE_GATEWAY_KEY_STORE_PWD

create_client_cert 4711
create_client_cert 4712
