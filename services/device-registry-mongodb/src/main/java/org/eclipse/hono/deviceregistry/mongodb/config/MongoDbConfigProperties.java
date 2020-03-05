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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.json.JsonObject;

/**
 * A POJO for configuring MongoDB properties used by the
 * MongoDbBasedRegistrationService.
 */
public class MongoDbConfigProperties {

    private static final Logger LOG = LoggerFactory.getLogger(MongoDbConfigProperties.class);

    private String host;
    private int port = 0;
    private String dbName;
    private String username;
    private String password;
    private String connectionString;
    private int serverSelectionTimeoutMS = 0;
    private int connectTimeoutMS = 0;
    private int createIndicesTimeoutMS = 3000;

    /**
     * Gets the name or literal IP address of the host the MongoDB instance is
     * running on.
     *
     * @return host name
     */
    public String getHost() {
        return host;
    }

    /**
     * Sets the name or literal IP address of the host the MongoDB instance is
     * running on.
     *
     * @param host host name or IP address
     * @return This instance for setter chaining.
     */
    public MongoDbConfigProperties setHost(final String host) {
        this.host = host;
        return this;
    }

    /**
     * Gets the ports the MongoDB is listening on.
     *
     * @return port number
     */
    public int getPort() {
        return port;
    }

    /**
     * Sets the ports the MongoDB is listening on.
     *
     * @param port port number
     * @return This instance for setter chaining.
     */
    public MongoDbConfigProperties setPort(final int port) {
        this.port = port;
        return this;
    }

    /**
     * Gets the database name.
     *
     * @return database name
     */
    public String getDbName() {
        return dbName;
    }

    /**
     * Sets the database name.
     *
     * @param dbName database name
     * @return This instance for setter chaining.
     */
    public MongoDbConfigProperties setDbName(final String dbName) {
        this.dbName = dbName;
        return this;
    }

    /**
     * Gets the user name used for authentication.
     *
     * @return user name
     */
    public String getUsername() {
        return username;
    }

    /**
     * Sets the user name used for authentication.
     *
     * @param username user name
     * @return This instance for setter chaining.
     */
    public MongoDbConfigProperties setUsername(final String username) {
        this.username = username;
        return this;
    }

    /**
     * Gets the password used for authentication.
     *
     * @return password
     */
    public String getPassword() {
        return password;
    }

    /**
     * Sets the password used for authentication.
     *
     * @param password the password
     * @return This instance for setter chaining.
     */
    public MongoDbConfigProperties setPassword(final String password) {
        this.password = password;
        return this;
    }

    /**
     * Gets the connection string for the MongoDB client.
     *
     * @return connection string
     */
    public String getConnectionString() {
        return connectionString;
    }

    /**
     * Sets the connection string for the MongoDB client. If set, the connection
     * string overrides the other connection settings. Format:
     * mongodb://[username:password@]host1[:port1][,host2[:port2],...[,hostN[:portN]]][/[database][?options]]
     *
     * @param connectionString connection string
     * @return This instance for setter chaining.
     */
    public MongoDbConfigProperties setConnectionString(final String connectionString) {
        this.connectionString = connectionString;
        return this;
    }

    /**
     * Gets the time in milliseconds that the mongo driver will wait to select a
     * server for an operation before raising an error.
     *
     * @return time in milliseconds
     */
    public int getServerSelectionTimeout() {
        return serverSelectionTimeoutMS;
    }

    /**
     * Sets the time in milliseconds that the mongo driver will wait to select a
     * server for an operation before raising an error.
     *
     * @param timeout timeout in milliseconds. Setting to zero means the default
     *                value of Vert.x should be used.
     * @return This instance for setter chaining.
     */
    public MongoDbConfigProperties setServerSelectionTimeout(final int timeout) {
        this.serverSelectionTimeoutMS = timeout;
        return this;
    }

    /**
     * Gets the time in milliseconds to attempt a connection before timing out.
     *
     * @return time in milliseconds
     */
    public int getConnectTimeout() {
        return connectTimeoutMS;
    }

    /**
     * Sets the time in milliseconds to attempt a connection before timing out.
     *
     * @param connectTimeout timeout in milliseconds. Setting to zero means the default
     *                       value of Vert.x should be used.
     * @return This instance for setter chaining.
     */
    public MongoDbConfigProperties setConnectTimeout(final int connectTimeout) {
        this.connectTimeoutMS = connectTimeout;
        return this;
    }

    /**
     * Gets the time in milliseconds to create indices during startup.
     *
     * @return time in milliseconds
     */
    public int getCreateIndicesTimeout() {
        return createIndicesTimeoutMS;
    }

    /**
     * Sets the time in milliseconds that the startup will try to create indices
     * during startup.
     *
     * @param timeout timeout in milliseconds.
     * @return This instance for setter chaining.
     */
    public MongoDbConfigProperties setCreateIndicesTimeout(final int timeout) {
        this.createIndicesTimeoutMS = timeout;
        return this;
    }

    /**
     * Returns the properties of this instance in a JsonObject suitable for
     * initializing a Vertx MongoClient object. Note: if the connectionString is
     * set, it will override all other connection settings.
     *
     * @return MongoDB client config object
     */
    public JsonObject asMongoClientConfigJson() {
        final JsonObject config = new JsonObject();
        if (connectionString != null) {
            config.put("connection_string", connectionString);

            if (host != null || port != 0 || dbName != null || username != null || password != null
                    || serverSelectionTimeoutMS > 0 || connectTimeoutMS > 0) {
                LOG.warn(
                        "asMongoClientConfigJson: connectionString is set, other connection properties will be ignored");
            }
        } else {
            if (host != null) {
                config.put("host", host);
            }
            if (port != 0) {
                config.put("port", port);
            }
            if (dbName != null) {
                config.put("db_name", dbName);
            }
            if (username != null) {
                config.put("username", username);
            }
            if (password != null) {
                config.put("password", password);
            }
            if (serverSelectionTimeoutMS > 0) {
                config.put("serverSelectionTimeoutMS", serverSelectionTimeoutMS);
            }
            if (connectTimeoutMS > 0) {
                config.put("connectTimeoutMS", connectTimeoutMS);
            }
        }
        return config;
    }
}
