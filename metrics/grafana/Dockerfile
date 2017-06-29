FROM debian:jessie

ARG DOWNLOAD_URL

RUN apt-get update && \
    apt-get -y --no-install-recommends install libfontconfig curl ca-certificates && \
    apt-get clean && \
    curl ${DOWNLOAD_URL} > /tmp/grafana.deb && \
    dpkg -i /tmp/grafana.deb && \
    rm /tmp/grafana.deb && \
    apt-get autoremove -y && \
    rm -rf /var/lib/apt/lists/*

ADD run.sh /
ADD grafana.db /tmp/

RUN chown -R root:root /var/lib/grafana && \
    chown -R root:root /var/log/grafana && \
    chmod +x /run.sh && \
    chmod -R o+r /etc/grafana && \
    chmod -R o+r /var/lib/grafana

EXPOSE 3000

VOLUME ["/var/lib/grafana", "/var/log/grafana", "/etc/grafana"]

ENTRYPOINT ["/run.sh"]
