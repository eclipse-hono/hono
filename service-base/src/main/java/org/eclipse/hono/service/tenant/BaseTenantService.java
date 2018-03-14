/**
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 1.0 which is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * SPDX-License-Identifier: EPL-1.0
 */

package org.eclipse.hono.service.tenant;

import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;

import java.net.HttpURLConnection;

import org.eclipse.hono.util.ConfigurationSupportingVerticle;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.TenantConstants;
import org.eclipse.hono.util.TenantResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Base class for implementing {@code TenantService}s.
 * <p>
 * In particular, this base class provides support for parsing tenant CRUD request messages received via the event bus
 * and route them to specific methods corresponding to the <em>action</em> indicated in the message.
 *
 * @param <T> The type of configuration properties this service requires.
 */
public abstract class BaseTenantService<T> extends ConfigurationSupportingVerticle<T> implements TenantService {

    /**
     * A logger to be shared by subclasses.
     */
    protected final Logger log = LoggerFactory.getLogger(getClass());
    private MessageConsumer<JsonObject> tenantConsumer;


    /**
     * Starts up this service.
     * <ol>
     * <li>Registers an event bus consumer for address {@link TenantConstants#EVENT_BUS_ADDRESS_TENANT_IN} listening for
     * tenant CRUD requests.</li>
     * <li>Invokes {@link #doStart(Future)}.</li>
     * </ol>
     *
     * @param startFuture The future to complete on successful startup.
     */
    public final void start(final Future<Void> startFuture) {
        registerConsumer();
        doStart(startFuture);
    }

    /**
     * Subclasses should override this method to perform any work required on start-up of this verticle.
     * <p>
     * This default implementation performs nothing except for completing the Future.
     * </p>
     * <p>
     * This method is invoked by {@link #start()} as part of the verticle deployment process.
     * </p>
     *
     * @param startFuture future to invoke once start up is complete.
     */
    protected void doStart(final Future<Void> startFuture) {
        // should be overridden by subclasses
        startFuture.complete();
    }

    private void registerConsumer() {
        tenantConsumer = vertx.eventBus().consumer(TenantConstants.EVENT_BUS_ADDRESS_TENANT_IN);
        tenantConsumer.handler(this::processTenantMessage);
        log.info("listening on event bus [address: {}] for Tenant API requests",
                TenantConstants.EVENT_BUS_ADDRESS_TENANT_IN);
    }

    /**
     * Unregisters the registration message consumer from the Vert.x event bus and then invokes {@link #doStop(Future)}.
     *
     * @param stopFuture the future to invoke once shutdown is complete.
     */
    public final void stop(final Future<Void> stopFuture) {
        tenantConsumer.unregister();
        log.info("unregistered Tenant API request consumer from event bus");
        doStop(stopFuture);
    }

    /**
     * Subclasses should override this method to perform any work required before shutting down this verticle.
     * <p>
     * This default implementation performs nothing except for completing the Future.
     * </p>
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
     * Processes a tenant request message received via the vert.x event bus.
     * <p>
     * This method validates the request payload against the Tenant API specification
     * before invoking the corresponding {@code TenantService} methods.
     * 
     * @param tenantMsg The request message.
     * @throws NullPointerException If the request message is {@code null}.
     */
    public final void processTenantMessage(final Message<JsonObject> tenantMsg) {
        try {
            final JsonObject body = tenantMsg.body();
            final String subject = body.getString(MessageHelper.SYS_PROPERTY_SUBJECT);

            switch (TenantConstants.TenantAction.from(subject)) {
            case get:
                processGetRequest(tenantMsg);
                break;
            case add:
                processAddRequest(tenantMsg);
                break;
            case update:
                processUpdateRequest(tenantMsg);
                break;
            case remove:
                processRemoveRequest(tenantMsg);
                break;
            default:
                processCustomTenantMessage(tenantMsg, subject);
                break;
            }
        } catch (final ClassCastException e) {
            log.debug("malformed request message [{}]", e.getMessage());
            reply(tenantMsg, TenantResult.from(HTTP_BAD_REQUEST));
        }
    }

    private void processGetRequest(final Message<JsonObject> request) {

        final JsonObject body = request.body();
        final String tenantId = body.getString(TenantConstants.FIELD_TENANT_ID);
        if (tenantId == null) {
            log.debug("request does not contain mandatory property [{}]",
                    TenantConstants.FIELD_TENANT_ID);
            reply(request, TenantResult.from(HttpURLConnection.HTTP_BAD_REQUEST));
            return;
        } else {
            log.debug("retrieving tenant [{}]", tenantId);
            get(tenantId, response -> reply(request, response));
        }
    }

    private void processAddRequest(final Message<JsonObject> request) {

        final JsonObject body = request.body();
        final String tenantId = body.getString(TenantConstants.FIELD_TENANT_ID);
        final JsonObject payload = body.getJsonObject(TenantConstants.FIELD_PAYLOAD, new JsonObject());

        if (tenantId == null) {
            log.debug("request does not contain mandatory property [{}]",
                    TenantConstants.FIELD_TENANT_ID);
            reply(request, TenantResult.from(HttpURLConnection.HTTP_BAD_REQUEST));
            return;
        } else if (isValidRequestPayload(payload)) {
            log.debug("creating tenant [{}]", tenantId);
            addNotPresentFieldsWithDefaultValuesForTenant(payload);
            add(tenantId, payload, response -> reply(request, response));
        } else {
            log.debug("request contains malformed payload");
            reply(request, TenantResult.from(HttpURLConnection.HTTP_BAD_REQUEST));
        }
    }

    private void processUpdateRequest(final Message<JsonObject> request) {

        final JsonObject body = request.body();
        final String tenantId = body.getString(TenantConstants.FIELD_TENANT_ID);
        final JsonObject payload = body.getJsonObject(TenantConstants.FIELD_PAYLOAD, new JsonObject());

        if (tenantId == null) {
            log.debug("request does not contain mandatory property [{}]",
                    TenantConstants.FIELD_TENANT_ID);
            reply(request, TenantResult.from(HttpURLConnection.HTTP_BAD_REQUEST));
            return;
        } else if (isValidRequestPayload(payload)) {
            log.debug("updating tenant [{}]", tenantId);
            addNotPresentFieldsWithDefaultValuesForTenant(payload);
            update(tenantId, payload, response -> reply(request, response));
        } else {
            log.debug("request contains malformed payload");
            reply(request, TenantResult.from(HttpURLConnection.HTTP_BAD_REQUEST));
        }
    }

    private void processRemoveRequest(final Message<JsonObject> request) {

        final JsonObject body = request.body();
        final String tenantId = body.getString(TenantConstants.FIELD_TENANT_ID);

        if (tenantId == null) {
            log.debug("request does not contain mandatory property [{}]",
                    TenantConstants.FIELD_TENANT_ID);
            reply(request, TenantResult.from(HttpURLConnection.HTTP_BAD_REQUEST));
            return;
        } else {
            log.debug("deleting tenant [{}]", tenantId);
            remove(tenantId, response -> reply(request, response));
        }
    }

    /**
     * Override the following method to extend TenantService with implementation
     * specific operations. Consequently, once the operation is completed, a {@link TenantResult} must be
     * set as reply to this tenantMsg. Use the {@link #reply(Message, AsyncResult)} for sending
     * the response for the Message. For example, if some concrete action looks like
     * <pre>{@code
     *     void doSomeSpecificAction(final Message<JsonObject> tenantMsg, final Handler<AsyncResult<TenantResult>> resultHandler);
     * }</pre>
     * then the overriding method would look like
     *  <pre>{@code
     *     processCustomTenantMessage(final Message<JsonObject> tenantMsg, String action) {
     *          if(action.equals("expected-action")) {
     *            doSomeSpecificAction(tenantMsg, result -> reply(tenantMsg, result));
     *          } else {
     *             reply(tenantMsg, TenantResult.from(HttpURLConnection.HTTP_BAD_REQUEST));
     *         }
     *       }
     * }</pre>
     *
     * @param tenantMsg target tenant message to be processed.
     * @param action implementation specific action to be executed.
     */
    protected void processCustomTenantMessage(final Message<JsonObject> tenantMsg, String action) {
        log.debug("invalid action in request message [{}]", action);
        reply(tenantMsg, TenantResult.from(HTTP_BAD_REQUEST));
    }

    /**
     * Sends a response to a tenant request over the vert.x event bus.
     *
     * @param request The message to respond to.
     * @param asyncActionResult The outcome of the processed request.
     * @throws NullPointerException If request or tenantResult is null.
     */
    protected final void reply(final Message<JsonObject> request, final AsyncResult<TenantResult<JsonObject>> asyncActionResult) {
        if (asyncActionResult.succeeded()) {
            if (asyncActionResult.result() != null) {
                reply(request, asyncActionResult.result());
            } else {
                log.debug("result is null in reply to tenant request");
                reply(request, TenantResult.from(HTTP_INTERNAL_ERROR));
            }
        } else {
            request.fail(HTTP_INTERNAL_ERROR, "cannot process tenant management request");
        }
    }

    /**
     * Sends a response to a tenant request over the vert.x event bus.
     *
     * @param request The message to respond to.
     * @param tenantResult The tenant result that should be conveyed in the response.
     * @throws NullPointerException If request or tenantResult is null.
     */
    protected final void reply(final Message<JsonObject> request, final TenantResult<JsonObject> tenantResult) {
        final JsonObject body = request.body();
        request.reply(TenantConstants.getServiceReplyAsJson(body.getString(TenantConstants.FIELD_TENANT_ID), tenantResult));
    }

    /**
     * Check the request payload for validity.
     *
     * @param payload The payload to check.
     * @return boolean The result of the check : {@link Boolean#TRUE} if the payload is valid, {@link Boolean#FALSE} otherwise.
     * @throws NullPointerException If the payload is {@code null}.
     */
    private boolean isValidRequestPayload(final JsonObject payload) {

        final Object adaptersObj = payload.getValue(TenantConstants.FIELD_ADAPTERS);
        if (adaptersObj == null) {
            // all adapters enabled with default config
            return true;
        } else if (adaptersObj instanceof JsonArray) {

            final JsonArray adapters = (JsonArray) adaptersObj;
            if (adapters.size() == 0) {
                // if given, adapters config array must not be empty
                return false;
            } else {
                return !adapters.stream()
                        .anyMatch(obj -> {
                            return !(obj instanceof JsonObject) ||
                                    !((JsonObject) obj).containsKey(TenantConstants.FIELD_ADAPTERS_TYPE);
                        });
            }
        } else {
            // malformed payload
            return false;
        }
    }

    /**
     * Add default values for optional fields that are not filled in the payload.
     * <p>
     * Payload should be checked for validity first, there is no error handling inside this method anymore.
     * </p>
     *
     * @param checkedPayload The checked payload to add optional fields to.
     * @throws ClassCastException If the {@link TenantConstants#FIELD_ADAPTERS_TYPE} element is not a {@link JsonArray}
     *       or the JsonArray contains elements that are not of type {@link JsonObject}.
     */
    protected final void addNotPresentFieldsWithDefaultValuesForTenant(final JsonObject checkedPayload) {
        if (!checkedPayload.containsKey(TenantConstants.FIELD_ENABLED)) {
            log.trace("adding 'enabled' key to payload");
            checkedPayload.put(TenantConstants.FIELD_ENABLED, Boolean.TRUE);
        }

        final JsonArray adapters = checkedPayload.getJsonArray(TenantConstants.FIELD_ADAPTERS);
        if (adapters != null) {
            adapters.forEach(elem -> addNotPresentFieldsWithDefaultValuesForAdapter((JsonObject) elem));
        }
    }

    private void addNotPresentFieldsWithDefaultValuesForAdapter(final JsonObject adapter) {
        if (!adapter.containsKey(TenantConstants.FIELD_ENABLED)) {
            log.trace("adding 'enabled' key to payload");
            adapter.put(TenantConstants.FIELD_ENABLED, Boolean.TRUE);
        }

        if (!adapter.containsKey(TenantConstants.FIELD_ADAPTERS_DEVICE_AUTHENTICATION_REQUIRED)) {
            log.trace("adding 'device-authentication-required' key to adapter payload");
            adapter.put(TenantConstants.FIELD_ADAPTERS_DEVICE_AUTHENTICATION_REQUIRED, Boolean.TRUE);
        }
    }

    /**
     * Wraps a given tenant ID, its properties data and its adapter configuration data into a JSON structure suitable
     * to be returned to clients as the result of a tenant API operation.
     *
     * @param tenantId The tenant ID.
     * @param data The tenant properties data.
     * @param adapterConfigurations The adapter configurations data for the tenant as JsonArray.
     * @return The JSON structure.
     */
    protected static final JsonObject getResultPayload(final String tenantId, final JsonObject data,
                                                       final JsonArray adapterConfigurations) {
        final JsonObject result = new JsonObject()
                .put(TenantConstants.FIELD_TENANT_ID, tenantId)
                .mergeIn(data);
        if (adapterConfigurations != null) {
            result.put(TenantConstants.FIELD_ADAPTERS, adapterConfigurations);
        }
        return result;
    }

    /**
     * {@inheritDoc}
     *
     * This default implementation simply returns an empty result with status code 501 (Not Implemented).
     * Subclasses should override this method in order to provide a reasonable implementation.
     */
    public void get(final String tenantId, final Handler<AsyncResult<TenantResult<JsonObject>>> resultHandler) {
        handleUnimplementedOperation(resultHandler);
    }

    /**
     * {@inheritDoc}
     *
     * This default implementation simply returns an empty result with status code 501 (Not Implemented).
     * Subclasses should override this method in order to provide a reasonable implementation.
     */
    public void add(final String tenantId, final JsonObject tenantObj, final Handler<AsyncResult<TenantResult<JsonObject>>> resultHandler) {
        handleUnimplementedOperation(resultHandler);
    }

    /**
     * {@inheritDoc}
     *
     * This default implementation simply returns an empty result with status code 501 (Not Implemented).
     * Subclasses should override this method in order to provide a reasonable implementation.
     */
    public void update(final String tenantId, final JsonObject tenantObj, final Handler<AsyncResult<TenantResult<JsonObject>>> resultHandler) {
        handleUnimplementedOperation(resultHandler);
    }

    /**
     * {@inheritDoc}
     *
     * This default implementation simply returns an empty result with status code 501 (Not Implemented).
     * Subclasses should override this method in order to provide a reasonable implementation.
     */
    public void remove(final String tenantId, final Handler<AsyncResult<TenantResult<JsonObject>>> resultHandler) {
        handleUnimplementedOperation(resultHandler);
    }

    private void handleUnimplementedOperation(final Handler<AsyncResult<TenantResult<JsonObject>>> resultHandler) {
        resultHandler.handle(Future.succeededFuture(TenantResult.from(HttpURLConnection.HTTP_NOT_IMPLEMENTED)));
    }

}
