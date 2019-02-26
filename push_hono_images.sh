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

if [ -n "$TAG" ]
then
  for image in hono-adapter-http-vertx hono-adapter-mqtt-vertx hono-adapter-kura hono-adapter-amqp-vertx hono-service-auth hono-service-device-registry
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
