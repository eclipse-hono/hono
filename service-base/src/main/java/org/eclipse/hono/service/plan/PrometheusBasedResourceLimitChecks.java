/*******************************************************************************
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.hono.service.plan;

import java.util.Objects;

import javax.annotation.PostConstruct;

import org.eclipse.hono.service.metric.MicrometerBasedMetrics;
import org.eclipse.hono.util.PortConfigurationHelper;
import org.eclipse.hono.util.TenantObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.predicate.ResponsePredicate;
import io.vertx.ext.web.codec.BodyCodec;

/**
 * Resource limit checks which compare configured limits with live metrics retrieved
 * from a <em>Prometheus</em> server.
 */
public final class PrometheusBasedResourceLimitChecks implements ResourceLimitChecks {

    private static final String CONNECTIONS_METRIC_NAME = MicrometerBasedMetrics.METER_CONNECTIONS_AUTHENTICATED
            .replace(".", "_");
    private static final Logger log = LoggerFactory.getLogger(PrometheusBasedResourceLimitChecks.class);

    private final WebClient client;
    private String host;
    private int port = 9090;
    private String queryUrl;

    /**
     * Creates new checks.
     *
     * @param vertx The vert.x instance to use for creating a web client.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public PrometheusBasedResourceLimitChecks(final Vertx vertx) {
        this.client = WebClient.create(Objects.requireNonNull(vertx));
    }

    @Override
    public Future<Boolean> isConnectionLimitReached(final TenantObject tenant) {

        Objects.requireNonNull(tenant);

        log.trace("connection limit for tenant [{}] is [{}]", tenant.getTenantId(), tenant.getConnectionsLimit());

        if (tenant.getConnectionsLimit() == -1) {
            return Future.succeededFuture(Boolean.FALSE);
        } else {
            final String queryParams = String.format("sum(%s{tenant=\"%s\"})", CONNECTIONS_METRIC_NAME,
                    tenant.getTenantId());
            return executeQuery(queryParams)
                    .map(currentConnections -> {
                        if (currentConnections < tenant.getConnectionsLimit()) {
                            return Boolean.FALSE;
                        } else {
                            log.trace(
                                    "connection limit exceeded [tenant: {}, current connections: {}, max-connections: {}]",
                                    tenant.getTenantId(), currentConnections, tenant.getConnectionsLimit());
                            return Boolean.TRUE;
                        }
                    }).otherwise(Boolean.FALSE);
        }
    }

    /**
     * Gets the host of the Prometheus server to retrieve metrics from.
     *
     * @return host The host name or IP address.
     */
    public String getHost() {
        return host;
    }

    /**
     * Sets the host of the Prometheus server to retrieve metrics from.
     * <p>
     * The default value of this property is {@code null}.
     *
     * @param host The host name or IP address.
     */
    public void setHost(final String host) {
        this.host = Objects.requireNonNull(host);
    }

    /**
     * Gets the port of the Prometheus server to retrieve metrics from.
     *
     * @return port The port number.
     */
    public int getPort() {
        return port;
    }

    /**
     * Sets the port of the Prometheus server to retrieve metrics from.
     * <p>
     * The default value of this property is 9090.
     *
     * @param port The port number.
     * @throws IllegalArgumentException if the port number is &lt; 0 or &gt; 2^16 - 1
     */
    public void setPort(final int port) {
        if (PortConfigurationHelper.isValidPort(port)) {
            this.port = port;
        } else {
            throw new IllegalArgumentException("invalid port number");
        }
    }

    @PostConstruct
    private void init() {
        queryUrl = String.format("http://%s:%s/api/v1/query", getHost(), getPort());
    }

    private Future<Long> executeQuery(final String query) {

        final Future<Long> result = Future.future();
        log.trace("running query [{}] against Prometheus backend [{}]", query, queryUrl);
        client.getAbs(queryUrl)
        .addQueryParam("query", query)
        .expect(ResponsePredicate.SC_OK)
        .as(BodyCodec.jsonObject())
        .send(sendAttempt -> {
            if (sendAttempt.succeeded()) {
                final HttpResponse<JsonObject> response = sendAttempt.result();
                result.complete(extractLongValue(response.body()));
            } else {
                log.debug("error fetching result from Prometheus [url: {}, query: {}]: {}",
                        queryUrl, query, sendAttempt.cause().getMessage());
                result.fail(sendAttempt.cause());
            }
        });
        return result;
    }

    /**
     * Extracts a long value from the JSON result returned by the Prometheus
     * server.
     * <p>
     * The result is expected to have the following structure:
     * <pre>
     * {
     *   "status": "success",
     *   "data": {
     *     "result": [
     *       {
     *         "value": [ $timestamp, "$value" ]
     *       }
     *     ]
     *   }
     * }
     * </pre>
     * 
     * @param response The response object.
     * @return The extracted value.
     * @see <a href="https://prometheus.io/docs/prometheus/latest/querying/api/">Prometheus HTTP API</a>
     */
    private Long extractLongValue(final JsonObject response) {

        Objects.requireNonNull(response);

        try {
            final String status = response.getString("status");
            if ("error".equals(status)) {
                log.debug("error while executing query [status: {}, error type: {}, error: {}]",
                        status, response.getString("errorType"), response.getString("error"));
                return 0L;
            } else {
                // success
                final JsonObject data = response.getJsonObject("data", new JsonObject());
                final JsonArray result = data.getJsonArray("result");
                if (result != null && result.size() == 1 && result.getJsonObject(0) != null) {
                    final JsonArray valueArray = result.getJsonObject(0).getJsonArray("value");
                    if (valueArray != null && valueArray.size() == 2) {
                        final String value = valueArray.getString(1);
                        if (value != null && !value.isEmpty()) {
                            return Long.parseLong(value);
                        }
                    }
                }
                log.debug("received malformed response from Prometheus server: {}", response.encodePrettily());
            }
        } catch (Exception e) {
            log.debug("received malformed response from Prometheus server: {}", response.encodePrettily(), e);
        }
        return 0L;
    }
}
