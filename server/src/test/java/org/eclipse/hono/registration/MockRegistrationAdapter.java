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
package org.eclipse.hono.registration;

import java.net.HttpURLConnection;

import org.eclipse.hono.registration.impl.BaseRegistrationAdapter;
import org.eclipse.hono.util.RegistrationResult;

import io.vertx.core.json.JsonObject;

/**
 * Simple mock implementation of RegistrationAdapter that always returns success.
 */
public class MockRegistrationAdapter extends BaseRegistrationAdapter {

    @Override
    public RegistrationResult getDevice(String tenantId, String deviceId) {
        return RegistrationResult.from(HttpURLConnection.HTTP_OK);
    }

    @Override
    public RegistrationResult findDevice(String tenantId, String key, String value) {
        return RegistrationResult.from(HttpURLConnection.HTTP_OK);
    }

    @Override
    public RegistrationResult addDevice(String tenantId, String deviceId, JsonObject data) {
        return RegistrationResult.from(HttpURLConnection.HTTP_CREATED);
    }

    @Override
    public RegistrationResult updateDevice(String tenantId, String deviceId, JsonObject data) {
        return RegistrationResult.from(HttpURLConnection.HTTP_OK);
    }

    @Override
    public RegistrationResult removeDevice(String tenantId, String deviceId) {
        return RegistrationResult.from(HttpURLConnection.HTTP_OK);
    }
}