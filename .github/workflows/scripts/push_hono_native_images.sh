#!/bin/bash
#*******************************************************************************
# Copyright (c) 2016 Contributors to the Eclipse Foundation
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

TAG=${1:-""}
CR=${2:-"docker.io"}
NAMESPACE=${3:-"eclipsehono"}
dryRun=${DRY_RUN:-"0"}

if [[ -z "$TAG" ]]
then
  me=$(basename "$0")
  echo "This script can be used to push Hono's images from"
  echo "the local Docker registry to a (remote) container registry."
  echo ""
  echo "usage: $me TAG [CR [NAMESPACE]]"
  echo ""
  echo "TAG       The (already existing) TAG for which to push images to the registry."
  echo "CR        The name of the container registry to push to. Defaults to 'docker.io'."
  echo "NAMESPACE The namespace part to use for all images' repository names. Defaults to 'eclipsehono'."
  echo "          For example, if CR is 'quay.io' and NAMESPACE is 'myorg', the image 'hono-adapter-amqp-native'"
  echo "          will be pushed as 'quay.io/myorg/hono-adapter-amqp-native:TAG'."
  exit 1
fi

ECLIPSE_REPO="eclipsehono"

COMMAND_ROUTER_IMAGE_LEGACY="hono-service-command-router-native"
COMMAND_ROUTER_IMAGE_INFINISPAN="hono-service-command-router-infinispan-native"

IMAGES="hono-adapter-amqp-native \
        hono-adapter-coap-native \
        hono-adapter-http-native \
        hono-adapter-lora-native \
        hono-adapter-mqtt-native \
        hono-adapter-sigfox-native \
        hono-service-auth-native \
        ${COMMAND_ROUTER_IMAGE_INFINISPAN} \
        ${COMMAND_ROUTER_IMAGE_LEGACY} \
        hono-service-device-registry-jdbc-native \
        hono-service-device-registry-mongodb-native"

ECLIPSE_COMMAND_ROUTER_INFINISPAN_IMAGE_NAME="${ECLIPSE_REPO}/${COMMAND_ROUTER_IMAGE_INFINISPAN}:${TAG}"
ECLIPSE_COMMAND_ROUTER_LEGACY_IMAGE_NAME="${ECLIPSE_REPO}/${COMMAND_ROUTER_IMAGE_LEGACY}:${TAG}"

# Tag the Infinispan Command Router image produced in the build with its legacy name for backwards compatibility.
# The Command Router will be published using both the new and legacy names when looping through the IMAGES array below.
echo "tagging existing command-router image '${ECLIPSE_COMMAND_ROUTER_INFINISPAN_IMAGE_NAME}'" \
     " with legacy name '${ECLIPSE_COMMAND_ROUTER_LEGACY_IMAGE_NAME}'"

if ! docker tag "${ECLIPSE_COMMAND_ROUTER_INFINISPAN_IMAGE_NAME}" "${ECLIPSE_COMMAND_ROUTER_LEGACY_IMAGE_NAME}"
then
    echo "re-tagging '${ECLIPSE_COMMAND_ROUTER_INFINISPAN_IMAGE_NAME}' with legacy name failed. Exiting..."
    exit 1
fi

IMAGES_TO_PUSH=()
# Collect all images for pushing, re-tagging if necessary
for image in $IMAGES
do
  ECLIPSE_IMAGE_NAME="${ECLIPSE_REPO}/$image"
  if [[ "docker.io" != "${CR}" || "${ECLIPSE_REPO}" != "${NAMESPACE}" ]]
  then
    IMAGE_NAME="${CR}/${NAMESPACE}/${image}"
    if ! docker tag "${ECLIPSE_IMAGE_NAME}:${TAG}" "${IMAGE_NAME}:${TAG}"
    then
        echo "re-tagging ${ECLIPSE_IMAGE_NAME}:${TAG} as ${IMAGE_NAME}:${TAG} failed. Exiting..."
        exit 1
    fi
  else
    IMAGE_NAME="${ECLIPSE_IMAGE_NAME}"
    if ! docker inspect "${IMAGE_NAME}:${TAG}" > /dev/null
    then
        echo "image ${IMAGE_NAME}:${TAG} does not exist. Exiting..."
        exit 1
    fi
  fi
  # Collect the image for pushing
  IMAGES_TO_PUSH+=("${IMAGE_NAME}:${TAG}")
done

# Now push all images that were collected
for image in "${IMAGES_TO_PUSH[@]}"
do
  if [[ "${dryRun}" != "0" ]]
  then
    echo "DRY RUN: skipping actual push of ${image}"
    continue
  fi
  echo "pushing image ${image} ..."
  docker push "${image}"
done
