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
import java.util.Optional;

import org.eclipse.hono.util.PortConfigurationHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;

/**
 * A POJO for configuring mongodb properties used by the
 * {@link org.eclipse.hono.deviceregistry.mongodb.service.MongoDbBasedRegistrationService}.
 */
public final class MongoDbConfigProperties {

    private static final int DEFAULT_PORT = 27017;
    private static final Logger LOG = LoggerFactory.getLogger(MongoDbConfigProperties.class);

    private String host = "localhost";
    private int port = DEFAULT_PORT;
    private String dbName;
    private String username;
    private String password;
    private String connectionString;
    private Integer serverSelectionTimeoutInMs;
    private Integer connectionTimeoutInMs;

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
     * @see "https://docs.mongodb.com/manual/reference/connection-string/"
     */
    public MongoDbConfigProperties setConnectionString(final String connectionString) {
        this.connectionString = Objects.requireNonNull(connectionString);
        return this;
    }

    /**
     * Gets the time in milliseconds that the mongo driver will wait to select a
     * server for an operation before raising an error.
     * <p>
     * When this property is not set, the Vert.x mongodb client uses a default value of 30000 ms.
     *
     * @return The server selection timeout in milliseconds.
     */
    public Integer getServerSelectionTimeout() {
        return serverSelectionTimeoutInMs;
    }

    /**
     * Sets the timeout in milliseconds that the mongo driver will wait to select a server 
     * for an operation before raising an error.
     * <p>
     * When this property is not set, the Vert.x mongodb client uses a default value of 30000 ms.
     *
     * @param serverSelectionTimeoutInMs The server selection timeout in milliseconds.
     * @return A reference to this for fluent use.
     * @throws IllegalArgumentException if the timeout is set to &lt; 0.
     */
    public MongoDbConfigProperties setServerSelectionTimeout(final int serverSelectionTimeoutInMs) {
        if (serverSelectionTimeoutInMs < 0) {
            throw new IllegalArgumentException("server selection timeout must not be negative");
        }
        this.serverSelectionTimeoutInMs = serverSelectionTimeoutInMs;
        return this;
    }

    /**
     * Gets the timeout in milliseconds to attempt a connection before timing out.
     * <p>
     * When this property is not set, the Vert.x mongodb client uses a default value of 10000 ms.
     *
     * @return The connection timeout in milliseconds.
     */
    public Integer getConnectTimeout() {
        return connectionTimeoutInMs;
    }

    /**
     * Sets the timeout in milliseconds to attempt a connection before timing out.
     * <p>
     * When this property is not set, the Vert.x mongodb client uses a default value of 10000 ms.
     *
     * @param connectionTimeoutInMs The connection timeout in milliseconds.
     * @return A reference to this for fluent use.
     * @throws IllegalArgumentException if the timeout is set to &lt; 0.
     */
    public MongoDbConfigProperties setConnectTimeout(final int connectionTimeoutInMs) {
        if (connectionTimeoutInMs < 0) {
            throw new IllegalArgumentException("connection timeout must not be negative");
        }
        this.connectionTimeoutInMs = connectionTimeoutInMs;
        return this;
    }

    /**
     * Returns the mongodb properties as a json object suited to instantiate a #{@link MongoClient}. 
     * <p>
     * If the connectionString is set, it will override all the other connection settings.
     *
     * @return The mongodb client configuration as a json object.
     */
    public JsonObject getMongoClientConfig() {
        final JsonObject configJson = new JsonObject();
        if (connectionString != null) {
            configJson.put("connection_string", connectionString);
            LOG.warn("Since connection string is set, the other connection properties if any set, will be ignored");
        } else {
            configJson.put("host", host)
                    .put("port", port)
                    .put("db_name", dbName)
                    .put("username", username)
                    .put("password", password);

            Optional.ofNullable(getServerSelectionTimeout())
                    .ifPresent(timeout -> configJson.put("serverSelectionTimeoutMS", timeout));
            Optional.ofNullable(getConnectTimeout())
                    .ifPresent(timeout -> configJson.put("connectTimeoutMS", timeout));
        }
        return configJson;
    }
}
