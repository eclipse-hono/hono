#!/bin/bash
#*******************************************************************************
# Copyright (c) 2019 Contributors to the Eclipse Foundation
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
JMX_FILE="/jmx/mqtt_messaging_throughput_test.jmx"
$JMETER_HOME/bin/jmeter -n -f -l $SAMPLE_LOG -j $TEST_LOG -t $JMX_FILE \
-Jplugin_dependency_paths=$JMETER_HOME/lib/ext \
-Jjmeterengine.stopfail.system.exit=true \
-Jrouter.host=$ROUTER_HOST -Jrouter.port=$ROUTER_PORT \
-Jregistration.host=$REGISTRATION_HOST -Jregistration.port=$REGISTRATION_PORT \
-Jmqtt.host=$MQTT_ADAPTER_HOST -Jmqtt.port=$MQTT_ADAPTER_PORT \
-Lorg.eclipse.hono.client.impl=INFO -Lorg.eclipse.hono.jmeter=INFO \
-JdeviceCount=$DEVICE_COUNT -JconsumerCount=$CONSUMER_COUNT 
