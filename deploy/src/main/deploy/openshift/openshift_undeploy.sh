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

OPENSHIFT_MASTER=${1:-"https://$(minishift ip):8443"}

echo UNDEPLOYING ECLIPSE HONO FROM OPENSHIFT

# deleting entire project with related resources
oc delete project hono

echo ECLIPSE HONO UNDEPLOYED FROM OPENSHIFT