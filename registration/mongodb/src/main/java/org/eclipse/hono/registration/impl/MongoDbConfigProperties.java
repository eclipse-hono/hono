/**
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */
package org.eclipse.hono.registration.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import io.vertx.core.json.JsonObject;

/**
 * A POJO for configuring MongoDB properties used by the MongoDbBasedRegistrationService.
 */
@Component
@ConfigurationProperties(prefix = "hono.registration.mongoDb")
public class MongoDbConfigProperties {

    private static final Logger LOG = LoggerFactory.getLogger(MongoDbConfigProperties.class);
    
    private String host;
    private int port = 0;
    private String dbName;
    private String username;
    private String password;

    private String connectionString;

    private String collection;

    /**
     * Gets the name or literal IP address of the host the MongoDB instance is running on.
     * 
     * @return host name
     */
    public String getHost() {
        return host;
    }

    /**
     * Sets the name or literal IP address of the host the MongoDB instance is running on.
     * 
     * @param host host name or IP address
     * @return This instance for setter chaining.
     */
    public MongoDbConfigProperties setHost(String host) {
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
    public MongoDbConfigProperties setPort(int port) {
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
    public MongoDbConfigProperties setDbName(String dbName) {
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
    public MongoDbConfigProperties setUsername(String username) {
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
    public MongoDbConfigProperties setPassword(String password) {
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
     * Sets the connection string for the MongoDB client. 
     * If set, the connection string overrides the other connection settings. 
     * Format: 
     * mongodb://[username:password@]host1[:port1][,host2[:port2],...[,hostN[:portN]]][/[database][?options]]
     * 
     * @param connectionString connection string
     * @return This instance for setter chaining.
     */
    public MongoDbConfigProperties setConnectionString(String connectionString) {
        this.connectionString = connectionString;
        return this;
    }

    /**
     * Gets the collection to be used. 
     * 
     * @return name of the collection
     */
    public String getCollection() {
        return collection;
    }

    /**
     * Sets the name of the collection to be used.
     * 
     * @param collection name of the collection
     * @return This instance for setter chaining.
     */
    public MongoDbConfigProperties setCollection(String collection) {
        this.collection = collection;
        return this;
    }

    
    /**
     * Returns the properties of this instance in a JsonObject suitable for initializing 
     * a Vertx MongoClient object.
     * Note: if the connectionString is set, it will override all other connection settings.
     * 
     * @return MongoDB client config object
     */
    public JsonObject asMongoClientConfigJson() {
        final JsonObject config = new JsonObject();
        if (connectionString != null) {
            config.put("connection_string", connectionString);

            if (host != null || port != 0 || dbName != null || username != null || password != null) {
                LOG.info("asMongoClientConfigJson: connectionString is set, other connection properties will be ignored");
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
        }
        return config;
    }
}
