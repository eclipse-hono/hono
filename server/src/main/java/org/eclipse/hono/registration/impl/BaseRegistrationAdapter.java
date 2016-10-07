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

import java.net.HttpURLConnection;

import org.eclipse.hono.registration.RegistrationAdapter;
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
 * Base class for implementing {@code RegistrationAdapter}s.
 * <p>
 * In particular, this base class provides support for parsing incoming AMQP 1.0
 * messages and route them to specific methods corresponding to the <em>action</em>
 * indicated in the message.
 */
public abstract class BaseRegistrationAdapter extends AbstractVerticle implements RegistrationAdapter {

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

    @Override
    public final void processRegistrationMessage(final Message<JsonObject> regMsg) {

        final JsonObject body = regMsg.body();
        final String tenantId = body.getString(MessageHelper.APP_PROPERTY_TENANT_ID);
        final String deviceId = body.getString(MessageHelper.APP_PROPERTY_DEVICE_ID);
        final String key = body.getString(RegistrationConstants.APP_PROPERTY_KEY);
        final String action = body.getString(RegistrationConstants.APP_PROPERTY_ACTION);
        final JsonObject payload = body.getJsonObject(RegistrationConstants.FIELD_PAYLOAD, new JsonObject());

        switch (action) {
        case ACTION_GET:
            LOG.debug("retrieving device [{}] of tenant [{}]", deviceId, tenantId);
            reply(regMsg, getDevice(tenantId, deviceId));
            break;
        case ACTION_FIND:
            LOG.debug("looking up device [key: {}, value: {}] of tenant [{}]", key, deviceId, tenantId);
            reply(regMsg, findDevice(tenantId, key, deviceId));
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

    /**
     * Gets device registration data by device ID.
     * 
     * @param tenantId The tenant the device belongs to.
     * @param deviceId The ID of the device to get registration data for.
     * @return The result of the operation. If a device with the given ID is registered for the tenant,
     *         the <em>status</em> will be {@link HttpURLConnection#HTTP_OK} and the <em>payload</em> will
     *         contain the keys registered for the device.
     *         Otherwise the status will be {@link HttpURLConnection#HTTP_NOT_FOUND}.
     */
    public abstract RegistrationResult getDevice(final String tenantId, final String deviceId);

    /**
     * Finds device registration data by a key registered for the device.
     * 
     * @param tenantId The tenant the device belongs to.
     * @param key The name of the key to look up the device registration by.
     * @param value The value for the key to match on.
     * @return The result of the operation. If a device with the given key/value is registered for the tenant,
     *         the <em>status</em> will be {@link HttpURLConnection#HTTP_OK} and the <em>payload</em> will
     *         contain the keys registered for the device.
     *         Otherwise the status will be {@link HttpURLConnection#HTTP_NOT_FOUND}.
     */
    public abstract RegistrationResult findDevice(final String tenantId, final String key, final String value);

    /**
     * Registers a device.
     * 
     * @param tenantId The tenant the device belongs to.
     * @param deviceId The ID the device should be registered under.
     * @param otherKeys A map containing additional keys and values that the device can be identified by (within the tenant).
     * @return The result of the operation. If a device with the given ID does not yet exist for the tenant,
     *         the <em>status</em> will be {@link HttpURLConnection#HTTP_CREATED}.
     *         Otherwise the status will be {@link HttpURLConnection#HTTP_CONFLICT}.
     */
    public abstract RegistrationResult addDevice(final String tenantId, final String deviceId, final JsonObject otherKeys);

    /**
     * Updates device registration data.
     * 
     * @param tenantId The tenant the device belongs to.
     * @param deviceId The ID of the device to update the registration for.
     * @param otherKeys A map containing additional keys and values that the device can be identified by (within the tenant).
     *                  The keys provided in this parameter will completely replace the former keys registered for the device.
     * @return The result of the operation. If a device with the given ID exists for the tenant,
     *         the <em>status</em> will be {@link HttpURLConnection#HTTP_OK} and the <em>payload</em> will contain the
     *         keys that had originally been registered for the device.
     *         Otherwise the status will be {@link HttpURLConnection#HTTP_NOT_FOUND}.
     */
    public abstract RegistrationResult updateDevice(final String tenantId, final String deviceId, final JsonObject otherKeys);

    /**
     * Removes a device.
     * 
     * @param tenantId The tenant the device belongs to.
     * @param deviceId The ID of the device to remove.
     * @return The result of the operation. If the device has been removed, the <em>status</em> will
     *         be {@link HttpURLConnection#HTTP_OK} and the <em>payload</em> will contain the keys
     *         that had been registered for the removed device.
     *         Otherwise the status will be {@link HttpURLConnection#HTTP_NOT_FOUND}.
     */
    public abstract RegistrationResult removeDevice(final String tenantId, final String deviceId);

    protected final void reply(final Message<JsonObject> request, final RegistrationResult result) {
        final JsonObject body = request.body();
        final String tenantId = body.getString(MessageHelper.APP_PROPERTY_TENANT_ID);
        final String deviceId = body.getString(MessageHelper.APP_PROPERTY_DEVICE_ID);

        request.reply(RegistrationConstants.getReply(tenantId, deviceId, result));
    }

}
