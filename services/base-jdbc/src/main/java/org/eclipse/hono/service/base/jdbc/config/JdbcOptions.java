/*******************************************************************************
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.service.base.jdbc.config;

import java.util.Optional;
import java.util.OptionalInt;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.ConfigMapping.NamingStrategy;

/**
 * Configuration properties for a JDBC service.
 */
@ConfigMapping(prefix = "hono.jdbc", namingStrategy = NamingStrategy.VERBATIM)
public interface JdbcOptions {

    /**
     * Gets the URL to use for connecting to the DB.
     *
     * @return The URL.
     */
    String url();

    /**
     * Gets the JDBC driver class name.
     *
     * @return The class name.
     */
    String driverClass();

    /**
     * Gets the user name to use for authenticating to the DB.
     *
     * @return The user name.
     */
    Optional<String> username();

    /**
     * Gets the password to use for authenticating to the DB.
     *
     * @return The password.
     */
    Optional<String> password();

    /**
     * Gets the maximum size of the DB connection pool.
     *
     * @return The maximum number of connections in the pool.
     */
    OptionalInt maximumPoolSize();

    /**
     * Gets the name of the table that contains the data.
     *
     * @return The table name.
     */
    Optional<String> tableName();
}
