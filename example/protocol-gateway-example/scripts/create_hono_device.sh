#!/bin/bash
#*******************************************************************************
# Copyright (c) 2020 Contributors to the Eclipse Foundation
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

# A simple shell script to get IP addresses for hono-service-device-registry-ext, hono-dispatch-router-ext and hono-adapter-amqp-vertx and creates a device id and password

# prior: setup hono in kubernetes namespace "hono"
export REGISTRY_IP=$(kubectl -n hono get service hono-service-device-registry-ext --output='jsonpath={.status.loadBalancer.ingress[0].ip}')
echo "REGISTRY_IP=${REGISTRY_IP}"
export AMQP_NETWORK_IP=$(kubectl -n hono get service hono-dispatch-router-ext --output='jsonpath={.status.loadBalancer.ingress[0].ip}')
echo "AMQP_NETWORK_IP=${AMQP_NETWORK_IP}"
export AMQP_ADAPTER_PORT=$(kubectl -n hono get service hono-adapter-amqp-vertx --output='jsonpath={.status.loadBalancer.ingress[0].port}')
echo "AMQP_ADAPTER_IP=${AMQP_ADAPTER_IP}"

# Get example tenant or
export MY_TENANT="DEFAULT_TENANT"
# register new tenant
# export MY_TENANT=$(curl -X POST http://$REGISTRY_IP:28080/v1/tenants 2>/dev/null | jq -r .id)

echo "MY_TENANT=\"${MY_TENANT}\""

# register new device
export MY_DEVICE=$(curl -X POST http://$REGISTRY_IP:28080/v1/devices/$MY_TENANT 2>/dev/null | jq -r .id)
echo "MY_DEVICE=\"${MY_DEVICE}\""

# set credential secret for device
export MY_PWD="dummyDevicePassword"
echo "MY_PWD=\"${MY_PWD}\""
curl -i -X PUT -H "content-type: application/json" --data-binary '[{
  "type": "hashed-password",
  "auth-id": "'$MY_DEVICE'",
  "secrets": [{ "pwd-plain": "'$MY_PWD'" }]
}]' http://$REGISTRY_IP:28080/v1/credentials/$MY_TENANT/$MY_DEVICE
