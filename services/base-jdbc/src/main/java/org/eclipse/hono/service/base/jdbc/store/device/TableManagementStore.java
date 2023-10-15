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

import java.net.HttpURLConnection;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.deviceregistry.service.device.DeviceKey;
import org.eclipse.hono.deviceregistry.util.DeviceRegistryUtils;
import org.eclipse.hono.deviceregistry.util.Versioned;
import org.eclipse.hono.service.base.jdbc.store.EntityNotFoundException;
import org.eclipse.hono.service.base.jdbc.store.OptimisticLockingException;
import org.eclipse.hono.service.base.jdbc.store.SQL;
import org.eclipse.hono.service.base.jdbc.store.Statement;
import org.eclipse.hono.service.base.jdbc.store.StatementConfiguration;
import org.eclipse.hono.service.base.jdbc.store.model.JdbcBasedDeviceDto;
import org.eclipse.hono.service.management.Filter;
import org.eclipse.hono.service.management.SearchResult;
import org.eclipse.hono.service.management.credentials.CommonCredential;
import org.eclipse.hono.service.management.credentials.CredentialsDto;
import org.eclipse.hono.service.management.device.Device;
import org.eclipse.hono.service.management.device.DeviceWithId;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.tracing.TracingHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.log.Fields;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.sql.SQLOperations;
import io.vertx.ext.sql.UpdateResult;

/**
 * A data store for devices and credentials, based on a table data model.
 */
public class TableManagementStore extends AbstractDeviceStore {

    private static final Logger log = LoggerFactory.getLogger(TableManagementStore.class);

    private static final String TENANT_ID = "tenant_id";
    private static final String DEVICE_ID = "device_id";
    private static final String VERSION = "version";
    private static final String DATA = "data";
    private static final String EXPECTED_VERSION = "expected_version";
    private static final String PAGE_SIZE = "page_size";
    private static final String PAGE_OFFSET = "page_offset";
    private static final String CREATED = "created";
    private static final String AUTO_PROVISIONED = "auto_provisioned";
    private static final String NEXT_VERSION = "next_version";
    private static final String FIELD = "field";
    private static final String VALUE = "value";
    private final Statement createStatement;
    private final Statement createMemberOfStatement;
    private final Statement deleteAllMemberOfStatement;

    private final Statement updateRegistrationVersionedStatement;
    private final Statement deleteStatement;
    private final Statement deleteVersionedStatement;
    private final Statement dropTenantStatement;

    private final Statement readForUpdateStatement;
    private final Statement readCredentialsStatement;

    private final Statement insertCredentialEntryStatement;
    private final Statement deleteAllCredentialsStatement;
    private final Statement updateDeviceVersionStatement;

    private final Statement countDevicesOfTenantStatement;
    private final Statement countDevicesWithFilterStatement;
    private final Statement countGatewaysOfTenantStatement;
    private final Statement countOnlyDevicesOfTenantStatement;

    private final Statement findDevicesOfTenantStatement;
    private final Statement findGatewaysOfTenantStatement;
    private final Statement findOnlyDevicesOfTenantStatement;
    private final Statement findDevicesOfTenantWithFilterStatement;

    /**
     * Create a new instance.
     *
     * @param client The client to use for accessing the DB.
     * @param tracer The tracer to use.
     * @param cfg The SQL statement configuration.
     */
    public TableManagementStore(final JDBCClient client, final Tracer tracer, final StatementConfiguration cfg) {
        super(client, tracer, cfg);
        cfg.dump(log);

        this.createStatement = cfg
                .getRequiredStatement("create")
                .validateParameters(
                        TENANT_ID,
                        DEVICE_ID,
                        VERSION,
                        DATA,
                        CREATED,
                        AUTO_PROVISIONED);

        this.createMemberOfStatement = cfg
                .getRequiredStatement("createMemberOf")
                .validateParameters(
                        TENANT_ID,
                        DEVICE_ID,
                        "group_id");

        this.deleteAllMemberOfStatement = cfg
                .getRequiredStatement("deleteAllMemberOf")
                .validateParameters(
                        TENANT_ID,
                        DEVICE_ID);

        this.updateRegistrationVersionedStatement = cfg
                .getRequiredStatement("updateRegistrationVersioned")
                .validateParameters(
                        TENANT_ID,
                        DEVICE_ID,
                        NEXT_VERSION,
                        DATA,
                        EXPECTED_VERSION,
                        "updated_on",
                        "auto_provisioning_notification_sent");

        this.deleteStatement = cfg
                .getRequiredStatement("delete")
                .validateParameters(
                        TENANT_ID,
                        DEVICE_ID);

        this.deleteVersionedStatement = cfg
                .getRequiredStatement("deleteVersioned")
                .validateParameters(
                        TENANT_ID,
                        DEVICE_ID,
                        EXPECTED_VERSION);

        this.dropTenantStatement = cfg
                .getRequiredStatement("dropTenant")
                .validateParameters(
                        TENANT_ID);

        this.readForUpdateStatement = cfg.getRequiredStatement("readForUpdate")
                .validateParameters(
                        TENANT_ID,
                        DEVICE_ID);

        this.readCredentialsStatement = cfg
                .getRequiredStatement("readCredentials")
                .validateParameters(
                        TENANT_ID,
                        DEVICE_ID);

        this.insertCredentialEntryStatement = cfg
                .getRequiredStatement("insertCredentialEntry")
                .validateParameters(
                        TENANT_ID,
                        DEVICE_ID,
                        "type",
                        "auth_id",
                        DATA);

        this.deleteAllCredentialsStatement = cfg
                .getRequiredStatement("deleteAllCredentials")
                .validateParameters(
                        TENANT_ID,
                        DEVICE_ID);

        this.updateDeviceVersionStatement = cfg
                .getRequiredStatement("updateDeviceVersion")
                .validateParameters(
                        TENANT_ID,
                        DEVICE_ID,
                        NEXT_VERSION,
                        EXPECTED_VERSION);

        this.countDevicesOfTenantStatement = cfg
                .getRequiredStatement("countDevicesOfTenant")
                .validateParameters(
                        TENANT_ID);

        this.countGatewaysOfTenantStatement = cfg
                .getRequiredStatement("countGatewaysOfTenant")
                .validateParameters(
                        TENANT_ID,
                        DEVICE_ID);

        this.countOnlyDevicesOfTenantStatement = cfg
                .getRequiredStatement("countOnlyDevicesOfTenant")
                .validateParameters(
                        TENANT_ID,
                        DEVICE_ID);

        this.countDevicesWithFilterStatement = cfg
                .getRequiredStatement("countDevicesOfTenantWithFilter")
                .validateParameters(
                        TENANT_ID,
                        FIELD,
                        VALUE);

        this.findDevicesOfTenantStatement = cfg
                .getRequiredStatement("findDevicesOfTenant")
                .validateParameters(
                        TENANT_ID,
                        PAGE_SIZE,
                        PAGE_OFFSET);

        this.findOnlyDevicesOfTenantStatement = cfg
                .getRequiredStatement("findOnlyDevicesOfTenant")
                .validateParameters(
                        TENANT_ID,
                        DEVICE_ID,
                        PAGE_SIZE,
                        PAGE_OFFSET);

        this.findGatewaysOfTenantStatement = cfg
                .getRequiredStatement("findGatewaysOfTenant")
                .validateParameters(
                        TENANT_ID,
                        DEVICE_ID,
                        PAGE_SIZE,
                        PAGE_OFFSET);

        this.findDevicesOfTenantWithFilterStatement = cfg
                .getRequiredStatement("findDevicesOfTenantWithFilter")
                .validateParameters(
                        TENANT_ID,
                        FIELD,
                        VALUE,
                        PAGE_SIZE,
                        PAGE_OFFSET);
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
     * @param span The span to contribute to.
     * @return A future tracking the outcome of the operation.
     */
    protected Future<ResultSet> readDeviceForUpdate(final SQLConnection connection, final DeviceKey key, final SpanContext span) {
        return read(connection, key, Optional.empty(), this.readForUpdateStatement, span);
    }

    /**
     * Creates a new device.
     * <p>
     * This method executes the {@code create} statement, providing the named parameters
     * {@code tenant_id}, {@code device_id}, {@code version}, and {@code data}.
     * <p>
     * It returns the plain update result. In case a device with the same ID already
     * exists, the underlying database must throw an {@link java.sql.SQLException}, indicating
     * a duplicate entity or constraint violation. This will be translated into a
     * failed future with an {@link org.eclipse.hono.service.base.jdbc.store.DuplicateKeyException}.
     *
     * @param key The key of the device to create.
     * @param device The device data.
     * @param tenant The configuration of the tenant that the device belongs to.
     * @param globalDevicesPerTenantLimit The globally defined maximum number of devices per tenant. A value
     *                                    &lt;= 0 will be interpreted as no limit being defined.
     * @param spanContext The span to contribute to.
     * @return A future, tracking the outcome of the operation.
     */
    public Future<Versioned<Void>> createDevice(
            final DeviceKey key,
            final Device device,
            final Tenant tenant,
            final int globalDevicesPerTenantLimit,
            final SpanContext spanContext) {

        final Span span = TracingHelper.buildChildSpan(this.tracer, spanContext, "create device", getClass().getSimpleName())
                .withTag(TracingHelper.TAG_TENANT_ID, key.getTenantId())
                .withTag(TracingHelper.TAG_DEVICE_ID, key.getDeviceId())
                .start();

        final JdbcBasedDeviceDto deviceDto = JdbcBasedDeviceDto.forCreation(
                key,
                device,
                DeviceRegistryUtils.getUniqueIdentifier());

        return SQL

                .runTransactionally(this.client, this.tracer, span.context(), (connection, context) -> {

                    final var expanded = this.createStatement.expand(params -> {
                        params.put(TENANT_ID, deviceDto.getTenantId());
                        params.put(DEVICE_ID, deviceDto.getDeviceId());
                        params.put(VERSION, deviceDto.getVersion());
                        params.put(DATA, deviceDto.getDeviceJson());
                        params.put(CREATED, Timestamp.from(deviceDto.getCreationTime()));
                        params.put(AUTO_PROVISIONED, deviceDto.isAutoProvisioned());
                    });

                    log.debug("createDevice - statement: {}", expanded);

                    return getDeviceCount(connection, key.getTenantId(), span.context(), this.countDevicesOfTenantStatement, null, null)
                            .compose(currentDeviceCount -> tenant.checkDeviceLimitReached(
                                    key.getTenantId(),
                                    currentDeviceCount,
                                    globalDevicesPerTenantLimit))
                            .compose(ok -> expanded
                                    .trace(this.tracer, context)
                                    .update(connection)
                                    .recover(SQL::translateException))

                            .compose(x -> createGroups(connection, key, new HashSet<>(device.getMemberOf()), context));

                })

                .map(new Versioned<Void>(deviceDto.getVersion(), null))
                .onComplete(x -> span.finish());

    }

    private Future<Void> createGroups(
            final SQLConnection connection,
            final DeviceKey key,
            final Set<String> memberOf,
            final SpanContext context) {

        return Future.all(memberOf.stream()
                        .map(groupId -> {

                            final var expanded = this.createMemberOfStatement.expand(params -> {
                                params.put(TENANT_ID, key.getTenantId());
                                params.put(DEVICE_ID, key.getDeviceId());
                                params.put("group_id", groupId);
                            });

                            log.debug("addToGroup - statement: {}", expanded);

                            return expanded
                                    .trace(this.tracer, context)
                                    .update(connection)
                                    .recover(SQL::translateException);
                        })
                        .collect(Collectors.toList()))
                .mapEmpty();

    }

    private Future<Void> deleteGroups(final SQLConnection connection,
                                      final DeviceKey key,
                                      final SpanContext context) {

        final var expanded = this.deleteAllMemberOfStatement.expand(params -> {
            params.put(TENANT_ID, key.getTenantId());
            params.put(DEVICE_ID, key.getDeviceId());
        });

        log.debug("deleteGroups - statement: {}", expanded);

        return expanded
                .trace(this.tracer, context)
                .update(connection)
                .recover(SQL::translateException)
                .mapEmpty();

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
     * @param nextVersion The new version to set.
     * @param span The span to contribute to.
     * @return A future, tracking the outcome of the operation.
     */
    protected Future<UpdateResult> updateJsonField(
            final DeviceKey key,
            final Statement statement,
            final String jsonValue,
            final Optional<String> resourceVersion,
            final String nextVersion,
            final Span span) {

        final var expanded = statement.expand(map -> {
            map.put(TENANT_ID, key.getTenantId());
            map.put(DEVICE_ID, key.getDeviceId());
            map.put(NEXT_VERSION, nextVersion);
            map.put(DATA, jsonValue);
            resourceVersion.ifPresent(version -> map.put(EXPECTED_VERSION, version));
        });

        log.debug("update - statement: {}", expanded);

        // execute update
        final var result = expanded
                .trace(this.tracer, span.context())
                .update(this.client);

        // process result, check optimistic lock
        return checkOptimisticLock(
                result, span,
                resourceVersion,
                checkSpan -> readDevice(this.client, key, checkSpan));

    }

    /**
     * Update device registration information.
     * <p>
     * This called the {@link #updateJsonField(DeviceKey, Statement, String, Optional, String, Span)} method
     * with either the {@code updateRegistration} or {@code updateRegistrationVersioned}
     * statement.
     *
     * @param key The key of the device to update.
     * @param device The device data to store.
     * @param resourceVersion The optional resource version.
     * @param spanContext The span to contribute to.
     * @return A future, tracking the outcome of the operation.
     */
    public Future<Versioned<Void>> updateDevice(
            final DeviceKey key,
            final Device device,
            final Optional<String> resourceVersion,
            final SpanContext spanContext) {

        final Span span = TracingHelper.buildChildSpan(this.tracer, spanContext, "update device", getClass().getSimpleName())
                .withTag(TracingHelper.TAG_TENANT_ID, key.getTenantId())
                .withTag(TracingHelper.TAG_DEVICE_ID, key.getDeviceId())
                .start();

        resourceVersion.ifPresent(version -> span.setTag(VERSION, version));

        final var memberOf = Optional.ofNullable(device.getMemberOf())
                .<Set<String>>map(HashSet::new)
                .orElse(Collections.emptySet());

        final JdbcBasedDeviceDto deviceDto = JdbcBasedDeviceDto.forUpdate(
                key,
                device,
                DeviceRegistryUtils.getUniqueIdentifier());

        return SQL
                .runTransactionally(this.client, this.tracer, span.context(), (connection, context) ->

                        readDeviceForUpdate(connection, key, context)

                                // check if we got back a result, if not this will abort early
                                .compose(result -> extractVersionForUpdate(result, resourceVersion))

                                // take the version and start processing on
                                .compose(version -> deleteGroups(connection, key, context)
                                        .map(version))

                                .compose(version -> createGroups(connection, key, memberOf, context)
                                        .map(version))

                                // update the version, this will release the lock
                                .compose(version -> this.updateRegistrationVersionedStatement
                                        .expand(map -> {
                                            map.put(TENANT_ID, deviceDto.getTenantId());
                                            map.put(DEVICE_ID, deviceDto.getDeviceId());
                                            map.put(DATA, deviceDto.getDeviceJson());
                                            map.put(EXPECTED_VERSION, version);
                                            map.put(NEXT_VERSION, deviceDto.getVersion());
                                            map.put("updated_on", Timestamp.from(deviceDto.getUpdatedOn()));
                                            map.put("auto_provisioning_notification_sent",
                                                    deviceDto.isAutoProvisioningNotificationSent());
                                        })
                                        .trace(this.tracer, span.context()).update(connection)

                                        // check the update outcome
                                        .compose(TableManagementStore::checkUpdateOutcome)
                                        .map(version)
                                )


                )

                .map(x -> new Versioned<Void>(deviceDto.getVersion(), null))
                .onComplete(x -> span.finish());

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
                            final JdbcBasedDeviceDto deviceDto = JdbcBasedDeviceDto.forRead(key.getTenantId(), key.getDeviceId(), entry);
                            return Future.succeededFuture(Optional.of(new DeviceReadResult(deviceDto.getDeviceWithStatus(), Optional.of(deviceDto.getVersion()))));
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
     * @return A future, tracking the outcome of the operation.
     */
    public Future<UpdateResult> deleteDevice(
            final DeviceKey key,
            final Optional<String> resourceVersion,
            final SpanContext spanContext) {

        final Span span = TracingHelper.buildChildSpan(this.tracer, spanContext, "delete device", getClass().getSimpleName())
                .withTag(TracingHelper.TAG_TENANT_ID, key.getTenantId())
                .withTag(TracingHelper.TAG_DEVICE_ID, key.getDeviceId())
                .start();

        resourceVersion.ifPresent(version -> span.setTag(VERSION, version));

        final Statement statement;
        if (resourceVersion.isPresent()) {
            statement = this.deleteVersionedStatement;
        } else {
            statement = this.deleteStatement;
        }

        final var expanded = statement.expand(map -> {
            map.put(TENANT_ID, key.getTenantId());
            map.put(DEVICE_ID, key.getDeviceId());
            resourceVersion.ifPresent(version -> map.put(EXPECTED_VERSION, version));
        });

        log.debug("delete - statement: {}", expanded);

        final var result = expanded
                .trace(this.tracer, span.context())
                .update(this.client);

        return checkOptimisticLock(
                result, span,
                resourceVersion,
                checkSpan -> readDevice(this.client, key, checkSpan))
                .onComplete(x -> span.finish());

    }

    /**
     * Delete all devices belonging to the provided tenant.
     *
     * @param tenantId The tenant to clean up.
     * @param spanContext The span to contribute to.
     * @return A future tracking the outcome of the operation.
     */
    public Future<UpdateResult> dropTenant(final String tenantId, final SpanContext spanContext) {

        final Span span = TracingHelper.buildChildSpan(this.tracer, spanContext, "drop tenant", getClass().getSimpleName())
                .withTag(TracingHelper.TAG_TENANT_ID, tenantId)
                .start();

        final var expanded = this.dropTenantStatement.expand(params -> {
            params.put(TENANT_ID, tenantId);
        });

        log.debug("delete - statement: {}", expanded);

        return expanded
                .trace(this.tracer, span.context())
                .update(this.client)
                .onComplete(x -> span.finish());

    }

    /**
     * Gets the number of devices that are registered for a tenant.
     *
     * @param operations The SQL operations instance to use.
     * @param tenantId The tenant to count devices for.
     * @param spanContext The span to contribute to.
     * @param countStatement The count statement to use.
     * @param field The field of filter expression.
     * @param value The value of the filter expression.
     * @return A future tracking the outcome of the operation.
     * @throws NullPointerException if tenant is {@code null}.
     */
    public Future<Integer> getDeviceCount(final SQLOperations operations, final String tenantId, final SpanContext spanContext, final Statement countStatement, final String field, final String value) {

        Objects.requireNonNull(tenantId);

        final Span span = TracingHelper.buildChildSpan(this.tracer, spanContext, "get device count", getClass().getSimpleName())
                .withTag(TracingHelper.TAG_TENANT_ID, tenantId)
                .start();

        final var expanded = countStatement.expand(params -> {
            params.put(TENANT_ID, tenantId);
            params.put(FIELD, field);
            params.put(VALUE, value);
        });

        log.debug("count - statement: {}", expanded);

        return expanded
                .trace(this.tracer, span.context())
                .query(operations)
                .map(r -> {
                    final var entries = r.getRows(true);
                    switch (entries.size()) {
                        case 1:
                            final Integer count = entries.get(0).getInteger("DEVICECOUNT");
                            log.debug("found {} devices registered for tenant [tenant-id: {}]", count, tenantId);
                            return count;
                        default:
                            throw new IllegalStateException("Could not count devices of tenant");
                    }
                })
                .onComplete(x -> span.finish());

    }

    /**
     * Set all credentials for a device.
     * <p>
     * This will set/update all credentials of the device. If the device does not exist, the result
     * will be {@code false}. If the update was successful, then the result will be {@code true}.
     * If the resource version was provided, but the provided version was no longer the current version,
     * then the future will fail with a {@link OptimisticLockingException}.
     *
     * @param key The key of the device to update.
     * @param credentials The credentials to set.
     * @param resourceVersion The optional resource version to update.
     * @param spanContext The span to contribute to.
     * @return A future, tracking the outcome of the operation.
     */
    public Future<Versioned<Boolean>> setCredentials(
            final DeviceKey key,
            final List<CommonCredential> credentials,
            final Optional<String> resourceVersion,
            final SpanContext spanContext) {

        final Span span = TracingHelper.buildChildSpan(this.tracer, spanContext, "set credentials", getClass().getSimpleName())
                .withTag(TracingHelper.TAG_TENANT_ID, key.getTenantId())
                .withTag(TracingHelper.TAG_DEVICE_ID, key.getDeviceId())
                .withTag("num_credentials", credentials.size())
                .start();

        resourceVersion.ifPresent(version -> span.setTag(VERSION, version));

        final String nextVersion = UUID.randomUUID().toString();

        return SQL.runTransactionally(this.client, this.tracer, span.context(), (connection, context) ->

                        readDeviceForUpdate(connection, key, context)

                                // check if we got back a result, if not this will abort early
                                .compose(result -> extractVersionForUpdate(result, resourceVersion))

                                // take the version and start processing on
                                .compose(version -> Future.succeededFuture()

                                        .compose(x -> {
                                            final Promise<CredentialsDto> result = Promise.promise();
                                            final var updatedCredentialsDto = CredentialsDto.forUpdate(
                                                    key.getTenantId(),
                                                    key.getDeviceId(),
                                                    credentials,
                                                    nextVersion);

                                            if (updatedCredentialsDto.requiresMerging()) {
                                                getCredentialsDto(key, connection, span)
                                                        .map(updatedCredentialsDto::merge)
                                                        .onComplete(result);
                                            } else {
                                                // simply replace the existing credentials with the
                                                // updated ones provided by the client
                                                result.complete(updatedCredentialsDto);
                                            }
                                            return result.future();
                                        })

                                        .compose(updatedCredentials -> this.deleteAllCredentialsStatement
                                                // delete the existing entries
                                                .expand(map -> {
                                                    map.put(TENANT_ID, key.getTenantId());
                                                    map.put(DEVICE_ID, key.getDeviceId());
                                                })
                                                .trace(this.tracer, span.context())
                                                .update(connection)
                                                .map(updatedCredentials)
                                        )

                                        // then create new entries
                                        .compose(updatedCredentials -> {
                                            updatedCredentials.createMissingSecretIds();
                                            return Future.all(updatedCredentials.getData().stream()
                                                            .map(JsonObject::mapFrom)
                                                            .filter(c -> c.containsKey("type") && c.containsKey("auth-id"))
                                                            .map(c -> this.insertCredentialEntryStatement
                                                                    .expand(map -> {
                                                                        map.put(TENANT_ID, key.getTenantId());
                                                                        map.put(DEVICE_ID, key.getDeviceId());
                                                                        map.put("type", c.getString("type"));
                                                                        map.put("auth_id", c.getString("auth-id"));
                                                                        map.put(DATA, c.toString());
                                                                    })
                                                                    .trace(this.tracer, span.context())
                                                                    .update(connection))
                                                            .collect(Collectors.toList()))
                                                    .mapEmpty();
                                        })

                                        // update the version, this will release the lock
                                        .compose(x -> this.updateDeviceVersionStatement
                                                .expand(map -> {
                                                    map.put(TENANT_ID, key.getTenantId());
                                                    map.put(DEVICE_ID, key.getDeviceId());
                                                    map.put(EXPECTED_VERSION, version);
                                                    map.put(NEXT_VERSION, nextVersion);
                                                })
                                                .trace(this.tracer, span.context())
                                                .update(connection)

                                                // check the update outcome
                                                .compose(TableManagementStore::checkUpdateOutcome))

                                        .map(true)

                                ))

                // when not found, then return "false"
                .recover(err -> recoverNotFound(span, err, () -> false))

                .map(ok -> new Versioned<>(nextVersion, ok))
                .onComplete(x -> span.finish());

    }

    private Future<CredentialsDto> getCredentialsDto(
            final DeviceKey key,
            final SQLConnection connection,
            final Span span) {

        return readCredentialsStatement
                // get the current credentials set
                .expand(map -> {
                    map.put(TENANT_ID, key.getTenantId());
                    map.put(DEVICE_ID, key.getDeviceId());
                })
                .trace(this.tracer, span.context())
                .query(connection)
                .map(this::parseCredentials)
                .map(existingCredentials -> CredentialsDto.forRead(
                        key.getTenantId(),
                        key.getDeviceId(),
                        existingCredentials,
                        null,
                        null,
                        null));
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

    private static Future<String> extractVersionForUpdate(final ResultSet device, final Optional<String> resourceVersion) {
        final Optional<String> version = device.getRows(true).stream().map(o -> o.getString("version")).findAny();

        if (version.isEmpty()) {
            log.debug("No version or no row found -> entity not found");
            return Future.failedFuture(new EntityNotFoundException());
        }

        final var currentVersion = version.get();

        return resourceVersion
                // if we expect a certain version
                .<Future<String>>map(expected -> {
                            // check ...
                            if (expected.equals(currentVersion)) {
                                // version matches, continue with current version
                                return Future.succeededFuture(currentVersion);
                            } else {
                                // version does not match, abort
                                return Future.failedFuture(new OptimisticLockingException());
                            }
                        }
                )
                // if we don't expect a version, continue with the current
                .orElseGet(() -> Future.succeededFuture(currentVersion));

    }

    /**
     * Get all credentials for a device.
     * <p>
     * This gets the credentials of a device. If the device cannot be found, the
     * result must be empty. If no credentials could be found for an existing device,
     * the result must not be empty, but provide an empty {@link CredentialsReadResult}.
     *
     * @param key The key of the device.
     * @param spanContext The span to contribute to.
     * @return A future, tracking the outcome of the operation.
     */
    public Future<Optional<CredentialsReadResult>> getCredentials(final DeviceKey key, final SpanContext spanContext) {

        final Span span = TracingHelper.buildChildSpan(this.tracer, spanContext, "get credentials", getClass().getSimpleName())
                .withTag(TracingHelper.TAG_TENANT_ID, key.getTenantId())
                .withTag(TracingHelper.TAG_DEVICE_ID, key.getDeviceId())
                .start();

        final var expanded = this.readCredentialsStatement.expand(map -> {
            map.put(TENANT_ID, key.getTenantId());
            map.put(DEVICE_ID, key.getDeviceId());
        });

        final Promise<SQLConnection> promise = Promise.promise();
        this.client.getConnection(promise);

        return promise.future()

                .compose(connection -> readDevice(connection, key, span)

                        // check if we got back a result, if not this will abort early

                        .compose(result -> extractVersionForUpdate(result, Optional.empty()))

                        // read credentials

                        .compose(version -> expanded.trace(this.tracer, span.context()).query(connection)

                                .compose(r -> {

                                    span.log(Map.of(
                                            Fields.EVENT, "read result",
                                            "rows", r.getNumRows()));
                                    final var credentials = parseCredentials(r);

                                    log.debug("Credentials: {}", credentials);
                                    return Future.succeededFuture(Optional.of(new CredentialsReadResult(key.getDeviceId(), credentials, Optional.ofNullable(version))));
                                }))

                        .onComplete(x -> connection.close()))

                .recover(err -> recoverNotFound(span, err, Optional::empty))

                .onComplete(x -> span.finish());

    }

    private List<CommonCredential> parseCredentials(final ResultSet result) {

        final var entries = result.getRows(true);

        return entries.stream()
                .map(o -> o.getString(DATA))
                .map(s -> Json.decodeValue(s, CommonCredential.class))
                .collect(Collectors.toList());

    }

    /**
     * Gets a list of devices of a specific tenant.
     *
     * @param tenantId The tenantId to search devices.
     * @param pageSize The page size.
     * @param pageOffset The page offset.
     * @param filters The list of filters (currently only the first value of the list is used).
     *                Will be ignored if parameter isGateway is being used.
     * @param isGateway Optional filter for searching only gateways or only devices.
     *                  If given parameter is Optional.empty() result will contain both gateways and devices.
     * @param spanContext The span to contribute to.
     * @return A future containing devices.
     */
    public Future<SearchResult<DeviceWithId>> findDevices(final String tenantId, final int pageSize, final int pageOffset, final List<Filter> filters, final Optional<Boolean> isGateway,
                                                          final SpanContext spanContext) {


        final Statement findDeviceSqlStatement;
        final Statement countStatement;
        final String field;
        final String value;

        if (isGateway.isPresent()) {
            field = "";
            value = "";

            findDeviceSqlStatement = isGateway.get() ? this.findGatewaysOfTenantStatement : this.findOnlyDevicesOfTenantStatement;
            countStatement = isGateway.get() ? this.countGatewaysOfTenantStatement : this.countOnlyDevicesOfTenantStatement;
        } else {
            final var filter = filters.stream().findFirst();

            field = filter.map(filter1 -> filter1.getField().toString().replace("/", "")).orElse("");
            value = filter.map(filter1 ->
                    filter1.getValue().toString()
                            .replace("/", "")
                            .replace("*", "%")
                            .replace("?", "_")
            ).orElse("");


            findDeviceSqlStatement = (filter.isPresent()) ? findDevicesOfTenantWithFilterStatement : this.findDevicesOfTenantStatement;
            countStatement = (filter.isPresent()) ? countDevicesWithFilterStatement : this.countDevicesOfTenantStatement;
        }


        final var expanded = findDeviceSqlStatement.expand(map -> {
            map.put(TENANT_ID, tenantId);
            map.put(PAGE_SIZE, pageSize);
            map.put(PAGE_OFFSET, pageOffset);
            map.put(FIELD, field);
            map.put(VALUE, value);
        });

        final Span span = TracingHelper.buildChildSpan(this.tracer, spanContext, "find devices", getClass().getSimpleName())
                .withTag(TracingHelper.TAG_TENANT_ID, tenantId)
                .start();

        final Future<Integer> deviceCountFuture = getDeviceCount(this.client, tenantId, span.context(), countStatement, field, value);

        return deviceCountFuture
                .compose(count -> expanded.trace(this.tracer, span.context()).query(this.client))
                .map(r -> {
                    if (r.getNumRows() == 0) {
                        throw new ClientErrorException(
                                tenantId,
                                HttpURLConnection.HTTP_NOT_FOUND,
                                "no devices matching searching criteria");
                    } else {
                        final var entries = r.getRows(true);
                        span.log(Map.of(
                                "event", "read result",
                                "rows", entries.size()));
                        final List<DeviceWithId> list = new ArrayList<>();
                        for (var entry : entries) {
                            final var id = entry.getString(DEVICE_ID);
                            final JdbcBasedDeviceDto deviceDto = JdbcBasedDeviceDto.forRead(tenantId, id, entry);
                            list.add(DeviceWithId.from(id, deviceDto.getDeviceWithStatus()));
                        }
                        return new SearchResult<>(deviceCountFuture.result(), list);
                    }

                })
                .onComplete(x -> span.finish());
    }
}
