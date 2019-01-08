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

set -e

SCRIPTPATH="$(cd "$(dirname "$0")" && pwd -P)"
source "$SCRIPTPATH/common.sh"

RESOURCES_BASE=https://raw.githubusercontent.com/operator-framework/operator-lifecycle-manager/4370fc33c4ff7b6ba3a23def82d4db837dfcbb43/deploy/upstream/manifests/0.8.0/

RESOURCES_GROUP_1="\
	0000_30_00-namespace.yaml \
	0000_30_01-olm-operator.serviceaccount.yaml \
	0000_30_02-clusterserviceversion.crd.yaml \
	0000_30_03-installplan.crd.yaml \
	0000_30_04-subscription.crd.yaml \
	0000_30_05-catalogsource.crd.yaml \
	0000_30_06-rh-operators.configmap.yaml \
	0000_30_10-olm-operator.deployment.yaml \
	0000_30_11-catalog-operator.deployment.yaml \
	0000_30_12-aggregated.clusterrole.yaml \
	0000_30_13-operatorgroup.crd.yaml \
	0000_30_14-olm-operators.configmap.yaml \
	0000_30_15-olm-operators.catalogsource.yaml \
	0000_30_16-operatorgroup-default.yaml \
	0000_30_17-packageserver.subscription.yaml \
	0000_30_18-operatorsource.crd.yaml
"

RESOURCES_GROUP_2="\
	0000_30_09-rh-operators.catalogsource.yaml \
	0000_30_19-operatorsource-default.yaml
"

echo DEPLOYING OPERATOR LIFECYCLE MANAGER TO KUBERNETES

if [ $(is_olm_deployed) == "YES" ]; then
	echo "OLM already deployed. Exiting ..."
	exit 0
fi

echo
echo "Deploying OLM ..."

for i in $RESOURCES_GROUP_1; do
	kubectl create -f "$RESOURCES_BASE/$i"
done

sleep 5

for i in $RESOURCES_GROUP_2; do
	kubectl create -f "$RESOURCES_BASE/$i"
done

echo ... done

echo OPERATOR LIFECYCLE MANAGER DEPLOYED TO KUBERNETES
