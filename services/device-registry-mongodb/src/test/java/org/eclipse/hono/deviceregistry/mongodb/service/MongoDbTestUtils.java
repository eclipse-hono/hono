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
import org.eclipse.hono.deviceregistry.mongodb.model.MongoDbBasedCredentialsDao;
import org.eclipse.hono.deviceregistry.mongodb.model.MongoDbBasedDeviceDao;
import org.eclipse.hono.deviceregistry.mongodb.model.MongoDbBasedTenantDao;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

import io.vertx.core.Vertx;
import io.vertx.ext.mongo.MongoClient;

/**
 * Utility class for creating clients for a running Mongo DB instance.
 */
public final class MongoDbTestUtils {

    private static final MongoDBContainer MONGO_DB_CONTAINER;
    private static final String MONGO_DB_IMAGE_NAME = System.getProperty("mongoDbImageName", "mongo:4.2");

    static {
        MONGO_DB_CONTAINER = new MongoDBContainer(DockerImageName.parse(MONGO_DB_IMAGE_NAME));
        MONGO_DB_CONTAINER.start();
    }

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

        final MongoDbConfigProperties mongoDbConfig = new MongoDbConfigProperties()
                .setConnectionString(MONGO_DB_CONTAINER.getReplicaSetUrl(dbName));
        return MongoClient.createShared(vertx, mongoDbConfig.getMongoClientConfig());
    }

    /**
     * Creates a new Tenant DAO.
     *
     * @param vertx The vert.x instance to run on.
     * @param dbName The name of the database to connect to.
     * @return The DAO.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public static MongoDbBasedTenantDao getTenantDao(final Vertx vertx, final String dbName) {

        final MongoDbConfigProperties mongoDbConfig = new MongoDbConfigProperties()
                .setConnectionString(MONGO_DB_CONTAINER.getReplicaSetUrl(dbName));
        return new MongoDbBasedTenantDao(mongoDbConfig, "tenants", vertx);
    }

    /**
     * Creates a new Device DAO.
     *
     * @param vertx The vert.x instance to run on.
     * @param dbName The name of the database to connect to.
     * @return The DAO.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public static MongoDbBasedDeviceDao getDeviceDao(final Vertx vertx, final String dbName) {

        final MongoDbConfigProperties mongoDbConfig = new MongoDbConfigProperties()
                .setConnectionString(MONGO_DB_CONTAINER.getReplicaSetUrl(dbName));
        return new MongoDbBasedDeviceDao(mongoDbConfig, "devices", vertx);
    }

    /**
     * Creates a new Credentials DAO.
     *
     * @param vertx The vert.x instance to run on.
     * @param dbName The name of the database to connect to.
     * @return The DAO.
     * @throws NullPointerException if any of the parameters are {@code null}.
     */
    public static MongoDbBasedCredentialsDao getCredentialsDao(final Vertx vertx, final String dbName) {

        final MongoDbConfigProperties mongoDbConfig = new MongoDbConfigProperties()
                .setConnectionString(MONGO_DB_CONTAINER.getReplicaSetUrl(dbName));
        return new MongoDbBasedCredentialsDao(mongoDbConfig, "credentials", vertx);
    }
}
