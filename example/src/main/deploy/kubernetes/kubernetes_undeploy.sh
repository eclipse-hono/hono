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

kubectl delete deploy,service,pvc -l group=org.eclipse.hono --namespace $NS

# deleting Hono namespace
kubectl delete namespace $NS

echo ECLIPSE HONO UNDEPLOYED FROM KUBERNETES
