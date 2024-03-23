# Copyright (c) 2022 Contributors to the Eclipse Foundation
#
# See the NOTICE file(s) distributed with this work for additional
# information regarding copyright ownership.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License 2.0 which is available at
# http://www.eclipse.org/legal/epl-2.0
#
# SPDX-License-Identifier: EPL-2.0

# This Dockerfile is based on https://github.com/findepi/graalvm-docker.
# It has been adapted to use Mandrel instead of Oracle's GraalVM distribution and has been
# extended with steps for installing Maven, GnuPG (required for creating signatures of
# artifacts to be deployed to Maven Central) and UPX.
# The resulting image can be used to build Hono from source, including the native executable
# of the hono-cli artifact.

FROM debian:bullseye-slim

ARG MANDREL_VERSION=23.0.2.1-Final
# either java11 or java17
ARG JDK_VERSION=java17
ARG MVN_VERSION=3.8.8

ENV GRAALVM_HOME=/graalvm
ENV MAVEN_HOME=/maven
ENV JAVA_HOME=${GRAALVM_HOME}

RUN set -xeu && \
    export DEBIAN_FRONTEND=noninteractive && \
    apt-get update && \
    apt-get install -y --no-install-recommends \
        ca-certificates \
        git \
        curl \
        gcc \
        g++ \
        libfreetype6-dev \
        libz-dev \
        gpg \
        gpg-agent \
        pinentry-tty \
        openssh-client \
        upx-ucl \
        && \
    mkdir ${GRAALVM_HOME} && \
    curl -fsSL "https://github.com/graalvm/mandrel/releases/download/mandrel-${MANDREL_VERSION}/mandrel-${JDK_VERSION}-linux-amd64-${MANDREL_VERSION}.tar.gz" \
        | tar -zxC ${GRAALVM_HOME} --strip-components 1 && \
    find ${GRAALVM_HOME} -name "*src.zip"  -printf "Deleting %p\n" -exec rm {} + && \
    { test ! -d ${GRAALVM_HOME}/legal || tar czf ${GRAALVM_HOME}/legal.tgz ${GRAALVM_HOME}/legal/; } && \
    { test ! -d ${GRAALVM_HOME}/legal || rm -r ${GRAALVM_HOME}/legal; } && \
    rm -rf ${GRAALVM_HOME}/man `# does not exist in java11 package` && \
    mkdir ${MAVEN_HOME} && \
    curl -fsSL "https://archive.apache.org/dist/maven/maven-3/${MVN_VERSION}/binaries/apache-maven-${MVN_VERSION}-bin.tar.gz" \
        | tar -zxC ${MAVEN_HOME} --strip-components 1 && \
    echo Cleaning up... && \
    apt-get remove -y \
        curl \
        && \
    apt-get autoremove -y && \
    apt-get clean && rm -r "/var/lib/apt/lists"/* && \
    echo 'PATH="${GRAALVM_HOME}/bin:$PATH"' | install --mode 0644 /dev/stdin /etc/profile.d/graal-on-path.sh && \
    echo 'PATH="${MAVEN_HOME}/bin:$PATH"' | install --mode 0644 /dev/stdin /etc/profile.d/maven-on-path.sh && \
    echo OK

# This applies to all container processes. However, `bash -l` will source `/etc/profile` and set $PATH on its own. For this reason, we
# *also* set $PATH in /etc/profile.d/*
ENV PATH=${GRAALVM_HOME}/bin:${MAVEN_HOME}/bin:$PATH
ENV SHELL=/bin/bash
