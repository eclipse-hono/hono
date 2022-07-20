#!/usr/bin/env bash

# Artemis launch script. Adapts and invokes the default Artemis image launch script.
#
# Copyright (c) 2022 Contributors to the Eclipse Foundation
##
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License 2.0 which is available at
# http://www.eclipse.org/legal/epl-2.0
#
# SPDX-License-Identifier: EPL-2.0
#

# Adapt launch.sh script (https://github.com/artemiscloud/activemq-artemis-broker-image/blob/1fa767251b3c345d6faf3c4b5a0c509ed294ad97/modules/activemq-artemis-install/added/launch.sh),
# replacing
#   configure
# with
#   configure
#   cp $AMQ_HOME/conf/* ~/broker/etc/
# .
# This allows usage of a custom broker.xml and other config files, provided in the "$AMQ_HOME/conf/" directory.
sed -i -E 's/^([[:space:]]*)configure[[:space:]]*$/\0\n\1cp $AMQ_HOME\/conf\/\* ~\/broker\/etc\//g' $AMQ_HOME/bin/launch.sh
$AMQ_HOME/bin/launch.sh
