/*******************************************************************************
 * Copyright (c) 2020, 2021 Contributors to the Eclipse Foundation
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.IndexOptions;
import io.vertx.ext.mongo.MongoClient;

/**
 * Utility class for invoking methods of the Vert.x Mongo DB client.
 */
public final class MongoDbCallExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(MongoDbCallExecutor.class);

    private final MongoClient mongoClient;
    private final Vertx vertx;

    /**
     * Creates an instance of the {@link MongoDbCallExecutor}.
     *
     * @param vertx The Vert.x instance to use.
     * @param mongoClient The mongo client instance to use.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public MongoDbCallExecutor(final Vertx vertx, final MongoClient mongoClient) {
        this.vertx = Objects.requireNonNull(vertx);
        this.mongoClient = Objects.requireNonNull(mongoClient);
    }

    /**
     * Creates an index on a collection.
     *
     * @param collectionName The name of the collection.
     * @param keys The keys to be indexed.
     * @param options The options for configuring the index (may be {@code null}).
     * @return A future indicating the outcome of the index creation operation.
     * @throws NullPointerException if collection name or keys are {@code null}.
     */
    public Future<Void> createIndex(
            final String collectionName,
            final JsonObject keys,
            final IndexOptions options) {

        Objects.requireNonNull(collectionName);
        Objects.requireNonNull(keys);

        final Promise<Void> result = Promise.promise();

        vertx.runOnContext(s -> {
            LOG.debug("creating index [collection: {}]", collectionName);
            mongoClient.createIndexWithOptions(collectionName, keys, options, result);
        });
        return result.future()
                .onSuccess(ok -> {
                    LOG.debug("successfully created index [collection: {}]", collectionName);
                })
                .onFailure(t -> {
                    LOG.info("failed to create index [collection: {}]", collectionName, t);
                });
    }
}
