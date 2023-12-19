/*******************************************************************************
 * Copyright (c) 2020, 2023 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.service.base.jdbc.store.device;

import java.util.Optional;

import org.eclipse.hono.deviceregistry.service.device.DeviceKey;
import org.eclipse.hono.service.base.jdbc.store.AbstractStore;
import org.eclipse.hono.service.base.jdbc.store.Statement;
import org.eclipse.hono.service.base.jdbc.store.StatementConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.vertx.core.Future;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLOperations;

/**
 * An abstract base for implementing a device registration store.
 */
public abstract class AbstractDeviceStore extends AbstractStore {

    private static final Logger log = LoggerFactory.getLogger(AbstractDeviceStore.class);

    protected final JDBCClient client;
    protected final Tracer tracer;

    private final Statement readRegistrationStatement;

    /**
     * Create a new instance.
     *
     * @param client The client to use for accessing the DB.
     * @param tracer The tracer to use.
     * @param cfg The SQL statement configuration.
     */
    public AbstractDeviceStore(final JDBCClient client, final Tracer tracer, final StatementConfiguration cfg) {
        super(client, tracer, cfg.getStatement("checkConnection"));

        this.client = client;
        this.tracer = tracer;

        this.readRegistrationStatement = cfg
                .getRequiredStatement("readRegistration")
                .validateParameters(
                        "tenant_id",
                        "device_id");

    }

    /**
     * Read the registration data of a device.
     * <p>
     * This will execute the {@code readRegistration} statement and simply
     * return the result set unprocessed.
     *
     * @param operations The SQL operations to use.
     * @param key The key to the device entry.
     * @param span The span to contribute to.
     *
     * @return The future, tracking the outcome of the operation.
     */
    protected Future<ResultSet> readDevice(final SQLOperations operations, final DeviceKey key, final Span span) {
        return read(operations, key, this.readRegistrationStatement, span.context());
    }

    /**
     * Read the registration data of a device.
     * <p>
     * This will execute the provided statement statement and simply
     * return the result set unprocessed. The statement must accept the named
     * parameters {@code tenant_id} and {@code device_id}.
     *
     * @param operations The SQL operations to use.
     * @param key The key to the device entry.
     * @param statement The statement to execute.
     * @param spanContext The span to contribute to.
     *
     * @return The future, tracking the outcome of the operation.
     */
    protected Future<ResultSet> read(final SQLOperations operations, final DeviceKey key, final Statement statement, final SpanContext spanContext) {
        return read(operations, key, Optional.empty(), statement, spanContext);
    }

    /**
     * Read the registration data of a device.
     * <p>
     * This will execute the provided statement statement and simply
     * return the result set unprocessed. The statement must accept the named
     * parameters {@code tenant_id}, {@code device_id} and {@code expected_version} (if not empty).
     *
     * @param operations The SQL operations to use.
     * @param key The key to the device entry.
     * @param statement The statement to execute.
     * @param resourceVersion An optional resource version to read.
     * @param spanContext The span to contribute to.
     *
     * @return The future, tracking the outcome of the operation.
     */
    protected Future<ResultSet> read(final SQLOperations operations, final DeviceKey key, final Optional<String> resourceVersion, final Statement statement, final SpanContext spanContext) {

        final var expanded = statement.expand(params -> {
            params.put("tenant_id", key.getTenantId());
            params.put("device_id", key.getDeviceId());
            resourceVersion.ifPresent(version -> params.put("expected_version", version));
        });

        log.debug("read - statement: {}", expanded);

        return expanded
                .trace(this.tracer, spanContext)
                .query(operations);

    }

}
