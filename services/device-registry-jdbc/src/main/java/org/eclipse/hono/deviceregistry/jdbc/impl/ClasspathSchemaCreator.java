/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.hono.deviceregistry.jdbc.impl;

import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.deviceregistry.jdbc.config.SchemaCreator;
import org.eclipse.hono.service.base.jdbc.config.JdbcProperties;
import org.eclipse.hono.service.base.jdbc.store.SQL;
import org.eclipse.hono.service.base.jdbc.store.Statement;
import org.eclipse.hono.tracing.TracingHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.jdbc.JDBCClient;

/**
 * Create the expected database schema if it does not exist from SQL script bundled with the classpath.
 */
public class ClasspathSchemaCreator implements SchemaCreator {

    private static final Logger log = LoggerFactory.getLogger(ClasspathSchemaCreator.class);

    private final JdbcProperties deviceProperties;
    private final JdbcProperties tenantProperties;
    private final Vertx vertx;
    private final Tracer tracer;

    /**
     * Creates a new instance.
     *
     * @param vertx The Vert.x instance to use.
     * @param tracer The tracer to use.
     * @param devicesProperties The configuration properties for the device store.
     * @param tenantsProperties The configuration properties for the tenant store - may be {@code null}.
     * @throws NullPointerException if vertx or devicesProperties is {@code null}.
     */
    public ClasspathSchemaCreator(final Vertx vertx, final Tracer tracer, final JdbcProperties devicesProperties,
            final JdbcProperties tenantsProperties) {

        Objects.requireNonNull(vertx);
        Objects.requireNonNull(tracer);
        Objects.requireNonNull(devicesProperties);

        this.vertx = vertx;
        this.tracer = tracer;
        this.deviceProperties = devicesProperties;
        this.tenantProperties = tenantsProperties;
    }

    @Override
    public Future<Void> createDbSchema() {
        final Span span = TracingHelper.buildServerChildSpan(tracer, null,
                "create database schema", getClass().getSimpleName()).start();

        if (tenantProperties == null) {
            return loadAndRunScript(deviceProperties, "02-create.devices.sql", span.context());
        } else {
            return loadAndRunScript(tenantProperties, "01-create.tenants.sql", span.context())
                    .compose(v -> loadAndRunScript(deviceProperties, "02-create.devices.sql", span.context()));
        }

    }

    private Future<Void> loadAndRunScript(final JdbcProperties jdbcProperties, final String scriptName,
            final SpanContext context) {

        final String dialect = SQL.getDatabaseDialect(jdbcProperties.getUrl());

        if (!SQL.isSupportedDatabaseDialect(dialect)) {
            return Future.failedFuture(new IllegalArgumentException("Unknown database dialect '" + dialect + "'"));
        }

        final String scriptPath = "sql/" + dialect + "/" + scriptName;
        log.info("Creating database schema from file {}", scriptPath);

        final Promise<Buffer> loadScriptTracker = Promise.promise();
        vertx.fileSystem().readFile(scriptPath, loadScriptTracker);
        return loadScriptTracker.future()
                .map(Buffer::toString)
                .compose(script -> runScript(jdbcProperties, script, context));
    }

    private Future<Void> runScript(final JdbcProperties jdbcProperties, final String script, final SpanContext ctx) {

        final JDBCClient jdbcClient = JdbcProperties.dataSource(vertx, jdbcProperties);

        final Promise<Void> clientCloseTracker = Promise.promise();
        SQL.runTransactionally(jdbcClient, tracer, ctx,
                (connection, context) -> {
                    return Optional.ofNullable(Statement.statement(script))
                            .map(Statement::expand)
                            .map(stmt -> {
                                log.debug("creating database schema in [{}] using script: {}", jdbcProperties.getUrl(), stmt);
                                return stmt
                                        .query(jdbcClient)
                                        .recover(SQL::translateException);
                            })
                            .orElseGet(() -> {
                                log.warn("cannot create database schema in [{}]: script can not be expanded to SQL statement",
                                        jdbcProperties.getUrl());
                                return Future.failedFuture("cannot create database schema using script");
                            });
                })
                .onComplete(ar -> jdbcClient.close(clientCloseTracker));
        return clientCloseTracker.future();
    }

}
