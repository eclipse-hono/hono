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

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.predicate.ResponsePredicate;
import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.service.metric.MicrometerBasedMetrics;
import org.eclipse.hono.util.PortConfigurationHelper;
import org.eclipse.hono.util.TenantObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.HttpURLConnection;
import java.util.Objects;
import java.util.Optional;

/**
 * An implementation of {@link ResourceLimitChecks} which uses metrics data from the prometheus backend to check if
 * further connections or messages are allowed by comparing with the configured limits.
 */
public final class PrometheusBasedResourceLimitChecks implements ResourceLimitChecks {
    private static final String CONNECTIONS_METRIC_NAME = MicrometerBasedMetrics.METER_CONNECTIONS_AUTHENTICATED
            .replace(".", "_");
    private final Logger log = LoggerFactory.getLogger(getClass());
    private final WebClient client;
    private String host;
    private int port = 9090;

    /**
     * Creates a new PrometheusBasedResourceLimitChecks instance.
     *
     * @param vertx The Vertx instance to use.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public PrometheusBasedResourceLimitChecks(final Vertx vertx) {
        this.client = WebClient.create(Objects.requireNonNull(vertx));
    }

    @Override
    public Future<?> isConnectionLimitExceeded(final TenantObject tenant) {
        Objects.requireNonNull(tenant);
        log.trace("Connections limit for tenant [{}] is [{}]", tenant.getTenantId(), tenant.getConnectionsLimit());
        if (tenant.getConnectionsLimit() != -1) {
            return queryForSumInPrometheus(CONNECTIONS_METRIC_NAME, tenant)
                    .recover(failure -> Future.succeededFuture(0L))
                    .compose(noOfConnections -> {
                        if (Optional.ofNullable(noOfConnections).orElse(0L) < tenant.getConnectionsLimit()) {
                            return Future.succeededFuture();
                        } else {
                            log.trace(
                                    "Connections limit exceeded for tenant. [tenant: {}, no.-Connections:{}, connections-limit:{}]",
                                    tenant.getTenantId(), noOfConnections, tenant.getConnectionsLimit());
                            return Future
                                    .failedFuture(new ClientErrorException(HttpURLConnection.HTTP_FORBIDDEN,
                                            String.format("Connections limit exceeded for tenant: [%s]",
                                                    tenant.getTenantId())));
                        }
                    });
        }
        return Future.succeededFuture(tenant);
    }

    /**
     * Gets the host of the prometheus backend.
     *
     * @return host The host of the Prometheus backend.
     */
    public String getHost() {
        return host;
    }

    /**
     * Sets the host of the prometheus backend. The default value of this property is empty.
     *
     * @param host The host of the Prometheus backend.
     */
    public void setHost(final String host) {
        this.host = Objects.requireNonNull(host);
    }

    /**
     * Gets the port of the prometheus backend.
     *
     * @return port The port of the Prometheus backend.
     */
    public int getPort() {
        return port;
    }

    /**
     * Sets the port of the prometheus backend. The default value of this property is 9090.
     *
     * @param port The port of the Prometheus backend.
     * @throws IllegalArgumentException if the port number is &lt; 0 or &gt; 2^16 - 1
     */
    public void setPort(final int port) {
        if (PortConfigurationHelper.isValidPort(port)) {
            this.port = port;
        } else {
            throw new IllegalArgumentException("invalid port number");
        }
    }

    private Future<Long> queryForSumInPrometheus(final String metricName, final TenantObject tenant) {
        final Future<Long> result = Future.future();
        final String queryUrl = String.format("http://%s:%s/api/v1/query", getHost(), getPort());
        final String queryParams = String.format("sum(%s{tenant=\"%s\"})", metricName, tenant.getTenantId());
        log.debug("Prometheus backend url: {}, queryParams: {}", queryUrl, queryParams);
        client.getAbs(queryUrl)
                .addQueryParam("query", queryParams)
                .expect(ResponsePredicate.SC_OK)
                .send(sendResult -> {
                    if (sendResult.succeeded()) {
                        final HttpResponse<Buffer> response = sendResult.result();
                        try {
                            Objects.requireNonNull(response);
                            result.complete(extractValue(response.bodyAsJsonObject()));
                        } catch (final Exception e) {
                            log.warn("Error fetching result from prometheus for tenant [{}]. Reason [{}]",
                                    tenant.getTenantId(), e.getMessage());
                            result.fail(String.format("Error fetching result from prometheus [%s]", e.getMessage()));
                        }
                    } else {
                        log.warn("Error fetching result from prometheus for tenant [{}]. Reason [{}]",
                                tenant.getTenantId(), sendResult.cause().getMessage());
                        result.fail(String.format("Error fetching result from prometheus [%s]",
                                sendResult.cause().getMessage()));
                    }
                });
        return result;
    }

    private Long extractValue(final JsonObject jsonObject) {
        Objects.requireNonNull(jsonObject);
        final String status = jsonObject.getString("status");
        if ("success".equals(status)) {
            final JsonObject data = jsonObject.getJsonObject("data");
            if (data != null) {
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
            }
            log.debug("No value available.");
        } else {
            log.warn("Error in query execution. [status:{}]", status);
        }
        return null;
    }
}
