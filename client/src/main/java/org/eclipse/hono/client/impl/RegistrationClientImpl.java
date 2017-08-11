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

package org.eclipse.hono.client.impl;

import static org.eclipse.hono.util.RegistrationConstants.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.client.RegistrationClient;
import org.eclipse.hono.client.RequestResponseClient;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RegistrationResult;
import org.eclipse.hono.util.RequestResponseResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.proton.ProtonConnection;

/**
 * A Vertx-Proton based client for Hono's Registration API.
 *
 */
public final class RegistrationClientImpl extends AbstractRequestResponseClient<RegistrationClient, RegistrationResult> implements RegistrationClient {

    private static final Logger                  LOG = LoggerFactory.getLogger(RegistrationClientImpl.class);
    private static final String                  REGISTRATION_NAME = "registration";

    private RegistrationClientImpl(final Context context, final ProtonConnection con, final String tenantId,
                                   final Handler<AsyncResult<RegistrationClient>> creationHandler) {

        super(context,con,tenantId,creationHandler);
    }


    @Override
    protected String getName() {

        return REGISTRATION_NAME;
    }

    @Override
    protected String createMessageId() {

        return String.format("reg-client-%d", messageCounter.getAndIncrement());
    }

    @Override
    protected RegistrationResult getResult(final int status, final String payload) {

        return RegistrationResult.from(status, payload);
    }

    /**
     * Creates a new registration client for a tenant.
     * 
     * @param context The vert.x context to run all interactions with the server on.
     * @param con The AMQP connection to the server.
     * @param tenantId The tenant to consumer events for.
     * @param creationHandler The handler to invoke with the outcome of the creation attempt.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    public static void create(final Context context, final ProtonConnection con, final String tenantId,
            final Handler<AsyncResult<RegistrationClient>> creationHandler) {

        new RegistrationClientImpl(
                Objects.requireNonNull(context),
                Objects.requireNonNull(con),
                Objects.requireNonNull(tenantId),
                Objects.requireNonNull(creationHandler));
    }

    private Map<String, Object> createDeviceIdProperties(final String deviceId) {
        final Map<String, Object> properties = new HashMap<>();
        properties.put(MessageHelper.APP_PROPERTY_DEVICE_ID, deviceId);
        return properties;
    }

    @Override
    public void register(final String deviceId, final JsonObject data, final Handler<AsyncResult<RegistrationResult>> resultHandler) {

        createAndSendRequest(ACTION_REGISTER, createDeviceIdProperties(deviceId), data, resultHandler);
    }

    @Override
    public void update(final String deviceId, final JsonObject data, final Handler<AsyncResult<RegistrationResult>> resultHandler) {

        createAndSendRequest(ACTION_UPDATE, createDeviceIdProperties(deviceId), data, resultHandler);
    }

    @Override
    public void deregister(final String deviceId, final Handler<AsyncResult<RegistrationResult>> resultHandler) {

        createAndSendRequest(ACTION_DEREGISTER, createDeviceIdProperties(deviceId), null, resultHandler);
    }

    @Override
    public void get(final String deviceId, final Handler<AsyncResult<RegistrationResult>> resultHandler) {

        createAndSendRequest(ACTION_GET, createDeviceIdProperties(deviceId), null, resultHandler);
    }

    @Override
    public void assertRegistration(final String deviceId, final Handler<AsyncResult<RegistrationResult>> resultHandler) {

        createAndSendRequest(ACTION_ASSERT, createDeviceIdProperties(deviceId), null, resultHandler);
    }
}
