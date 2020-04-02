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

package org.eclipse.hono.deviceregistry.mongodb.utils;

import java.util.Objects;
import java.util.Optional;

import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbConfigProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.IndexOptions;
import io.vertx.ext.mongo.MongoClient;

/**
 * Utility class for Vert.x mongodb client access.
 */
public final class MongoDbCallExecutor {

    private static final Logger log = LoggerFactory.getLogger(MongoDbCallExecutor.class);
    private static final int INDEX_CREATION_RETRY_INTERVAL_IN_MS = 3000;

    private final MongoDbConfigProperties config;
    private final MongoClient mongoClient;
    private final Vertx vertx;

    /**
     * Creates an instance of the {@link MongoDbCallExecutor}.
     *
     * @param vertx The Vert.x instance to use.
     * @param config The mongodb configuration properties to use.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public MongoDbCallExecutor(final Vertx vertx, final MongoDbConfigProperties config) {
        this.vertx = Objects.requireNonNull(vertx);
        this.config = Objects.requireNonNull(config);
        this.mongoClient = MongoClient.createShared(vertx, getMongoClientConfigAsJson());
    }

    /**
     * Gets the mongo client.
     *
     * @return The mongo client.
     */
    public MongoClient getMongoClient() {
        return mongoClient;
    }

    /**
     * Creates an index for the given mongodb collection.
     *
     * @param collectionName The name of the mongodb collection.
     * @param keys The keys to be indexed.
     * @param options The options used to configure index, which is optional.
     * @param noOfRetries Then number of retries in case the index creation fails.
     * @return A future indicating the outcome of the index creation operation.
     */
    public Future<Void> createCollectionIndex(final String collectionName, final JsonObject keys,
            final IndexOptions options, final int noOfRetries) {
        final Promise<Void> indexCreationPromise = Promise.promise();
        log.debug("Creating an index for the collection [{}]", collectionName);

        mongoClient.createIndexWithOptions(collectionName, keys, options, res -> {
            if (res.succeeded()) {
                log.debug("Successfully created an index for the collection[{}]", collectionName);
                indexCreationPromise.complete();
            } else {
                if (noOfRetries > 0) {
                    log.error("Failed creating an index for the collection [{}], retry creating index.", collectionName,
                            res.cause());
                    vertx.setTimer(INDEX_CREATION_RETRY_INTERVAL_IN_MS,
                            id -> createCollectionIndex(collectionName, keys, options, noOfRetries - 1));
                } else {
                    log.error("Failed creating an index for the collection [{}]", collectionName, res.cause());
                    indexCreationPromise.fail(res.cause());
                }
            }
        });
        return indexCreationPromise.future();
    }

    /**
     * Returns the mongodb properties as a json object suited to instantiate a #{@link MongoClient}. 
     * <p>
     * If the connectionString is set, it will override all the other connection settings.
     *
     * @return The mongodb client configuration as a json object.
     */
    private JsonObject getMongoClientConfigAsJson() {
        final JsonObject configJson = new JsonObject();
        if (config.getConnectionString() != null) {
            configJson.put("connection_string", config.getConnectionString());
            log.warn("Since connection string is set, the other connection properties if any set, will be ignored");
        } else {
            configJson.put("host", config.getHost())
                    .put("port", config.getPort())
                    .put("db_name", config.getDbName())
                    .put("username", config.getUsername())
                    .put("password", config.getPassword());

            Optional.ofNullable(config.getServerSelectionTimeout())
                    .ifPresent(timeout -> configJson.put("serverSelectionTimeoutMS", timeout));
            Optional.ofNullable(config.getConnectTimeout())
                    .ifPresent(timeout -> configJson.put("connectTimeoutMS", timeout));
        }
        return configJson;
    }
}
