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

package org.eclipse.hono.service.base.jdbc.store.tenant;

import java.io.IOException;
import java.util.Optional;

import org.eclipse.hono.service.base.jdbc.config.JdbcProperties;

import io.opentracing.Tracer;
import io.vertx.core.Vertx;

/**
 * Helper class for working with tenant backing stores.
 */
public final class Stores {

    private Stores() {
    }

    /**
     * Create a new adapter store.
     *
     * @param vertx The vert.x instance to use.
     * @param tracer The tracer to use.
     * @param properties The JDBC driver properties.
     * @return A new adapter store.
     * @throws IOException if the SQL configuration could not be read.
     */
    public static AdapterStore adapterStore(
            final Vertx vertx,
            final Tracer tracer,
            final JdbcProperties properties) throws IOException {

        final var client = JdbcProperties.dataSource(vertx, properties);
        final var jdbcUrl = properties.getUrl();

        return new AdapterStore(
                client, tracer,
                AdapterStore.defaultStatementConfiguration(
                        jdbcUrl,
                        Optional.ofNullable(properties.getTableName()),
                        Optional.ofNullable(properties.getTableName()).map(name -> name + "_trust_anchors")
                ));

    }

    /**
     * Create a new management store.
     *
     * @param vertx The vert.x instance to use.
     * @param tracer The tracer to use.
     * @param properties The JDBC driver properties.
     * @return A new adapter store.
     * @throws IOException if the SQL configuration could not be read.
     */
    public static ManagementStore managementStore(
            final Vertx vertx,
            final Tracer tracer,
            final JdbcProperties properties) throws IOException {

        final var client = JdbcProperties.dataSource(vertx, properties);
        final var jdbcUrl = properties.getUrl();

        return new ManagementStore(
                client, tracer,
                ManagementStore.defaultStatementConfiguration(
                        jdbcUrl,
                        Optional.ofNullable(properties.getTableName()),
                        Optional.ofNullable(properties.getTableName()).map(name -> name + "_trust_anchors")
                ));

    }

}
