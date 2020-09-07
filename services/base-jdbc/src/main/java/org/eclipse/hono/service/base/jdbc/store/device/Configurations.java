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
     * Default table name of the credentials table, when using the flat table model.
     */
    public static final String DEFAULT_TABLE_NAME_CREDENTIALS = "device_credentials";

    /**
     * Default table name of the registrations table, when using the flat table model.
     */
    public static final String DEFAULT_TABLE_NAME_REGISTRATIONS = "device_registrations";

    /**
     * Default table name of the groups table, when using the flat table model.
     */
    public static final String DEFAULT_TABLE_NAME_GROUPS = "device_groups";

    private Configurations() {
    }

    /**
     * Create a new default table configuration.
     *
     * @param jdbcUrl The JDBC URL.
     * @param tableNameCredentials The optional table name for the credentials table.
     * @param tableNameRegistrations The optional table name for the registration table.
     * @param tableNameGroups The optional table name for the groups table.
     *
     * @return The newly created configuration.
     *
     * @throws IOException in case an IO error occurs, when reading the statement configuration.
     */
    public static StatementConfiguration tableConfiguration(
            final String jdbcUrl,
            final Optional<String> tableNameCredentials,
            final Optional<String> tableNameRegistrations,
            final Optional<String> tableNameGroups)
            throws IOException {

        final String dialect = SQL.getDatabaseDialect(jdbcUrl);

        final String tableNameCredentialsString = tableNameCredentials.orElse(DEFAULT_TABLE_NAME_CREDENTIALS);
        final String tableNameRegistrationsString = tableNameRegistrations.orElse(DEFAULT_TABLE_NAME_REGISTRATIONS);
        final String tableNameGroupsString = tableNameGroups.orElse(DEFAULT_TABLE_NAME_GROUPS);

        final Path base = StatementConfiguration.DEFAULT_PATH.resolve("device");

        return StatementConfiguration
                .empty(tableNameRegistrationsString, tableNameCredentialsString, tableNameGroupsString)
                .overrideWithDefaultPattern("base", dialect, Configurations.class, base);

    }

}
