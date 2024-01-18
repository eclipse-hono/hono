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

TAG=$1
CR=${2:-"docker.io"}
REPO=${3:-"eclipse"}

IMAGES="hono-adapter-amqp \
        hono-adapter-coap \
        hono-adapter-http \
        hono-adapter-lora \
        hono-adapter-mqtt \
        hono-adapter-sigfox \
        hono-service-auth \
        hono-service-command-router \
        hono-service-device-registry-jdbc \
        hono-service-device-registry-mongodb"

NATIVE_IMAGES="hono-adapter-amqp-native \
        hono-adapter-coap-native \
        hono-adapter-http-native \
        hono-adapter-lora-native \
        hono-adapter-mqtt-native \
        hono-adapter-sigfox-native \
        hono-service-auth-native \
        hono-service-command-router-native \
        hono-service-device-registry-jdbc-native \
        hono-service-device-registry-mongodb-native"

ME=$(basename "$0")
echo "called as $ME"

if [[ "push_hono_native_images.sh" == "$ME" ]]
then
  IMAGES=${NATIVE_IMAGES}
fi

if [[ -n "$TAG" ]]
then
  for image in $IMAGES
  do
    ECLIPSE_IMAGE_NAME="eclipse/$image"
    if [[ "docker.io" != "${CR}" || "eclipse" != "${REPO}" ]]
    then
      IMAGE_NAME="${CR}/${REPO}/${image}"
      docker tag "${ECLIPSE_IMAGE_NAME}:${TAG}" "${IMAGE_NAME}:${TAG}"
    else
      IMAGE_NAME="${ECLIPSE_IMAGE_NAME}"
    fi
    echo "pushing image ${IMAGE_NAME}:${TAG} ..."
    docker push "${IMAGE_NAME}:${TAG}"
  done
else
  echo "This script can be used to push Hono's images from"
  echo "the local Docker registry to a (remote) container registry."
  echo ""
  echo "usage: push_hono_images.sh TAG [CR [REPOSITORY]]"
  echo "TAG is the (already existing) TAG to push to the registry."
  echo "CR is the name of the container registry to push to."
  echo "REPOSITORY is the name of the repository within the registry to push to."
  echo "If only TAG is specified, then the images are pushed to 'docker.io' using repository name 'eclipse'."
fi
