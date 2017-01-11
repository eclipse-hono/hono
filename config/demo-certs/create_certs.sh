#!/bin/bash
: '

 * Copyright (c) 2016 Bosch Software Innovations GmbH.
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
HONO_KEY_STORE=honoKeyStore.p12
HONO_KEY_STORE_PWD=honokeys
MQTT_ADAPTER_KEY_STORE=mqttKeyStore.p12
MQTT_ADAPTER_KEY_STORE_PWD=mqttkeys
REST_ADAPTER_KEY_STORE=restKeyStore.p12
REST_ADAPTER_KEY_STORE_PWD=restkeys

if [ -d $DIR ]
then
  rm -rf $DIR
fi
mkdir $DIR

echo "creating root key and certificate"
openssl ecparam -name $CURVE -genkey -noout -out $DIR/root-key.pem
openssl req -x509 -config ca_opts -new -key $DIR/root-key.pem -out $DIR/root-cert.pem -days 365 -subj "/C=CA/L=Ottawa/O=Eclipse IoT/OU=Hono/CN=root"

echo "creating CA key and certificate"
openssl ecparam -name $CURVE -genkey -noout -out $DIR/ca-key.pem
openssl req -config ca_opts -reqexts intermediate_ext -new -key $DIR/ca-key.pem -days 365 -subj "/C=CA/L=Ottawa/O=Eclipse IoT/OU=Hono/CN=ca" | \
 openssl x509 -req -extfile ca_opts -extensions intermediate_ext -out $DIR/ca-cert.pem -days 365 -CA $DIR/root-cert.pem -CAkey $DIR/root-key.pem -CAcreateserial

function create_cert {

  echo "creating $1 key and certificate"
  openssl ecparam -name $CURVE -genkey -noout -out $DIR/$1-key.pem
  openssl req -config ca_opts -new -key $DIR/$1-key.pem -days 365 -subj "/C=CA/L=Ottawa/O=Eclipse IoT/OU=Hono/CN=$1" | \
    openssl x509 -req -extfile ca_opts -extensions req_ext -out $DIR/$1.pem -days 365 -CA $DIR/ca-cert.pem -CAkey $DIR/ca-key.pem -CAcreateserial
  cat $DIR/$1.pem $DIR/ca-cert.pem > $DIR/$1-cert.pem && rm $DIR/$1.pem
  if [ $2 ]
  then
    openssl pkcs12 -export -inkey $DIR/$1-key.pem -in $DIR/$1-cert.pem -out $DIR/$2 -password pass:$3
  fi
}

create_cert hono $HONO_KEY_STORE $HONO_KEY_STORE_PWD
create_cert qdrouter
create_cert rest-adapter $REST_ADAPTER_KEY_STORE $REST_ADAPTER_KEY_STORE_PWD
create_cert mqtt-adapter $MQTT_ADAPTER_KEY_STORE $MQTT_ADAPTER_KEY_STORE_PWD
