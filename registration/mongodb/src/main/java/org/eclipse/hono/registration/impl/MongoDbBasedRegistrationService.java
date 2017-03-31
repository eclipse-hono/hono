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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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

    private static final Logger LOG = LoggerFactory.getLogger(MongoDbBasedRegistrationService.class);
    
    private static final String COLLECTION_DEVICES = "devices";
    
    private static final String FIELD_DEVICE_ID = "deviceId";
    private static final String FIELD_TENANT_ID = "tenantId";
    private static final String FIELD_DATA = "data";
    
    private MongoClient mongoClient;

    /**
     * Connection string for the MongoDB client. 
     * If set, the connection string overrides the other configuration settings.
     * Format:
     * mongodb://[username:password@]host1[:port1][,host2[:port2],...[,hostN[:portN]]][/[database][?options]]
     * See https://docs.mongodb.com/manual/reference/connection-string/
     */
    @Value("${hono.registration.mongoDb.connectionString:#{null}}")
    private String connectionString;
    
    @Value("${hono.registration.mongoDb.host:#{null}'}")
    private String host;
    @Value("${hono.registration.mongoDb.port:0}")
    private int port;
    @Value("${hono.registration.mongoDb.dbName:#{null}}")
    private String dbName;
    @Value("${hono.registration.mongoDb.username:#{null}}")
    private String username;
    @Value("${hono.registration.mongoDb.password:#{null}}")
    private String password;
    
    public MongoDbBasedRegistrationService() {
        // empty
    }
    
    public MongoDbBasedRegistrationService(final String host, final int port, final String dbName,
            final String username, final String password) {
        this.host = host;
        this.port = port;
        this.dbName = dbName;
        this.username = username;
        this.password = password;
    }

    public MongoDbBasedRegistrationService(final String connectionString) {
        this.connectionString = connectionString;
    }

    @Override
    protected void doStart(final Future<Void> startFuture) throws Exception {
        JsonObject mongoConfig = getMongoConfig();
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

    private JsonObject getMongoConfig() {
        final JsonObject config = new JsonObject();
        if (connectionString != null) {
            config.put("connection_string", connectionString);
            
            if (host != null || port != 0 || dbName != null || username != null || password != null) {
                LOG.info("hono.registration.mongoDb.connectionString is set, other properties will be ignored");
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

    private void createIndices(final Handler<AsyncResult<Void>> indexCreationTracker) {
        JsonObject keys = new JsonObject()
            .put(FIELD_DEVICE_ID, 1)
            .put(FIELD_TENANT_ID, 1);
        IndexOptions options = new IndexOptions().unique(true);
        mongoClient.createIndexWithOptions(COLLECTION_DEVICES, keys, options, indexCreationTracker);
    }

    @Override
    protected void doStop(final Future<Void> stopFuture) {
        mongoClient.close();
        stopFuture.complete();
    }

    @Override
    public void getDevice(final String tenantId, final String deviceId, final Handler<AsyncResult<RegistrationResult>> resultHandler) {
        JsonObject query = createDeviceDocument(tenantId, deviceId);
        findDevice(query, resultHandler);
    }

    @Override
    public void findDevice(final String tenantId, final String key, final String value, final Handler<AsyncResult<RegistrationResult>> resultHandler) {
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
        mongoClient.findOne(COLLECTION_DEVICES, query, null, res -> {
            JsonObject foundData = res.succeeded() && res.result() != null ? res.result().getJsonObject(FIELD_DATA) : null;
            if (foundData != null) {
                handleResult(resultHandler, RegistrationResult.from(HTTP_OK, foundData));
            } else {
                handleResult(resultHandler, RegistrationResult.from(HTTP_NOT_FOUND));
            }
        });
    }

    @Override
    public void removeDevice(final String tenantId, final String deviceId, final Handler<AsyncResult<RegistrationResult>> resultHandler) {
        // data of the removed device shall be returned, so try to get it first
        getDevice(tenantId, deviceId, getResult -> {
            final JsonObject dataOfDevice = getResult.result() != null ? getResult.result().getPayload() : null;
            // now remove
            mongoClient.removeDocuments(COLLECTION_DEVICES, createDeviceDocument(tenantId, deviceId), res -> {
                if (res.succeeded() && res.result().getRemovedCount() == 1) {
                    handleResult(resultHandler, RegistrationResult.from(HTTP_OK, dataOfDevice));
                } else {
                    handleResult(resultHandler, RegistrationResult.from(HTTP_NOT_FOUND));
                }
            });
        });
    }

    @Override
    public void addDevice(final String tenantId, final String deviceId, final JsonObject data, final Handler<AsyncResult<RegistrationResult>> resultHandler) {
        mongoClient.insert(COLLECTION_DEVICES, createDeviceDocument(tenantId, deviceId, data), res -> {
            if (res.succeeded()) {
                handleResult(resultHandler, RegistrationResult.from(HTTP_CREATED));
            } else {
                handleResult(resultHandler, RegistrationResult.from(HTTP_CONFLICT));
            }
        });
    }

    @Override
    public void updateDevice(final String tenantId, final String deviceId, final JsonObject data, final Handler<AsyncResult<RegistrationResult>> resultHandler) {
        // the original data of the updated device shall be returned, so try to get it first
        getDevice(tenantId, deviceId, getResult -> {
            final JsonObject prevDataOfDevice = getResult.result() != null ? getResult.result().getPayload() : null;
            
            JsonObject query = createDeviceDocument(tenantId, deviceId);
            JsonObject update = new JsonObject()
                .put("$set", new JsonObject().put(FIELD_DATA, data != null ? data : new JsonObject()));
            mongoClient.updateCollection(COLLECTION_DEVICES, query, update, res -> {
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
        mongoClient.removeDocuments(COLLECTION_DEVICES, new JsonObject(), res -> {
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
