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

if [ ! -d themes/hugo-theme-docdock ]
then
  git clone https://github.com/vjeantet/hugo-theme-docdock.git themes/hugo-theme-docdock
  cd themes/hugo-theme-docdock
  git checkout 1d12f5733354d9bd4e19e439f068bdc3cfdabe4f
  cd ../..
fi
if [ $1 ]
then
  hugo --theme hugo-theme-docdock -d $1
else
  hugo --theme hugo-theme-docdock
fi

