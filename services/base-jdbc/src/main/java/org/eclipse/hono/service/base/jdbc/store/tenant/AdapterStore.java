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

package org.eclipse.hono.service.base.jdbc.store.tenant;

import java.io.IOException;
import java.util.Optional;

import org.eclipse.hono.deviceregistry.util.DeviceRegistryUtils;
import org.eclipse.hono.service.base.jdbc.store.SQL;
import org.eclipse.hono.service.base.jdbc.store.Statement;
import org.eclipse.hono.service.base.jdbc.store.StatementConfiguration;
import org.eclipse.hono.tracing.TracingHelper;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;


/**
 * A data store for tenant information.
 */
public class AdapterStore extends AbstractTenantStore {

    private final Statement readByTrustAnchorStatement;

    /**
     * Create a new instance.
     *
     * @param client The client to use for accessing the DB.
     * @param tracer The tracer to use.
     * @param cfg The statement configuration to use.
     */
    public AdapterStore(final JDBCClient client, final Tracer tracer, final StatementConfiguration cfg) {
        super(client, tracer, cfg);

        this.readByTrustAnchorStatement = cfg
                .getRequiredStatement("readByTrustAnchor")
                .validateParameters("subject_dn");
    }

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
                .overrideWithDefaultPattern("base", dialect, AdapterStore.class, StatementConfiguration.DEFAULT_PATH.resolve("tenant"));

    }

    /**
     * Get tenant by ID operation.
     *
     * @param id The ID of the tenant to get.
     * @param spanContext The span to contribute to.
     * @return A future, tracking the outcome of the operation.
     */
    public Future<Optional<JsonObject>> getById(final String id, final SpanContext spanContext) {

        final Span span = TracingHelper.buildChildSpan(this.tracer, spanContext, "get tenant by id", getClass().getSimpleName())
                .withTag(TracingHelper.TAG_TENANT_ID, id)
                .start();

        return readTenant(id, span.context())
                .map(result -> result.map(AdapterStore::convert))
                .onComplete(x -> span.finish());

    }

    private Future<Optional<TenantReadResult>> readTenantByTrustAnchor(final String subjectDn, final SpanContext spanContext) {

        final var expanded = this.readByTrustAnchorStatement.expand(map -> {
            map.put("subject_dn", subjectDn);
        });

        return readTenantBy(this.client, expanded, spanContext);

    }

    /**
     * Get tenant by trust anchor operation.
     *
     * @param subjectDn The subject DN of the trust anchor.
     * @param spanContext The span to contribute to.
     * @return A future, tracking the outcome of the operation.
     */
    public Future<Optional<JsonObject>> getByTrustAnchor(final String subjectDn, final SpanContext spanContext) {

        final Span span = TracingHelper.buildChildSpan(this.tracer, spanContext, "get tenant by trust anchor", getClass().getSimpleName())
                .withTag("subject_dn", subjectDn)
                .start();

        return readTenantByTrustAnchor(subjectDn, span.context())
                .map(result -> result.map(AdapterStore::convert))
                .onComplete(x -> span.finish());

    }

    /**
     * Convert a stored tenant to a tenant object for adapters.
     *
     * @param tenantReadResult The tenant read result to convert.
     * @return The converted result.
     */
    static JsonObject convert(final TenantReadResult tenantReadResult) {

        return DeviceRegistryUtils.convertTenant(
                tenantReadResult.getId(),
                tenantReadResult.getTenant());

    }

}
