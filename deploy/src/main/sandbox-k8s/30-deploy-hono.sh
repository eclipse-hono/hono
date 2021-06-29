#!/bin/bash
#*******************************************************************************
# Copyright (c) 2021 Contributors to the Eclipse Foundation
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

# This script deploys Hono to the Eclipse Foundation's sandbox at hono.eclipseprojects.io
# It requires that the namespace "hono" already exists and that the secret for the certificate is present.

SCRIPTPATH="$(cd "$(dirname "$0")" && pwd -P)"
source $SCRIPTPATH/common.sh


# add Helm repos
helm repo add eclipse-iot https://eclipse.org/packages/charts $KUBECONFIG
helm repo update $KUBECONFIG

echo ""
echo "Deploying hono..."
helm install eclipse-hono --dependency-update -f hono-values.yml -n hono eclipse-iot/hono $KUBECONFIG $HELM_WAIT
