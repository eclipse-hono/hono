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

package org.eclipse.hono.service.base.jdbc.store.device;

import java.util.Optional;

import org.eclipse.hono.deviceregistry.service.credentials.CredentialKey;
import org.eclipse.hono.deviceregistry.service.device.DeviceKey;
import org.eclipse.hono.service.base.jdbc.store.StatementConfiguration;
import org.eclipse.hono.service.management.device.Device;
import org.eclipse.hono.tracing.TracingHelper;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.vertx.core.Future;
import io.vertx.core.json.Json;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLClient;

/**
 * Create a new abstract device registry store for the adapter side.
 */
public abstract class AbstractDeviceAdapterStore extends AbstractDeviceStore {

    /**
     * Create a new instance.
     *
     * @param client The SQL client to use.
     * @param tracer The tracer to use.
     * @param cfg The SQL statement configuration.
     */
    public AbstractDeviceAdapterStore(final SQLClient client, final Tracer tracer, final StatementConfiguration cfg) {
        super(client, tracer, cfg);
    }

    /**
     * Find credentials for a device.
     *
     * @param key The credentials key to look for.
     * @param spanContext The span context.
     *
     * @return A future tracking the outcome of the operation.
     */
    public abstract Future<Optional<CredentialsReadResult>> findCredentials(CredentialKey key, SpanContext spanContext);

    /**
     * Read a device using {@link #readDevice(io.vertx.ext.sql.SQLOperations, DeviceKey, Span)} and the
     * current SQL client.
     *
     * @param key The key of the device to read.
     * @param span The span to contribute to.
     *
     * @return The result from {@link #readDevice(io.vertx.ext.sql.SQLOperations, DeviceKey, Span)}.
     */
    protected Future<ResultSet> readDevice(final DeviceKey key, final Span span) {
        return readDevice(this.client, key, span);
    }

    /**
     * Reads the device data.
     * <p>
     * This reads the device data using
     * {@link #readDevice(io.vertx.ext.sql.SQLOperations, DeviceKey, Span)} and
     * transforms the plain result into a {@link DeviceReadResult}.
     * <p>
     * If now rows where found, the result will be empty. If more than one row is found,
     * the result will be failed with an {@link IllegalStateException}.
     * <p>
     * If there is exactly one row, it will read the device registration information from the column
     * {@code data} and optionally current resource version from the column {@code version}.
     *
     * @param key The key of the device to read.
     * @param spanContext The span to contribute to.
     *
     * @return A future, tracking the outcome of the operation.
     */
    public Future<Optional<DeviceReadResult>> readDevice(final DeviceKey key, final SpanContext spanContext) {

        final Span span = TracingHelper.buildChildSpan(this.tracer, spanContext, "read device", getClass().getSimpleName())
                .withTag("tenant_instance_id", key.getTenantId())
                .withTag("device_id", key.getDeviceId())
                .start();

        return readDevice(this.client, key, span)

                .<Optional<DeviceReadResult>>flatMap(r -> {
                    final var entries = r.getRows(true);
                    switch (entries.size()) {
                        case 0:
                            return Future.succeededFuture((Optional.empty()));
                        case 1:
                            final var entry = entries.get(0);
                            final var device = Json.decodeValue(entry.getString("data"), Device.class);
                            final var version = Optional.ofNullable(entry.getString("version"));
                            return Future.succeededFuture(Optional.of(new DeviceReadResult(device, version)));
                        default:
                            return Future.failedFuture(new IllegalStateException("Found multiple entries for a single device"));
                    }
                })

                .onComplete(x -> span.finish());

    }

}
