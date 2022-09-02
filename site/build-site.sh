#!/bin/bash
#*******************************************************************************
# Copyright (c) 2016, 2022 Contributors to the Eclipse Foundation
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


if ! hugo version; then
  echo "Please install \"hugo\" to be able to build the hono documentation. See readme.md for further details."
  exit 0
fi

if [ "$1" ]
then
  TARGET="$1"
else
  TARGET="public"
fi

if ! git submodule status; then
  echo "Initializing submodules containing Hugo themes."
  git submodule update --init
fi

cd homepage || exit
echo "Building homepage in directory: $TARGET"
hugo -v -d $TARGET
cd .. 

cd documentation || exit
echo "Building documentation in directory: $TARGET/docs"
hugo -v -d $TARGET/docs
cd ..
