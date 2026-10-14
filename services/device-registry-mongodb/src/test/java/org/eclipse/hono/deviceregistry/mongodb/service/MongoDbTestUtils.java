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

import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbBasedCredentialsConfigProperties;
import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbBasedRegistrationConfigProperties;
import org.eclipse.hono.deviceregistry.mongodb.config.MongoDbBasedTenantsConfigProperties;
import org.eclipse.hono.deviceregistry.mongodb.model.MongoDbBasedCredentialsDao;
import org.eclipse.hono.deviceregistry.mongodb.model.MongoDbBasedDeviceDao;
import org.eclipse.hono.deviceregistry.mongodb.model.MongoDbBasedTenantDao;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

import com.mongodb.reactivestreams.client.MongoClients;

import io.quarkus.mongodb.impl.ReactiveMongoClientImpl;
import io.quarkus.mongodb.reactive.ReactiveMongoClient;
import io.vertx.core.Vertx;

/**
 * Utility class for creating clients for a running Mongo DB instance.
 */
public final class MongoDbTestUtils {

    static final String MONGO_DB_NAME = "hono-test-db";

    private static final MongoDBContainer MONGO_DB_CONTAINER;
    private static final String MONGO_DB_IMAGE_NAME = System.getProperty("mongoDbImageName", "mongo:7.0");
    private static final ReactiveMongoClient MONGO_CLIENT;

    static {
        MONGO_DB_CONTAINER = new MongoDBContainer(DockerImageName
                .parse(MONGO_DB_IMAGE_NAME)
                .asCompatibleSubstituteFor("mongo"));
        MONGO_DB_CONTAINER.start();
        final var mongoClient = MongoClients.create(MONGO_DB_CONTAINER.getReplicaSetUrl(MONGO_DB_NAME));
        MONGO_CLIENT = new ReactiveMongoClientImpl(mongoClient);
    }

    private MongoDbTestUtils() {
        // prevent instantiation
    }

    /**
     * Returns the shared vert.x Mongo DB client.
     *
     * @return The client.
     */
    public static ReactiveMongoClient getMongoClient() {
        return MONGO_CLIENT;
    }

    /**
     * Creates a new Tenant DAO.
     *
     * @param vertx The vert.x instance to use for asynchronous operations.
     * @return The DAO.
     */
    public static MongoDbBasedTenantDao getTenantDao(final Vertx vertx) {

        final var properties = new MongoDbBasedTenantsConfigProperties();
        properties.setDatabaseName(MONGO_DB_NAME);

        return new MongoDbBasedTenantDao(vertx, MONGO_CLIENT, properties, null);
    }

    /**
     * Creates a new Device DAO.
     *
     * @param vertx The vert.x instance to use for asynchronous operations.
     * @return The DAO.
     */
    public static MongoDbBasedDeviceDao getDeviceDao(final Vertx vertx) {

        final var properties = new MongoDbBasedRegistrationConfigProperties();
        properties.setDatabaseName(MONGO_DB_NAME);

        return new MongoDbBasedDeviceDao(vertx, MONGO_CLIENT, properties, null);
    }

    /**
     * Creates a new Credentials DAO.
     *
     * @param vertx The vert.x instance to use for asynchronous operations.
     * @return The DAO.
     */
    public static MongoDbBasedCredentialsDao getCredentialsDao(final Vertx vertx) {

        final var properties = new MongoDbBasedCredentialsConfigProperties();
        properties.setDatabaseName(MONGO_DB_NAME);

        return new MongoDbBasedCredentialsDao(vertx, MONGO_CLIENT, properties, null, null);
    }
}
