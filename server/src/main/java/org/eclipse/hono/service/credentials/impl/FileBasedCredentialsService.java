/**
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 * <p>
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * <p>
 * Contributors:
 * Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.service.credentials.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileSystem;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.eclipse.hono.service.credentials.BaseCredentialsService;
import org.eclipse.hono.util.CredentialsResult;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import static java.net.HttpURLConnection.*;
import static org.eclipse.hono.util.CredentialsConstants.*;


/**
 * A credentials service that keeps all data in memory but is backed by a file.
 * <p>
 * On startup this adapter loads all added credentials from a file. On shutdown all
 * credentials kept in memory are written to the file.
 */
@Repository
@ConfigurationProperties(prefix = "hono.credentials")
@Profile({"default", "credentials-file"})
public class FileBasedCredentialsService extends BaseCredentialsService {

    private static final String ARRAY_CREDENTIALS = "credentials";
    private static final String FIELD_TENANT = "tenant";

    // <tenantId, <authId, credentialsData[]>>
    private Map<String, Map<String, JsonArray>> credentials = new HashMap<>();
    // the name of the file used to persist the registry content
    private String filename = "credentials.json";
    private boolean saveToFile = false;
    private boolean running = false;
    private boolean dirty = false;

    /**
     * Sets the path to the file that the content of the credentials registry should be persisted
     * periodically.
     * <p>
     * Default value is <em>credentials.json</em>.
     *
     * @param filename The name of the file to persist to (can be a relative or absolute path).
     * @throws IllegalStateException if this registry is already running.
     */
    public final void setFilename(final String filename) {
        if (running) {
            throw new IllegalStateException("credentials registry already started");
        }
        this.filename = filename;
    }

    /**
     * Sets whether the content of the credentials registry should be persisted to the file system
     * periodically.
     * <p>
     * Default value is {@code true}.
     *
     * @param enabled {@code true} if credentials registry content should be persisted.
     * @throws IllegalStateException if this credentials registry is already running.
     */
    public final void setSaveToFile(final boolean enabled) {
        if (running) {
            throw new IllegalStateException("credentials registry already started");
        }
        this.saveToFile = enabled;
    }


    @Override
    protected void doStart(final Future<Void> startFuture) throws Exception {

        if (!running) {
            if (filename != null) {
                loadCredentialsData();
                if (saveToFile) {
                    log.info("saving credentials to file every 3 seconds");
                    vertx.setPeriodic(3000, saveIdentities -> {
                        saveToFile(Future.future());
                    });
                } else {
                    log.info("persistence is disabled, will not save credentials to file");
                }
            }
        }
        running = true;
        startFuture.complete();
    }

    protected void loadCredentialsData() {
        if (filename != null) {
            final FileSystem fs = vertx.fileSystem();
            log.debug("trying to load credentials information from file {}", filename);
            if (fs.existsBlocking(filename)) {
                final AtomicInteger credentialsCount = new AtomicInteger();
                fs.readFile(filename, readAttempt -> {
                    if (readAttempt.succeeded()) {
                        JsonArray allObjects = new JsonArray(new String(readAttempt.result().getBytes()));
                        for (Object obj : allObjects) {
                            JsonObject tenant = (JsonObject) obj;
                            String tenantId = tenant.getString(FIELD_TENANT);
                            Map<String, JsonArray> credentialsMap = new HashMap<>();
                            for (Object credentialsObj : tenant.getJsonArray(ARRAY_CREDENTIALS)) {
                                JsonObject credentials = (JsonObject) credentialsObj;
                                JsonArray authIdCredentials;
                                if (credentialsMap.containsKey(credentials.getString(FIELD_AUTH_ID))) {
                                    authIdCredentials = credentialsMap.get(credentials.getString(FIELD_AUTH_ID));
                                } else {
                                    authIdCredentials = new JsonArray();
                                }
                                authIdCredentials.add(credentials);
                                credentialsMap.put(credentials.getString(FIELD_AUTH_ID), authIdCredentials);
                                credentialsCount.incrementAndGet();
                            }
                            credentials.put(tenantId, credentialsMap);
                        }
                        log.info("successfully loaded {} credentials from file [{}]", credentialsCount.get(), filename);
                    } else {
                        log.warn("could not load credentials from file [{}]", filename, readAttempt.cause());
                    }
                });
            } else {
                log.debug("credentials file {} does not exist (yet)", filename);
            }
        }
    }

    @Override
    protected void doStop(final Future<Void> stopFuture) {

        if (running) {
            Future<Void> stopTracker = Future.future();
            stopTracker.setHandler(stopAttempt -> {
                running = false;
                stopFuture.complete();
            });

            if (saveToFile) {
                saveToFile(stopTracker);
            } else {
                stopTracker.complete();
            }
        } else {
            stopFuture.complete();
        }
    }

    protected void saveToFile(final Future<Void> writeResult) {

        if (!dirty) {
            log.trace("credentials registry does not need to be persisted");
            return;
        }

        final FileSystem fs = vertx.fileSystem();
        if (!fs.existsBlocking(filename)) {
            fs.createFileBlocking(filename);
        }
        final AtomicInteger idCount = new AtomicInteger();
        JsonArray tenants = new JsonArray();
        for (Entry<String, Map<String, JsonArray>> entry : credentials.entrySet()) {
            JsonArray credentialsArray = new JsonArray();
            for (Entry<String, JsonArray> credentialEntry : entry.getValue().entrySet()) { // authId -> full json attributes object
                JsonArray singleAuthIdCredentials = credentialEntry.getValue(); // from one authId

                credentialsArray.add(singleAuthIdCredentials);
                idCount.incrementAndGet();
            }
            tenants.add(
                    new JsonObject()
                            .put(FIELD_TENANT, entry.getKey())
                            .put(ARRAY_CREDENTIALS, credentialsArray));
        }
        fs.writeFile(filename, Buffer.factory.buffer(tenants.encodePrettily()), writeAttempt -> {
            if (writeAttempt.succeeded()) {
                dirty = false;
                log.trace("successfully wrote {} credentials to file {}", idCount.get(), filename);
                writeResult.complete();
            } else {
                log.warn("could not write credentials to file {}", filename, writeAttempt.cause());
                writeResult.fail(writeAttempt.cause());
            }
        });
    }

    @Override
    public final void getCredentials(final String tenantId, final String type, final String authId, final Handler<AsyncResult<CredentialsResult>> resultHandler) {
        CredentialsResult credentialsResult = getCredentialsResult(tenantId, authId, type);
        resultHandler.handle(Future.succeededFuture(credentialsResult));
    }

    @Override
    public void addCredentials(final String tenantId, final JsonObject otherKeys, final Handler<AsyncResult<CredentialsResult>> resultHandler) {
        // TODO: implement
        CredentialsResult credentialsResult = CredentialsResult.from(HTTP_NOT_IMPLEMENTED);
        resultHandler.handle(Future.succeededFuture(credentialsResult));
    }

    @Override
    public void updateCredentials(final String tenantId, final JsonObject otherKeys, final Handler<AsyncResult<CredentialsResult>> resultHandler) {
        // TODO: implement
        CredentialsResult credentialsResult = CredentialsResult.from(HTTP_NOT_IMPLEMENTED);
        resultHandler.handle(Future.succeededFuture(credentialsResult));
    }

    @Override
    public void removeCredentials(final String tenantId, final String deviceId, final String type, final String authId, final Handler<AsyncResult<CredentialsResult>> resultHandler) {
        // TODO: implement
        CredentialsResult credentialsResult = CredentialsResult.from(HTTP_NOT_IMPLEMENTED);
        resultHandler.handle(Future.succeededFuture(credentialsResult));
    }

    protected CredentialsResult getCredentialsResult(final String tenantId, final String authId, final String type) {
        JsonObject data = getCredentials(tenantId, authId, type);
        if (data != null) {
            JsonObject resultPayload = getResultPayload(
                    data.getString(FIELD_DEVICE_ID),
                    data.getString(FIELD_TYPE),
                    data.getString(FIELD_AUTH_ID),
                    data.getBoolean(FIELD_ENABLED),
                    data.getJsonArray(FIELD_SECRETS)
            );
            return CredentialsResult.from(HTTP_OK, resultPayload);
        } else {
            return CredentialsResult.from(HTTP_NOT_FOUND);
        }
    }

    private JsonObject getCredentials(final String tenantId, final String authId, final String type) {
        Objects.nonNull(tenantId);
        Objects.nonNull(authId);
        Objects.nonNull(type);

        final Map<String, JsonArray> credentialsForTenant = credentials.get(tenantId);
        if (credentialsForTenant != null) {
            JsonArray authIdCredentials = credentialsForTenant.get(authId);
            if (authIdCredentials == null) {
                return null;
            }

            for (Object authIdCredentialEntry : authIdCredentials) {
                JsonObject authIdCredential = (JsonObject) authIdCredentialEntry;
                // return the first matching type entry for this authId
                if (type.equals(authIdCredential.getString(FIELD_TYPE))) {
                    return authIdCredential;
                }
            }
        }
        return null;
    }

    /**
     * Removes all credentials from the registry.
     */
    public final void clear() {
        dirty = true;
        credentials.clear();
    }

    @Override
    public String toString() {
        return String.format("%s[filename=%s]", FileBasedCredentialsService.class.getSimpleName(), filename);
    }
}
