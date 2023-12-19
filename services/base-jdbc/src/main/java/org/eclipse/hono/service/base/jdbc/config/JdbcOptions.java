/*******************************************************************************
 * Copyright (c) 2022, 2023 Contributors to the Eclipse Foundation
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

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.ConfigMapping.NamingStrategy;
import io.smallrye.config.WithDefault;

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
    @WithDefault("15")
    int maximumPoolSize();

    /**
     * Gets the minimum size of the DB connection pool.
     *
     * @return The minimum number of connections in the pool.
     */
    @WithDefault("3")
    int minimumPoolSize();

    /**
     * Gets the initial size of the DB connection pool.
     *
     * @return The initial number of connections in the pool.
     */
    @WithDefault("3")
    int initialPoolSize();

    /**
     * Gets the maximum idle time of connections in the DB connection pool.
     *
     * @return The maximum idle time of connections in the pool.
     */
    @WithDefault("3600")
    int maximumIdleTime();

    /**
     * Gets the maximum connection time for acquiring a connection from the DB connection pool.
     *
     * @return The maximum connection time for acquiring a connection from the pool.
     */
    @WithDefault("30")
    int maximumConnectionTime();

    /**
     * Gets the connection validation time interval in the DB connection pool.
     *
     * @return The connection validation time interval in the pool.
     */
    @WithDefault("30")
    int validationTime();

    /**
     * Gets the connection leak time limit from the DB connection pool.
     *
     * @return The connection leak time limit from the pool.
     */
    @WithDefault("60")
    int leakTime();

    /**
     * Gets the name of the table that contains the data.
     *
     * @return The table name.
     */
    Optional<String> tableName();
}
