/**
 * Copyright (c) 2016, 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.deviceregistry;

import static java.net.HttpURLConnection.*;
import static org.eclipse.hono.util.RegistrationConstants.FIELD_DATA;
import static org.eclipse.hono.util.RequestResponseApiConstants.FIELD_DEVICE_ID;
import static org.eclipse.hono.util.RequestResponseApiConstants.FIELD_ENABLED;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.hono.service.registration.BaseRegistrationService;
import org.eclipse.hono.util.RegistrationResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileSystem;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * A registration service that keeps all data in memory but is backed by a file.
 * <p>
 * On startup this adapter loads all registered devices from a file. On shutdown all
 * devices kept in memory are written to the file.
 */
@Repository
public final class FileBasedRegistrationService extends BaseRegistrationService<FileBasedRegistrationConfigProperties> {

    private static final String ARRAY_DEVICES = "devices";
    private static final String FIELD_TENANT = "tenant";

    // <tenantId, <deviceId, registrationData>>
    private Map<String, Map<String, JsonObject>> identities = new HashMap<>();
    private boolean running = false;
    private boolean dirty = false;

    @Autowired
    @Override
    public void setConfig(final FileBasedRegistrationConfigProperties configuration) {
        setSpecificConfig(configuration);
    }

    @Override
    protected void doStart(Future<Void> startFuture) {

        if (!running) {
            if (!getConfig().isModificationEnabled()) {
                log.info("modification of registered devices has been disabled");
            }
            if (getConfig().getFilename() != null) {
                loadRegistrationData();
                if (getConfig().isSaveToFile()) {
                    log.info("saving device identities to file every 3 seconds");
                    vertx.setPeriodic(3000, saveIdentities -> {
                        saveToFile(Future.future());
                    });
                } else {
                    log.info("persistence is disabled, will not save device identities to file");
                }
            }
        }
        running = true;
        startFuture.complete();
    }

    private void loadRegistrationData() {
        if (getConfig().getFilename() != null) {
            final FileSystem fs = vertx.fileSystem();
            log.debug("trying to load device registration information from file {}", getConfig().getFilename());
            if (fs.existsBlocking(getConfig().getFilename())) {
                final AtomicInteger deviceCount = new AtomicInteger();
                fs.readFile(getConfig().getFilename(), readAttempt -> {
                   if (readAttempt.succeeded()) {
                       JsonArray allObjects = new JsonArray(new String(readAttempt.result().getBytes()));
                       for (Object obj : allObjects) {
                           JsonObject tenant = (JsonObject) obj;
                           String tenantId = tenant.getString(FIELD_TENANT);
                           Map<String, JsonObject> deviceMap = new HashMap<>();
                           for (Object deviceObj : tenant.getJsonArray(ARRAY_DEVICES)) {
                               JsonObject device = (JsonObject) deviceObj;
                               deviceMap.put(device.getString(FIELD_DEVICE_ID), device.getJsonObject(FIELD_DATA));
                               deviceCount.incrementAndGet();
                           }
                           identities.put(tenantId, deviceMap);
                       }
                       log.info("successfully loaded {} device identities from file [{}]", deviceCount.get(), getConfig().getFilename());
                   } else {
                       log.warn("could not load device identities from file [{}]", getConfig().getFilename(), readAttempt.cause());
                   }
                });
            } else {
                log.debug("device identity file {} does not exist (yet)", getConfig().getFilename());
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

            if (getConfig().isSaveToFile()) {
                saveToFile(stopTracker);
            } else {
                stopTracker.complete();
            }
        } else {
            stopFuture.complete();
        }
    }

    private void saveToFile(final Future<Void> writeResult) {

        if (!dirty) {
            log.trace("registry does not need to be persisted");
            return;
        }

        final FileSystem fs = vertx.fileSystem();
        if (!fs.existsBlocking(getConfig().getFilename())) {
            fs.createFileBlocking(getConfig().getFilename());
        }
        final AtomicInteger idCount = new AtomicInteger();
        JsonArray tenants = new JsonArray();
        for (Entry<String, Map<String, JsonObject>> entry : identities.entrySet()) {
            JsonArray devices = new JsonArray();
            for (Entry<String, JsonObject> deviceEntry : entry.getValue().entrySet()) {
                devices.add(
                        new JsonObject()
                            .put(FIELD_DEVICE_ID, deviceEntry.getKey())
                            .put(FIELD_DATA, deviceEntry.getValue()));
                idCount.incrementAndGet();
            }
            tenants.add(
                    new JsonObject()
                        .put(FIELD_TENANT, entry.getKey())
                        .put(ARRAY_DEVICES, devices));
        }
        fs.writeFile(getConfig().getFilename(), Buffer.factory.buffer(tenants.encodePrettily()), writeAttempt -> {
            if (writeAttempt.succeeded()) {
                dirty = false;
                log.trace("successfully wrote {} device identities to file {}", idCount.get(), getConfig().getFilename());
                writeResult.complete();
            } else {
                log.warn("could not write device identities to file {}", getConfig().getFilename(), writeAttempt.cause());
                writeResult.fail(writeAttempt.cause());
            }
        });
    }

    @Override
    public void getDevice(final String tenantId, final String deviceId, final Handler<AsyncResult<RegistrationResult>> resultHandler) {
        resultHandler.handle(Future.succeededFuture(getDevice(tenantId, deviceId)));
    }

    RegistrationResult getDevice(final String tenantId, final String deviceId) {
        JsonObject data = getRegistrationData(tenantId, deviceId);
        if (data != null) {
            return RegistrationResult.from(HTTP_OK, getResultPayload(deviceId, data));
        } else {
            return RegistrationResult.from(HTTP_NOT_FOUND);
        }
    }

    private JsonObject getRegistrationData(final String tenantId, final String deviceId) {

        final Map<String, JsonObject> devices = identities.get(tenantId);
        if (devices != null) {
            return devices.get(deviceId);
        } else {
            return null;
        }
    }

    @Override
    public void removeDevice(final String tenantId, final String deviceId, final Handler<AsyncResult<RegistrationResult>> resultHandler) {

        resultHandler.handle(Future.succeededFuture(removeDevice(tenantId, deviceId)));
    }

    RegistrationResult removeDevice(final String tenantId, final String deviceId) {

        if (getConfig().isModificationEnabled()) {
            final Map<String, JsonObject> devices = identities.get(tenantId);
            if (devices != null && devices.containsKey(deviceId)) {
                dirty = true;
                return RegistrationResult.from(HTTP_OK, getResultPayload(deviceId, devices.remove(deviceId)));
            } else {
                return RegistrationResult.from(HTTP_NOT_FOUND);
            }
        } else {
            return RegistrationResult.from(HTTP_FORBIDDEN);
        }
    }

    @Override
    public void addDevice(final String tenantId, final String deviceId, final JsonObject data, final Handler<AsyncResult<RegistrationResult>> resultHandler) {

        resultHandler.handle(Future.succeededFuture(addDevice(tenantId, deviceId, data)));
    }

    /**
     * Adds a device to this registry.
     * 
     * @param tenantId The tenant the device belongs to.
     * @param deviceId The ID of the device to add.
     * @param data Additional data to register with the device (may be {@code null}).
     * @return The outcome of the operation indicating success or failure.
     */
    public RegistrationResult addDevice(final String tenantId, final String deviceId, final JsonObject data) {

        JsonObject obj = data != null ? data : new JsonObject().put(FIELD_ENABLED, Boolean.TRUE);
        Map<String, JsonObject> devices = getDevicesForTenant(tenantId);
        if (devices.size() < getConfig().getMaxDevicesPerTenant()) {
            if (devices.putIfAbsent(deviceId, obj) == null) {
                dirty = true;
                return RegistrationResult.from(HTTP_CREATED);
            } else {
                return RegistrationResult.from(HTTP_CONFLICT);
            }
        } else {
            return RegistrationResult.from(HTTP_FORBIDDEN);
        }
    }

    @Override
    public void updateDevice(final String tenantId, final String deviceId, final JsonObject data, final Handler<AsyncResult<RegistrationResult>> resultHandler) {

        resultHandler.handle(Future.succeededFuture(updateDevice(tenantId, deviceId, data)));
    }

    RegistrationResult updateDevice(final String tenantId, final String deviceId, final JsonObject data) {

        if (getConfig().isModificationEnabled()) {
            JsonObject obj = data != null ? data : new JsonObject().put(FIELD_ENABLED, Boolean.TRUE);
            final Map<String, JsonObject> devices = identities.get(tenantId);
            if (devices != null && devices.containsKey(deviceId)) {
                dirty = true;
                return RegistrationResult.from(HTTP_OK, getResultPayload(deviceId, devices.put(deviceId, obj)));
            } else {
                return RegistrationResult.from(HTTP_NOT_FOUND);
            }
        } else {
            return RegistrationResult.from(HTTP_FORBIDDEN);
        }
    }

    private Map<String, JsonObject> getDevicesForTenant(final String tenantId) {
        return identities.computeIfAbsent(tenantId, id -> new ConcurrentHashMap<>());
    }

    /**
     * Removes all devices from the registry.
     */
    public void clear() {
        dirty = true;
        identities.clear();
    }

    @Override
    public String toString() {
        return String.format("%s[filename=%s]", FileBasedRegistrationService.class.getSimpleName(), getConfig().getFilename());
    }
}
