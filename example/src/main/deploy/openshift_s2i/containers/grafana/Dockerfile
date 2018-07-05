FROM centos:7

# The GRAFANA_VERSION will be overridden by the OpenShift build

ENV \
    GRAFANA_VERSION=5.1.4 \
    CONF_FILE=/etc/grafana/grafana.ini \
    DATA_DIR=/var/lib/grafana \
    LOG_DIR=/var/log/grafana \
    PLUGINS_DIR=/var/lib/grafana/plugins \
    PROVISIONING_DIR=/etc/grafana/provisioning

RUN yum -y update \
    && yum -y install https://s3-us-west-2.amazonaws.com/grafana-releases/release/grafana-"$GRAFANA_VERSION"-1.x86_64.rpm \
    && yum clean all

EXPOSE 3000

VOLUME [ "/var/lib/grafana" ]

RUN \
    for i in /usr/share/grafana /etc/grafana /var/lib/grafana /var/log/grafana ; do \
       chmod -R a=rw "$i"; \
       find "$i" -type d -exec chmod a+x {} + ; \
    done

USER 1000

WORKDIR /usr/share/grafana

ENTRYPOINT \
        /usr/sbin/grafana-server \
        --config=$CONF_FILE \
        cfg:default.paths.data=$DATA_DIR \
        cfg:default.paths.logs=$LOG_DIR \
        cfg:default.paths.plugins=$PLUGINS_DIR \
        cfg:default.paths.provisioning=$PROVISIONING_DIR \
        cfg:analytics.reporting_enabled=false \
        cfg:analytics.check_for_updates=false
