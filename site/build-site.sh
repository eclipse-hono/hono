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
WEBSITE_THEME_CLONING_REQUIRED=1
if [ -d themes/hugo-universal-theme/.git ]
then
  HEAD=$(cat themes/hugo-universal-theme/.git/HEAD)
  WEBSITE_THEME_HEAD_REVISION="78887402aeca0ab44deb8f0de800f7a6974f8c8a"
  echo "rev required: ${WEBSITE_THEME_HEAD_REVISION}, rev found: ${HEAD}"
  if [[ "${WEBSITE_THEME_HEAD_REVISION}" == "${HEAD}" ]]
  then
    WEBSITE_THEME_CLONING_REQUIRED=0
  fi
fi

if [[ ${WEBSITE_THEME_CLONING_REQUIRED} -eq 1 ]]
then
  rm -rf themes/hugo-universal-theme
  echo "cloning website theme repository..."
  git clone --depth 1 --branch 1.3.2 https://github.com/devcows/hugo-universal-theme.git themes/hugo-universal-theme
fi

echo "Building homepage in directory: $TARGET"
hugo -v -d $TARGET
cd .. 

cd documentation/
DOC_THEME_CLONING_REQUIRED=1
if [ -d themes/hugo-theme-relearn/.git ]
then
  HEAD=$(cat themes/hugo-theme-relearn/.git/HEAD)
  DOC_THEME_HEAD_REVISION="e9938d80ae8b5fd0d3db918e3643c01f485be19c"
  if [[ "${DOC_THEME_HEAD_REVISION}" == "${HEAD}" ]]
  then
    DOC_THEME_CLONING_REQUIRED=0
  fi
fi

if [[ ${DOC_THEME_CLONING_REQUIRED} -eq 1 ]]
then
  rm -rf themes/hugo-theme-relearn
  echo "cloning doc theme repository..."
  git clone --depth 1 --branch 4.0.3 https://github.com/McShelby/hugo-theme-relearn.git themes/hugo-theme-relearn
fi

echo "Building documentation in directory: $TARGET/docs"
hugo -v -d $TARGET/docs
