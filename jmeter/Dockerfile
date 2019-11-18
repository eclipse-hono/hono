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
# Stage image (for downloading files etc)
FROM index.docker.io/busybox as stage

# Set workdir to Container root
WORKDIR /

# Download and extract/install apache-jmeter 5.1.1
RUN [ "wget", "https://archive.apache.org/dist/jmeter/binaries/apache-jmeter-5.1.1.tgz" ]
RUN tar -xvzf apache-jmeter-5.1.1.tgz
RUN rm apache-jmeter-5.1.1.tgz
# Download and install mqtt-xmeter-1.0.1 into apache-jmeter
RUN [ "wget", "https://github.com/emqx/mqtt-jmeter/releases/download/1.0.1/mqtt-xmeter-1.0.1-jar-with-dependencies.jar" ]
RUN mv mqtt-xmeter-1.0.1-jar-with-dependencies.jar apache-jmeter-5.1.1/lib/ext/.

# Install Hono Plugin into apache-jmeter
COPY target/plugin/hono-jmeter-*.jar apache-jmeter-5.1.1/lib/ext/.

# Runtime image
FROM openjdk:11-jre-slim

# Set workdir to Container root
WORKDIR /

# Download and extract/install apache-jmeter 5.1.1
COPY --from=stage apache-jmeter-5.1.1/ apache-jmeter-5.1.1/

# Check if it is executable
RUN /apache-jmeter-5.1.1/bin/jmeter --version

# Place Load Test scripts into work dir
COPY src/jmeter/load-test-http-router-docker.sh load-test-http-router.sh
COPY src/jmeter/load-test-messaging-router-docker.sh load-test-messaging-router.sh
COPY src/jmeter/load-test-mqtt-router-docker.sh load-test-mqtt-router.sh
# Place all jmeter load test files into work dir
COPY src/jmeter/*.jmx /jmx/

# Environment Variables used by the scripts
ENV JMETER_HOME=/apache-jmeter-5.1.1 \
REGISTRATION_HOST=host \
REGISTRATION_PORT=28080 \
ROUTER_HOST=host \
ROUTER_PORT=15672 \
MQTT_ADAPTER_HOST=host \
MQTT_ADAPTER_PORT=1883 \
HTTP_ADAPTER_HOST=host \
HTTP_ADAPTER_PORT=8080 \
SAMPLE_LOG=load-test-router.jtl \
TEST_LOG=load-test-router.log \
DEVICE_COUNT=10 \
CONSUMER_COUNT=2
