FROM centos:7

ENV \
    GRAFANA_VERSION=5.1.3 \
    CONF_FILE=/etc/grafana/grafana.ini \
    DATA_DIR=/var/lib/grafana \
    LOG_DIR=/var/log/grafana \
    PLUGINS_DIR=/var/lib/grafana/plugins

RUN yum -y update \
    && yum -y install https://s3-us-west-2.amazonaws.com/grafana-releases/release/grafana-"$GRAFANA_VERSION"-1.x86_64.rpm \
    && yum clean all

EXPOSE 3000

RUN install -d 0777 /var/lib/grafana-external/dashboards
VOLUME [ \
    "/var/lib/grafana", \
    "/var/lib/grafana-external/dashboards" \
    ]

RUN \
    for i in /usr/share/grafana /etc/grafana /var/lib/grafana /var/log/grafana ; do \
       chmod -R a=rw "$i"; \
       find "$i" -type d -exec chmod a+x {} + ; \
    done

USER 1000

# Create dashboard provisioner

COPY dashboard-provisioner.yml /etc/grafana/provisioning/dashboards

WORKDIR /usr/share/grafana

ENTRYPOINT \
        /usr/sbin/grafana-server \
        --config=$CONF_FILE \
        cfg:default.paths.data=$DATA_DIR \
        cfg:default.paths.logs=$LOG_DIR \
        cfg:default.paths.plugins=$PLUGINS_DIR \
        cfg:default.paths.provisioning=/etc/grafana/provisioning \
        cfg:analytics.reporting_enabled=false \
        cfg:analytics.check_for_updates=false
