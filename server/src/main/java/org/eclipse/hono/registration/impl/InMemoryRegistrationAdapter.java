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

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.RegistrationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import io.vertx.core.eventbus.Message;
import io.vertx.core.impl.ConcurrentHashSet;
import io.vertx.core.json.JsonObject;

/**
 * Simple "in memory" device registration.
 */
@Service
@Profile("testing")
public class InMemoryRegistrationAdapter extends BaseRegistrationAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(InMemoryRegistrationAdapter.class);

    /* map tenant -> set of devices */
    private final Map<String, Set<String>> deviceMap = new ConcurrentHashMap<>();

    /**
     * Removes all devices from this (in-memory) registry.
     */
    public void clear() {
        LOGGER.debug("clearing registration store");
        deviceMap.clear();
    }

    @Override
    public void processRegistrationMessage(final Message<JsonObject> regMsg) {

        final JsonObject body = regMsg.body();
        final String tenantId = body.getString(MessageHelper.APP_PROPERTY_TENANT_ID);
        final String deviceId = body.getString(MessageHelper.APP_PROPERTY_DEVICE_ID);
        final String action = body.getString(RegistrationConstants.APP_PROPERTY_ACTION);

        switch (action) {
        case ACTION_GET:
            LOGGER.debug("Retrieve device {} for tenant {}.", deviceId, tenantId);
            reply(regMsg, getDevice(tenantId, deviceId));
            break;
        case ACTION_REGISTER:
            LOGGER.debug("Register device {} for tenant {}.", deviceId, tenantId);
            reply(regMsg, addDevice(tenantId, deviceId));
            break;
        case ACTION_DEREGISTER:
            LOGGER.debug("Deregister device {} for tenant {}.", deviceId, tenantId);
            reply(regMsg, removeDevice(tenantId, deviceId));
            break;
        default:
            LOGGER.info("Action {} not supported.", action);
            reply(regMsg, RegistrationResult.from(HTTP_BAD_REQUEST));
        }
    }

    public RegistrationResult getDevice(final String tenantId, final String deviceId) {
        final Set<String> devices = deviceMap.get(tenantId);
        if (devices != null && devices.contains(deviceId)) {
            return RegistrationResult.from(HTTP_OK);
        } else {
            return RegistrationResult.from(HTTP_NOT_FOUND);
        }
    }

    public RegistrationResult removeDevice(final String tenantId, final String deviceId) {
        if (getDevicesForTenant(tenantId).remove(deviceId)) {
            return RegistrationResult.from(HTTP_NO_CONTENT);
        } else {
            return RegistrationResult.from(HTTP_NOT_FOUND);
        }
    }

    public RegistrationResult addDevice(final String tenantId, final String deviceId) {
        if (getDevicesForTenant(tenantId).add(deviceId)) {
            return RegistrationResult.from(HTTP_CREATED);
        } else {
            return RegistrationResult.from(HTTP_CONFLICT);
        }
    }

    private Set<String> getDevicesForTenant(final String tenantId) {
        return deviceMap.computeIfAbsent(tenantId, id -> new ConcurrentHashSet<>());
    }

    @Override
    public String toString() {
        return String.format("InMemoryRegistrationAdapter{registeredDevices: %d}", deviceMap.size());
    }
}
