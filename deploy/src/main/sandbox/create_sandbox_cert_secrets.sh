#!/bin/sh
#*******************************************************************************
# Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
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

# This script creates two Docker Swarm secrets
# - "hono.eclipse.org-cert.pem" holding the Let's Encrypt certificate to use for external ports
# - "hono.eclipse.org-key.pem" holding the corresponding private key
# The script expects the absolute paths to the certificate and private key as arguments
# This script should be run BEFORE deploying the sandbox Hono instance so that
# the services can successfully access the secrets created by this script.
# NOTE: this script can only be run on the host where the certificate and private key
# are located, i.e. on the sandbox.

SECRET_NAME_CERT="hono.eclipse.org-cert.pem"
SECRET_NAME_KEY="hono.eclipse.org-key.pem"
CERT_PATH=${1:-"/etc/letsencrypt/live/hono.eclipse.org/fullchain.pem"}
KEY_PATH=${2:-"/etc/letsencrypt/live/hono.eclipse.org/privkey.pem"}

docker secret rm $SECRET_NAME_CERT
docker secret create -l project=hono $SECRET_NAME_CERT $CERT_PATH
docker secret rm $SECRET_NAME_KEY
docker secret create -l project=hono $SECRET_NAME_KEY $KEY_PATH
