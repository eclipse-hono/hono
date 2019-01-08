#!/bin/bash
#*******************************************************************************
# Copyright (c) 2018 Contributors to the Eclipse Foundation
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

echo UNDEPLOYING OPERATOR LIFECYCLE MANAGER FROM KUBERNETES

kubectl delete namespace olm
kubectl delete namespace operators

kubectl delete clusterrole/system:controller:operator-lifecycle-manager
kubectl delete clusterrolebinding/olm-operator-binding-olm
kubectl delete customresourcedefinition/operatorgroups.operators.coreos.com
kubectl delete customresourcedefinition/operatorsources.marketplace.redhat.com
kubectl delete customresourcedefinition/clusterserviceversions.operators.coreos.com
kubectl delete customresourcedefinition/installplans.operators.coreos.com
kubectl delete customresourcedefinition/subscriptions.operators.coreos.com
kubectl delete customresourcedefinition/catalogsources.operators.coreos.com
kubectl delete clusterroles/aggregate-olm-edit
kubectl delete clusterroles/aggregate-olm-view

echo OPERATOR LIFECYCLE MANAGER UNDEPLOYED FROM KUBERNETES
echo
echo "The git repository of OLM is still kept in the local directory. If you would like to delete it as well, you can simply run:"
echo
echo "    rm -Rf operator-lifecycle-manager"
echo
