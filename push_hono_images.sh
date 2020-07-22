#!/bin/bash
#*******************************************************************************
# Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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

TAG=$1
CR=$2
NEWTAG=$3
IMAGES="hono-adapter-amqp-vertx \
        hono-adapter-coap-vertx \
        hono-adapter-http-vertx \
        hono-adapter-kura \
        hono-adapter-lora-vertx \
        hono-adapter-mqtt-vertx \
        hono-adapter-sigfox-vertx \
        hono-service-auth \
        hono-service-device-connection \
        hono-service-device-registry-file \
        hono-service-device-registry-mongodb"

if [ -n "$TAG" ]
then
  for image in $IMAGES
  do
    ECLIPSE_IMAGE_NAME="eclipse/$image"
    IMAGE_NAME=$ECLIPSE_IMAGE_NAME
    if [ -n "$CR" ]
    then
      IMAGE_NAME="$CR/$IMAGE_NAME"
      docker tag $ECLIPSE_IMAGE_NAME:$TAG $IMAGE_NAME:$NEWTAG
    fi
    echo "pushing image $IMAGE_NAME:$NEWTAG ..."
    docker push $IMAGE_NAME:$NEWTAG
  done
else
  echo "This script can be used to push Hono's images from"
  echo "the local Docker registry to Docker Hub."
  echo ""
  echo "usage: push_hono_images.sh TAG [CR]"
  echo "where TAG is the TAG to push to Docker Hub"
  echo "and the (optional) CR is the name of the container registry to push to"
fi
