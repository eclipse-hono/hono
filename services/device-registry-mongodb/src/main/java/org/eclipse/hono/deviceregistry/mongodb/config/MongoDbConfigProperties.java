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

package org.eclipse.hono.deviceregistry.mongodb.config;

import java.util.Objects;

import org.eclipse.hono.util.PortConfigurationHelper;

/**
 * A POJO for configuring mongodb properties used by the
 * {@link org.eclipse.hono.deviceregistry.mongodb.service.MongoDbBasedRegistrationService}.
 */
public final class MongoDbConfigProperties {

    private static final int DEFAULT_CREATE_INDICES_TIMEOUT_IN_MS = 3000;
    private static final int DEFAULT_PORT = 27017;

    private String host = "localhost";
    private int port = DEFAULT_PORT;
    private String dbName;
    private String username;
    private String password;
    private String connectionString;
    private int serverSelectionTimeoutInMs = 0;
    private int connectionTimeoutInMs = 0;
    private int createIndicesTimeoutInMs = DEFAULT_CREATE_INDICES_TIMEOUT_IN_MS;

    /**
     * Gets the name or literal IP address of the host the mongodb instance is
     * running on.
     *
     * @return The host name.
     */
    public String getHost() {
        return host;
    }

    /**
     * Sets the name or literal IP address of the host the mongodb instance is
     * running on.
     *
     * @param host host name or IP address
     * @return A reference to this for fluent use.
     * @throws NullPointerException if host is {@code null}.
     */
    public MongoDbConfigProperties setHost(final String host) {
        this.host = Objects.requireNonNull(host);
        return this;
    }

    /**
     * Gets the TCP port that the mongodb is listening on.
     *
     * @return The port number.
     */
    public int getPort() {
        return port;
    }

    /**
     * Sets the TCP port that the mongodb is listening on.
     * <p>
     * The default port value is {@link #DEFAULT_PORT}.
     *
     * @param port The port number.
     * @return A reference to this for fluent use.
     * @throws IllegalArgumentException if port &lt; 1000 or port &gt; 65535.
     */
    public MongoDbConfigProperties setPort(final int port) {
        if (PortConfigurationHelper.isValidPort(port)) {
            this.port = port;
        } else {
            throw new IllegalArgumentException("invalid port number");
        }
        return this;
    }

    /**
     * Gets the database name.
     *
     * @return The database name.
     */
    public String getDbName() {
        return dbName;
    }

    /**
     * Sets the database name.
     *
     * @param dbName The database name
     * @return A reference to this for fluent use.
     * @throws NullPointerException if dbName is {@code null}.
     */
    public MongoDbConfigProperties setDbName(final String dbName) {
        this.dbName = Objects.requireNonNull(dbName);
        return this;
    }

    /**
     * Gets the user name used for authentication.
     *
     * @return The user name.
     */
    public String getUsername() {
        return username;
    }

    /**
     * Sets the user name used for authentication.
     *
     * @param username The user name.
     * @return A reference to this for fluent use.
     * @throws NullPointerException if the username is {@code null}.
     */
    public MongoDbConfigProperties setUsername(final String username) {
        this.username = Objects.requireNonNull(username);
        return this;
    }

    /**
     * Gets the password used for authentication.
     *
     * @return The password.
     */
    public String getPassword() {
        return password;
    }

    /**
     * Sets the password used for authentication.
     *
     * @param password the password
     * @return A reference to this for fluent use.
     * @throws NullPointerException if password is {@code null}.
     */
    public MongoDbConfigProperties setPassword(final String password) {
        this.password = Objects.requireNonNull(password);
        return this;
    }

    /**
     * Gets the connection string for the mongodb client.
     *
     * @return The connection string.
     */
    public String getConnectionString() {
        return connectionString;
    }

    /**
     * Sets the connection string for the mongodb client. If set, the connection string
     * overrides the other connection settings. Format:
     * mongodb://[username:password@]host1[:port1][,host2[:port2],...[,hostN[:portN]]][/[database][?options]]
     *
     * @param connectionString The connection string.
     * @return A reference to this for fluent use.
     * @throws NullPointerException if the connectionString is {@code null}.
     */
    public MongoDbConfigProperties setConnectionString(final String connectionString) {
        this.connectionString = Objects.requireNonNull(connectionString);
        return this;
    }

    /**
     * Gets the time in milliseconds that the mongo driver will wait to select a
     * server for an operation before raising an error.
     *
     * @return The server selection timeout in milliseconds.
     */
    public int getServerSelectionTimeout() {
        return serverSelectionTimeoutInMs;
    }

    /**
     * Sets the timeout in milliseconds that the mongo driver will wait to select a server 
     * for an operation before raising an error.
     * <p>
     * When this property is set to 0, the default value of Vert.x should be used.
     * 
     * @param serverSelectionTimeoutInMs The server selection timeout in milliseconds.
     * @return A reference to this for fluent use.
     * @throws IllegalArgumentException if the timeout is set to &lt;= 0.
     */
    public MongoDbConfigProperties setServerSelectionTimeout(final int serverSelectionTimeoutInMs) {
        if (serverSelectionTimeoutInMs <= 0) {
            throw new IllegalArgumentException("server selection timeout must be greater than zero");
        }
        this.serverSelectionTimeoutInMs = serverSelectionTimeoutInMs;
        return this;
    }

    /**
     * Gets the timeout in milliseconds to attempt a connection before timing out.
     *
     * @return The connection timeout in milliseconds.
     */
    public int getConnectTimeout() {
        return connectionTimeoutInMs;
    }

    /**
     * Sets the timeout in milliseconds to attempt a connection before timing out.
     * <p>
     * When this property is set to 0, the default value of Vert.x should be used.
     *
     * @param connectionTimeoutInMs The connection timeout in milliseconds.
     * @return A reference to this for fluent use.
     * @throws IllegalArgumentException if the timeout is set to &lt;= 0.
     */
    public MongoDbConfigProperties setConnectTimeout(final int connectionTimeoutInMs) {
        if (connectionTimeoutInMs <= 0) {
            throw new IllegalArgumentException("connection timeout must be greater than zero");
        }
        this.connectionTimeoutInMs = connectionTimeoutInMs;
        return this;
    }

    /**
     * Gets the timeout in milliseconds to create indices during startup.
     *
     * @return The create indices timeout in milliseconds
     */
    public int getCreateIndicesTimeout() {
        return createIndicesTimeoutInMs;
    }

    /**
     * Sets the time in milliseconds that the startup will try to create indices 
     * during startup.
     * <p>
     * The default value of this property is {@link #DEFAULT_CREATE_INDICES_TIMEOUT_IN_MS}.
     *
     * @param createIndicesTimeoutInMs The create indices timeout in milliseconds.
     * @return A reference to this for fluent use.
     * @throws IllegalArgumentException if the timeout is set to &lt;= 0.
     */
    public MongoDbConfigProperties setCreateIndicesTimeout(final int createIndicesTimeoutInMs) {
        if (connectionTimeoutInMs <= 0) {
            throw new IllegalArgumentException("create indices timeout must be greater than zero");
        }
        this.createIndicesTimeoutInMs = createIndicesTimeoutInMs;
        return this;
    }

}
