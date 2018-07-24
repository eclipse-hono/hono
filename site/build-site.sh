#!/bin/bash
#*******************************************************************************
# Copyright (c) 2016, 2017 Contributors to the Eclipse Foundation
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

if [ ! -d themes/hugo-material-docs ]
then
  git clone https://github.com/digitalcraftsman/hugo-material-docs.git themes/hugo-material-docs
  cd themes/hugo-material-docs
  git checkout 194c497216c8389e02e9719381168a668a0ffb05
  cd ../..
fi
if [ $1 ]
then
  hugo --theme hugo-material-docs -d $1
else
  hugo --theme hugo-material-docs
fi

