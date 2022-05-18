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

hugo version
if [ $? != 0 ]
then
  echo "Please install \"hugo\" to be able to build the hono documentation. See readme.md for further details."
  exit 0
fi

if [ $1 ]
then
  TARGET="$1"
else
  TARGET="public"
fi

cd homepage/
if [ ! -d themes/hugo-universal-theme ]
then
  git clone --depth 1 --branch 1.3.2 https://github.com/devcows/hugo-universal-theme.git themes/hugo-universal-theme
fi

echo "Going to build homepage in directory: $TARGET"
hugo -v -d $TARGET
cd .. 

cd documentation/
if [ ! -d themes/hugo-theme-relearn ]
then
  git clone --depth 1 --branch 3.4.1 https://github.com/McShelby/hugo-theme-relearn.git themes/hugo-theme-relearn
fi

echo "Going to build documentation in directory: $TARGET/docs"
hugo -v -d $TARGET/docs
