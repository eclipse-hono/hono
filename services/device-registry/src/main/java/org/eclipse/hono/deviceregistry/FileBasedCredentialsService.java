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

package org.eclipse.hono.deviceregistry;

import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.hono.service.credentials.BaseCredentialsService;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;



/**
 * A credentials service that keeps all data in memory but is backed by a file.
 * <p>
 * On startup this adapter tries to load credentials from a file (if configured).
 * On shutdown all credentials kept in memory are written to the file (if configured).
 */
@Repository
public final class FileBasedCredentialsService extends BaseCredentialsService<FileBasedCredentialsConfigProperties> {

    /**
     * The name of the JSON array within a tenant that contains the credentials.
     */
    public static final String ARRAY_CREDENTIALS = "credentials";
    /**
     * The name of the JSON property containing the tenant's ID.
     */
    public static final String FIELD_TENANT = "tenant";

    // <tenantId, <authId, credentialsData[]>>
    private Map<String, Map<String, JsonArray>> credentials = new HashMap<>();
    private boolean running = false;
    private boolean dirty = false;

    @Autowired
    @Override
    public void setConfig(final FileBasedCredentialsConfigProperties configuration) {
        setSpecificConfig(configuration);
    }

    private Future<Void> checkFileExists(final boolean createIfMissing) {

        Future<Void> result = Future.future();
        if (getConfig().getCredentialsFilename() == null) {
            result.fail("no filename set");
        } else if (vertx.fileSystem().existsBlocking(getConfig().getCredentialsFilename())) {
            result.complete();
        } else if (createIfMissing) {
            vertx.fileSystem().createFile(getConfig().getCredentialsFilename(), result.completer());
        } else {
            log.debug("no such file [{}]", getConfig().getCredentialsFilename());
            result.complete();
        }
        return result;
    }

    @Override
    protected void doStart(final Future<Void> startFuture) {

        if (running) {
            startFuture.complete();
        } else {

            if (!getConfig().isModificationEnabled()) {
                log.info("modification of credentials has been disabled");
            }

            if (getConfig().getCredentialsFilename() == null) {
                log.debug("credentials filename is not set, no credentials will be loaded");
                running = true;
                startFuture.complete();
            } else {
                checkFileExists(getConfig().isSaveToFile()).compose(ok -> {
                    return loadCredentials();
                }).compose(s -> {
                    if (getConfig().isSaveToFile()) {
                        log.info("saving credentials to file every 3 seconds");
                        vertx.setPeriodic(3000, saveIdentities -> {
                            saveToFile();
                        });
                    } else {
                        log.info("persistence is disabled, will not save credentials to file");
                    }
                    running = true;
                    startFuture.complete();
                }, startFuture);
            }
        }
    }

    Future<Void> loadCredentials() {

        if (getConfig().getCredentialsFilename() == null) {
            // no need to load anything
            return Future.succeededFuture();
        } else {
            final Future<Buffer> readResult = Future.future();
            log.debug("trying to load credentials from file {}", getConfig().getCredentialsFilename());
            vertx.fileSystem().readFile(getConfig().getCredentialsFilename(), readResult.completer());
            return readResult.compose(buffer -> {
                return addAll(buffer);
            }).recover(t -> {
                log.debug("cannot load credentials from file [{}]: {}", getConfig().getCredentialsFilename(), t.getMessage());
                return Future.succeededFuture();
            });
        }
    }

    private Future<Void> addAll(final Buffer credentials) {
        Future<Void> result = Future.future();
        try {
            int credentialsCount = 0;
            JsonArray allObjects = credentials.toJsonArray();
            log.debug("trying to load credentials for {} tenants", allObjects.size());
            for (Object obj : allObjects) {
                if (JsonObject.class.isInstance(obj)) {
                    credentialsCount += addCredentialsForTenant((JsonObject) obj);
                }
            }
            log.info("successfully loaded {} credentials from file [{}]", credentialsCount, getConfig().getCredentialsFilename());
            result.complete();
        } catch (DecodeException e) {
            log.warn("cannot read malformed JSON from credentials file [{}]", getConfig().getCredentialsFilename());
            result.fail(e);
        }
        return result;
    };

    int addCredentialsForTenant(final JsonObject tenant) {
        int count = 0;
        String tenantId = tenant.getString(FIELD_TENANT);
        Map<String, JsonArray> credentialsMap = new HashMap<>();
        for (Object credentialsObj : tenant.getJsonArray(ARRAY_CREDENTIALS)) {
            JsonObject credentials = (JsonObject) credentialsObj;
            JsonArray authIdCredentials;
            if (credentialsMap.containsKey(credentials.getString(CredentialsConstants.FIELD_AUTH_ID))) {
                authIdCredentials = credentialsMap.get(credentials.getString(CredentialsConstants.FIELD_AUTH_ID));
            } else {
                authIdCredentials = new JsonArray();
            }
            authIdCredentials.add(credentials);
            credentialsMap.put(credentials.getString(CredentialsConstants.FIELD_AUTH_ID), authIdCredentials);
            count++;
        }
        credentials.put(tenantId, credentialsMap);
        return count;
    }

    @Override
    protected void doStop(final Future<Void> stopFuture) {

        if (running) {
            saveToFile().compose(s -> {
                running = false;
                stopFuture.complete();
            }, stopFuture);
        } else {
            stopFuture.complete();
        }
    }

    Future<Void> saveToFile() {

        if (!getConfig().isSaveToFile()) {
            return Future.succeededFuture();
        } else if (dirty) {
            return checkFileExists(true).compose(s -> {
                final AtomicInteger idCount = new AtomicInteger();
                JsonArray tenants = new JsonArray();
                for (Entry<String, Map<String, JsonArray>> entry : credentials.entrySet()) {
                    JsonArray credentialsArray = new JsonArray();
                    for (JsonArray singleAuthIdCredentials : entry.getValue().values()) {
                        credentialsArray.addAll(singleAuthIdCredentials.copy());
                        idCount.incrementAndGet();
                    }
                    tenants.add(
                            new JsonObject()
                                    .put(FIELD_TENANT, entry.getKey())
                                    .put(ARRAY_CREDENTIALS, credentialsArray));
                }
                Future<Void> writeHandler = Future.future();
                vertx.fileSystem().writeFile(
                        getConfig().getCredentialsFilename(),
                        Buffer.buffer(tenants.encodePrettily(), StandardCharsets.UTF_8.name()),
                        writeHandler.completer());
                return writeHandler.map(ok -> {
                    dirty = false;
                    log.trace("successfully wrote {} credentials to file {}", idCount.get(), getConfig().getCredentialsFilename());
                    return (Void) null;
                }).otherwise(t -> {
                    log.warn("could not write credentials to file {}", getConfig().getCredentialsFilename(), t);
                    return (Void) null;
                });
            });
        } else {
            log.trace("credentials registry does not need to be persisted");
            return Future.succeededFuture();
        }
    }

    @Override
    public final void get(
            final String tenantId,
            final String type,
            final String authId,
            final Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(type);
        Objects.requireNonNull(authId);
        Objects.requireNonNull(resultHandler);
        final JsonObject data = getSingleCredentials(tenantId, authId, type);
        if (data == null) {
            resultHandler.handle(Future.succeededFuture(CredentialsResult.from(HttpURLConnection.HTTP_NOT_FOUND)));
        } else {
            resultHandler.handle(Future.succeededFuture(CredentialsResult.from(HttpURLConnection.HTTP_OK, data.copy())));
        }
    }

    @Override
    public void getAll(final String tenantId, final String deviceId, final Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(resultHandler);

        Map<String, JsonArray> credentialsForTenant = credentials.get(tenantId);
        if (credentialsForTenant == null) {
            resultHandler.handle(Future.succeededFuture(CredentialsResult.from(HttpURLConnection.HTTP_NOT_FOUND)));
        } else {
            JsonArray matchingCredentials = new JsonArray();
            // iterate over all credentials per auth-id in order to find credentials matching the given device
            for (JsonArray credentialsForAuthId : credentialsForTenant.values()) {
                findCredentialsForDevice(credentialsForAuthId, deviceId, matchingCredentials);
            }
            if (matchingCredentials.isEmpty()) {
                resultHandler.handle(Future.succeededFuture(CredentialsResult.from(HttpURLConnection.HTTP_NOT_FOUND)));
            } else {
                JsonObject result = new JsonObject()
                        .put(CredentialsConstants.FIELD_CREDENTIALS_TOTAL, matchingCredentials.size())
                        .put(CredentialsConstants.CREDENTIALS_ENDPOINT, matchingCredentials);
                resultHandler.handle(Future.succeededFuture(CredentialsResult.from(HttpURLConnection.HTTP_OK, result)));
            }
        }
    }

    private void findCredentialsForDevice(final JsonArray credentials, final String deviceId, final JsonArray result) {

        for (Object obj : credentials) {
            if (obj instanceof JsonObject) {
                final JsonObject currentCredentials = (JsonObject) obj;
                if (deviceId.equals(getTypesafeValueForField(currentCredentials, CredentialsConstants.FIELD_DEVICE_ID, String.class))) {
                    // device ID matches, add a copy of credentials to result
                    result.add(currentCredentials.copy());
                }
            }
        }
    }

    /**
     * Get the credentials associated with the authId and the given type.
     * If type is null, all credentials associated with the authId are returned (as JsonArray inside the return value).
     *
     * @param tenantId The id of the tenant the credentials belong to.
     * @param authId The authentication identifier to look up credentials for.
     * @param type The type of credentials to look up.
     * @return The credentials object of the given type or {@code null} if no matching credentials exist.
     */
    private JsonObject getSingleCredentials(final String tenantId, final String authId, final String type) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(authId);
        Objects.requireNonNull(type);

        final Map<String, JsonArray> credentialsForTenant = credentials.get(tenantId);
        if (credentialsForTenant != null) {
            JsonArray authIdCredentials = credentialsForTenant.get(authId);
            if (authIdCredentials != null) {
                for (Object authIdCredentialEntry : authIdCredentials) {
                    JsonObject authIdCredential = (JsonObject) authIdCredentialEntry;
                    // return the first matching type entry for this authId
                    if (type.equals(authIdCredential.getString(CredentialsConstants.FIELD_TYPE))) {
                        return authIdCredential;
                    }
                }
            }
        }
        return null;
    }

    @Override
    public void add(final String tenantId, final JsonObject credentials, final Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(credentials);
        Objects.requireNonNull(resultHandler);
        CredentialsResult<JsonObject> credentialsResult = addCredentialsResult(tenantId, credentials);
        resultHandler.handle(Future.succeededFuture(credentialsResult));
    }

    private CredentialsResult<JsonObject> addCredentialsResult(final String tenantId, final JsonObject credentialsToAdd) {

        String authId = credentialsToAdd.getString(CredentialsConstants.FIELD_AUTH_ID);

        Map<String, JsonArray> credentialsForTenant = getCredentialsForTenant(tenantId);

        JsonArray authIdCredentials = getAuthIdCredentials(authId, credentialsForTenant);

        // check if credentials already exist with the type and auth-id for the device-id from the payload.
        for (Object credentialsObj: authIdCredentials) {
            JsonObject credentials = (JsonObject) credentialsObj;
            if (credentials.getString(CredentialsConstants.FIELD_TYPE).equals(credentialsToAdd.getString(CredentialsConstants.FIELD_TYPE))) {
                return CredentialsResult.from(HttpURLConnection.HTTP_CONFLICT);
            }
        }

        authIdCredentials.add(credentialsToAdd);
        dirty = true;
        return CredentialsResult.from(HttpURLConnection.HTTP_CREATED);
    }

    @Override
    public void update(final String tenantId, final JsonObject newCredentials, final Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(newCredentials);
        Objects.requireNonNull(resultHandler);

        if (getConfig().isModificationEnabled()) {
            final String authId = newCredentials.getString(CredentialsConstants.FIELD_AUTH_ID);
            final String type = newCredentials.getString(CredentialsConstants.FIELD_TYPE);

            Map<String, JsonArray> credentialsForTenant = getCredentialsForTenant(tenantId);
            if (credentialsForTenant == null) {
                resultHandler.handle(Future.succeededFuture(CredentialsResult.from(HttpURLConnection.HTTP_NOT_FOUND)));
            } else {
                final JsonArray credentialsForAuthId = credentialsForTenant.get(authId);
                if (credentialsForAuthId == null) {
                    resultHandler.handle(Future.succeededFuture(CredentialsResult.from(HttpURLConnection.HTTP_NOT_FOUND)));
                } else {
                    // find credentials of given type
                    boolean removed = false;
                    Iterator<Object> credentialsIterator = credentialsForAuthId.iterator();
                    while (credentialsIterator.hasNext()) {
                        JsonObject creds = (JsonObject) credentialsIterator.next();
                        if (creds.getString(CredentialsConstants.FIELD_TYPE).equals(type)) {
                            credentialsIterator.remove();
                            removed = true;
                            break;
                        }
                    }
                    if (removed) {
                        credentialsForAuthId.add(newCredentials);
                        dirty = true;
                        resultHandler.handle(Future.succeededFuture(CredentialsResult.from(HttpURLConnection.HTTP_NO_CONTENT)));
                    } else {
                        resultHandler.handle(Future.succeededFuture(CredentialsResult.from(HttpURLConnection.HTTP_NOT_FOUND)));
                    }
                }
            }
        } else {
            resultHandler.handle(Future.succeededFuture(CredentialsResult.from(HttpURLConnection.HTTP_FORBIDDEN)));
        }
    }

    @Override
    public void remove(final String tenantId, final String type, final String authId, final Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(type);
        Objects.requireNonNull(authId);
        Objects.requireNonNull(resultHandler);

        if (getConfig().isModificationEnabled()) {
            final Map<String, JsonArray> credentialsForTenant = credentials.get(tenantId);
            if (credentialsForTenant == null) {
                resultHandler.handle(Future.succeededFuture(CredentialsResult.from(HttpURLConnection.HTTP_NOT_FOUND)));
            } else {
                final JsonArray credentialsForAuthId = credentialsForTenant.get(authId);
                if (credentialsForAuthId == null) {
                    resultHandler.handle(Future.succeededFuture(CredentialsResult.from(HttpURLConnection.HTTP_NOT_FOUND)));
                } else if (removeCredentialsFromCredentialsArray(null, type, credentialsForAuthId)) {
                    if (credentialsForAuthId.isEmpty()) {
                        credentialsForTenant.remove(authId); // do not leave empty array as value
                    }
                    resultHandler.handle(Future.succeededFuture(CredentialsResult.from(HttpURLConnection.HTTP_NO_CONTENT)));
                } else {
                    resultHandler.handle(Future.succeededFuture(CredentialsResult.from(HttpURLConnection.HTTP_NOT_FOUND)));
                }
            }
        } else {
            resultHandler.handle(Future.succeededFuture(CredentialsResult.from(HttpURLConnection.HTTP_FORBIDDEN)));
        }
    }

    @Override
    public void removeAll(final String tenantId, final String deviceId, final Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(resultHandler);

        if (getConfig().isModificationEnabled()) {
            boolean removedAnyElement = false;

            final Map<String, JsonArray> credentialsForTenant = credentials.get(tenantId);
            if (credentialsForTenant == null) {
                resultHandler.handle(Future.succeededFuture(CredentialsResult.from(HttpURLConnection.HTTP_NOT_FOUND)));
            } else {
                // delete based on type (no authId provided) - this might consume more time on large data sets and is thus
                // handled explicitly
                for (JsonArray credentialsForAuthId : credentialsForTenant.values()) {
                    if (removeCredentialsFromCredentialsArray(deviceId, CredentialsConstants.SPECIFIER_WILDCARD, credentialsForAuthId)) {
                        removedAnyElement = true;
                    }
                }

                // there might be empty credentials arrays left now, so remove them in a second run
                cleanupEmptyCredentialsArrays(credentialsForTenant);
            }

            if (removedAnyElement) {
                dirty = true;
                resultHandler.handle(Future.succeededFuture(CredentialsResult.from(HttpURLConnection.HTTP_NO_CONTENT)));
            } else {
                resultHandler.handle(Future.succeededFuture(CredentialsResult.from(HttpURLConnection.HTTP_NOT_FOUND)));
            }
        } else {
            resultHandler.handle(Future.succeededFuture(CredentialsResult.from(HttpURLConnection.HTTP_FORBIDDEN)));
        }
    }

    private void cleanupEmptyCredentialsArrays(final Map<String, JsonArray> mapToCleanup) {

        // use an iterator here to allow removal during looping (streams currently do not allow this)
        Iterator<Entry<String, JsonArray>> entries = mapToCleanup.entrySet().iterator();
        while (entries.hasNext()) {
            Entry<String, JsonArray> entry = entries.next();
            if (entry.getValue().isEmpty()) {
                entries.remove();
            }
        }
    }

    private boolean removeCredentialsFromCredentialsArray(final String deviceId, final String type, final JsonArray credentialsForAuthId) {

        boolean removedElement = false;

        if (credentialsForAuthId != null) {
            // the credentials in the array always have the same authId, but possibly different types
            // use an iterator here to allow removal during looping (streams currently do not allow this)
            Iterator<Object> credentialsIterator = credentialsForAuthId.iterator();
            while (credentialsIterator.hasNext()) {

                final JsonObject element = (JsonObject) credentialsIterator.next();
                String credType = element.getString(CredentialsConstants.FIELD_TYPE);
                String credDevice = element.getString(CredentialsConstants.FIELD_DEVICE_ID);

                if (!CredentialsConstants.SPECIFIER_WILDCARD.equals(type) && credType.equals(type)) {
                    // delete a single credentials instance
                    credentialsIterator.remove();
                    removedElement = true;
                    break; // there can only be one matching instance due to uniqueness guarantees
                } else if (CredentialsConstants.SPECIFIER_WILDCARD.equalsIgnoreCase(type) && credDevice.equals(deviceId)) {
                    // delete all credentials for device
                    credentialsIterator.remove();
                    removedElement = true;
                } else if (credDevice.equals(deviceId) && credType.equals(type)) {
                    // delete all credentials for device of given type
                    credentialsIterator.remove();
                    removedElement = true;
                }
            }
        }

        return removedElement;
    }

    private Map<String, JsonArray> getCredentialsForTenant(final String tenantId) {
        return credentials.computeIfAbsent(tenantId, id -> new HashMap<>());
    }

    private JsonArray getAuthIdCredentials(final String authId, final Map<String, JsonArray> credentialsForTenant) {
        return credentialsForTenant.computeIfAbsent(authId, id -> new JsonArray());
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
        return String.format("%s[filename=%s]", FileBasedCredentialsService.class.getSimpleName(), getConfig().getCredentialsFilename());
    }
}
