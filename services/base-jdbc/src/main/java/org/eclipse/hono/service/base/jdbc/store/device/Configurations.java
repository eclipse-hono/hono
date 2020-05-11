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

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

import org.eclipse.hono.service.base.jdbc.store.SQL;
import org.eclipse.hono.service.base.jdbc.store.StatementConfiguration;

/**
 * A class for configuring data stores.
 */
public final class Configurations {

    /**
     * Default table name, when using the JSON data model.
     */
    public static final String DEFAULT_TABLE_NAME_JSON = "devices";

    /**
     * Default table name of the credentials table, when using the flat table model.
     */
    public static final String DEFAULT_TABLE_NAME_CREDENTIALS = "device_credentials";

    /**
     * Default table name of the registrations table, when using the flat table model.
     */
    public static final String DEFAULT_TABLE_NAME_REGISTRATIONS = "device_registrations";

    private Configurations() {
    }

    /**
     * Create a new default table configuration.
     *
     * @param jdbcUrl The JDBC URL.
     * @param tableNameCredentials The optional table name for the credentials table.
     * @param tableNameRegistrations The optional table name for the registration table.
     * @return The newly created configuration.
     *
     * @throws IOException in case an IO error occurs, when reading the statement configuration.
     */
    public static StatementConfiguration tableConfiguration(final String jdbcUrl, final Optional<String> tableNameCredentials, final Optional<String> tableNameRegistrations)
            throws IOException {

        final String dialect = SQL.getDatabaseDialect(jdbcUrl);

        final String tableNameCredentialsString = tableNameCredentials.orElse(DEFAULT_TABLE_NAME_CREDENTIALS);
        final String tableNameRegistrationsString = tableNameRegistrations.orElse(DEFAULT_TABLE_NAME_REGISTRATIONS);

        final Path base = StatementConfiguration.DEFAULT_PATH.resolve("device");

        return StatementConfiguration
                .empty(tableNameRegistrationsString, tableNameCredentialsString)
                .overrideWithDefaultPattern("base", dialect, Configurations.class, base)
                .overrideWithDefaultPattern("table", dialect, Configurations.class, base);

    }

    /**
     * Create a new default table configuration.
     *
     * @param jdbcUrl The JDBC URL.
     * @param tableName The optional table name.
     * @param hierarchical If the JSON store uses a hierarchical model for a flat one.
     * @return The newly created configuration.
     *
     * @throws IOException in case an IO error occurs, when reading the statement configuration.
     */
    public static StatementConfiguration jsonConfiguration(final String jdbcUrl, final Optional<String> tableName, final boolean hierarchical) throws IOException {

        final String dialect = SQL.getDatabaseDialect(jdbcUrl);
        final String tableNameString = tableName.orElse(DEFAULT_TABLE_NAME_JSON);
        final String jsonModel = hierarchical ? "json.tree" : "json.flat";

        final Path base = StatementConfiguration.DEFAULT_PATH.resolve("device");

        return StatementConfiguration
                .empty(tableNameString)
                .overrideWithDefaultPattern("base", dialect, Configurations.class, base)
                .overrideWithDefaultPattern("json", dialect, Configurations.class, base)
                .overrideWithDefaultPattern(jsonModel, dialect, Configurations.class, base);

    }

}
