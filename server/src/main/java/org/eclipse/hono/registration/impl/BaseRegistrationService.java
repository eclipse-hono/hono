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
import static org.eclipse.hono.util.RegistrationConstants.*;

import org.eclipse.hono.registration.RegistrationService;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.RegistrationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;

/**
 * Base class for implementing {@code RegistrationService}s.
 * <p>
 * In particular, this base class provides support for parsing incoming AMQP 1.0
 * messages and route them to specific methods corresponding to the <em>action</em>
 * indicated in the message.
 */
public abstract class BaseRegistrationService extends AbstractVerticle implements RegistrationService {

    protected final Logger LOG = LoggerFactory.getLogger(getClass());
    private MessageConsumer<JsonObject> registrationConsumer;

    /**
     * Registers a Vert.x event consumer for address {@link RegistrationConstants#EVENT_BUS_ADDRESS_REGISTRATION_IN}
     * and then invokes {@link #doStart(Future)}.
     *
     * @param startFuture future to invoke once start up is complete.
     */
    @Override
    public final void start(final Future<Void> startFuture) throws Exception {
        registerConsumer();
        doStart(startFuture);
    }

    /**
     * Subclasses should override this method to perform any work required on start-up of this verticle.
     * <p>
     * This method is invoked by {@link #start()} as part of the verticle deployment process.
     * </p>
     *
     * @param startFuture future to invoke once start up is complete.
     * @throws Exception if start-up fails
     */
    protected void doStart(final Future<Void> startFuture) throws Exception {
        // should be overridden by subclasses
        startFuture.complete();
    }

    private void registerConsumer() {
        registrationConsumer = vertx.eventBus().consumer(EVENT_BUS_ADDRESS_REGISTRATION_IN);
        registrationConsumer.handler(this::processRegistrationMessage);
        LOG.info("listening on event bus [address: {}] for incoming registration messages",
                EVENT_BUS_ADDRESS_REGISTRATION_IN);
    }

    /**
     * Unregisters the registration message consumer from the Vert.x event bus and then invokes {@link #doStop(Future)}.
     *
     * @param stopFuture the future to invoke once shutdown is complete.
     */
    @Override
    public final void stop(final Future<Void> stopFuture) {
        registrationConsumer.unregister();
        LOG.info("unregistered registration data consumer from event bus");
        doStop(stopFuture);
    }

    /**
     * Subclasses should override this method to perform any work required before shutting down this verticle.
     * <p>
     * This method is invoked by {@link #stop()} as part of the verticle deployment process.
     * </p>
     *
     * @param stopFuture the future to invoke once shutdown is complete.
     */
    protected void doStop(final Future<Void> stopFuture) {
        // to be overridden by subclasses
        stopFuture.complete();
    }

    public final void processRegistrationMessage(final Message<JsonObject> regMsg) {

        final JsonObject body = regMsg.body();
        final String tenantId = body.getString(MessageHelper.APP_PROPERTY_TENANT_ID);
        final String deviceId = body.getString(MessageHelper.APP_PROPERTY_DEVICE_ID);
        final String key = body.getString(RegistrationConstants.APP_PROPERTY_KEY);
        final String value = body.getString(RegistrationConstants.APP_PROPERTY_VALUE);
        final String action = body.getString(RegistrationConstants.APP_PROPERTY_ACTION);
        final JsonObject payload = body.getJsonObject(RegistrationConstants.FIELD_PAYLOAD, new JsonObject());

        switch (action) {
        case ACTION_GET:
            LOG.debug("retrieving device [{}] of tenant [{}]", deviceId, tenantId);
            reply(regMsg, getDevice(tenantId, deviceId));
            break;
        case ACTION_FIND:
            LOG.debug("looking up device [key: {}, value: {}] of tenant [{}]", key, value, tenantId);
            reply(regMsg, findDevice(tenantId, key, value));
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

    protected final void reply(final Message<JsonObject> request, final RegistrationResult result) {
        final JsonObject body = request.body();
        final String tenantId = body.getString(MessageHelper.APP_PROPERTY_TENANT_ID);
        final String deviceId = body.getString(MessageHelper.APP_PROPERTY_DEVICE_ID);

        request.reply(RegistrationConstants.getReply(tenantId, deviceId, result));
    }

}
