#!/bin/bash

# Copyright (c) 2018 Contributors to the Eclipse Foundation
#
# See the NOTICE file(s) distributed with this work for additional
# information regarding copyright ownership.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License 1.0 which is available at
# https://www.eclipse.org/legal/epl-v10.html
#
# SPDX-License-Identifier: EPL-1.0

TAG=$1

if [ -n "$TAG" ]
then
  for image in hono-adapter-http-vertx hono-adapter-mqtt-vertx hono-adapter-kura hono-service-auth hono-service-device-registry hono-service-messaging
  do
    docker push eclipse/$image:$TAG
  done
else
  echo "This script can be used to push Hono's images from"
  echo "the local Docker registry to Docker Hub."
  echo ""
  echo "usage: push_hono_images.sh TAG"
  echo "where TAG is the TAG to push to Docker Hub"
fi
