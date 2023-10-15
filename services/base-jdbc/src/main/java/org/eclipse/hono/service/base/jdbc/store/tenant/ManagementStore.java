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
package org.eclipse.hono.service.base.jdbc.store.tenant;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.deviceregistry.util.Versioned;
import org.eclipse.hono.service.base.jdbc.store.EntityNotFoundException;
import org.eclipse.hono.service.base.jdbc.store.SQL;
import org.eclipse.hono.service.base.jdbc.store.Statement;
import org.eclipse.hono.service.base.jdbc.store.StatementConfiguration;
import org.eclipse.hono.service.management.SearchResult;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.service.management.tenant.TenantWithId;
import org.eclipse.hono.tracing.TracingHelper;
import org.eclipse.hono.util.RequestResponseApiConstants;
import org.eclipse.hono.util.TenantConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.sql.SQLOperations;
import io.vertx.ext.sql.UpdateResult;

/**
 * A data store for tenant management information.
 */
public class ManagementStore extends AbstractTenantStore {

    private static final Logger log = LoggerFactory.getLogger(ManagementStore.class);

    private final String countStatementSql;

    private final Statement createStatement;

    private final Statement insertTrustAnchorStatement;
    private final Statement deleteAllTrustAnchorsStatement;

    private final Statement updateStatement;
    private final Statement updateVersionedStatement;

    private final Statement deleteStatement;
    private final Statement deleteVersionedStatement;

    private final Statement findTenantsStatement;

    /**
     * Create a new instance.
     *
     * @param client The client to use for accessing the DB.
     * @param tracer The tracer to use.
     * @param cfg The statement configuration to use.
     */
    public ManagementStore(final JDBCClient client, final Tracer tracer, final StatementConfiguration cfg) {
        super(client, tracer, cfg);


        this.countStatementSql = cfg
                .getRequiredStatement("count")
                .expand()
                .getSql();

        this.createStatement = cfg
                .getRequiredStatement("create")
                .validateParameters(
                        "tenant_id",
                        "version",
                        "data"
                );

        this.insertTrustAnchorStatement = cfg
                .getRequiredStatement("insertTrustAnchor")
                .validateParameters(
                        "tenant_id",
                        "subject_dn",
                        "data"
                );
        this.deleteAllTrustAnchorsStatement = cfg
                .getRequiredStatement("deleteAllTrustAnchors")
                .validateParameters(
                        "tenant_id"
                );

        this.updateStatement = cfg
                .getRequiredStatement("update")
                .validateParameters(
                        "tenant_id",
                        "next_version",
                        "data"
                );
        this.updateVersionedStatement = cfg
                .getRequiredStatement("updateVersioned")
                .validateParameters(
                        "tenant_id",
                        "next_version",
                        "data",
                        "expected_version"
                );

        this.deleteStatement = cfg.getRequiredStatement("delete")
                .validateParameters(
                        "tenant_id"
                );
        this.deleteVersionedStatement = cfg.getRequiredStatement("deleteVersioned")
                .validateParameters(
                        "tenant_id",
                        "expected_version"
                );

        this.findTenantsStatement = cfg.getRequiredStatement("findTenants")
            .validateParameters(
                "page_size",
                "page_offset");    }

    /**
     * Create a device statement configuration for the tenant store.
     *
     * @param jdbcUrl The JDBC URL for detecting the database dialect.
     * @param tenantTableName The table name to use for tenants.
     * @param trustAnchorsTenantName The table name to use trust anchors.
     * @return The new configuration.
     * @throws IOException in case an IO error occurs.
     */
    public static StatementConfiguration defaultStatementConfiguration(final String jdbcUrl, final Optional<String> tenantTableName, final Optional<String> trustAnchorsTenantName) throws IOException {

        final String dialect = SQL.getDatabaseDialect(jdbcUrl);
        final String tenantTableNameString = tenantTableName.orElse(DEFAULT_TENANTS_TABLE_NAME);
        final String trustAnchorsTableNameString = trustAnchorsTenantName.orElse(DEFAULT_TRUST_ANCHORS_NAME);

        return StatementConfiguration
                .empty(tenantTableNameString, trustAnchorsTableNameString)
                .overrideWithDefaultPattern("base", dialect, ManagementStore.class, StatementConfiguration.DEFAULT_PATH.resolve("tenant"));

    }

    /**
     * Encode the tenant information as JSON, for storing in the data field.
     *
     * @param tenant The tenant to encode.
     * @return The encoded JSON, without the trust anchors.
     */
    private String tenantToJson(final Tenant tenant) {
        final var jsonObjectObj = JsonObject.mapFrom(tenant);
        // we store this separately
        jsonObjectObj.remove(TenantConstants.FIELD_PAYLOAD_TRUSTED_CA);
        return jsonObjectObj.toString();
    }

    /**
     * Gets the current number of tenants.
     *
     * @return A future containing the total number of tenants.
     */
    public Future<Integer> getTenantCount() {

        final Promise<Integer> result = Promise.promise();
        this.client.querySingle(countStatementSql, rs -> {
            if (rs.failed()) {
                result.fail(new IllegalStateException("failed to query number of tenants", rs.cause()));
            } else {
                result.complete(rs.result().getInteger(0));
            }
        });
        return result.future();
    }

    /**
     * Create a new tenant.
     * <p>
     * The operation may fail with a {@link org.eclipse.hono.service.base.jdbc.store.DuplicateKeyException} if a
     * tenant with the ID or trust anchor already exists.
     *
     * @param tenantId The ID of the new tenant.
     * @param tenant The tenant information.
     * @param spanContext The span to contribute to.
     * @return A future, tracking the outcome of the operation.
     */
    public Future<Versioned<Void>> create(final String tenantId, final Tenant tenant, final SpanContext spanContext) {

        final var json = tenantToJson(tenant);
        final var version = UUID.randomUUID().toString();

        final Span span = TracingHelper.buildChildSpan(this.tracer, spanContext, "create tenant", getClass().getSimpleName())
                .withTag(TracingHelper.TAG_TENANT_ID, tenantId)
                .start();

        return SQL.runTransactionally(this.client, this.tracer, span.context(),
                (connection, context) -> {

                    final var expanded = this.createStatement.expand(params -> {
                        params.put("tenant_id", tenantId);
                        params.put("version", version);
                        params.put("data", json);
                    });

                    log.debug("create - statement: {}", expanded);
                    return expanded
                            .trace(this.tracer, span.context())
                            .update(connection)
                            .recover(SQL::translateException)

                            // insert all trust anchors
                            .flatMap(r -> insertAllTrustAnchors(connection, tenantId, tenant,  span));

                }
        )
                .map(new Versioned<Void>(version, null))

                .onComplete(x -> span.finish());

    }

    private Future<Void> deleteAllTrustAnchors(final SQLConnection connection, final String tenantId, final Span span) {

        return this.deleteAllTrustAnchorsStatement

                .expand(params -> params.put("tenant_id", tenantId))
                .trace(this.tracer, span.context())
                .update(connection)

                .mapEmpty();

    }

    private Future<Void> insertAllTrustAnchors(final SQLConnection connection, final String tenantId, final Tenant tenant, final Span span) {

        if (tenant.getTrustedCertificateAuthorities() == null || tenant.getTrustedCertificateAuthorities().isEmpty()) {
            return Future.succeededFuture();
        }

        return Future

                .all(tenant.getTrustedCertificateAuthorities().stream()

                        .map(anchor -> {
                            final var json = JsonObject.mapFrom(anchor);
                            final var subjectDn = json.remove(RequestResponseApiConstants.FIELD_PAYLOAD_SUBJECT_DN);

                            if (!(subjectDn instanceof String subjectDnString) || subjectDnString.isEmpty()) {
                                return Future.failedFuture(new IllegalArgumentException(
                                        String.format("Missing field '%s' in trust anchor", RequestResponseApiConstants.FIELD_PAYLOAD_SUBJECT_DN)));
                            }

                            return this.insertTrustAnchorStatement
                                .expand(params -> {
                                    params.put("tenant_id", tenantId);
                                    params.put("subject_dn", subjectDn);
                                    params.put("data", json.toString());
                                })
                                .trace(this.tracer, span.context())
                                .update(connection)
                                .recover(SQL::translateException);
                            }
                        )
                        .toList()
                )

                .mapEmpty();

    }

    /**
     * Read a tenant.
     *
     * @param id The ID of the tenant to read.
     * @param spanContext The span to contribute to.
     * @return A future, tracking the outcome of the operation.
     */
    public Future<Optional<TenantReadResult>> read(final String id, final SpanContext spanContext) {

        final Span span = TracingHelper.buildChildSpan(this.tracer, spanContext, "get tenant by id", getClass().getSimpleName())
                .withTag(TracingHelper.TAG_TENANT_ID, id)
                .start();

        return readTenant(id, span.context())
                .onComplete(x -> span.finish());

    }

    /**
     * Delete the tenant.
     *
     * @param tenantId The tenant to delete.
     * @param resourceVersion The version of the resource to delete.
     * @param spanContext The span to contribute to.
     * @return The future, tracking the outcome of the operation.
     */
    public Future<UpdateResult> delete(final String tenantId, final Optional<String> resourceVersion, final SpanContext spanContext) {

        final Span span = TracingHelper.buildChildSpan(this.tracer, spanContext, "delete tenant", getClass().getSimpleName())
                .withTag(TracingHelper.TAG_TENANT_ID, tenantId)
                .start();

        resourceVersion.ifPresent(version -> span.setTag("version", version));

        final Statement statement;
        if (resourceVersion.isPresent()) {
            statement = this.deleteVersionedStatement;
        } else {
            statement = this.deleteStatement;
        }

        final var expanded = statement.expand(map -> {
            map.put("tenant_id", tenantId);
            resourceVersion.ifPresent(version -> map.put("expected_version", version));
        });

        log.debug("delete - statement: {}", expanded);

        final var result = expanded
                .trace(this.tracer, span.context())
                .update(this.client);

        return checkOptimisticLock(
                result, span,
                resourceVersion,
                checkSpan -> readTenantEntryById(this.client, tenantId, checkSpan.context()))
                .onComplete(x -> span.finish());

    }

    /**
     * Create a new tenant.
     * <p>
     * The operation may fail with a {@link org.eclipse.hono.service.base.jdbc.store.EntityNotFoundException} if the
     * specified tenant does not exist.
     * <p>
     * The operation may fail with a {@link org.eclipse.hono.service.base.jdbc.store.DuplicateKeyException} if a
     * tenant with the ID or trust anchor already exists.
     * <p>
     * The operation may fail with an {@link org.eclipse.hono.service.base.jdbc.store.OptimisticLockingException} if
     * an expected resource version was provided, but the current version did not match.
     *
     * @param tenantId The ID of the new tenant.
     * @param tenant The tenant information.
     * @param resourceVersion An optional resource version.
     * @param spanContext The span to contribute to.
     * @return A future, tracking the outcome of the operation.
     */
    public Future<Versioned<Void>> update(final String tenantId, final Tenant tenant, final Optional<String> resourceVersion, final SpanContext spanContext) {

        final var json = tenantToJson(tenant);

        final Span span = TracingHelper.buildChildSpan(this.tracer, spanContext, "update tenant", getClass().getSimpleName())
                .withTag(TracingHelper.TAG_TENANT_ID, tenantId)
                .start();

        final var nextVersion = UUID.randomUUID().toString();
        resourceVersion.ifPresent(version -> span.setTag("version", version));

        final Statement statement = resourceVersion.isPresent() ? this.updateVersionedStatement : this.updateStatement;

        return SQL

                .runTransactionally(this.client, this.tracer, span.context(), (connection, context) ->

                        // update the base entity, will change the version
                        updateJsonField(connection, tenantId, statement, json, resourceVersion, nextVersion, span)

                                // check if the entity was found
                                .flatMap(r -> {
                                    if (r.getUpdated() <= 0) {
                                        return Future.failedFuture(new EntityNotFoundException());
                                    } else {
                                        return Future.succeededFuture();
                                    }
                                })

                                // delete all trust anchors
                                .flatMap(x -> deleteAllTrustAnchors(connection, tenantId, span))

                                // re-insert new trust anchors
                                .flatMap(r -> insertAllTrustAnchors(connection, tenantId, tenant, span))
                )

                .map(new Versioned<Void>(nextVersion, null))
                .onComplete(x -> span.finish());

    }

    /**
     * Update a tenant entry's data field.
     * <p>
     * This method will update the data field of a tenant object. Optionally checking
     * for the optimistic lock.
     * <p>
     * If the method updated an entry, the update result must report more than one change. If a resource
     * version was provided and the current version is no longer the expected version an
     * {@link org.eclipse.hono.service.base.jdbc.store.OptimisticLockingException} is reported via the future. If the
     * entity was not found, an update result with zero changes is returned.
     *
     * @param operations The SQL instance to use.
     * @param tenantId The tenant ID to update.
     * @param statement The update statement to use.
     * @param jsonValue The JSON value of the data field.
     * @param resourceVersion The optional resource version to check.
     * @param nextVersion The new version to apply.
     * @param span The span to contribute to.
     * @return The future, tracking the outcome of the operation.
     */
    protected Future<UpdateResult> updateJsonField(
            final SQLOperations operations,
            final String tenantId,
            final Statement statement,
            final String jsonValue,
            final Optional<String> resourceVersion,
            final String nextVersion,
            final Span span) {

        final var expanded = statement.expand(map -> {
            map.put("tenant_id", tenantId);
            map.put("next_version", nextVersion);
            map.put("data", jsonValue);
            resourceVersion.ifPresent(version -> map.put("expected_version", version));
        });

        log.debug("update - statement: {}", expanded);

        // execute update
        final var result = expanded
                .trace(this.tracer, span.context())
                .update(operations);

        // process result, check optimistic lock
        return checkOptimisticLock(
                result, span,
                resourceVersion,
                checkSpan -> readTenantEntryById(operations, tenantId, checkSpan.context()));
    }

    /**
     * Gets a list of tenants.
     * @param pageSize the page size
     * @param pageOffset the page offset
     * @param spanContext The span to contribute to.
     *
     * @return A future containing tenants
     */
    public Future<SearchResult<TenantWithId>> find(final int pageSize, final int pageOffset, final SpanContext spanContext) {

        final var expanded = this.findTenantsStatement.expand(map -> {
            map.put("page_size", pageSize);
            map.put("page_offset", pageOffset);
        });

        final Span span = TracingHelper.buildChildSpan(this.tracer, spanContext, "find tenants", getClass().getSimpleName())
            .start();

        final Future<Integer> tenantCountFuture = getTenantCount();

        return tenantCountFuture
                .compose(count -> expanded.trace(this.tracer, span.context()).query(this.client))

                .map(r -> {
                    if (r.getNumRows() == 0) {
                        throw new ClientErrorException(
                                HttpURLConnection.HTTP_NOT_FOUND,
                                "no tenants found");
                    } else {
                        final var entries = r.getRows(true);
                        span.log(Map.of(
                            "event", "read result",
                            "rows", entries.size()));
                        final List<TenantWithId> list = new ArrayList<>();
                        for (var entry : entries) {
                            final var id = entry.getString("tenant_id");
                            final var tenant = Json.decodeValue(entry.getString("data"), Tenant.class);
                            list.add(TenantWithId.from(id, tenant));
                        }
                        return new SearchResult<>(tenantCountFuture.result(), list);
                    }

                })
                .onComplete(x -> span.finish());
    }
}
