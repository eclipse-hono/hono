#!/bin/bash
#*******************************************************************************
# Copyright (c) 2019, 2022 Contributors to the Eclipse Foundation
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

NS=${1:-hono}

function get_service_ip {
  
  if IP_ADDR=$(kubectl get service "$1" --output='jsonpath={.status.loadBalancer.ingress[0].ip}' -n "$NS" 2> /dev/null); then
    if [ "${IP_ADDR}" != '' ]
    then
      echo "export $2=${IP_ADDR}"
    else
      echo "echo \"could not determine IP address of service '$1'\""
    fi
  fi
}

get_service_ip hono-dispatch-router-ext AMQP_NETWORK_IP
get_service_ip hono-service-device-registry-ext REGISTRY_IP
get_service_ip hono-adapter-amqp AMQP_ADAPTER_IP
get_service_ip hono-adapter-coap COAP_ADAPTER_IP
get_service_ip hono-adapter-http HTTP_ADAPTER_IP
get_service_ip hono-adapter-lora LORA_ADAPTER_IP
get_service_ip hono-adapter-mqtt MQTT_ADAPTER_IP
echo "# Run this command to populate environment variables"
echo "# with the IP addresses of Hono's API endpoints:"
echo "# eval \"\$(./services.sh namespace)\""
echo "# with namespace being the Kubernetes namespace that you deployed Hono to"
