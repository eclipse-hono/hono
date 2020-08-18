/*******************************************************************************
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.service.base.jdbc.store.devcon;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import org.eclipse.hono.deviceregistry.service.deviceconnection.DeviceConnectionKey;
import org.eclipse.hono.service.base.jdbc.store.AbstractStore;
import org.eclipse.hono.service.base.jdbc.store.SQL;
import org.eclipse.hono.service.base.jdbc.store.Statement;
import org.eclipse.hono.service.base.jdbc.store.StatementConfiguration;
import org.eclipse.hono.tracing.TracingHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.vertx.core.Future;
import io.vertx.ext.sql.SQLClient;
import io.vertx.ext.sql.UpdateResult;

/**
 * A data store for device connection information.
 */
public class Store extends AbstractStore {

    public static final String DEFAULT_TABLE_NAME = "device_states";

    private static final Logger log = LoggerFactory.getLogger(Store.class);

    private final SQLClient client;
    private final Tracer tracer;

    private final Statement readStatement;
    private final Statement updateStatement;
    private final Statement dropTenantStatement;

    /**
     * Create a new instance.
     *
     * @param client The SQL client to use.
     * @param tracer The tracer to use.
     * @param cfg The statement configuration to use.
     */
    public Store(final SQLClient client, final Tracer tracer, final StatementConfiguration cfg) {
        super(client, tracer, cfg.getStatement("checkConnection"));
        cfg.dump(log);

        this.client = client;
        this.tracer = tracer;

        this.readStatement = cfg
                .getRequiredStatement("read")
                .validateParameters(
                        "tenant_id",
                        "device_id");

        this.updateStatement = cfg.getRequiredStatement("update")
                .validateParameters(
                        "tenant_id",
                        "device_id",
                        "gateway_id");

        this.dropTenantStatement = cfg.getRequiredStatement("dropTenant")
                .validateParameters("tenant_id");

    }

    /**
     * Create a device statement configuration for the device connection store.
     *
     * @param jdbcUrl The JDBC URL for detecting the database dialect.
     * @param tableName The table name to use.
     * @return The new configuration.
     * @throws IOException in case an IO error occurs.
     */
    public static StatementConfiguration defaultStatementConfiguration(final String jdbcUrl, final Optional<String> tableName) throws IOException {

        final String dialect = SQL.getDatabaseDialect(jdbcUrl);
        final String tableNameString = tableName.orElse(DEFAULT_TABLE_NAME);

        return StatementConfiguration
                .empty(tableNameString)
                .overrideWithDefaultPattern("base", dialect, Store.class, StatementConfiguration.DEFAULT_PATH.resolve("org/eclipse/hono/service/base/jdbc/store/devcon"));

    }

    /**
     * Read the state of a device.
     * <p>
     * This will execute the {@code read} statement, read the column named {@code last_known_gateway},
     * if there is exactly one entry.
     * <p>
     * If there are no entries, and empty result will be returned.
     * <p>
     * If more than one entry is being found, the result will be failed with an
     * {@link IllegalStateException} exception.
     *
     * @param key The key to the device entry.
     * @param spanContext The span to contribute to.
     *
     * @return The future, tracking the outcome of the operation.
     */
    public Future<Optional<DeviceState>> readDeviceState(final DeviceConnectionKey key, final SpanContext spanContext) {

        final Span span = TracingHelper.buildChildSpan(this.tracer, spanContext, "read device state", getClass().getSimpleName())
                .withTag("tenant_instance_id", key.getTenantId())
                .withTag("device_id", key.getDeviceId())
                .start();

        final var expanded = this.readStatement.expand(map -> {
            map.put("tenant_id", key.getTenantId());
            map.put("device_id", key.getDeviceId());
        });

        log.debug("readDeviceState - statement: {}", expanded);
        final var result = expanded.trace(this.tracer, span.context()).query(this.client);

        final var f = result
                .<Optional<DeviceState>>flatMap(r -> {
                    final var entries = r.getRows(true);
                    span.log(Map.of(
                            "event", "read result",
                            "rows", entries.size()));
                    switch (entries.size()) {
                        case 0:
                            return Future.succeededFuture(Optional.empty());
                        case 1:
                            final var entry = entries.get(0);
                            final var state = new DeviceState();
                            state.setLastKnownGateway(Optional.ofNullable(entry.getString("last_known_gateway")));
                            return Future.succeededFuture(Optional.of(state));
                        default:
                            return Future.failedFuture(new IllegalStateException("Found multiple entries for a single device"));
                    }
                });

        return f.onComplete(x -> span.finish());

    }

    /**
     * Set the last known gateway information of the device state.
     * <p>
     * This will execute the {@code update} statement to update the entry.
     *
     * @param key The key to the device entry.
     * @param spanContext The span to contribute to.
     * @param gatewayId The value to set.
     *
     * @return The future, tracking the outcome of the operation.
     */
    public Future<UpdateResult> setLastKnownGateway(final DeviceConnectionKey key, final String gatewayId, final SpanContext spanContext) {

        final Span span = TracingHelper.buildChildSpan(this.tracer, spanContext, "update device state", getClass().getSimpleName())
                .withTag("tenant_instance_id", key.getTenantId())
                .withTag("device_id", key.getDeviceId())
                .withTag("gateway_id", gatewayId)
                .start();

        final var expanded = this.updateStatement.expand(params -> {
            params.put("tenant_id", key.getTenantId());
            params.put("device_id", key.getDeviceId());
            params.put("gateway_id", gatewayId);
        });

        log.debug("setLastKnownGateway - statement: {}", expanded);
        final var result = expanded.trace(this.tracer, span.context()).update(this.client);

        return result.onComplete(x -> span.finish());

    }

    /**
     * Drop all entries for a tenant.
     *
     * @param tenantId The tenant to drop.
     * @param spanContext The span to contribute to.
     *
     * @return The future, tracking the outcome of the operation.
     */
    public Future<UpdateResult> dropTenant(final String tenantId, final SpanContext spanContext) {

        final Span span = TracingHelper.buildChildSpan(this.tracer, spanContext, "drop tenant", getClass().getSimpleName())
                .withTag("tenant_instance_id", tenantId)
                .start();

        final var expanded = this.dropTenantStatement.expand(params -> {
            params.put("tenant_id", tenantId);
        });

        log.debug("dropTenant - statement: {}", expanded);
        final var result = expanded.trace(this.tracer, span.context()).update(this.client);

        return result.onComplete(x -> span.finish());

    }

}
