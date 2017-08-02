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

NS=hono

echo UNDEPLOYING ECLIPSE HONO FROM KUBERNETES

# deleting Hono namespace (and implicitly all services, deployments etc)
kubectl delete namespace $NS

echo ECLIPSE HONO UNDEPLOYED FROM KUBERNETES
