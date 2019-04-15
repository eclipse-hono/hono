#!/bin/bash

# Absolute path this script is in
SCRIPTPATH="$(cd "$(dirname "$0")" && pwd -P)"

set +e
source $SCRIPTPATH/olm_undeploy.sh
source $SCRIPTPATH/kubernetes_undeploy.sh
set -e

echo DONE!

