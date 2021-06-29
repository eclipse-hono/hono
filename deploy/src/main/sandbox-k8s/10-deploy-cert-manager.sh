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

# This script deploys the Cert-Manager and adds an issuer definition for Let's Encrypt.
# To create a valid certificate, the script must be started with the parameter "--use-prod",
# otherwise the issuer will be configured to use the Let's Encrypt Staging API. This is to avoid
# hitting a limit on LE's productive API.

SCRIPTPATH="$(cd "$(dirname "$0")" && pwd -P)"
NS_CERT_MANAGER=cert-manager

source $SCRIPTPATH/common.sh


retry() {
  "$@"
  while [ $? -ne 0 ]; do
    sleep 2s
    echo "retrying" "$@"
    "$@"
  done
}


# add Helm repo
helm repo add jetstack https://charts.jetstack.io $KUBECONFIG
helm repo update $KUBECONFIG

echo ""
echo "Deploying cert-manager..."
helm install cert-manager jetstack/cert-manager --namespace $NS_CERT_MANAGER --create-namespace --set installCRDs=true $KUBECONFIG $HELM_WAIT #  --version v1.4.0

echo ""
echo "Adding issuer Let's Encrypt to cert-manager with API:"
if [[ "$*" =~ "--use-prod" ]] ; then
  echo " PRODUCTION"
  ISSUER_DEFINITION="letsencrypt-issuer-production.yml"
else
  echo " STAGING"
  ISSUER_DEFINITION="letsencrypt-issuer-staging.yml"
fi

# checking when cert-manager is ready is tricky: https://cert-manager.io/docs/installation/kubernetes/#verifying-the-installation
# we just retry the following command
echo ""
echo "Adding issuer Let's Encrypt to cert-manager with API:"
retry kubectl apply -f $ISSUER_DEFINITION $KUBECONFIG || true # do not abort although '-e' is set
