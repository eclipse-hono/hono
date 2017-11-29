#!/bin/sh

# Copyright (c) 2017 Red Hat and others.
#
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the Eclipse Public License v1.0
# which accompanies this distribution, and is available at
# http://www.eclipse.org/legal/epl-v10.html
#
# Contributors:
#    Red Hat - initial creation
#    Bosch Software Innovations GmbH

OPENSHIFT_MASTER=${1:-"https://$(minishift ip):8443"}

echo UNDEPLOYING ECLIPSE HONO FROM OPENSHIFT

# deleting entire project with related resources
oc login $OPENSHIFT_MASTER -u developer
oc delete project hono

# deleting Hono Server persistent volume
oc login $OPENSHIFT_MASTER -u system:admin
oc adm policy remove-scc-from-user anyuid useroot
oc delete serviceaccount useroot
oc delete pv hono

oc login $OPENSHIFT_MASTER -u developer

echo ECLIPSE HONO UNDEPLOYED FROM OPENSHIFT