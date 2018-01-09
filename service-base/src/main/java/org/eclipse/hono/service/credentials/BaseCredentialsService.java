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

import java.net.HttpURLConnection;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Objects;

import org.eclipse.hono.util.ConfigurationSupportingVerticle;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.CredentialsResult;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RequestResponseApiConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

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
        credentialsConsumer = vertx.eventBus().consumer(CredentialsConstants.EVENT_BUS_ADDRESS_CREDENTIALS_IN);
        credentialsConsumer.handler(this::processCredentialsMessage);
        log.info("listening on event bus [address: {}] for incoming credentials messages",
                CredentialsConstants.EVENT_BUS_ADDRESS_CREDENTIALS_IN);
    }

    /**
     * Unregisters the credentials message consumer from the Vert.x event bus and then invokes {@link #doStop(Future)}.
     *
     * @param stopFuture the future to invoke once shutdown is complete.
     */
    @Override
    public final void stop(final Future<Void> stopFuture) {
        log.info("unregistering event bus listener [address: {}]", CredentialsConstants.EVENT_BUS_ADDRESS_CREDENTIALS_IN);
        credentialsConsumer.unregister();
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
            log.debug("credentials request does not contain body");
            reply(regMsg, CredentialsResult.from(HttpURLConnection.HTTP_BAD_REQUEST));
            return;
        }

        log.trace("credentials request message: {}", body.encodePrettily());

        final String tenantId = body.getString(CredentialsConstants.FIELD_TENANT_ID);
        final String subject = body.getString(MessageHelper.SYS_PROPERTY_SUBJECT);
        final JsonObject payload = getRequestPayload(body);

        if (tenantId == null) {
            log.debug("credentials request does not contain mandatory property [{}]", CredentialsConstants.FIELD_TENANT_ID);
            reply(regMsg, CredentialsResult.from(HttpURLConnection.HTTP_BAD_REQUEST));
            return;
        } else if (subject == null) {
            log.debug("credentials request does not contain mandatory property [{}]", MessageHelper.SYS_PROPERTY_SUBJECT);
            reply(regMsg, CredentialsResult.from(HttpURLConnection.HTTP_BAD_REQUEST));
            return;
        } else if (payload == null) {
            log.debug("credentials request contains invalid or no payload at all (expected JSON)");
            reply(regMsg, CredentialsResult.from(HttpURLConnection.HTTP_BAD_REQUEST));
            return;
        }

        switch (subject) {
            case CredentialsConstants.OPERATION_GET:
                processCredentialsMessageGetOperation(regMsg, tenantId, payload);
                break;
            case CredentialsConstants.OPERATION_ADD:
                processCredentialsMessageAddOperation(regMsg, tenantId, payload);
                break;
            case CredentialsConstants.OPERATION_UPDATE:
                processCredentialsMessageUpdateOperation(regMsg, tenantId, payload);
                break;
            case CredentialsConstants.OPERATION_REMOVE:
                processCredentialsMessageRemoveOperation(regMsg, tenantId, payload);
                break;
            default:
                log.debug("operation [{}] not supported", subject);
                reply(regMsg, CredentialsResult.from(HttpURLConnection.HTTP_BAD_REQUEST));
        }
    }

    private void processCredentialsMessageGetOperation(final Message<JsonObject> regMsg, final String tenantId, final JsonObject payload) {

        final String type = getTypesafeValueForField(payload, CredentialsConstants.FIELD_TYPE, String.class);
        if (type == null) {
            log.debug("get credentials request does not contain required parameter [{}]", CredentialsConstants.FIELD_TYPE);
            reply(regMsg, CredentialsResult.from(HttpURLConnection.HTTP_BAD_REQUEST));
            return;
        }

        final String authId = getTypesafeValueForField(payload, CredentialsConstants.FIELD_AUTH_ID, String.class);
        final String deviceId = getTypesafeValueForField(payload, CredentialsConstants.FIELD_DEVICE_ID, String.class);


        if (authId != null && deviceId == null) {
            log.debug("getting credentials [tenant: {}, type: {}, auth-id: {}]", tenantId, type, authId);
            get(tenantId, type, authId, result -> reply(regMsg, result));
        } else if (deviceId != null && authId == null) {
            log.debug("getting credentials for device [tenant: {}, device-id: {}]", tenantId, deviceId);
            getAll(tenantId, deviceId, result -> reply(regMsg, result));
        } else {
            log.debug("get credentials request contains invalid search criteria [type: {}, device-id: {}, auth-id: {}]",
                    type, deviceId, authId);
            reply(regMsg, CredentialsResult.from(HttpURLConnection.HTTP_BAD_REQUEST));
        }
    }

    private void processCredentialsMessageAddOperation(final Message<JsonObject> regMsg, final String tenantId, final JsonObject payload) {
        if (!isValidCredentialsObject(payload)) {
            reply(regMsg, CredentialsResult.from(HttpURLConnection.HTTP_BAD_REQUEST));
            return;
        }
        add(tenantId, payload, result -> reply(regMsg, result));
    }

    private void processCredentialsMessageUpdateOperation(final Message<JsonObject> regMsg, final String tenantId, final JsonObject payload) {

        if (!isValidCredentialsObject(payload)) {
            reply(regMsg, CredentialsResult.from(HttpURLConnection.HTTP_BAD_REQUEST));
            return;
        }
        update(tenantId, payload, result -> reply(regMsg, result)); 
    }

    private void processCredentialsMessageRemoveOperation(final Message<JsonObject> regMsg, final String tenantId, final JsonObject payload) {

        final String deviceId = getTypesafeValueForField(payload, CredentialsConstants.FIELD_DEVICE_ID, String.class);
        final String type = getTypesafeValueForField(payload, CredentialsConstants.FIELD_TYPE, String.class);
        final String authId = getTypesafeValueForField(payload, CredentialsConstants.FIELD_AUTH_ID, String.class);

        // there exist several valid combinations of parameters

        if (type == null) {
            log.debug("remove credentials request does not contain mandatory type parameter");
            reply(regMsg, CredentialsResult.from(HttpURLConnection.HTTP_BAD_REQUEST));
        } else if (!type.equals(CredentialsConstants.SPECIFIER_WILDCARD) && authId != null) {
            // delete a single credentials instance
            log.debug("removing specific credentials [tenant: {}, type: {}, auth-id: {}]", tenantId, type, authId);
            remove(tenantId, type, authId, result -> reply(regMsg, result));
        } else if (deviceId != null && type.equals(CredentialsConstants.SPECIFIER_WILDCARD)) {
            // delete all credentials for device
            log.debug("removing all credentials for device [tenant: {}, device-id: {}]", tenantId, deviceId);
            removeAll(tenantId, deviceId, result -> reply(regMsg, result));
        } else {
            log.debug("remove credentials request contains invalid search criteria [type: {}, device-id: {}, auth-id: {}]",
                    type, deviceId, authId);
            reply(regMsg, CredentialsResult.from(HttpURLConnection.HTTP_BAD_REQUEST));
        }
    }

    /**
     * {@inheritDoc}
     * 
     * This default implementation simply returns an empty result with status code 501 (Not Implemented).
     * Subclasses should override this method in order to provide a reasonable implementation.
     */
    @Override
    public void add(final String tenantId, final JsonObject otherKeys, final Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {
        handleUnimplementedOperation(resultHandler);
    }

    /**
     * {@inheritDoc}
     * 
     * This default implementation simply returns an empty result with status code 501 (Not Implemented).
     * Subclasses should override this method in order to provide a reasonable implementation.
     */
    @Override
    public void get(final String tenantId, final String type, final String authId, final Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {
        handleUnimplementedOperation(resultHandler);
    }

    /**
     * {@inheritDoc}
     * 
     * This default implementation simply returns an empty result with status code 501 (Not Implemented).
     * Subclasses should override this method in order to provide a reasonable implementation.
     */
    @Override
    public void getAll(final String tenantId, final String deviceId, final Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {
        handleUnimplementedOperation(resultHandler);
    }

    /**
     * {@inheritDoc}
     * 
     * This default implementation simply returns an empty result with status code 501 (Not Implemented).
     * Subclasses should override this method in order to provide a reasonable implementation.
     */
    @Override
    public void update(final String tenantId, final JsonObject otherKeys, final Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {
        handleUnimplementedOperation(resultHandler);
    }

    /**
     * {@inheritDoc}
     * 
     * This default implementation simply returns an empty result with status code 501 (Not Implemented).
     * Subclasses should override this method in order to provide a reasonable implementation.
     */
    @Override
    public void remove(final String tenantId, final String type, final String authId, final Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {
        handleUnimplementedOperation(resultHandler);
    }

    /**
     * {@inheritDoc}
     * 
     * This default implementation simply returns an empty result with status code 501 (Not Implemented).
     * Subclasses should override this method in order to provide a reasonable implementation.
     */
    @Override
    public void removeAll(final String tenantId, final String deviceId, final Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {
        handleUnimplementedOperation(resultHandler);
    }

    private void handleUnimplementedOperation(final Handler<AsyncResult<CredentialsResult<JsonObject>>> resultHandler) {
        resultHandler.handle(Future.succeededFuture(CredentialsResult.from(HttpURLConnection.HTTP_NOT_IMPLEMENTED)));
    }

    private boolean isValidCredentialsObject(final JsonObject credentials) {
        return containsStringValueForField(credentials, CredentialsConstants.FIELD_DEVICE_ID)
                && containsStringValueForField(credentials, CredentialsConstants.FIELD_TYPE)
                && containsStringValueForField(credentials, CredentialsConstants.FIELD_AUTH_ID)
                && containsValidSecretValue(credentials);
    }

    private boolean containsValidSecretValue(final JsonObject credentials) {

        final Object obj = credentials.getValue(CredentialsConstants.FIELD_SECRETS);

        if (JsonArray.class.isInstance(obj)) {

            JsonArray secrets = (JsonArray) obj;
            if (secrets.isEmpty()) {

                log.debug("credentials request payload contains no secrets");
                return false;

            } else {

                for (int i = 0; i < secrets.size(); i++) {
                    JsonObject currentSecret = secrets.getJsonObject(i);
                    if (!containsValidTimestampIfPresentForField(currentSecret, CredentialsConstants.FIELD_SECRETS_NOT_BEFORE)
                            || !containsValidTimestampIfPresentForField(currentSecret, CredentialsConstants.FIELD_SECRETS_NOT_AFTER)) {
                        log.debug("credentials request contains invalid timestamp values in payload");
                        return false;
                    }
                }

                return true;
            }

        } else {

            log.debug("credentials request does not contain a {} array in payload - not supported", CredentialsConstants.FIELD_SECRETS);
            return false;

        }
    }

    private boolean containsStringValueForField(final JsonObject payload, final String field) {

        final Object value = payload.getValue(field);
        if (StringUtils.isEmpty(value)) {
            log.debug("credentials request payload does not contain required string typed field [{}]", field);
            return false;
        }

        return true;
    }

    /**
     * Gets a property value of a given type from a JSON object.
     * 
     * @param payload The object to get the property from.
     * @param field The name of the property.
     * @param type The expected type of the property.
     * @param <T> The type of the field.
     * @return The property value or {@code null} if no such property exists or is not of the expected type.
     * @throws NullPointerException if any of the params is {@code null}.
     */
    @SuppressWarnings({ "unchecked", "hiding" })
    protected final <T> T getTypesafeValueForField(final JsonObject payload, final String field, final Class<T> type) {

        Objects.requireNonNull(payload);
        Objects.requireNonNull(field);
        Objects.requireNonNull(type);

        final Object value = payload.getValue(field);
        if (type.isInstance(value)) {
            return (T) value;
        } else {
            return null;
        }
    }

    private boolean containsValidTimestampIfPresentForField(final JsonObject payload, final String field) {

        final Object value = payload.getValue(field);
        if (value == null) {
            return true;
        } else if (String.class.isInstance(value)) {
            return isValidTimestamp((String) value);
        } else {
            return false;
        }
    }

    private boolean isValidTimestamp(final String dateTime) {

        try {
            final DateTimeFormatter timeFormatter = DateTimeFormatter.ISO_DATE_TIME;
            timeFormatter.parse(dateTime);

            return true;
        } catch (DateTimeParseException e) {
            log.debug("credentials request did contain invalid timestamp in payload");
            return false;
        }
    }

    private void reply(final Message<JsonObject> request, final AsyncResult<CredentialsResult<JsonObject>> result) {

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
    protected final void reply(final Message<JsonObject> request, final CredentialsResult<JsonObject> result) {

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
        } else {
            JsonObject payload = null;
            Object payloadObject = request.getValue(CredentialsConstants.FIELD_PAYLOAD);
            if (JsonObject.class.isInstance(payloadObject)) {
                payload = (JsonObject) payloadObject;
                if (!payload.containsKey(CredentialsConstants.FIELD_ENABLED)) {
                    log.debug("adding 'enabled' key to payload");
                    payload.put(CredentialsConstants.FIELD_ENABLED, Boolean.TRUE);
                }
            }
            return payload;
        }
    }

    /**
     * Wraps a given device ID and credentials data into a JSON structure suitable
     * to be returned to clients as the result of a credentials operation.
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
                put(CredentialsConstants.FIELD_DEVICE_ID, deviceId).
                put(CredentialsConstants.FIELD_TYPE, type).
                put(CredentialsConstants.FIELD_AUTH_ID, authId).
                put(CredentialsConstants.FIELD_ENABLED, enabled).
                put(CredentialsConstants.FIELD_SECRETS, secrets);
    }
}
