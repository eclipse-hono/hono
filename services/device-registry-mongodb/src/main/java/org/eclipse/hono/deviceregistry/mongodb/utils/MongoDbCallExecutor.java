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

    private static final Logger LOG = LoggerFactory.getLogger(MongoDbCallExecutor.class);
    private static final int INDEX_CREATION_RETRY_INTERVAL_IN_MS = 3000;

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
     * @param noOfRetries The number of retries in case the index creation fails.
     * @return A future indicating the outcome of the index creation operation.
     * @throws NullPointerException if any of the parameters except options are {@code null}.
     */
    public Future<Void> createCollectionIndex(
            final String collectionName,
            final JsonObject keys,
            final IndexOptions options,
            final int noOfRetries) {

        final Promise<Void> indexCreationPromise = Promise.promise();
        LOG.info("creating index [collection: {}]", collectionName);

        mongoClient.createIndexWithOptions(collectionName, keys, options, res -> {
            if (res.succeeded()) {
                LOG.info("successfully created index [collection: {}]", collectionName);
                indexCreationPromise.complete();
            } else {
                if (noOfRetries > 0) {
                    LOG.error("failed to create index [collection: {}], scheduling new attempt to create index",
                            collectionName, res.cause());
                    vertx.setTimer(INDEX_CREATION_RETRY_INTERVAL_IN_MS,
                            id -> createCollectionIndex(collectionName, keys, options, noOfRetries - 1));
                } else {
                    LOG.error("failed to create index [collection: {}]", collectionName, res.cause());
                    indexCreationPromise.fail(res.cause());
                }
            }
        });
        return indexCreationPromise.future();
    }
}
