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

# This script requests a certificate from Let's Encrypt (LE). It creates the namespace "hono".
# To create a valid certificate, the script must be started with the parameter "--use-prod",
# otherwise the certificate will be requested from the Let's Encrypt Staging API. This is to avoid
# hitting a limit on LE's productive API.

SCRIPTPATH="$(cd "$(dirname "$0")" && pwd -P)"
source $SCRIPTPATH/common.sh


kubectl create namespace hono $KUBECONFIG

echo ""
echo "Requesting certificate from Let's Encrypt using API:"
if [[ "$*" =~ "--use-prod" ]] ; then
  echo " PRODUCTION"
  CERT_DEFINITION="letsencrypt-certificate-production.yml"
else
  echo " STAGING"
  CERT_DEFINITION="letsencrypt-certificate-staging.yml"
fi

kubectl apply -f $CERT_DEFINITION $KUBECONFIG
