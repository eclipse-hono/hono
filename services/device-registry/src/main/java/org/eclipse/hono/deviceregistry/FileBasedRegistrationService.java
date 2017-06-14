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

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileSystem;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.eclipse.hono.service.registration.BaseRegistrationService;
import org.eclipse.hono.util.RegistrationResult;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static java.net.HttpURLConnection.*;
import static org.eclipse.hono.util.RegistrationConstants.*;

/**
 * A registration service that keeps all data in memory but is backed by a file.
 * <p>
 * On startup this adapter loads all registered devices from a file. On shutdown all
 * devices kept in memory are written to the file.
 */
@Repository
@ConfigurationProperties(prefix = "hono.registration")
@Profile({"default", "registration-file"})
public final class FileBasedRegistrationService extends BaseRegistrationService {

    /**
     * The default number of devices that can be registered for each tenant.
     */
    public static final int DEFAULT_MAX_DEVICES_PER_TENANT = 100;
    private static final String ARRAY_DEVICES = "devices";
    private static final String FIELD_TENANT = "tenant";

    // <tenantId, <deviceId, registrationData>>
    private Map<String, Map<String, JsonObject>> identities = new HashMap<>();
    // the name of the file used to persist the registry content
    private String filename = "device-identities.json";
    private boolean saveToFile = false;
    private boolean isModificationEnabled = true;
    private int maxDevicesPerTenant = DEFAULT_MAX_DEVICES_PER_TENANT;
    private boolean running = false;
    private boolean dirty = false;

    /**
     * Sets the path to the file that the content of the registry should be persisted
     * periodically.
     * <p>
     * Default value is <em>device-identities.json</em>.
     * 
     * @param filename The name of the file to persist to (can be a relative or absolute path).
     * @throws IllegalStateException if this registry is already running.
     */
    public void setFilename(final String filename) {
        if (running) {
            throw new IllegalStateException("registry already started");
        }
        this.filename = filename;
    }

    /**
     * Sets whether the content of the registry should be persisted to the file system
     * periodically.
     * <p>
     * Default value is {@code true}.
     * 
     * @param enabled {@code true} if registry content should be persisted.
     * @throws IllegalStateException if this registry is already running.
     */
    public void setSaveToFile(final boolean enabled) {
        if (running) {
            throw new IllegalStateException("registry already started");
        }
        this.saveToFile = enabled;
    }

    /**
     * Sets whether this registry allows modification and removal of registered devices.
     * <p>
     * If set to {@code false} then the methods {@link #updateDevice(String, String, JsonObject, Handler)}
     * and {@link #removeDevice(String, String, Handler)} always return a <em>403 Forbidden</em> response.
     * <p>
     * The default value of this property is {@code true}.
     * 
     * @param flag The flag.
     */
    public void setModificationEnabled(final boolean flag) {
        isModificationEnabled = flag;
    }

    /**
     * Sets the maximum nubmer of devices that can be registered for each tenant.
     * <p>
     * The default value of this property is {@link #DEFAULT_MAX_DEVICES_PER_TENANT}.
     * 
     * @param maxDevices The maximum number of devices.
     * @throws IllegalArgumentException if the number of devices is &lt;= 0.
     */
    public void setMaxDevicesPerTenant(final int maxDevices) {
        if (maxDevices <= 0) {
            throw new IllegalArgumentException("max devices must be > 0");
        }
        this.maxDevicesPerTenant = maxDevices;
    }

    @Override
    protected void doStart(Future<Void> startFuture) {

        if (!running) {
            if (!isModificationEnabled) {
                log.info("modification of registered devices has been disabled");
            }
            if (filename != null) {
                loadRegistrationData();
                if (saveToFile) {
                    log.info("saving device identities to file every 3 seconds");
                    vertx.setPeriodic(3000, saveIdentities -> {
                        saveToFile(Future.future());
                    });
                } else {
                    log.info("persistence is disabled, will not safe device identities to file");
                }
            }
        }
        running = true;
        startFuture.complete();
    }

    private void loadRegistrationData() {
        if (filename != null) {
            final FileSystem fs = vertx.fileSystem();
            log.debug("trying to load device registration information from file {}", filename);
            if (fs.existsBlocking(filename)) {
                final AtomicInteger deviceCount = new AtomicInteger();
                fs.readFile(filename, readAttempt -> {
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
                       log.info("successfully loaded {} device identities from file [{}]", deviceCount.get(), filename);
                   } else {
                       log.warn("could not load device identities from file [{}]", filename, readAttempt.cause());
                   }
                });
            } else {
                log.debug("device identity file {} does not exist (yet)", filename);
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

    private void saveToFile(final Future<Void> writeResult) {

        if (!dirty) {
            log.trace("registry does not need to be persisted");
            return;
        }

        final FileSystem fs = vertx.fileSystem();
        if (!fs.existsBlocking(filename)) {
            fs.createFileBlocking(filename);
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
        fs.writeFile(filename, Buffer.factory.buffer(tenants.encodePrettily()), writeAttempt -> {
            if (writeAttempt.succeeded()) {
                dirty = false;
                log.trace("successfully wrote {} device identities to file {}", idCount.get(), filename);
                writeResult.complete();
            } else {
                log.warn("could not write device identities to file {}", filename, writeAttempt.cause());
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
    public void findDevice(final String tenantId, final String key, final String value, final Handler<AsyncResult<RegistrationResult>> resultHandler) {
        resultHandler.handle(Future.succeededFuture(findDevice(tenantId, key, value)));
    }

    RegistrationResult findDevice(final String tenantId, final String key, final String value) {
        final Map<String, JsonObject> devices = identities.get(tenantId);
        if (devices != null) {
            for (Entry<String, JsonObject> entry : devices.entrySet()) {
                if (value.equals(entry.getValue().getString(key))) {
                    return RegistrationResult.from(HTTP_OK, getResultPayload(entry.getKey(), entry.getValue()));
                }
            }
        }
        return RegistrationResult.from(HTTP_NOT_FOUND);
    }

    @Override
    public void removeDevice(final String tenantId, final String deviceId, final Handler<AsyncResult<RegistrationResult>> resultHandler) {

        resultHandler.handle(Future.succeededFuture(removeDevice(tenantId, deviceId)));
    }

    RegistrationResult removeDevice(final String tenantId, final String deviceId) {

        if (isModificationEnabled) {
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
        if (devices.size() < maxDevicesPerTenant) {
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

        if (isModificationEnabled) {
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
        return String.format("%s[filename=%s]", FileBasedRegistrationService.class.getSimpleName(), filename);
    }
}
