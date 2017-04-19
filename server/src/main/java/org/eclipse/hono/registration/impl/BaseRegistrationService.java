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
package org.eclipse.hono.registration.impl;

import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static org.eclipse.hono.util.RegistrationConstants.*;

import java.net.HttpURLConnection;

import org.eclipse.hono.registration.RegistrationService;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.RegistrationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
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

    /**
     * A logger to be shared by subclasses.
     */
    protected final Logger log = LoggerFactory.getLogger(getClass());
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
        log.info("listening on event bus [address: {}] for incoming registration messages",
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
        log.info("unregistered registration data consumer from event bus");
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

    /**
     * Processes a registration request message received via the Vertx event bus.
     * 
     * @param regMsg The message.
     */
    public final void processRegistrationMessage(final Message<JsonObject> regMsg) {

        try {
            final JsonObject body = regMsg.body();
            final String tenantId = body.getString(MessageHelper.APP_PROPERTY_TENANT_ID);
            final String deviceId = body.getString(MessageHelper.APP_PROPERTY_DEVICE_ID);
            final String action = body.getString(RegistrationConstants.APP_PROPERTY_ACTION);

            switch (action) {
            case ACTION_GET:
                log.debug("retrieving device [{}] of tenant [{}]", deviceId, tenantId);
                getDevice(tenantId, deviceId, result -> reply(regMsg, result));
                break;
            case ACTION_FIND:
                final String key = body.getString(RegistrationConstants.APP_PROPERTY_KEY);
                log.debug("looking up device [key: {}, value: {}] of tenant [{}]", key, deviceId, tenantId);
                findDevice(tenantId, key, deviceId, result -> reply(regMsg, result));
                break;
            case ACTION_REGISTER:
                JsonObject payload = getRequestPayload(body);
                log.debug("registering device [{}] of tenant [{}] with data {}", deviceId, tenantId,
                        payload != null ? payload.encode() : null);
                addDevice(tenantId, deviceId, payload, result -> reply(regMsg, result));
                break;
            case ACTION_UPDATE:
                payload = getRequestPayload(body);
                log.debug("updating registration for device [{}] of tenant [{}] with data {}", deviceId, tenantId,
                        payload != null ? payload.encode() : null);
                updateDevice(tenantId, deviceId, payload, result -> reply(regMsg, result));
                break;
            case ACTION_DEREGISTER:
                log.debug("deregistering device [{}] of tenant [{}]", deviceId, tenantId);
                removeDevice(tenantId, deviceId, result -> reply(regMsg, result));
                break;
            case ACTION_ENABLED:
                log.debug("checking if device [{}] of tenant [{}] is enabled", deviceId, tenantId);
                isEnabled(tenantId, deviceId, result -> reply(regMsg, result));
                break;
            default:
                log.info("action [{}] not supported", action);
                reply(regMsg, RegistrationResult.from(HTTP_BAD_REQUEST));
            }
        } catch (ClassCastException e) {
            log.debug("malformed request message");
            reply(regMsg, RegistrationResult.from(HTTP_BAD_REQUEST));
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * This default implementation simply invokes {@link RegistrationService#getDevice(String, String, Handler)}
     * with the parameters passed in to this method.
     * <p>
     * Subclasses should override this method in order to use a more efficient way of determining the device's status.
     */
    @Override
    public void isEnabled(final String tenantId, final String deviceId, Handler<AsyncResult<RegistrationResult>> resultHandler) {
        getDevice(tenantId, deviceId, getAttempt -> {
            if (getAttempt.succeeded()) {
                RegistrationResult result = getAttempt.result();
                if (result.getStatus() == HttpURLConnection.HTTP_OK) {
                    resultHandler.handle(Future.succeededFuture(RegistrationResult.from(result.getStatus(), result.getPayload().getJsonObject(RegistrationConstants.FIELD_DATA))));
                } else {
                    resultHandler.handle(getAttempt);
                }
            } else {
                resultHandler.handle(getAttempt);
            }
        });
    }

    private void reply(final Message<JsonObject> request, final AsyncResult<RegistrationResult> result) {

        if (result.succeeded()) {
            reply(request, result.result());
        } else {
            request.fail(HttpURLConnection.HTTP_INTERNAL_ERROR, "cannot process registration request");
        }
    }

    /**
     * Sends a response to a registration request over the Vertx event bus.
     * 
     * @param request The message to respond to.
     * @param result The registration result that should be conveyed in the response.
     */
    protected final void reply(final Message<JsonObject> request, final RegistrationResult result) {

        final JsonObject body = request.body();
        final String tenantId = body.getString(MessageHelper.APP_PROPERTY_TENANT_ID);
        final String deviceId = body.getString(MessageHelper.APP_PROPERTY_DEVICE_ID);

        request.reply(RegistrationConstants.getReply(tenantId, deviceId, result));
    }

    private final JsonObject getRequestPayload(final JsonObject request) {

        final JsonObject payload = request.getJsonObject(RegistrationConstants.FIELD_PAYLOAD, new JsonObject());
        Boolean enabled = payload.getBoolean(FIELD_ENABLED);
        if (enabled == null) {
            log.debug("adding 'enabled' key to payload");
            payload.put(FIELD_ENABLED, Boolean.TRUE);
        }
        return payload;
    }

    /**
     * Wraps a given device ID and registration data into a JSON structure suitable
     * to be returned to clients as the result of a registration operation.
     * 
     * @param id The device ID.
     * @param data The registration data.
     * @return The JSON structure.
     */
    protected final static JsonObject getResultPayload(final String id, final JsonObject data) {

        return new JsonObject().put(FIELD_HONO_ID, id).put(FIELD_DATA, data);
    }

}
