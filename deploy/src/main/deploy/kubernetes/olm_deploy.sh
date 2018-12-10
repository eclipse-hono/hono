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

: ${OLM_COMMIT:=81104ffdc4fb3ac29ebf060abd375399ca8f1bea}

echo DEPLOYING OPERATOR LIFECYCLE MANAGER TO KUBERNETES

if [ $(is_olm_deployed) == "YES" ]; then
	echo "OLM already deployed. Exiting ..."
	exit 0
fi


echo
echo "Deploying OLM ..."
if ! test -d operator-lifecycle-manager; then
	git clone https://github.com/operator-framework/operator-lifecycle-manager.git operator-lifecycle-manager
	pushd operator-lifecycle-manager
	git checkout "${OLM_COMMIT}"
	popd
fi

kubectl create -f operator-lifecycle-manager/deploy/upstream/manifests/latest/ || true

#
# The next delay is necessary due to operator-framework/operator-lifecycle-manager#558
# Sometimes it happens that in the previous step the use of a newly deployed
# CRD fails, and so a catalog source is missing. The next steps wait "a bit"
# and then re-register the catalog source. Which again might fail, because
# the previous step might have been successful.
#

sleep 5 # delay and retry CRD

kubectl create -f operator-lifecycle-manager/deploy/upstream/manifests/latest/*rh-operators.catalogsource.yaml || true
kubectl create -f operator-lifecycle-manager/deploy/upstream/manifests/latest/*-operatorsource-default.yaml || true

echo ... done

echo OPERATOR LIFECYCLE MANAGER DEPLOYED TO KUBERNETES
