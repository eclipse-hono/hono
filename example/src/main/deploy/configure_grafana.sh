#!/usr/bin/env bash

# Copyright (c) 2017, 2018 Bosch Software Innovations GmbH and others.
#
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the Eclipse Public License v1.0
# which accompanies this distribution, and is available at
# http://www.eclipse.org/legal/epl-v10.html
#
# Contributors:
#    Bosch Software Innovations GmbH - initial creation
#    Red Hat Inc

# Absolute path this script is in
SCRIPTPATH="$(cd "$(dirname "$0")" && pwd -P)"
HOST=${1:-localhost}
PORT=${2:-3000}

while true; do echo "Waiting for Grafana service at ${HOST}:${PORT} to come up...";
curl http://${HOST}:${PORT} --connect-timeout 1 -o /dev/null >/dev/null 2>/dev/null && break; sleep 5;
done;

echo .. Grafana is up, set its datasource and dashboard

# add the data source to grafana
curl -X POST -i -H 'Content-Type: application/json' --data-binary @$SCRIPTPATH/grafana_datasource.json \
  http://admin:admin@${HOST}:${PORT}/api/datasources

# add the dashboard to grafana
# to use a changed dashboard (grafana_dashboard.json) from Grafana make a HTTP call to
# http://<host>:<port>/api/dashboards/db/hono and change the dashboard id in the resulting JSON to null
curl -X POST -i -H 'Content-Type: application/json' --data-binary @$SCRIPTPATH/grafana_dashboard.json \
  http://admin:admin@${HOST}:${PORT}/api/dashboards/db
