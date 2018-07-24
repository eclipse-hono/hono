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

NS=hono

echo UNDEPLOYING ECLIPSE HONO FROM KUBERNETES

# deleting Hono namespace (and implicitly all services, deployments etc)
kubectl delete namespace $NS

echo ECLIPSE HONO UNDEPLOYED FROM KUBERNETES
