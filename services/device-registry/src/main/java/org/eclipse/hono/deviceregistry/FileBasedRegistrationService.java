/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.deviceregistry;

import static java.net.HttpURLConnection.*;
import static org.eclipse.hono.util.RegistrationConstants.FIELD_DATA;
import static org.eclipse.hono.util.RequestResponseApiConstants.FIELD_PAYLOAD_DEVICE_ID;
import static org.eclipse.hono.util.RequestResponseApiConstants.FIELD_ENABLED;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.hono.service.registration.CompleteBaseRegistrationService;
import org.eclipse.hono.util.RegistrationResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * A registration service that keeps all data in memory but is backed by a file.
 * <p>
 * On startup this adapter loads all registered devices from a file. On shutdown all
 * devices kept in memory are written to the file.
 */
@Repository
@ConditionalOnProperty(name = "hono.app.type", havingValue = "file", matchIfMissing = true)
public final class FileBasedRegistrationService extends CompleteBaseRegistrationService<FileBasedRegistrationConfigProperties> {

    /**
     * The name of the JSON array containing device registration information for a tenant.
     */
    public static final String ARRAY_DEVICES = "devices";
    /**
     * The name of the JSON property containing the tenant ID.
     */
    public static final String FIELD_TENANT = "tenant";

    // <tenantId, <deviceId, registrationData>>
    private final Map<String, Map<String, JsonObject>> identities = new HashMap<>();
    private boolean running = false;
    private boolean dirty = false;

    @Autowired
    @Override
    public void setConfig(final FileBasedRegistrationConfigProperties configuration) {
        setSpecificConfig(configuration);
    }

    @Override
    protected void doStart(final Future<Void> startFuture) {

        if (running) {
            startFuture.complete();
        } else {

            if (!getConfig().isModificationEnabled()) {
                log.info("modification of registered devices has been disabled");
            }

            if (getConfig().getFilename() == null) {
                log.debug("device identity filename is not set, no identity information will be loaded");
                running = true;
                startFuture.complete();
            } else {
                checkFileExists(getConfig().isSaveToFile()).compose(ok -> {
                    return loadRegistrationData();
                }).compose(s -> {
                    if (getConfig().isSaveToFile()) {
                        log.info("saving device identities to file every 3 seconds");
                        vertx.setPeriodic(3000, tid -> {
                            saveToFile();
                        });
                    } else {
                        log.info("persistence is disabled, will not save device identities to file");
                    }
                    running = true;
                    startFuture.complete();
                }, startFuture);
            }
        }
    }

    Future<Void> loadRegistrationData() {

        if (getConfig().getFilename() == null || getConfig().isStartEmpty()) {
            log.info("Either filename is null or empty start is set, won't load any device identities");
            return Future.succeededFuture();
        } else {
            final Future<Buffer> readResult = Future.future();
            vertx.fileSystem().readFile(getConfig().getFilename(), readResult.completer());
            return readResult.compose(buffer -> {
                return addAll(buffer);
            }).recover(t -> {
                log.debug("cannot load device identities from file [{}]: {}", getConfig().getFilename(), t.getMessage());
                return Future.succeededFuture();
            });
        }
    }

    private Future<Void> checkFileExists(final boolean createIfMissing) {

        final Future<Void> result = Future.future();
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

    private Future<Void> addAll(final Buffer deviceIdentities) {

        final Future<Void> result = Future.future();
        try {
            int deviceCount = 0;
            final JsonArray allObjects = deviceIdentities.toJsonArray();
            for (final Object obj : allObjects) {
                if (JsonObject.class.isInstance(obj)) {
                    deviceCount += addDevicesForTenant((JsonObject) obj);
                }
            }
            log.info("successfully loaded {} device identities from file [{}]", deviceCount, getConfig().getFilename());
            result.complete();
        } catch (final DecodeException e) {
            log.warn("cannot read malformed JSON from device identity file [{}]", getConfig().getFilename());
            result.fail(e);
        }
        return result;
    }

    private int addDevicesForTenant(final JsonObject tenant) {
        int count = 0;
        final String tenantId = tenant.getString(FIELD_TENANT);
        if (tenantId != null) {
            log.debug("loading devices for tenant [{}]", tenantId);
            final Map<String, JsonObject> deviceMap = new HashMap<>();
            for (final Object deviceObj : tenant.getJsonArray(ARRAY_DEVICES)) {
                if (JsonObject.class.isInstance(deviceObj)) {
                    final JsonObject device = (JsonObject) deviceObj;
                    final String deviceId = device.getString(FIELD_PAYLOAD_DEVICE_ID);
                    if (deviceId != null) {
                        log.trace("loading device [{}]", deviceId);
                        final JsonObject data = device.getJsonObject(FIELD_DATA,
                                new JsonObject().put(FIELD_ENABLED, Boolean.TRUE));
                        deviceMap.put(deviceId, data);
                        count++;
                    }
                }
            }
            identities.put(tenantId, deviceMap);
        }
        log.debug("Loaded {} devices for tenant {}", count, tenantId);
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
                final JsonArray tenants = new JsonArray();
                for (final Entry<String, Map<String, JsonObject>> entry : identities.entrySet()) {
                    final JsonArray devices = new JsonArray();
                    for (final Entry<String, JsonObject> deviceEntry : entry.getValue().entrySet()) {
                        devices.add(
                                new JsonObject()
                                        .put(FIELD_PAYLOAD_DEVICE_ID, deviceEntry.getKey())
                                        .put(FIELD_DATA, deviceEntry.getValue()));
                        idCount.incrementAndGet();
                    }
                    tenants.add(
                            new JsonObject()
                                    .put(FIELD_TENANT, entry.getKey())
                                    .put(ARRAY_DEVICES, devices));
                }

                final Future<Void> writeHandler = Future.future();
                vertx.fileSystem().writeFile(getConfig().getFilename(), Buffer.factory.buffer(tenants.encodePrettily()), writeHandler.completer());
                return writeHandler.map(ok -> {
                    dirty = false;
                    log.trace("successfully wrote {} device identities to file {}", idCount.get(), getConfig().getFilename());
                    return (Void) null;
                }).otherwise(t -> {
                    log.warn("could not write device identities to file {}", getConfig().getFilename(), t);
                    return (Void) null;
                });
            });
        } else {
            log.trace("registry does not need to be persisted");
            return Future.succeededFuture();
        }
    }

    @Override
    public void getDevice(final String tenantId, final String deviceId, final Handler<AsyncResult<RegistrationResult>> resultHandler) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(resultHandler);
        resultHandler.handle(Future.succeededFuture(getDevice(tenantId, deviceId)));
    }

    RegistrationResult getDevice(final String tenantId, final String deviceId) {
        final JsonObject data = getRegistrationData(tenantId, deviceId);
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

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(resultHandler);

        resultHandler.handle(Future.succeededFuture(removeDevice(tenantId, deviceId)));
    }

    RegistrationResult removeDevice(final String tenantId, final String deviceId) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);

        if (getConfig().isModificationEnabled()) {
            final Map<String, JsonObject> devices = identities.get(tenantId);
            if (devices != null && devices.remove(deviceId) != null) {
                dirty = true;
                return RegistrationResult.from(HTTP_NO_CONTENT);
            } else {
                return RegistrationResult.from(HTTP_NOT_FOUND);
            }
        } else {
            return RegistrationResult.from(HTTP_FORBIDDEN);
        }
    }

    @Override
    public void addDevice(final String tenantId, final String deviceId, final JsonObject data, final Handler<AsyncResult<RegistrationResult>> resultHandler) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(resultHandler);

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

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);

        final JsonObject obj = data != null ? data : new JsonObject().put(FIELD_ENABLED, Boolean.TRUE);
        final Map<String, JsonObject> devices = getDevicesForTenant(tenantId);
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

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(resultHandler);

        resultHandler.handle(Future.succeededFuture(updateDevice(tenantId, deviceId, data)));
    }

    RegistrationResult updateDevice(final String tenantId, final String deviceId, final JsonObject data) {

        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(deviceId);

        if (getConfig().isModificationEnabled()) {
            final JsonObject obj = data != null ? data : new JsonObject().put(FIELD_ENABLED, Boolean.TRUE);
            final Map<String, JsonObject> devices = identities.get(tenantId);
            if (devices != null && devices.containsKey(deviceId)) {
                devices.put(deviceId, obj);
                dirty = true;
                return RegistrationResult.from(HTTP_NO_CONTENT);
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
