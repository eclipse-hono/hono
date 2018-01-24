/**
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 1.0 which is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * SPDX-License-Identifier: EPL-1.0
 */

package org.eclipse.hono.deviceregistry;

import static java.net.HttpURLConnection.*;
import static org.eclipse.hono.util.RequestResponseApiConstants.FIELD_ENABLED;
import static org.eclipse.hono.util.TenantConstants.FIELD_ADAPTERS;
import static org.eclipse.hono.util.TenantConstants.FIELD_ADAPTERS_TYPE;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

import org.eclipse.hono.service.tenant.BaseTenantService;
import org.eclipse.hono.util.TenantResult;
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
 * A tenant service that keeps all data in memory but is backed by a file.
 * <p>
 * On startup this adapter loads all registered tenants from a file. On shutdown all tenants kept in memory are written
 * to the file.
 */
@Repository
public final class FileBasedTenantService extends BaseTenantService<FileBasedTenantsConfigProperties> {

    /**
     * The name of the JSON property containing the tenant ID.
     */
    public static final String FIELD_TENANT = "tenant";
    /**
     * The name of the JSON property containing the tenant data.
     */
    public static final String FIELD_DATA = "data";

    // <tenantId, tenantPropertyValueObject>
    private Map<String, JsonObject> tenants = new HashMap<>();
    // <tenantId, <adapterType, adapterDataObject>>
    private Map<String, Map<String, JsonObject>> tenantsAdapterConfigurations = new HashMap<>();
    private boolean running = false;
    private boolean dirty = false;

    @Autowired
    @Override
    public void setConfig(final FileBasedTenantsConfigProperties configuration) {
        setSpecificConfig(configuration);
    }

    @Override
    protected void doStart(Future<Void> startFuture) {

        if (running) {
            startFuture.complete();
        } else {

            if (!getConfig().isModificationEnabled()) {
                log.info("modification of registered tenants has been disabled");
            }

            if (getConfig().getFilename() == null) {
                log.debug("tenant manager filename is not set, no tenant information will be loaded");
                running = true;
                startFuture.complete();
            } else {
                checkFileExists(getConfig().isSaveToFile()).compose(ok -> {
                    return loadTenantData();
                }).compose(s -> {
                    if (getConfig().isSaveToFile()) {
                        log.info("saving tenants to file every 3 seconds");
                        vertx.setPeriodic(3000, tid -> {
                            saveToFile();
                        });
                    } else {
                        log.info("persistence is disabled, will not save tenants to file");
                    }
                    running = true;
                    startFuture.complete();
                }, startFuture);
            }
        }
    }

    Future<Void> loadTenantData() {

        if (getConfig().getFilename() == null) {
            return Future.succeededFuture();
        } else {
            Future<Buffer> readResult = Future.future();
            vertx.fileSystem().readFile(getConfig().getFilename(), readResult.completer());
            return readResult.compose(buffer -> {
                return addAll(buffer);
            }).recover(t -> {
                log.debug("cannot load tenants from file [{}]: {}", getConfig().getFilename(), t.getMessage());
                return Future.succeededFuture();
            });
        }
    }

    private Future<Void> checkFileExists(final boolean createIfMissing) {

        Future<Void> result = Future.future();
        if (getConfig().getFilename() == null) {
            result.fail("no filename set");
        } else if (vertx.fileSystem().existsBlocking(getConfig().getFilename())) {
            result.complete();
        } else if (createIfMissing) {
            vertx.fileSystem().createFile(getConfig().getFilename(), result.completer());
        } else {
            log.debug("no such file [{}]", getConfig().getFilename());
            result.complete();
        }
        return result;
    }

    private Future<Void> addAll(final Buffer tenantsBuffer) {

        final Future<Void> result = Future.future();
        try {
            int tenantCount = 0;
            final JsonArray allObjects = tenantsBuffer.toJsonArray();
            for (Object obj : allObjects) {
                if (JsonObject.class.isInstance(obj)) {
                    tenantCount++;
                    addTenants((JsonObject) obj);
                }
            }
            log.info("successfully loaded {} tenants from file [{}]", tenantCount, getConfig().getFilename());
            result.complete();
        } catch (DecodeException e) {
            log.warn("cannot read malformed JSON from tenants file [{}]", getConfig().getFilename());
            result.fail(e);
        }
        return result;
    }

    private void addTenants(final JsonObject tenant) {
        final String tenantId = tenant.getString(FIELD_TENANT);
        if (tenantId != null) {

            log.debug("loading tenant [{}]", tenantId);
            final JsonObject tenantData = (JsonObject) tenant.getJsonObject(FIELD_DATA);

            tenants.put(tenantId, tenantData);
            final JsonObject adaptersData = (JsonObject) tenant.getJsonObject(FIELD_ADAPTERS);

            if (adaptersData != null) {
                final Map<String, JsonObject> adapterPropertiesMap = new HashMap<>();

                for (final String adapterType : adaptersData.fieldNames()) {
                    if (adapterType != null) {
                        final JsonObject adapterConfigObject = adaptersData.getJsonObject(adapterType);
                        if (JsonObject.class.isInstance(adapterConfigObject)) {
                            log.debug("loading adapter properties for type [{}]", adapterType);
                            adapterPropertiesMap.put(adapterType, adapterConfigObject);
                        }
                    }
                }
                tenantsAdapterConfigurations.put(tenantId, adapterPropertiesMap);
            }
        }
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
                final JsonArray tenantsJson = new JsonArray();

                for (final Entry<String, JsonObject> entry : tenants.entrySet()) {
                    final String tenantId = entry.getKey();
                    final JsonObject tenantJson = new JsonObject();
                    tenantJson.put(FIELD_TENANT, entry.getKey());
                    tenantJson.put(FIELD_DATA, entry.getValue());

                    // now the adapters (if any)
                    final Map<String, JsonObject> adapters = tenantsAdapterConfigurations.get(tenantId);
                    if (adapters != null) {
                        final JsonObject adaptersJson = new JsonObject();
                        for (final Entry<String, JsonObject> adapterEntry : adapters.entrySet()) {
                            final String adapterType = adapterEntry.getKey();
                            adaptersJson.put(adapterType, adapterEntry.getValue());
                        }
                        tenantJson.put(FIELD_ADAPTERS, adaptersJson);
                    }

                    tenantsJson.add(tenantJson);
                }

                final Future<Void> writeHandler = Future.future();
                vertx.fileSystem().writeFile(getConfig().getFilename(),
                        Buffer.factory.buffer(tenantsJson.encodePrettily()), writeHandler.completer());
                return writeHandler.map(ok -> {
                    dirty = false;
                    log.trace("successfully wrote {} tenants to file {}", tenantsJson.size(),
                            getConfig().getFilename());
                    return (Void) null;
                }).otherwise(t -> {
                    log.warn("could not write tenants to file {}", getConfig().getFilename(), t);
                    return (Void) null;
                });
            });
        } else {
            log.trace("tenants registry does not need to be persisted");
            return Future.succeededFuture();
        }
    }

    @Override
    public void get(final String tenantId, final Handler<AsyncResult<TenantResult>> resultHandler) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(resultHandler);
        resultHandler.handle(Future.succeededFuture(getTenantResult(tenantId)));
    }

    TenantResult getTenantResult(final String tenantId) {
        final JsonObject data = tenants.get(tenantId);

        if (data != null) {
            final JsonArray adapterConfigurationsJson = getAdapterConfigurationsData(tenantId);
            return TenantResult.from(HTTP_OK, getResultPayload(tenantId, data, adapterConfigurationsJson));
        } else {
            return TenantResult.from(HTTP_NOT_FOUND);
        }
    }

    private JsonArray getAdapterConfigurationsData(final String tenantId) {
        final Map<String, JsonObject> adapterConfigurations = tenantsAdapterConfigurations.get(tenantId);
        if (adapterConfigurations == null) {
            return null;
        }
        final JsonArray adapterDataArray = new JsonArray();
        for (Entry<String, JsonObject> configEntry : adapterConfigurations.entrySet()) {
            final JsonObject data = new JsonObject();
            data.put(FIELD_ADAPTERS_TYPE, configEntry.getKey()).mergeIn(configEntry.getValue());
            adapterDataArray.add(data);
        }
        return adapterDataArray;
    }

    @Override
    public void remove(final String tenantId, final Handler<AsyncResult<TenantResult>> resultHandler) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(resultHandler);
        resultHandler.handle(Future.succeededFuture(removeTenant(tenantId)));
    }

    TenantResult removeTenant(final String tenantId) {

        Objects.requireNonNull(tenantId);

        if (getConfig().isModificationEnabled()) {
            if (tenants.remove(tenantId) != null) {
                dirty = true;
                tenantsAdapterConfigurations.remove(tenantId);
                return TenantResult.from(HTTP_NO_CONTENT);
            } else {
                return TenantResult.from(HTTP_NOT_FOUND);
            }
        } else {
            return TenantResult.from(HTTP_FORBIDDEN);
        }
    }

    @Override
    public void add(final String tenantId, final JsonObject data,
            final Handler<AsyncResult<TenantResult>> resultHandler) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(data);
        Objects.requireNonNull(resultHandler);

        resultHandler.handle(Future.succeededFuture(addOrUpdateTenant(tenantId, data, false)));
    }

    /**
     * Adds or updates a tenant to this tenant registry.
     *
     * @param tenantId The tenant to add.
     * @param data Additional data to register with the tenant (may be {@code null}).
     * @param update If true, an existing tenant shall be overwritten (otherwise the outcome of this method will result
     *               in {@link java.net.HttpURLConnection#HTTP_CONFLICT};
     * @return The outcome of the operation indicating success or failure.
     */
    public TenantResult addOrUpdateTenant(final String tenantId, final JsonObject data, final boolean update) {

        Objects.requireNonNull(tenantId);
        if (update) {
            if (!tenants.containsKey(tenantId)) {
                return TenantResult.from(HTTP_NOT_FOUND);
            }
        } else {
            if (tenants.containsKey(tenantId)) {
                return TenantResult.from(HTTP_CONFLICT);
            }
        }

        JsonObject obj = data != null ? data : new JsonObject().put(FIELD_ENABLED, Boolean.TRUE);

        try {
            final JsonArray adaptersConfigurationArray = obj.getJsonArray(FIELD_ADAPTERS);
            if (adaptersConfigurationArray != null) {
                final Map<String, JsonObject> adapterConfigurationsMap = new HashMap<>();
                for (int i = 0; i < adaptersConfigurationArray.size(); i++) {
                    final JsonObject adapterConfiguration = adaptersConfigurationArray.getJsonObject(i);
                    final String adapterType = (String) adapterConfiguration.remove(FIELD_ADAPTERS_TYPE);
                    if (adapterType != null) {
                        adapterConfiguration.remove(FIELD_ADAPTERS_TYPE);
                        adapterConfigurationsMap.put(adapterType, adapterConfiguration);
                    } else {
                        return TenantResult.from(HTTP_BAD_REQUEST);
                    }
                }
                // all is checked and prepared, now store it internally
                tenantsAdapterConfigurations.put(tenantId, adapterConfigurationsMap);
            }
        }
        catch (ClassCastException cce) {
            log.warn("addTenant invoked with wrong type for {}: not a JsonArray!", FIELD_ADAPTERS);
            return TenantResult.from(HTTP_BAD_REQUEST);
        }

        dirty = true;
        obj.remove(FIELD_TENANT);
        obj.remove(FIELD_ADAPTERS);
        tenants.put(tenantId, obj);

        if (update) {
            return TenantResult.from(HTTP_NO_CONTENT);
        } else {
            return TenantResult.from(HTTP_CREATED);
        }
    }

    @Override
    public void update(final String tenantId, final JsonObject data, final Handler<AsyncResult<TenantResult>> resultHandler) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(data);
        Objects.requireNonNull(resultHandler);

        resultHandler.handle(Future.succeededFuture(addOrUpdateTenant(tenantId, data, true)));
    }

    /**
     * Removes all devices from the tenant registry.
     */
    public void clear() {
        dirty = true;
        tenants.clear();
        tenantsAdapterConfigurations.clear();
    }

    @Override
    public String toString() {
        return String.format("%s[filename=%s]", FileBasedTenantService.class.getSimpleName(),
                getConfig().getFilename());
    }
}
