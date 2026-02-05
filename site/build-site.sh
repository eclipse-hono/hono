#!/bin/bash
#*******************************************************************************
# Copyright (c) 2016 Contributors to the Eclipse Foundation
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

target="public"
hugo_cmd="hugo"

print_usage() {
  cmd_name=$(basename "$0")
  cat <<EOF >&2 
Usage: ${cmd_name} OPTIONS ...

OPTIONS

-h | --help          Display this usage information.
-H | --hugo          The path to the Hugo executable to use for building the site. [$hugo_cmd]
-t | --target PATH   The path to the folder to contain the site. [$target]

EOF
}

while [[ "$1" =~ ^- && ! "$1" == "--" ]]; do case $1 in
  -h | --help )
    print_usage
    exit 1
    ;;
  -H | --hugo )
    shift; hugo_cmd=$1
    ;;
  -t | --target )
    shift; target=$1
    ;;
  *)
    echo "Ignoring unknown option: $1"
    echo "Run with flag -h for usage"
    ;;
esac; shift; done
if [[ "$1" == '--' ]]; then shift; fi

hugo_version=$($hugo_cmd version)
if [[ -z "$hugo_version" ]]
then
  echo "Could not find \"$hugo_cmd\" executable on your PATH. See readme.md for further details."
  exit 0
fi

submodule_status=$(git submodule status)
if [[ -z "$submodule_status" ]]
then
  echo "Initializing submodules containing Hugo themes."
  git submodule update --init
  echo
fi

cd homepage || exit
echo "Building homepage in directory: $target"
$hugo_cmd -d "$target"
echo
echo
cd .. 

cd documentation || exit
echo "Building documentation in directory: $target/docs"
$hugo_cmd -d "$target/docs"
cd ..
