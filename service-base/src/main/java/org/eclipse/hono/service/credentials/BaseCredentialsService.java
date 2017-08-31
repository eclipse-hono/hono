/**
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */
package org.eclipse.hono.service.credentials;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.eclipse.hono.util.ConfigurationSupportingVerticle;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsResult;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RequestResponseApiConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.HttpURLConnection;

import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static org.eclipse.hono.util.CredentialsConstants.*;

/**
 * Base class for implementing {@code CredentialsService}s.
 * <p>
 * In particular, this base class provides support for parsing credentials request messages
 * received via the event bus and route them to specific methods corresponding to the <em>subject</em>
 * indicated in the message.
 * 
 * @param <T> The type of configuration class this service supports.
 */
public abstract class BaseCredentialsService<T> extends ConfigurationSupportingVerticle<T> implements CredentialsService {

    /**
     * A logger to be shared by subclasses.
     */
    protected final Logger log = LoggerFactory.getLogger(getClass());
    private MessageConsumer<JsonObject> credentialsConsumer;

    /**
     * Registers a Vert.x event consumer for address {@link CredentialsConstants#EVENT_BUS_ADDRESS_CREDENTIALS_IN}
     * and then invokes {@link #doStart(Future)}.
     *
     * @param startFuture future to invoke once start up is complete.
     */
    @Override
    public final void start(final Future<Void> startFuture) throws Exception {
        credentialsConsumer();
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

    private void credentialsConsumer() {
        credentialsConsumer = vertx.eventBus().consumer(EVENT_BUS_ADDRESS_CREDENTIALS_IN);
        credentialsConsumer.handler(this::processCredentialsMessage);
        log.info("listening on event bus [address: {}] for incoming credentials messages",
                EVENT_BUS_ADDRESS_CREDENTIALS_IN);
    }

    /**
     * Unregisters the credentials message consumer from the Vert.x event bus and then invokes {@link #doStop(Future)}.
     *
     * @param stopFuture the future to invoke once shutdown is complete.
     */
    @Override
    public final void stop(final Future<Void> stopFuture) {
        credentialsConsumer.unregister();
        log.info("unregistered credentials data consumer from event bus");
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
     * Processes a credentials request message received via the Vertx event bus.
     * 
     * @param regMsg The message.
     */
    public final void processCredentialsMessage(final Message<JsonObject> regMsg) {

        final JsonObject body = regMsg.body();
        if (body == null) {
            log.debug("credentials request did not contain body - not supported");
            reply(regMsg, CredentialsResult.from(HTTP_BAD_REQUEST));
            return;
        }

        final String tenantId = body.getString(RequestResponseApiConstants.FIELD_TENANT_ID);
        final String subject = body.getString(MessageHelper.SYS_PROPERTY_SUBJECT);
        final JsonObject payload = getRequestPayload(body);

        if (tenantId == null) {
            log.debug("credentials request did not contain tenantId - not supported");
            reply(regMsg, CredentialsResult.from(HTTP_BAD_REQUEST));
            return;
        } else if (subject == null) {
            log.debug("credentials request did not contain subject - not supported");
            reply(regMsg, CredentialsResult.from(HTTP_BAD_REQUEST));
            return;
        } else if (payload == null) {
            log.debug("credentials request contained invalid or no payload at all (expected json format) - not supported");
            reply(regMsg, CredentialsResult.from(HTTP_BAD_REQUEST));
            return;
        }

        switch (subject) {
            case OPERATION_GET:
                processCredentialsMessageGetOperation(regMsg, tenantId, payload);
                break;
            default:
                log.debug("operation [{}] not supported", subject);
                reply(regMsg, CredentialsResult.from(HTTP_BAD_REQUEST));
        }
    }

    private void processCredentialsMessageGetOperation(final Message<JsonObject> regMsg, final String tenantId, final JsonObject payload) {
        final String type = payload.getString(FIELD_TYPE);
        if (type == null) {
            log.debug("credentials get request did not contain type in payload - not supported");
            reply(regMsg, CredentialsResult.from(HTTP_BAD_REQUEST));
            return;
        }

        final String authId = payload.getString(FIELD_AUTH_ID);
        if (authId == null) {
            log.debug("credentials get request did not contain authId in payload - not supported");
            reply(regMsg, CredentialsResult.from(HTTP_BAD_REQUEST));
            return;
        }
        log.debug("getting credentials [{}:{}] of tenant [{}]", type, authId, tenantId);
        getCredentials(tenantId, type, authId, result -> reply(regMsg, result));
    }


    private void reply(final Message<JsonObject> request, final AsyncResult<CredentialsResult> result) {

        if (result.succeeded()) {
            reply(request, result.result());
        } else {
            request.fail(HttpURLConnection.HTTP_INTERNAL_ERROR, "cannot process credentials request");
        }
    }

    /**
     * Sends a response to a credentials request over the Vertx event bus.
     * 
     * @param request The message to respond to.
     * @param result The credentials result that should be conveyed in the response.
     */
    protected final void reply(final Message<JsonObject> request, final CredentialsResult result) {

        final JsonObject body = request.body();
        final String tenantId = body.getString(RequestResponseApiConstants.FIELD_TENANT_ID);
        final String deviceId = body.getString(RequestResponseApiConstants.FIELD_DEVICE_ID);

        request.reply(CredentialsConstants.getServiceReplyAsJson(tenantId, deviceId, result));
    }

    /**
     * Gets the payload from the request and ensures that the enabled flag is contained.
     *
     * @param request The request from which the payload is tried to be extracted. Must not be null.
     * @return The payload as JsonObject (if found). Null otherwise.
     */
    private JsonObject getRequestPayload(final JsonObject request) {

        if (request == null) {
            return null;
        }


        Object payloadObject = request.getValue(CredentialsConstants.FIELD_PAYLOAD);
        if (!(payloadObject instanceof JsonObject)) {
            return null;
        }
        final JsonObject payload = (JsonObject) payloadObject;


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
     * @param deviceId The identifier of the device.
     * @param type The type of credentials returned.
     * @param authId The authentication identifier the device uses.
     * @param enabled {@code true} if the returned credentials may be used to authenticate.
     * @param secrets The secrets that need to be used in conjunction with the authentication identifier.
     * @return The JSON structure.
     */
    protected final static JsonObject getResultPayload(final String deviceId, final  String type, final String authId, final boolean enabled, final JsonArray secrets) {
        return new JsonObject().
                put(FIELD_DEVICE_ID, deviceId).
                put(FIELD_TYPE, type).
                put(FIELD_AUTH_ID, authId).
                put(FIELD_ENABLED, enabled).
                put(FIELD_SECRETS, secrets);
    }
}
