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
package org.eclipse.hono.deviceregistry.mongodb.service;

import java.util.Objects;

import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbConfigProperties;

import io.vertx.core.Vertx;
import io.vertx.ext.mongo.MongoClient;

/**
 * Utility class for creating clients for a running Mongo DB instance.
 */
public final class MongoDbTestUtils {

    private static final String MONGO_DB_HOST = System.getProperty("mongodb.host", "127.0.0.1");
    private static final int MONGO_DB_PORT = Integer.getInteger("mongodb.port", -1);

    private MongoDbTestUtils() {
        // prevent instantiation
    }

    /**
     * Creates a new vert.x Mongo DB client.
     *
     * @param vertx The vert.x instance to run on.
     * @param dbName The name of the database to connect to.
     * @return The client.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public static MongoClient getMongoClient(final Vertx vertx, final String dbName) {

        Objects.requireNonNull(vertx);
        Objects.requireNonNull(dbName);

        if (MONGO_DB_PORT == -1) {
            throw new RuntimeException("Missing 'mongodb.port' system property; ensure test is run via maven.");
        }
        final MongoDbConfigProperties mongoDbConfig = new MongoDbConfigProperties()
                .setHost(MongoDbTestUtils.MONGO_DB_HOST)
                .setPort(MongoDbTestUtils.MONGO_DB_PORT)
                .setDbName(dbName);
        return MongoClient.createShared(vertx, mongoDbConfig.getMongoClientConfig());
    }
}
