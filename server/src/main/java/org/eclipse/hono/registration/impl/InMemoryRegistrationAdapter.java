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

import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static java.net.HttpURLConnection.HTTP_CONFLICT;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.eclipse.hono.registration.RegistrationConstants.ACTION_DEREGISTER;
import static org.eclipse.hono.registration.RegistrationConstants.ACTION_GET;
import static org.eclipse.hono.registration.RegistrationConstants.ACTION_REGISTER;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.impl.ConcurrentHashSet;
import org.springframework.stereotype.Service;

/**
 * Simple "in memory" device registration.
 */
@Service
public class InMemoryRegistrationAdapter extends BaseRegistrationAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(InMemoryRegistrationAdapter.class);

    /* map tenant -> set of devices */
    private final Map<String, Set<String>> deviceMap = new ConcurrentHashMap<>();

    @Override
    public void processRegistrationMessage(final String tenantId, final String deviceId, final String action, final String msgId) {

        switch (action) {
        case ACTION_GET:
            getDevice(tenantId, deviceId, msgId);
            LOGGER.debug("Retrieve device {} for tenant {}.", deviceId, tenantId);
            break;
        case ACTION_REGISTER:
            addDevice(tenantId, deviceId, msgId);
            LOGGER.debug("Register device {} for tenant {}.", deviceId, tenantId);
            break;
        case ACTION_DEREGISTER:
            LOGGER.debug("Deregister device {} for tenant {}.", deviceId, tenantId);
            removeDevice(tenantId, deviceId, msgId);
            break;
        default:
            reply(HTTP_BAD_REQUEST, deviceId, tenantId, msgId);
            LOGGER.info("Action {} not supported.", action);
        }

        LOGGER.debug("Registered devices for {}: {}", tenantId, deviceMap.get(tenantId) != null ? deviceMap.get(tenantId).toArray() : "<empty>");
    }

    private void getDevice(final String tenantId, final String deviceId, final String msgId) {
        final Set<String> devices = deviceMap.get(tenantId);
        if (devices != null && devices.contains(deviceId)) {
            reply(HTTP_OK, deviceId, tenantId, msgId);
        }
        else {
            reply(HTTP_NOT_FOUND, deviceId, tenantId, msgId);
        }
    }

    private void removeDevice(final String tenantId, final String deviceId, final String msgId) {
        if (getDevicesForTenant(tenantId).remove(deviceId)) {
            reply(HTTP_OK, deviceId, tenantId, msgId);
        }
        else {
            reply(HTTP_NOT_FOUND, deviceId, tenantId, msgId);
        }
    }

    private void addDevice(final String tenantId, final String deviceId, final String msgId) {
        if (getDevicesForTenant(tenantId).add(deviceId)) {
            reply(HTTP_OK, deviceId, tenantId, msgId);
        }
        else {
            reply(HTTP_CONFLICT, deviceId, tenantId, msgId);
        }
    }

    private Set<String> getDevicesForTenant(final String tenantId) {
        return deviceMap.computeIfAbsent(tenantId, id -> new ConcurrentHashSet<>());
    }
}
