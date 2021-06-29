#!/bin/bash
#*******************************************************************************
# Copyright (c) 2021 Contributors to the Eclipse Foundation
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

set -ue

KUBECONFIG="--kubeconfig /etc/rancher/k3s/k3s.yaml"
HELM_WAIT="--wait --timeout 5m0s"
