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

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.eclipse.hono.deviceregistry.service.device.DeviceKey;
import org.eclipse.hono.service.base.jdbc.store.DuplicateKeyException;
import org.eclipse.hono.service.base.jdbc.store.OptimisticLockingException;
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
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLClient;
import io.vertx.ext.sql.SQLOperations;
import io.vertx.ext.sql.UpdateResult;

/**
 * Create a new abstract device registry store for the management side.
 */
public abstract class AbstractDeviceManagementStore extends AbstractDeviceStore {

    private static final Logger log = LoggerFactory.getLogger(AbstractDeviceManagementStore.class);

    private final Statement createStatement;

    private final Statement updateRegistrationStatement;
    private final Statement updateRegistrationVersionedStatement;
    private final Statement deleteStatement;
    private final Statement deleteVersionedStatement;
    private final Statement dropTenantStatement;

    /**
     * Create a new instance.
     *
     * @param client The SQL client to use.
     * @param tracer The tracer to use.
     * @param cfg The SQL statement configuration.
     */
    public AbstractDeviceManagementStore(final SQLClient client, final Tracer tracer, final StatementConfiguration cfg) {
        super(client, tracer, cfg);

        this.createStatement = cfg
                .getRequiredStatment("create")
                .validateParameters(
                        "tenant_id",
                        "device_id",
                        "version",
                        "data");

        this.updateRegistrationStatement = cfg
                .getRequiredStatment("updateRegistration")
                .validateParameters(
                        "tenant_id",
                        "device_id",
                        "next_version",
                        "data");

        this.updateRegistrationVersionedStatement = cfg
                .getRequiredStatment("updateRegistrationVersioned")
                .validateParameters(
                        "tenant_id",
                        "device_id",
                        "next_version",
                        "data",
                        "expected_version");

        this.deleteStatement = cfg
                .getRequiredStatment("delete")
                .validateParameters(
                        "tenant_id",
                        "device_id");

        this.deleteVersionedStatement = cfg
                .getRequiredStatment("deleteVersioned")
                .validateParameters(
                        "tenant_id",
                        "device_id",
                        "expected_version");

        this.dropTenantStatement = cfg
                .getRequiredStatment("dropTenant")
                .validateParameters(
                        "tenant_id");

    }

    /**
     * Get all credentials for a device.
     * <p>
     * The gets the credentials of a device. If the device cannot be found, the
     * result must be empty. If no credentials could be found for an existing device,
     * the result must not be empty, but provide an empty {@link CredentialsReadResult}.
     *
     * @param key The key of the device.
     * @param spanContext The span to contribute to.
     *
     * @return A future, tracking the outcome of the operation.
     */
    public abstract Future<Optional<CredentialsReadResult>> getCredentials(DeviceKey key, SpanContext spanContext);

    /**
     * Set all credentials for a device.
     * <p>
     * This will set/replace all credentials of the device. If the device does not exists, the result
     * will be {@code false}. If the update was successful, then the result will be {@code true}.
     * If the resource version was provided, but the provided version was no longer the current version,
     * then the future will fail with a {@link OptimisticLockingException}.
     *
     * @param key The key of the device to update.
     * @param credentials The credentials to set.
     * @param resourceVersion The optional resource version to update.
     * @param spanContext The span to contribute to.
     *
     * @return A future, tracking the outcome of the operation.
     */
    public abstract Future<Boolean> setCredentials(DeviceKey key, List<CommonCredential> credentials, Optional<String> resourceVersion, SpanContext spanContext);

    /**
     * Create a new device.
     * <p>
     * This method executes the {@code create} statement, providing the named parameters
     * {@code tenant_id}, {@code device_id}, {@code version}, and {@code data}.
     * <p>
     * It returns the plain update result. In case a device with the same ID already
     * exists, the underlying database must throw an {@link SQLException}, indicating
     * a duplicate entity or constraint violation. This will be translated into a
     * failed future with an {@link DuplicateKeyException}.
     *
     * @param key The key of the device to create.
     * @param device The device data.
     * @param spanContext The span to contribute to.
     *
     * @return A future, tracking the outcome of the operation.
     */
    public Future<UpdateResult> createDevice(final DeviceKey key, final Device device, final SpanContext spanContext) {

        final String json = Json.encode(device);

        final Span span = TracingHelper.buildChildSpan(this.tracer, spanContext, "create device", getClass().getSimpleName())
                .withTag("tenant_instance_id", key.getTenantId())
                .withTag("device_id", key.getDeviceId())
                .withTag("data", json)
                .start();

        final var expanded = this.createStatement.expand(params -> {
            params.put("tenant_id", key.getTenantId());
            params.put("device_id", key.getDeviceId());
            params.put("version", UUID.randomUUID().toString());
            params.put("data", json);
        });

        log.debug("createDevice - statement: {}", expanded);

        return expanded
                .trace(this.tracer, span)
                .update(this.client)
                .recover(SQL::translateException)
                .onComplete(x -> span.finish());

    }

    /**
     * Update a field of device information entry.
     * <p>
     * The method executes the provided statement, setting the named parameters
     * {@code tenant_id}, {@code device_id}, {@code next_version} and {@code data}.
     * Additionally it will provide the named parameter {@code expected_version}, if
     * resource version is not empty.
     * <p>
     * The update must only be performed if the resource version is either empty
     * or matches the current version.
     * <p>
     * It returns the plain update result, which includes the number of rows changes.
     * This is one, if the device was updated. It may also be zero, if the device does
     * not exists. If the device exists, but the resource version does not match, the result
     * will fail with an {@link OptimisticLockingException}.
     *
     * @param key The key of the device to update.
     * @param statement The statement to use for the update.
     * @param jsonValue The value to set.
     * @param resourceVersion The optional resource version.
     * @param span The span to contribute to.
     *
     * @return A future, tracking the outcome of the operation.
     */
    protected Future<UpdateResult> updateJsonField(final DeviceKey key, final Statement statement, final String jsonValue, final Optional<String> resourceVersion,
            final Span span) {

        final var expanded = statement.expand(map -> {
            map.put("tenant_id", key.getTenantId());
            map.put("device_id", key.getDeviceId());
            map.put("next_version", UUID.randomUUID().toString());
            map.put("data", jsonValue);
            resourceVersion.ifPresent(version -> map.put("expected_version", version));
        });

        log.debug("update - statement: {}", expanded);

        // execute update
        final var result = expanded.trace(this.tracer, span).update(this.client);

        // process result, check optimistic lock
        return checkOptimisticLock(
                result, span,
                resourceVersion,
                checkSpan -> readDevice(this.client, key, checkSpan));
    }

    /**
     * Update device registration information.
     * <p>
     * This called the {@link #updateJsonField(DeviceKey, Statement, String, Optional, Span)} method
     * with either the {@code updateRegistration} or {@code updateRegistrationVersioned}
     * statement.
     *
     * @param key The key of the device to update.
     * @param device The device data to store.
     * @param resourceVersion The optional resource version.
     * @param spanContext The span to contribute to.
     *
     * @return A future, tracking the outcome of the operation.
     */
    public Future<UpdateResult> updateDevice(final DeviceKey key, final Device device, final Optional<String> resourceVersion, final SpanContext spanContext) {

        final String json = Json.encode(device);

        final Span span = TracingHelper.buildChildSpan(this.tracer, spanContext, "update device", getClass().getSimpleName())
                .withTag("tenant_instance_id", key.getTenantId())
                .withTag("device_id", key.getDeviceId())
                .withTag("data", json)
                .start();

        resourceVersion.ifPresent(version -> span.setTag("version", version));

        final Statement statement = resourceVersion.isPresent() ? this.updateRegistrationVersionedStatement : this.updateRegistrationStatement;

        return updateJsonField(key, statement, json, resourceVersion, span)
                .onComplete(x -> span.finish());

    }

    @Override
    protected Future<ResultSet> read(final SQLOperations operations, final DeviceKey key, final Optional<String> resourceVersion, final Statement statement, final Span span) {

        final var expanded = statement.expand(params -> {
            params.put("tenant_id", key.getTenantId());
            params.put("device_id", key.getDeviceId());
            resourceVersion.ifPresent(version -> params.put("expected_version", version));
        });

        log.debug("read - statement: {}", expanded);

        return expanded
                .trace(this.tracer, span)
                .query(this.client);
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

    /**
     * Delete a single device.
     * <p>
     * This will execute the {@code delete} or {@code deleteVersioned} SQL statement and provide
     * the named parameters {@code tenant_id}, {@code device_id}, and {@code expected_version} (if set).
     * It will return the plain update result of the operation.
     *
     * @param key The key of the device to delete.
     * @param resourceVersion An optional resource version.
     * @param spanContext The span to contribute to.
     *
     * @return A future, tracking the outcome of the operation.
     */
    public Future<UpdateResult> deleteDevice(final DeviceKey key, final Optional<String> resourceVersion, final SpanContext spanContext) {

        final Span span = TracingHelper.buildChildSpan(this.tracer, spanContext, "delete device", getClass().getSimpleName())
                .withTag("tenant_instance_id", key.getTenantId())
                .withTag("device_id", key.getDeviceId())
                .start();

        resourceVersion.ifPresent(version -> span.setTag("version", version));

        final Statement statement;
        if (resourceVersion.isPresent()) {
            statement = this.deleteVersionedStatement;
        } else {
            statement = this.deleteStatement;
        }

        final var expanded = statement.expand(map -> {
            map.put("tenant_id", key.getTenantId());
            map.put("device_id", key.getDeviceId());
            resourceVersion.ifPresent(version -> map.put("expected_version", version));
        });

        log.debug("delete - statement: {}", expanded);

        return expanded
                .trace(this.tracer, span)
                .update(this.client)
                .onComplete(x -> span.finish());

    }

    /**
     * Delete all devices belonging to the provided tenant.
     *
     * @param tenantId The tenant to clean up.
     * @param spanContext The span to contribute to.
     *
     * @return A future tracking the outcome of the operation.
     */
    public Future<UpdateResult> dropTenant(final String tenantId, final SpanContext spanContext) {

        final Span span = TracingHelper.buildChildSpan(this.tracer, spanContext, "drop tenant", getClass().getSimpleName())
                .withTag("tenant_instance_id", tenantId)
                .start();

        final var expanded = this.dropTenantStatement.expand(params -> {
            params.put("tenant_id", tenantId);
        });

        log.debug("delete - statement: {}", expanded);

        return expanded
                .trace(this.tracer, span)
                .update(this.client)
                .onComplete(x -> span.finish());

    }

}
