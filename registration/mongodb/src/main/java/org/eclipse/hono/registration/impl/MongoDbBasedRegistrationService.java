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

import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static java.net.HttpURLConnection.HTTP_CONFLICT;
import static java.net.HttpURLConnection.HTTP_CREATED;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_OK;

import org.eclipse.hono.util.RegistrationResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.IndexOptions;
import io.vertx.ext.mongo.MongoClient;

/**
 * A registration service implementation that stores data in a MongoDB.
 */
@Repository
@Profile("registration-mongodb")
public class MongoDbBasedRegistrationService extends BaseRegistrationService {

    private static final String DEFAULT_COLLECTION_NAME = "devices";
    
    private static final String FIELD_DEVICE_ID = "deviceId";
    private static final String FIELD_TENANT_ID = "tenantId";
    private static final String FIELD_DATA = "data";
    
    private MongoClient mongoClient;

    @Autowired
    private MongoDbConfigProperties mongoDbConfigProperties;
    
    public MongoDbBasedRegistrationService() {
        // empty
    }
    
    public MongoDbBasedRegistrationService(final MongoDbConfigProperties mongoDbConfigProperties) {
        this.mongoDbConfigProperties = mongoDbConfigProperties;
    }
    
    @Override
    protected void doStart(final Future<Void> startFuture) throws Exception {
        if (mongoDbConfigProperties.getCollection() == null) {
            mongoDbConfigProperties.setCollection(DEFAULT_COLLECTION_NAME);
        }
        JsonObject mongoConfig = mongoDbConfigProperties.asMongoClientConfigJson();
        log.info("Starting MongoDB client ...");
        mongoClient = MongoClient.createShared(vertx, mongoConfig);

        createIndices(res -> {
            if (res.succeeded()) {
                startFuture.complete();
            } else {
                log.error("Error creating index", res.cause());
                startFuture.fail(res.cause());
            }
        });
    }

    private void createIndices(final Handler<AsyncResult<Void>> indexCreationTracker) {
        JsonObject keys = new JsonObject()
            .put(FIELD_DEVICE_ID, 1)
            .put(FIELD_TENANT_ID, 1);
        IndexOptions options = new IndexOptions().unique(true);
        mongoClient.createIndexWithOptions(mongoDbConfigProperties.getCollection(), keys, options, indexCreationTracker);
    }

    @Override
    protected void doStop(final Future<Void> stopFuture) {
        mongoClient.close();
        stopFuture.complete();
    }

    @Override
    public void getDevice(final String tenantId, final String deviceId,
            final Handler<AsyncResult<RegistrationResult>> resultHandler) {
        JsonObject query = createDeviceDocument(tenantId, deviceId);
        findDevice(query, resultHandler);
    }

    @Override
    public void findDevice(final String tenantId, final String key, final String value,
            final Handler<AsyncResult<RegistrationResult>> resultHandler) {
        if (key == null) {
            handleResult(resultHandler, RegistrationResult.from(HTTP_BAD_REQUEST));
            return;
        }
        JsonObject query = new JsonObject()
            .put(FIELD_TENANT_ID, tenantId)
            .put(FIELD_DATA + "." + key, value);
        findDevice(query, resultHandler);
    }

    private void findDevice(final JsonObject query, final Handler<AsyncResult<RegistrationResult>> resultHandler) {
        mongoClient.findOne(mongoDbConfigProperties.getCollection(), query, null, res -> {
            JsonObject foundData = res.succeeded() && res.result() != null ? res.result().getJsonObject(FIELD_DATA) : null;
            if (foundData != null) {
                handleResult(resultHandler, RegistrationResult.from(HTTP_OK, foundData));
            } else {
                handleResult(resultHandler, RegistrationResult.from(HTTP_NOT_FOUND));
            }
        });
    }

    @Override
    public void removeDevice(final String tenantId, final String deviceId,
            final Handler<AsyncResult<RegistrationResult>> resultHandler) {
        // data of the removed device shall be returned, so try to get it first
        getDevice(tenantId, deviceId, getResult -> {
            final JsonObject dataOfDevice = getResult.result() != null ? getResult.result().getPayload() : null;
            // now remove
            mongoClient.removeDocuments(mongoDbConfigProperties.getCollection(), createDeviceDocument(tenantId, deviceId), res -> {
                if (res.succeeded() && res.result().getRemovedCount() == 1) {
                    handleResult(resultHandler, RegistrationResult.from(HTTP_OK, dataOfDevice));
                } else {
                    handleResult(resultHandler, RegistrationResult.from(HTTP_NOT_FOUND));
                }
            });
        });
    }

    @Override
    public void addDevice(final String tenantId, final String deviceId, final JsonObject data,
            final Handler<AsyncResult<RegistrationResult>> resultHandler) {
        mongoClient.insert(mongoDbConfigProperties.getCollection(), createDeviceDocument(tenantId, deviceId, data), res -> {
            if (res.succeeded()) {
                handleResult(resultHandler, RegistrationResult.from(HTTP_CREATED));
            } else {
                handleResult(resultHandler, RegistrationResult.from(HTTP_CONFLICT));
            }
        });
    }

    @Override
    public void updateDevice(final String tenantId, final String deviceId, final JsonObject data,
            final Handler<AsyncResult<RegistrationResult>> resultHandler) {
        // the original data of the updated device shall be returned, so try to get it first
        getDevice(tenantId, deviceId, getResult -> {
            final JsonObject prevDataOfDevice = getResult.result() != null ? getResult.result().getPayload() : null;
            
            JsonObject query = createDeviceDocument(tenantId, deviceId);
            JsonObject update = new JsonObject()
                .put("$set", new JsonObject().put(FIELD_DATA, data != null ? data : new JsonObject()));
            mongoClient.updateCollection(mongoDbConfigProperties.getCollection(), query, update, res -> {
                if (res.succeeded() && res.result().getDocMatched() == 1) {
                    handleResult(resultHandler, RegistrationResult.from(HTTP_OK, prevDataOfDevice));
                } else {
                    handleResult(resultHandler, RegistrationResult.from(HTTP_NOT_FOUND));
                }
            });
        });
    }

    /**
     * Removes all devices from the registry.
     */
    void clear(final Handler<AsyncResult<Void>> resultHandler) {
        mongoClient.removeDocuments(mongoDbConfigProperties.getCollection(), new JsonObject(), res -> {
            log.debug("all devices removed");
            resultHandler.handle(Future.succeededFuture());
        });
    }

    private void handleResult(final Handler<AsyncResult<RegistrationResult>> resultHandler, final RegistrationResult result) {
        resultHandler.handle(Future.succeededFuture(result));
    }

    private JsonObject createDeviceDocument(final String tenantId, final String deviceId, final JsonObject data) {
        return createDeviceDocument(tenantId, deviceId)
            .put(FIELD_DATA, data != null ? data : new JsonObject());
    }

    private JsonObject createDeviceDocument(final String tenantId, final String deviceId) {
        return new JsonObject()
            .put(FIELD_TENANT_ID, tenantId)
            .put(FIELD_DEVICE_ID, deviceId);
    }

}
