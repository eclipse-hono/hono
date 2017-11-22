#!/usr/bin/env bash

# Copyright (c) 2017 Bosch Software Innovations GmbH and others.
#
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the Eclipse Public License v1.0
# which accompanies this distribution, and is available at
# http://www.eclipse.org/legal/epl-v10.html
#
# Contributors:
#    Bosch Software Innovations GmbH - initial creation

# Absolute path this script is in
SCRIPTPATH="$(cd "$(dirname "$0")" && pwd -P)"
HONO_HOME=$SCRIPTPATH/../../..
CONFIG=$SCRIPTPATH/../config
HOST=${1:-localhost}
PORT=${2:-3000}

while true; do echo "Waiting for Grafana service at ${HOST}:${PORT} to come up...";
wget http://${HOST}:${PORT} -q -T 1 -O /dev/null >/dev/null 2>/dev/null && break; sleep 1;
done;

echo .. Grafana is up, set its datasource and dashboard

# add the data source to grafana
curl -X POST -i -H 'Content-Type: application/json' --data-binary @$CONFIG/grafana_datasource.json \
  http://admin:admin@${HOST}:${PORT}/api/datasources

# add the dashboard to grafana
# to use a changed dashboard (grafana_dashboard.json) from Grafana make a HTTP call to
# http://<host>:<port>/api/dashboards/db/hono and change the dashboard id in the resulting JSON to null
curl -X POST -i -H 'Content-Type: application/json' --data-binary @$CONFIG/grafana_dashboard.json \
  http://admin:admin@${HOST}:${PORT}/api/dashboards/db
