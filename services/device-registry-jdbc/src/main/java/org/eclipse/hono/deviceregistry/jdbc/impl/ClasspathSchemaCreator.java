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

import org.eclipse.hono.service.base.jdbc.config.JdbcProperties;
import org.eclipse.hono.service.base.jdbc.store.SQL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.jdbc.JDBCClient;

/**
 * Create the expected database schema if it does not exist from SQL script bundled with the classpath.
 */
public class ClasspathSchemaCreator implements org.eclipse.hono.deviceregistry.jdbc.SchemaCreator {

    private static final Logger log = LoggerFactory.getLogger(ClasspathSchemaCreator.class);

    private final JdbcProperties deviceProperties;
    private final JdbcProperties tenantProperties;
    private final Vertx vertx;

    /**
     * Creates a new instance.
     *
     * @param vertx The Vert.x instance to use.
     * @param devicesProperties The configuration properties for the device store.
     * @param tenantsProperties The configuration properties for the tenant store - may be {@code null}.
     * @throws NullPointerException if vertx or devicesProperties is {@code null}.
     */
    public ClasspathSchemaCreator(final Vertx vertx, final JdbcProperties devicesProperties,
            final JdbcProperties tenantsProperties) {

        Objects.requireNonNull(vertx);
        Objects.requireNonNull(devicesProperties);

        this.vertx = vertx;
        this.deviceProperties = devicesProperties;
        this.tenantProperties = tenantsProperties;
    }

    @Override
    public Future<Void> createDbSchema() {
        return createTenantSchema().compose(v -> createDeviceSchema());
    }

    private Future<Void> createTenantSchema() {
        if (tenantProperties == null) {
            return Future.succeededFuture();
        }

        final JDBCClient jdbcClient = JdbcProperties.dataSource(vertx, tenantProperties);

        switch (SQL.getDatabaseDialect(tenantProperties.getUrl())) {
        case SQL.DIALECT_POSTGRESQL:
            return readScript(jdbcClient, "'classpath:/sql/postgresql/01-create.tenants.sql'");
        case SQL.DIALECT_H2:
            return readScript(jdbcClient, "'classpath:/sql/h2/01-create.tenants.sql'");
        default:
            return Future.failedFuture(new IllegalArgumentException(
                    "Unknown database dialect '" + SQL.getDatabaseDialect(tenantProperties.getUrl()) + "'"));
        }
    }

    private Future<Void> createDeviceSchema() {
        final JDBCClient jdbcClient = JdbcProperties.dataSource(vertx, deviceProperties);

        switch (SQL.getDatabaseDialect(deviceProperties.getUrl())) {
        case SQL.DIALECT_POSTGRESQL:
            return readScript(jdbcClient, "'classpath:/sql/postgresql/02-create.devices.sql'");
        case SQL.DIALECT_H2:
            return readScript(jdbcClient, "'classpath:/sql/h2/02-create.devices.sql'");
        default:
            return Future.failedFuture(new IllegalArgumentException(
                    "Unknown database dialect '" + SQL.getDatabaseDialect(deviceProperties.getUrl()) + "'"));
        }
    }

    private Future<Void> readScript(final JDBCClient client, final String scriptPath) {
        log.debug("Creating database schema from file {}", scriptPath);
        client.call("RUNSCRIPT FROM " + scriptPath, ar -> {
            if (ar.succeeded()) {
                log.info("Created database schema from file {}", scriptPath);
            } else {
                log.error("Failed to create database schema from file {}", scriptPath, ar.cause());
            }
        });
        return Future.succeededFuture();
    }

}
