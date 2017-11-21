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

echo UNDEPLOYING ECLIPSE HONO FROM OPENSHIFT

# deleting entire project with related resources
oc login -u developer
oc delete project hono

# deleting Hono Server persistent volume
oc login -u system:admin
oc delete pv hono

oc login -u developer

echo ECLIPSE HONO UNDEPLOYED FROM OPENSHIFT