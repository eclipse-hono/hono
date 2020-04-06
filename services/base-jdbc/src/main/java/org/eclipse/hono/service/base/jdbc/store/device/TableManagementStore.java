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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.eclipse.hono.deviceregistry.service.device.DeviceKey;
import org.eclipse.hono.service.base.jdbc.store.EntityNotFoundException;
import org.eclipse.hono.service.base.jdbc.store.OptimisticLockingException;
import org.eclipse.hono.service.base.jdbc.store.SQL;
import org.eclipse.hono.service.base.jdbc.store.Statement;
import org.eclipse.hono.service.base.jdbc.store.StatementConfiguration;
import org.eclipse.hono.service.management.credentials.CommonCredential;
import org.eclipse.hono.tracing.TracingHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.log.Fields;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLClient;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.sql.UpdateResult;

/**
 * A data store for devices and credentials, based on a table data model.
 */
public class TableManagementStore extends AbstractDeviceManagementStore {

    private static final Logger log = LoggerFactory.getLogger(TableManagementStore.class);

    private final Statement readForUpdateStatement;
    private final Statement readForUpdateVersionedStatement;
    private final Statement readCredentialsStatement;

    private final Statement insertCredentialEntryStatement;
    private final Statement deleteAllCredentialsStatement;
    private final Statement updateDeviceVersionStatement;

    /**
     * Create a new instance.
     *
     * @param client The SQL client ot use.
     * @param tracer The tracer to use.
     * @param cfg The SQL statement configuration.
     */
    public TableManagementStore(final SQLClient client, final Tracer tracer, final StatementConfiguration cfg) {
        super(client, tracer, cfg);
        cfg.dump(log);

        this.readForUpdateStatement = cfg.getRequiredStatment("readForUpdate")
                .validateParameters(
                        "tenant_id",
                        "device_id");
        this.readForUpdateVersionedStatement = cfg.getRequiredStatment("readForUpdateVersioned")
                .validateParameters(
                        "tenant_id",
                        "device_id",
                        "expected_version");

        this.readCredentialsStatement = cfg
                .getRequiredStatment("readCredentials")
                .validateParameters(
                        "tenant_id",
                        "device_id");

        this.insertCredentialEntryStatement = cfg
                .getRequiredStatment("insertCredentialEntry")
                .validateParameters(
                        "tenant_id",
                        "device_id",
                        "type",
                        "auth_id",
                        "data");

        this.deleteAllCredentialsStatement = cfg
                .getRequiredStatment("deleteAllCredentials")
                .validateParameters(
                        "tenant_id",
                        "device_id");

        this.updateDeviceVersionStatement = cfg
                .getRequiredStatment("updateDeviceVersion")
                .validateParameters(
                        "tenant_id",
                        "device_id",
                        "next_version",
                        "expected_version");

    }

    /**
     * Read a device and lock it for updates.
     * <p>
     * This uses the {@code readForUpdate} or {@code readForUpdateVersioned} statement
     * to read and lock the device entry for further updates (select for update).
     * <p>
     * It returns the plain result set from the query, which may also be empty.
     *
     * @param connection The connection to use.
     * @param key The key of the device.
     * @param resourceVersion An optional resource version.
     * @param span The span to contribute to.
     *
     * @return A future tracking the outcome of the operation.
     */
    protected Future<ResultSet> readDeviceForUpdate(final SQLConnection connection, final DeviceKey key, final Optional<String> resourceVersion, final Span span) {

        final Statement readStatement;

        if (resourceVersion.isPresent()) {
            readStatement = this.readForUpdateVersionedStatement;
        } else {
            readStatement = this.readForUpdateStatement;
        }

        return read(connection, key, resourceVersion, readStatement, span);

    }

    @Override
    public Future<Boolean> setCredentials(final DeviceKey key, final List<CommonCredential> credentials, final Optional<String> resourceVersion,
            final SpanContext spanContext) {

        final String json = Json.encode(credentials.toArray(CommonCredential[]::new));

        final Span span = TracingHelper.buildChildSpan(this.tracer, spanContext, "set credentials", getClass().getSimpleName())
                .withTag("tenant_instance_id", key.getTenantId())
                .withTag("device_id", key.getDeviceId())
                .withTag("data", json)
                .start();

        resourceVersion.ifPresent(version -> span.setTag("version", version));

        final String nextVersion = UUID.randomUUID().toString();

        final Promise<SQLConnection> promise = Promise.promise();
        this.client.getConnection(promise);

        return promise.future()

                // disable autocommit, which is enabled by default
                .flatMap(connection -> SQL.setAutoCommit(this.tracer, span.context(), connection, false))

                // read the device "for update", locking the entry
                .flatMap(connection -> readDeviceForUpdate(connection, key, resourceVersion, span)

                        // check if we got back a result, if not this will abort early
                        .flatMap(TableManagementStore::extractVersionForUpdate)

                        // take the version and start processing on
                        .flatMap(version -> this.deleteAllCredentialsStatement

                                // delete the existing entries
                                .expand(map -> {
                                    map.put("tenant_id", key.getTenantId());
                                    map.put("device_id", key.getDeviceId());
                                })
                                .trace(this.tracer, span).update(connection)

                                // then create new entries
                                .flatMap(x -> CompositeFuture.all(credentials.stream()
                                        .map(JsonObject::mapFrom)
                                        .filter(c -> c.containsKey("type") && c.containsKey("auth-id"))
                                        .map(c -> this.insertCredentialEntryStatement
                                                .expand(map -> {
                                                    map.put("tenant_id", key.getTenantId());
                                                    map.put("device_id", key.getDeviceId());
                                                    map.put("type", c.getString("type"));
                                                    map.put("auth_id", c.getString("auth-id"));
                                                    map.put("data", c.toString());
                                                })
                                                .trace(this.tracer, span).update(connection))
                                        .collect(Collectors.toList())).map(x))

                                // update the version, this will release the lock
                                .flatMap(x -> this.updateDeviceVersionStatement
                                        .expand(map -> {
                                            map.put("tenant_id", key.getTenantId());
                                            map.put("device_id", key.getDeviceId());
                                            map.put("expected_version", version);
                                            map.put("next_version", nextVersion);
                                        })
                                        .trace(this.tracer, span).update(connection)

                                        // check the update outcome
                                        .flatMap(updateResult -> checkUpdateOutcome(updateResult)))

                        )

                        // commit or rollback ... return original result
                        .flatMap(x -> SQL.commit(this.tracer, span.context(), connection).map(true))
                        .recover(x -> SQL.rollback(this.tracer, span.context(), connection).flatMap(y -> Future.<Boolean>failedFuture(x))))

                .recover(err -> recoverNotFound(span, err, () -> false))

                .onComplete(x -> span.finish());

    }

    private <T> Future<T> recoverNotFound(final Span span, final Throwable err, final Supplier<T> orProvider) {
        log.debug("Failed to update", err);
        // map EntityNotFoundException to proper result
        if (SQL.hasCauseOf(err, EntityNotFoundException.class)) {
            TracingHelper.logError(span, "Entity not found");
            return Future.succeededFuture(orProvider.get());
        } else {
            return Future.failedFuture(err);
        }
    }

    private static Future<Object> checkUpdateOutcome(final UpdateResult updateResult) {
        if (updateResult.getUpdated() < 0) {
            // conflict
            log.debug("Optimistic lock broke");
            return Future.failedFuture(new OptimisticLockingException());
        }

        return Future.succeededFuture();
    }

    private static Future<String> extractVersionForUpdate(final ResultSet device) {
        final Optional<String> version = device.getRows(true).stream().map(o -> o.getString("version")).findAny();

        if (version.isEmpty()) {
            log.debug("No version or no row found -> entity not found");
            return Future.failedFuture(new EntityNotFoundException());
        }

        return Future.succeededFuture(version.get());
    }

    @Override
    public Future<Optional<CredentialsReadResult>> getCredentials(final DeviceKey key, final SpanContext spanContext) {

        final Span span = TracingHelper.buildChildSpan(this.tracer, spanContext, "get credentials", getClass().getSimpleName())
                .withTag("tenant_instance_id", key.getTenantId())
                .withTag("device_id", key.getDeviceId())
                .start();

        final Statement query = this.readCredentialsStatement;
        final var expanded = query.expand(map -> {
            map.put("tenant_id", key.getTenantId());
            map.put("device_id", key.getDeviceId());
        });

        final Promise<SQLConnection> promise = Promise.promise();
        this.client.getConnection(promise);

        return promise.future()

                .flatMap(connection -> readDevice(connection, key, span)

                        // check if we got back a result, if not this will abort early

                        .flatMap(TableManagementStore::extractVersionForUpdate)

                        // read credentials

                        .flatMap(version -> expanded.trace(this.tracer, span).query(connection)

                                .flatMap(r -> {

                                    final var entries = r.getRows(true);
                                    span.log(Map.of(
                                            Fields.EVENT, "read result",
                                            "rows", entries.size()));

                                    final List<CommonCredential> credentials = entries.stream()
                                            .map(o -> o.getString("data"))
                                            .map(s -> Json.decodeValue(s, CommonCredential.class))
                                            .collect(Collectors.toList());

                                    log.debug("Credentials: {}", credentials);

                                    return Future.succeededFuture(Optional.of(new CredentialsReadResult(key.getDeviceId(), credentials, Optional.ofNullable(version))));
                                })))
                .recover(err -> recoverNotFound(span, err, () -> Optional.empty()))

                .onComplete(x -> span.finish());

    }

}
