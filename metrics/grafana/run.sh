#!/bin/bash -e

: "${GF_PATHS_DATA:=/var/lib/grafana}"
: "${GF_PATHS_LOGS:=/var/log/grafana}"
: "${GF_PATHS_PLUGINS:=/var/lib/grafana/plugins}"
: "${CONFIG_FILE:=/etc/grafana/grafana.ini}"

if [ -z "$GF_DATABASE_PATH" ] && [ ! -f $GF_PATHS_DATA/grafana.db ]; then
	echo Using default Hono database file...
	cp /tmp/grafana.db $GF_PATHS_DATA/
fi

if [ ! -z "${GF_INSTALL_PLUGINS}" ]; then
  for plugin in ${GF_INSTALL_PLUGINS}; do
    grafana-cli  --pluginsDir "${GF_PATHS_PLUGINS}" plugins install ${plugin}
  done
fi

exec /usr/sbin/grafana-server                   \
  --homepath=/usr/share/grafana                 \
  --config=$CONFIG_FILE                         \
  cfg:default.log.mode="console"                \
  cfg:default.paths.data="$GF_PATHS_DATA"       \
  cfg:default.paths.logs="$GF_PATHS_LOGS"       \
  cfg:default.paths.plugins="$GF_PATHS_PLUGINS" \
  "$@"
