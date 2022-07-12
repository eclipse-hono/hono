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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.hono.service.base.jdbc.store.AbstractStore;
import org.eclipse.hono.service.base.jdbc.store.SQL;
import org.eclipse.hono.service.base.jdbc.store.Statement;
import org.eclipse.hono.service.base.jdbc.store.Statement.ExpandedStatement;
import org.eclipse.hono.service.base.jdbc.store.StatementConfiguration;
import org.eclipse.hono.service.management.tenant.Tenant;
import org.eclipse.hono.service.management.tenant.TrustedCertificateAuthority;
import org.eclipse.hono.tracing.TracingHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.vertx.core.Future;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLOperations;

/**
 * A data store for tenant information.
 */
public abstract class AbstractTenantStore extends AbstractStore {

    public static final String DEFAULT_TENANTS_TABLE_NAME = "tenants";
    public static final String DEFAULT_TRUST_ANCHORS_NAME = "tenant_trust_anchors";

    private static final Logger log = LoggerFactory.getLogger(AbstractTenantStore.class);

    protected final JDBCClient client;
    protected final Tracer tracer;

    private final Statement readStatement;
    private final Statement readTrustAnchorsStatement;

    /**
     * Create a new instance.
     *
     * @param client The client to use for accessing the DB.
     * @param tracer The tracer to use.
     * @param cfg The statement configuration to use.
     */
    public AbstractTenantStore(final JDBCClient client, final Tracer tracer, final StatementConfiguration cfg) {
        super(client, tracer, cfg.getStatement("checkConnection"));
        cfg.dump(log);

        this.client = client;
        this.tracer = tracer;

        this.readStatement = cfg
                .getRequiredStatement("read")
                .validateParameters(
                        "tenant_id"
                );

        this.readTrustAnchorsStatement = cfg
                .getRequiredStatement("readTrustAnchors")
                .validateParameters(
                        "tenant_id"
                );
    }

    /**
     * Read the content of a tenant.
     * <p>
     * This will execute the {@code read} statement and return the unprocessed information.
     * <p>
     * If there are no entries, an empty result will be returned.
     * <p>
     * If more than one entry is being found, the result will be failed with an
     * {@link IllegalStateException} exception.
     *
     * @param id The key to the tenant entry.
     * @param spanContext The span to contribute to.
     * @return The future, tracking the outcome of the operation.
     */
    public Future<Optional<TenantReadResult>> readTenant(final String id, final SpanContext spanContext) {

        final Span span = TracingHelper.buildChildSpan(this.tracer, spanContext, "read tenant", getClass().getSimpleName())
                .withTag(TracingHelper.TAG_TENANT_ID, id)
                .start();

        final var expanded = this.readStatement.expand(map -> {
            map.put("tenant_id", id);
        });

        return SQL.runTransactionally(
                this.client,
                this.tracer,
                span.context(),
                (connection, context) -> readTenantBy(connection, expanded, context))
                .onComplete(x -> span.finish());

    }

    /**
     * Read a tenant, using the provided statement.
     *
     * @param operations The operations to use.
     * @param expanded The statement to use.
     * @param spanContext The span to contribute to.
     * @return A future, tracking the outcome of the operation.
     */
    protected Future<Optional<TenantReadResult>> readTenantBy(final SQLOperations operations, final ExpandedStatement expanded, final SpanContext spanContext) {

        final Span span = TracingHelper.buildChildSpan(this.tracer, spanContext, "read tenant by", getClass().getSimpleName())
                .start();

        return expanded

                .trace(this.tracer, span.context())
                .query(operations)

                .<Optional<TenantReadResult>>flatMap(r -> {
                    final var entries = r.getRows(true);
                    span.log(Map.of(
                            "event", "read result",
                            "rows", entries.size()));
                    switch (entries.size()) {
                        case 0:
                            return Future.succeededFuture(Optional.empty());
                        case 1:
                            return Future
                                    .succeededFuture(entries.get(0))
                                    .map(entry -> {
                                        final var id = entry.getString("tenant_id");
                                        final var tenant = Json.decodeValue(entry.getString("data"), Tenant.class);
                                        final var version = Optional.ofNullable(entry.getString("version"));
                                        return Optional.of(new TenantReadResult(id, tenant, version));
                                    });
                        default:
                            return Future.failedFuture(new IllegalStateException("Found multiple entries for a single tenant"));
                    }
                })

                // fill trust anchors

                .flatMap(result -> {

                    if (result.isPresent()) {
                        return fillTrustAnchors(operations, result.get(), span.context())
                                .map(Optional::ofNullable);
                    } else {
                        return Future.succeededFuture(result);
                    }

                })

                .onComplete(x -> span.finish());

    }

    /**
     * Read main tenant entry by ID.
     * <p>
     * The result set will contain zero or one rows.
     *
     * @param operations The operations to use.
     * @param id The ID of the tenant to read.
     * @param spanContext The span to contribute to.
     * @return A future, tracking the outcome of the operation.
     */
    protected Future<ResultSet> readTenantEntryById(final SQLOperations operations, final String id, final SpanContext spanContext) {

        final Span span = TracingHelper.buildChildSpan(this.tracer, spanContext, "read tenant entry", getClass().getSimpleName())
                .withTag(TracingHelper.TAG_TENANT_ID, id)
                .start();

        final var expanded = this.readStatement.expand(map -> {
            map.put("tenant_id", id);
        });

        return expanded
                .trace(this.tracer, span.context())
                .query(operations)
                .onComplete(x -> span.finish());

    }

    /**
     * Read all trust anchors for a tenant.
     * <p>
     * The result set will contain zero or more rows.
     *
     * @param operations The operations to use.
     * @param id The ID of the tenant to read the trust anchors for.
     * @param spanContext The span to contribute to.
     * @return A future, tracking the outcome of the operation.
     */
    protected Future<ResultSet> readTenantTrustAnchors(final SQLOperations operations, final String id, final SpanContext spanContext) {

        final Span span = TracingHelper.buildChildSpan(this.tracer, spanContext, "populate trust anchors", getClass().getSimpleName())
                .withTag(TracingHelper.TAG_TENANT_ID, id)
                .start();

        final var expanded = this.readTrustAnchorsStatement.expand(map -> {
            map.put("tenant_id", id);
        });

        log.debug("readTenantTrustAnchors - statement: {}", expanded);

        return expanded
                .trace(this.tracer, span.context())
                .query(operations)
                .onComplete(x -> span.finish());

    }

    /**
     * Fill the trust anchors for an already loaded tenant.
     *
     * @param operations The SQL operations to use.
     * @param tenant The tenant read result to populate.
     * @param spanContext The span to contribute to.
     * @return The future, tracking the outcome of the operation.
     */
    protected Future<TenantReadResult> fillTrustAnchors(
            final SQLOperations operations,
            final TenantReadResult tenant,
            final SpanContext spanContext
    ) {

        return readTenantTrustAnchors(operations, tenant.getId(), spanContext)
                .map(result -> {
                    tenant
                            .getTenant()
                            .setTrustedCertificateAuthorities(convertTrustAnchors(result));
                    return tenant;
                });

    }

    /**
     * Convert trust anchors from a result set.
     *
     * @param result The result to convert.
     * @return The converted trust anchors.
     */
    protected List<TrustedCertificateAuthority> convertTrustAnchors(final ResultSet result) {

        return result
                .getRows(true)
                .stream()
                .map(this::convertTrustAnchor)
                .collect(Collectors.toList());

    }

    /**
     * Convert a single trust anchor from a row.
     *
     * @param row The row to convert.
     * @return The result.
     */
    protected TrustedCertificateAuthority convertTrustAnchor(final JsonObject row) {

        final var subjectDn = row.getString("subject_dn");
        final var result = Json.decodeValue(row.getString("data"), TrustedCertificateAuthority.class);

        // set subject DN
        result.setSubjectDn(subjectDn);

        return result;

    }

}
