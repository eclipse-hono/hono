#!/bin/bash
: '

 * Copyright (c) 2016, 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation

 A simple shell script for generating example certificates to be used with Hono.
'

CURVE=prime256v1
DIR=certs
HONO_MESSAGING_KEY_STORE=honoKeyStore.p12
HONO_MESSAGING_KEY_STORE_PWD=honokeys
HONO_TRUST_STORE=trustStore.jks
HONO_TRUST_STORE_PWD=honotrust
AUTH_SERVER_KEY_STORE=authServerKeyStore.p12
AUTH_SERVER_KEY_STORE_PWD=authkeys
DEVREG_SERVER_KEY_STORE=deviceRegistryKeyStore.p12
DEVREG_SERVER_KEY_STORE_PWD=deviceregistrykeys
MQTT_ADAPTER_KEY_STORE=mqttKeyStore.p12
MQTT_ADAPTER_KEY_STORE_PWD=mqttkeys
HTTP_ADAPTER_KEY_STORE=httpKeyStore.p12
HTTP_ADAPTER_KEY_STORE_PWD=httpkeys
KURA_ADAPTER_KEY_STORE=kuraKeyStore.p12
KURA_ADAPTER_KEY_STORE_PWD=kurakeys
ARTEMIS_KEY_STORE=artemisKeyStore.p12
ARTEMIS_KEY_STORE_PWD=artemiskeys

function to_pkcs8 {

  # turn key into PKCS8 format
  openssl pkcs8 -topk8 -nocrypt -inform PEM -outform PEM -in $1 -out $2 && rm $1
}

function create_cert {

  echo ""
  echo "creating $1 key and certificate"
  #openssl ecparam -name $CURVE -genkey -noout -out $DIR/$1-key-orig.pem
  openssl genrsa -out $DIR/$1-key-orig.pem 4096
  to_pkcs8 $DIR/$1-key-orig.pem $DIR/$1-key.pem
  openssl req -config ca_opts -new -key $DIR/$1-key.pem -days 365 -subj "/C=CA/L=Ottawa/O=Eclipse IoT/OU=Hono/CN=$1" | \
    openssl x509 -req -extfile ca_opts -extensions req_ext_$1 -out $DIR/$1.pem -days 365 -CA $DIR/ca-cert.pem -CAkey $DIR/ca-key.pem -CAcreateserial
  cat $DIR/$1.pem $DIR/ca-cert.pem > $DIR/$1-cert.pem && rm $DIR/$1.pem
  if [ $2 ]
  then
    echo "adding key/cert for $1 to key store $DIR/$2"
    openssl pkcs12 -export -inkey $DIR/$1-key.pem -in $DIR/$1-cert.pem -out $DIR/$2 -password pass:$3
  fi
}

if [ -d $DIR ]
then
rm $DIR/*.pem
rm $DIR/*.p12
rm $DIR/*.jks
else
mkdir $DIR
fi

echo "creating root key and certificate"
#openssl ecparam -name $CURVE -genkey -noout -out $DIR/root-key-orig.pem
openssl genrsa -out $DIR/root-key-orig.pem 4096
to_pkcs8 $DIR/root-key-orig.pem $DIR/root-key.pem
openssl req -x509 -config ca_opts -new -key $DIR/root-key.pem -out $DIR/root-cert.pem -days 365 -subj "/C=CA/L=Ottawa/O=Eclipse IoT/OU=Hono/CN=root"

echo ""
echo "creating CA key and certificate"
#openssl ecparam -name $CURVE -genkey -noout -out $DIR/ca-key-orig.pem
openssl genrsa -out $DIR/ca-key-orig.pem 4096
to_pkcs8 $DIR/ca-key-orig.pem $DIR/ca-key.pem
openssl req -config ca_opts -reqexts intermediate_ext -new -key $DIR/ca-key.pem -days 365 -subj "/C=CA/L=Ottawa/O=Eclipse IoT/OU=Hono/CN=ca" | \
 openssl x509 -req -extfile ca_opts -extensions intermediate_ext -out $DIR/ca-cert.pem -days 365 -CA $DIR/root-cert.pem -CAkey $DIR/root-key.pem -CAcreateserial

echo ""
echo "creating PEM trust store ($DIR/trusted-certs.pem) containing CA certificate"
cat $DIR/ca-cert.pem $DIR/root-cert.pem > $DIR/trusted-certs.pem

echo ""
echo "creating JKS trust store ($DIR/$HONO_TRUST_STORE) containing CA certificate"
keytool -import -trustcacerts -noprompt -alias root -file $DIR/root-cert.pem -keystore $DIR/$HONO_TRUST_STORE -storepass $HONO_TRUST_STORE_PWD
keytool -import -trustcacerts -noprompt -alias ca -file $DIR/ca-cert.pem -keystore $DIR/$HONO_TRUST_STORE -storepass $HONO_TRUST_STORE_PWD

create_cert hono-messaging $HONO_MESSAGING_KEY_STORE $HONO_MESSAGING_KEY_STORE_PWD
create_cert qdrouter
create_cert auth-server $AUTH_SERVER_KEY_STORE $AUTH_SERVER_KEY_STORE_PWD
create_cert device-registry $DEVREG_SERVER_KEY_STORE $DEVREG_SERVER_KEY_STORE_PWD
create_cert http-adapter $HTTP_ADAPTER_KEY_STORE $HTTP_ADAPTER_KEY_STORE_PWD
create_cert mqtt-adapter $MQTT_ADAPTER_KEY_STORE $MQTT_ADAPTER_KEY_STORE_PWD
create_cert kura-adapter $KURA_ADAPTER_KEY_STORE $KURA_ADAPTER_KEY_STORE_PWD
create_cert artemis $ARTEMIS_KEY_STORE $ARTEMIS_KEY_STORE_PWD
