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

# This script takes care to provide a public certificate from Let's Encrypt.
# It creates the namespace "hono", deploys the Cert-Manager and requests a certificate.
# The script expects an email address to be used by Let's Encrypt, as the first argument.
# The second argument must be "production" to create a valid certificate. If it is not set,
# the issuer will be configured to use the Let's Encrypt Staging API. This is to avoid
# hitting a limit on Let's Encrypt's productive API.

SCRIPTPATH="$(cd "$(dirname "$0")" && pwd -P)"
source $SCRIPTPATH/common.sh

NS_CERT_MANAGER=cert-manager
EMAIL=$1
LE_API=${2:-"staging"}


retry() {
  "$@"
  while [ $? -ne 0 ]; do
    sleep 2s
    echo "retrying" "$@"
    "$@"
  done
}

kubectl create namespace hono $KUBECONFIG

# add Helm repo
helm repo add jetstack https://charts.jetstack.io $KUBECONFIG
helm repo update $KUBECONFIG

echo ""
echo "Deploying cert-manager..."
helm install cert-manager jetstack/cert-manager --namespace $NS_CERT_MANAGER --create-namespace --set installCRDs=true $KUBECONFIG $HELM_WAIT #  --version v1.4.0

# checking when cert-manager is ready is tricky: https://cert-manager.io/docs/installation/kubernetes/#verifying-the-installation
# we just retry the following command
echo ""
echo "Adding issuer Let's Encrypt to cert-manager with API: $LE_API"
LE_CONFIG=$(helm template ./letsencrypt-chart -f ./letsencrypt-chart/values-$LE_API.yaml --set spec.acme.email=$EMAIL)
retry echo "$LE_CONFIG" | kubectl apply -f - $KUBECONFIG || true # do not abort although '-e' is set
