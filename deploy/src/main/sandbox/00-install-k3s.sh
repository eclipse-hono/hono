#!/bin/sh
#*******************************************************************************
# Copyright (c) 2021, 2022 Contributors to the Eclipse Foundation
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

# This script installs the current version of k3s. It is intended to be only used
# on the Eclipse Foundation's Hono sandbox VM.

# Also see https://www.suse.com/support/kb/doc/?id=000020071 regarding configuration
# of (container) log rotation

set -ue

curl -sfL https://get.k3s.io | sh -s - server --node-name hono.eclipseprojects.io \
  --kubelet-arg container-log-max-files=3 \
  --kubelet-arg container-log-max-size=1Mi \
  --disable traefik
