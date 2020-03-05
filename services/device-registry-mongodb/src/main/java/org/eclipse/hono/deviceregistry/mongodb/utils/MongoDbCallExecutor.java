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

import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbConfigProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.MongoSocketException;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.IndexOptions;
import io.vertx.ext.mongo.MongoClient;

/**
 * Utility for vertx mongodb client access.
 */
public final class MongoDbCallExecutor {

    private static final Logger log = LoggerFactory.getLogger(MongoDbCallExecutor.class);

    private final MongoClient mongoClient;
    private final MongoDbConfigProperties mongoDbConfig;
    private final Vertx vertx;

    /**
     * Creates an instance of the {@link MongoDbCallExecutor}.
     *
     * @param vertx         The Vert.x instance to use.
     * @param mongoDbConfig The mongodb configuration properties to use.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public MongoDbCallExecutor(final Vertx vertx, final MongoDbConfigProperties mongoDbConfig) {
        this.vertx = Objects.requireNonNull(vertx);
        this.mongoDbConfig = Objects.requireNonNull(mongoDbConfig);
        final JsonObject mongoConfigJson = this.mongoDbConfig.asMongoClientConfigJson();
        this.mongoClient = MongoClient.createShared(vertx, mongoConfigJson);
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
     * Creates mongodb collection index. Wrapper of {@link #createIndex(String, JsonObject, IndexOptions, Handler)}
     *
     * @param collectionName The name of the collection of documents.
     * @param keys           The keys to be indexed.
     * @param options        The options used to configure index, which is optional.
     * @return A succeeded Future if the tenant creation is successful,
     * else failed future with the error reason.
     */
    public Future<Void> createCollectionIndex(final String collectionName, final JsonObject keys,
                                              final IndexOptions options) {
        final Promise<Void> indexCreationPromise = Promise.promise();
        createIndex(collectionName, keys, options, res -> {
            if (res.succeeded()) {
                indexCreationPromise.complete();
            } else if (res.cause() instanceof MongoSocketException) {
                log.info("Create indices failed, wait for retry, cause:", res.cause());
                vertx.setTimer(this.mongoDbConfig.getCreateIndicesTimeout(),
                        timer -> createIndex(collectionName, keys, options, res2 -> {
                            if (res2.succeeded()) {
                                indexCreationPromise.complete();
                            } else if (res2.cause() instanceof MongoSocketException) {
                                log.info("Create indices failed, wait for second retry, cause:", res2.cause());
                                vertx.setTimer(this.mongoDbConfig.getCreateIndicesTimeout(),
                                        timer2 -> createIndex(collectionName, keys, options, res3 -> {
                                            if (res3.succeeded()) {
                                                indexCreationPromise.complete();
                                            } else {
                                                log.error("Error creating index", res3.cause());
                                                indexCreationPromise.fail(res3.cause());
                                            }
                                        }));
                            } else {
                                log.error("Error creating index", res2.cause());
                                indexCreationPromise.fail(res2.cause());
                            }
                        }));
            } else {
                log.error("Error creating index", res.cause());
                indexCreationPromise.fail(res.cause());
            }
        });
        return indexCreationPromise.future();
    }

    /**
     * Creates mongodb indexes.
     *
     * @param collectionName       The name of the collection of documents.
     * @param keys                 The keys to be indexed.
     * @param options              The options used to configure index, which is optional.
     * @param indexCreationTracker The callback handler.
     */
    private void createIndex(final String collectionName, final JsonObject keys, final IndexOptions options,
                             final Handler<AsyncResult<Void>> indexCreationTracker) {
        log.info("Create indices");
        mongoClient.createIndexWithOptions(collectionName, keys, options, indexCreationTracker);
    }
}
