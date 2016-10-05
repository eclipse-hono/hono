/**
 * Copyright (c) 2016 Bosch Software Innovations GmbH.
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

import static java.net.HttpURLConnection.*;
import static org.eclipse.hono.util.RegistrationConstants.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.RegistrationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import io.vertx.core.file.FileSystem;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * A registration adapter that keeps all data in memory but is backed by a file.
 * <p>
 * On startup this adapter loads all registered devices from a file. On shutdown all
 * devices kept in memory are written to the file.
 */
@Service
public class FileBasedRegistrationAdapter extends BaseRegistrationAdapter {

    private static final String ARRAY_DEVICES = "devices";
    private static final String FIELD_ID = "id";
    private static final String FIELD_TENANT = "tenant";
    private static final Logger LOG = LoggerFactory.getLogger(FileBasedRegistrationAdapter.class);

    private final Map<String, Map<String, JsonObject>> identities = new HashMap<>();
    // the name of the file used to persist the registry content
    @Value("${hono.registration.filename:device-identities.json}")
    private String filename;

    @Override
    protected void doStart(Future<Void> startFuture) throws Exception {

        final FileSystem fs = vertx.fileSystem();
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
                           String deviceId = device.getString(FIELD_ID);
                           deviceMap.put(deviceId, device);
                           deviceCount.incrementAndGet();
                       }
                       identities.put(tenantId, deviceMap);
                   }
                   LOG.info("successfully loaded {} device identities from file [{}]", deviceCount.get(), filename);
                   startFuture.complete();
               } else {
                   LOG.warn("could not load device identities from file [{}]", filename, readAttempt.cause());
                   startFuture.fail(readAttempt.cause());
               }
            });
        } else {
            startFuture.complete();
        }
        vertx.setPeriodic(3000, saveIdentities -> {
            saveToFile(Future.future());
        });
    }

    @Override
    protected void doStop(Future<Void> stopFuture) {
        saveToFile(stopFuture);
    }

    private void saveToFile(final Future<Void> writeResult) {
        final FileSystem fs = vertx.fileSystem();
        if (!fs.existsBlocking(filename)) {
            fs.createFileBlocking(filename);
        }
        final AtomicInteger idCount = new AtomicInteger();
        JsonArray tenants = new JsonArray();
        for (Entry<String, Map<String, JsonObject>> entry : identities.entrySet()) {
            JsonArray devices = new JsonArray();
            for (Entry<String, JsonObject> deviceEntry : entry.getValue().entrySet()) {
                devices.add(deviceEntry.getValue());
                idCount.incrementAndGet();
            }
            tenants.add(new JsonObject().put(FIELD_TENANT, entry.getKey()).put(ARRAY_DEVICES, devices));
        }
        fs.writeFile(filename, Buffer.factory.buffer(tenants.encodePrettily()), writeAttempt -> {
            if (writeAttempt.succeeded()) {
                LOG.trace("successfully wrote {} device identities to file {}", idCount.get(), filename);
                writeResult.complete();
            } else {
                LOG.warn("could not write device identities to file {}", filename, writeAttempt.cause());
                writeResult.fail(writeAttempt.cause());
            }
        });
    }

    @Override
    public void processRegistrationMessage(final Message<JsonObject> regMsg) {

        final JsonObject body = regMsg.body();
        final String tenantId = body.getString(MessageHelper.APP_PROPERTY_TENANT_ID);
        final String deviceId = body.getString(MessageHelper.APP_PROPERTY_DEVICE_ID);
        final String action = body.getString(RegistrationConstants.APP_PROPERTY_ACTION);
        final JsonObject payload = body.getJsonObject(RegistrationConstants.FIELD_PAYLOAD, new JsonObject());

        switch (action) {
        case ACTION_GET:
            LOG.debug("retrieving device [{}] of tenant [{}]", deviceId, tenantId);
            reply(regMsg, getDevice(tenantId, deviceId));
            break;
        case ACTION_REGISTER:
            LOG.debug("registering device [{}] of tenant [{}] with data {}", deviceId, tenantId, payload.encode());
            reply(regMsg, addDevice(tenantId, deviceId, payload));
            break;
        case ACTION_UPDATE:
            LOG.debug("updating registration for device [{}] of tenant [{}] with data {}", deviceId, tenantId, payload.encode());
            reply(regMsg, updateDevice(tenantId, deviceId, payload));
            break;
        case ACTION_DEREGISTER:
            LOG.debug("deregistering device [{}] of tenant [{}]", deviceId, tenantId);
            reply(regMsg, removeDevice(tenantId, deviceId));
            break;
        default:
            LOG.info("action [{}] not supported", action);
            reply(regMsg, RegistrationResult.from(HTTP_BAD_REQUEST));
        }
    }

    public RegistrationResult getDevice(final String tenantId, final String deviceId) {
        final Map<String, JsonObject> devices = identities.get(tenantId);
        if (devices != null) {
            JsonObject data = devices.get(deviceId);
            if (data != null) {
                return RegistrationResult.from(HTTP_OK, data);
            }
        }
        return RegistrationResult.from(HTTP_NOT_FOUND);
    }

    public RegistrationResult removeDevice(final String tenantId, final String deviceId) {

        final Map<String, JsonObject> devices = identities.get(tenantId);
        if (devices != null) {
            return RegistrationResult.from(HTTP_OK, devices.remove(deviceId));
        } else {
            return RegistrationResult.from(HTTP_NOT_FOUND);
        }
    }

    public RegistrationResult addDevice(final String tenantId, final String deviceId, final JsonObject data) {
        if (getDevicesForTenant(tenantId).putIfAbsent(deviceId, data) == null) {
            return RegistrationResult.from(HTTP_CREATED);
        } else {
            return RegistrationResult.from(HTTP_CONFLICT);
        }
    }

    public RegistrationResult updateDevice(final String tenantId, final String deviceId, final JsonObject data) {

        final Map<String, JsonObject> devices = identities.get(tenantId);
        if (devices != null && devices.containsKey(deviceId)) {
            return RegistrationResult.from(HTTP_OK, devices.put(deviceId, data));
        } else {
            return RegistrationResult.from(HTTP_NOT_FOUND);
        }
    }

    private Map<String, JsonObject> getDevicesForTenant(final String tenantId) {
        return identities.computeIfAbsent(tenantId, id -> new ConcurrentHashMap<>());
    }

    void clear() {
        identities.clear();
    }

    @Override
    public String toString() {
        return String.format("%s[filename=%s]", FileBasedRegistrationAdapter.class.getSimpleName(), filename);
    }
}
