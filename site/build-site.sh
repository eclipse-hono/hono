#!/bin/bash
#*******************************************************************************
# Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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
  git clone https://github.com/devcows/hugo-universal-theme.git themes/hugo-universal-theme
  cd themes/hugo-universal-theme
  git checkout 1.0.0
  echo "Remove images from theme" # We do not need the pictures. Removing them, so they don't get deployed
  rm static/img/*
  cd ../..
fi

echo "Going to build homepage in directory: $TARGET"
hugo -v -d $TARGET
cd .. 

cd documentation/
if [ ! -d themes/hugo-theme-learn ]
then
  git clone https://github.com/matcornic/hugo-theme-learn.git themes/hugo-theme-learn
  cd themes/hugo-theme-learn
  git checkout 2.2.0
  cd ../..
fi

echo "Going to build documentation in directory: $TARGET/docs"
hugo -v -d $TARGET/docs
