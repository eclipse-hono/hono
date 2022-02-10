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

package org.eclipse.hono.deviceregistry.mongodb.config;

import java.util.Optional;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.ConfigMapping.NamingStrategy;
import io.smallrye.config.WithDefault;

/**
 * Options for configuring a connection to a Mongo DB server.
 */
@ConfigMapping(prefix = "hono.mongodb", namingStrategy = NamingStrategy.VERBATIM)
public interface MongoDbConfigOptions {

    /**
     * Gets the name or literal IP address of the host the Mongo DB instance is
     * running on.
     *
     * @return The host name.
     */
    Optional<String> host();

    /**
     * Gets the TCP port that the Mongo DB is listening on.
     *
     * @return The port number.
     */
    @WithDefault("27017")
    int port();

    /**
     * Gets the database name.
     *
     * @return The database name.
     */
    Optional<String> dbName();

    /**
     * Gets the user name for authenticating to the Mongo DB.
     *
     * @return The user name.
     */
    Optional<String> username();

    /**
     * Gets the password for authenticating to the Mongo DB.
     *
     * @return The password.
     */
    Optional<String> password();

    /**
     * Gets the connection string for the Mongo DB.
     * <p>
     * If set, the connection string overrides the other connection settings.
     * Format:
     * <pre>
     * mongodb://[username:password@]host1[:port1][,host2[:port2],...[,hostN[:portN]]][/[database][?options]]
     * </pre>
     *
     * @return The connection string.
     * @see "https://docs.mongodb.com/manual/reference/connection-string/"
     */
    Optional<String> connectionString();

    /**
     * Gets the timeout for selecting a Mongo DB server for an operation.
     * <p>
     * When this property is not set, the Vert.x Mongo DB client uses a default value of 1000ms.
     *
     * @return The server selection timeout in milliseconds.
     */
    @WithDefault("1000")
    int serverSelectionTimeout();

    /**
     * Gets the timeout for connecting to the Mongo DB.
     * <p>
     * When this property is not set, the Vert.x Mongo DB client uses a default value of 10000 ms.
     *
     * @return The connection timeout in milliseconds.
     */
    @WithDefault("10000")
    int connectTimeout();
}
