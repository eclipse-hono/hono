/*******************************************************************************
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client.impl;

import java.util.Objects;

import org.eclipse.hono.client.GatewayMapper;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.RegistrationClientFactory;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.RegistrationConstants;

import io.opentracing.SpanContext;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * A component that maps a given device to the gateway through which data was last published for the given device.
 */
public class GatewayMapperImpl extends ConnectionLifecycleWrapper<HonoConnection> implements GatewayMapper {

    private final RegistrationClientFactory registrationClientFactory;

    /**
     * Creates a new GatewayMapperImpl instance.
     *
     * @param registrationClientFactory The factory to create a registration client instance.
     */
    public GatewayMapperImpl(final RegistrationClientFactory registrationClientFactory) {
        super(registrationClientFactory);
        this.registrationClientFactory = registrationClientFactory;
    }

    @Override
    public Future<String> getMappedGatewayDevice(final String tenantId, final String deviceId, final SpanContext context) {
        return registrationClientFactory.getOrCreateRegistrationClient(tenantId).compose(client -> {
            return client.get(deviceId, context);
        }).map(deviceData -> {
            return getDeviceLastVia(deviceId, deviceData);
        });
    }

    /**
     * Extracts the 'last-via' field content from the given device registration data JSON.
     * <p>
     * If the given device has no 'via' device(s) defined, the given device id is returned.
     *
     * @param deviceId The device identifier.
     * @param deviceData The device registration data JSON.
     * @return The content of the "last-via" field (or {@code null} if field doesn't exist) if a "via" is defined;
     *         otherwise the deviceId.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    protected static String getDeviceLastVia(final String deviceId, final JsonObject deviceData) {
        Objects.requireNonNull(deviceId);
        Objects.requireNonNull(deviceData);
        if (deviceData.containsKey(RegistrationConstants.FIELD_VIA)) {
            final JsonObject lastViaObj = deviceData.getJsonObject(RegistrationConstants.FIELD_LAST_VIA);
            return lastViaObj != null ? lastViaObj.getString(Constants.JSON_FIELD_DEVICE_ID) : null;
        } else {
            // device not configured to connect via gateway - return device id itself
            return deviceId;
        }
    }
}
