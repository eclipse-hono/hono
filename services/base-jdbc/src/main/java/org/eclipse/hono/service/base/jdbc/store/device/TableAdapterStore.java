/*******************************************************************************
 * Copyright (c) 2020, 2022 Contributors to the Eclipse Foundation
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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.hono.deviceregistry.service.credentials.CredentialKey;
import org.eclipse.hono.deviceregistry.service.device.DeviceKey;
import org.eclipse.hono.service.base.jdbc.store.SQL;
import org.eclipse.hono.service.base.jdbc.store.Statement;
import org.eclipse.hono.service.base.jdbc.store.StatementConfiguration;
import org.eclipse.hono.service.management.credentials.CommonCredential;
import org.eclipse.hono.service.management.device.Device;
import org.eclipse.hono.tracing.TracingHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.vertx.core.Future;
import io.vertx.core.json.Json;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.ResultSet;

/**
 * A data store for devices and credentials, based on a table data model.
 */
public class TableAdapterStore extends AbstractDeviceStore {

    private static final Logger log = LoggerFactory.getLogger(TableAdapterStore.class);

    private final Statement findCredentialsStatement;
    private final Statement resolveGroupsStatement;
    private final String dialect;

    /**
     * Create a new instance.
     *
     * @param client The client to use for accessing the DB.
     * @param tracer The tracer to use.
     * @param cfg The SQL statement configuration.
     * @param dialect Database type, from the JDBC URL scheme
     */
    public TableAdapterStore(final JDBCClient client, final Tracer tracer, final StatementConfiguration cfg, final String dialect) {
        super(client, tracer, cfg);
        this.dialect = dialect;
        cfg.dump(log);

        this.findCredentialsStatement = cfg
                .getRequiredStatement("findCredentials")
                .validateParameters(
                        "tenant_id",
                        "type",
                        "auth_id");

        this.resolveGroupsStatement = cfg
                .getRequiredStatement("resolveGroups")
                .validateParameters(
                        "tenant_id",
                        "group_ids");

    }


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
                .withTag(TracingHelper.TAG_TENANT_ID, key.getTenantId())
                .withTag(TracingHelper.TAG_DEVICE_ID, key.getDeviceId())
                .start();

        return readDevice(this.client, key, span)

                .<Optional<DeviceReadResult>>flatMap(r -> {
                    final var entries = r.getRows(true);
                    switch (entries.size()) {
                        case 0:
                            return Future.succeededFuture(Optional.empty());
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

    /**
     * Find credentials for a device.
     *
     * @param key The credentials key to look for.
     * @param spanContext The span context.
     *
     * @return A future tracking the outcome of the operation.
     */
    public Future<Optional<CredentialsReadResult>> findCredentials(final CredentialKey key, final SpanContext spanContext) {

        final Span span = TracingHelper.buildChildSpan(this.tracer, spanContext, "find credentials", getClass().getSimpleName())
                .withTag(TracingHelper.TAG_AUTH_ID, key.getAuthId())
                .withTag(TracingHelper.TAG_CREDENTIALS_TYPE, key.getType())
                .withTag(TracingHelper.TAG_TENANT_ID, key.getTenantId())
                .start();

        final var expanded = this.findCredentialsStatement.expand(params -> {
            params.put("tenant_id", key.getTenantId());
            params.put("type", key.getType());
            params.put("auth_id", key.getAuthId());
        });

        log.debug("findCredentials - statement: {}", expanded);
        return expanded
                .trace(this.tracer, span.context())
                .query(this.client)
                .<Optional<CredentialsReadResult>>flatMap(r -> {
                    final var entries = r.getRows(true);
                    span.log(Map.of(
                            "event", "read result",
                            "rows", entries.size()));

                    final Set<String> deviceIds = entries.stream()
                            .map(o -> o.getString("device_id"))
                            .filter(Objects::nonNull)
                            .collect(Collectors.toSet());

                    final int num = deviceIds.size();
                    if (num <= 0) {
                        return Future.succeededFuture(Optional.empty());
                    } else if (num > 1) {
                        TracingHelper.logError(span, "Found multiple entries for a single device");
                        return Future.failedFuture(new IllegalStateException("Found multiple entries for a single device"));
                    }

                    // we know now that we have exactly one entry
                    final String deviceId = deviceIds.iterator().next();

                    final List<CommonCredential> credentials = entries.stream()
                            .map(o -> o.getString("data"))
                            .map(s -> Json.decodeValue(s, CommonCredential.class))
                            .collect(Collectors.toList());

                    return Future.succeededFuture(Optional.of(new CredentialsReadResult(deviceId, credentials, Optional.empty())));
                })

                .onComplete(x -> span.finish());

    }

    /**
     * Resolve a list of group members.
     *
     * @param tenantId The tenant the device belongs to.
     * @param viaGroups The viaGroups list of a device. This list contains the ids of groups.
     * @param spanContext The span to contribute to.
     *
     * @return A future tracking the outcome of the operation.
     */
    public Future<Set<String>> resolveGroupMembers(final String tenantId, final Set<String> viaGroups, final SpanContext spanContext) {

        final Span span = TracingHelper.buildChildSpan(this.tracer, spanContext, "resolve group members", getClass().getSimpleName())
                .withTag(TracingHelper.TAG_TENANT_ID, tenantId)
                .withTag("via_groups", String.join(", ", viaGroups))
                .start();

        final var expanded = this.resolveGroupsStatement.expand(params -> {
            params.put("tenant_id", tenantId);
            params.put("group_ids", convertToArrayValue(viaGroups));
        });

        log.debug("resolveGroupMembers - statement: {}", expanded);

        return expanded

                .trace(this.tracer, span.context())
                .query(this.client)

                .flatMap(r -> {

                    final var entries = r.getRows(true);
                    span.log(Map.of(
                            "event", "read result",
                            "rows", entries.size()));

                    return Future.succeededFuture(
                            entries.stream()
                                    .map(o -> o.getString("device_id"))
                                    .filter(Objects::nonNull)
                                    .collect(Collectors.toSet()));

                })

                .onComplete(x -> span.finish());

    }

    private Object convertToArrayValue(final Collection<String> values) {
        // SQLServer and Postgres driver fails to recognize String array value
        // pass as CSV string instead and use database specific functions
        // to convert the CSV string to an array
        // vertx jdbc doesn't support arrays, see https://stackoverflow.com/a/42295098
        // TODO: introduce a better way to configure the way array values get passed
        if (dialect.equals(SQL.DIALECT_POSTGRESQL)) {
            return String.join(",", values);
        }
        return values.toArray(String[]::new);
    }
}
