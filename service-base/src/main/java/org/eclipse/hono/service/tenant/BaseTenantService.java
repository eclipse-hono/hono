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
import static org.eclipse.hono.util.TenantConstants.*;

import java.net.HttpURLConnection;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import org.eclipse.hono.util.ConfigurationSupportingVerticle;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RequestResponseApiConstants;
import org.eclipse.hono.util.TenantConstants;
import org.eclipse.hono.util.TenantResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
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
    protected final Logger log = LoggerFactory.getLogger(this.getClass());
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
        this.tenantConsumer();
        this.doStart(startFuture);
    }

    /**
     * Subclasses should override this method to perform any work required on start-up of this verticle.
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

    private void tenantConsumer() {
        this.tenantConsumer = this.vertx.eventBus().consumer(TenantConstants.EVENT_BUS_ADDRESS_TENANT_IN);
        this.tenantConsumer.handler(this::processTenantMessage);
        this.log.info("listening on event bus [address: {}] for incoming tenant CRUD messages",
                TenantConstants.EVENT_BUS_ADDRESS_TENANT_IN);
    }

    /**
     * Unregisters the registration message consumer from the Vert.x event bus and then invokes {@link #doStop(Future)}.
     *
     * @param stopFuture the future to invoke once shutdown is complete.
     */
    public final void stop(final Future<Void> stopFuture) {
        this.tenantConsumer.unregister();
        this.log.info("unregistered tenant data consumer from event bus");
        this.doStop(stopFuture);
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
     * Processes a tenant request message received via the Vertx event bus.
     *
     * @param tenantMsg The message.
     */
    public final void processTenantMessage(final Message<JsonObject> tenantMsg) {
        try {
            final JsonObject body = tenantMsg.body();
            final String tenantId = body.getString(RequestResponseApiConstants.FIELD_TENANT_ID);
            final String operation = body.getString(MessageHelper.SYS_PROPERTY_SUBJECT);

            if (tenantId == null) {
                log.debug("tenant request does not contain mandatory property [{}]",
                        RequestResponseApiConstants.FIELD_TENANT_ID);
                reply(tenantMsg, TenantResult.from(HttpURLConnection.HTTP_BAD_REQUEST));
                return;
            }

            JsonObject payload;

            switch (operation) {
            case ACTION_GET:
                log.debug("retrieving tenant [{}]", tenantId);
                get(tenantId, result -> reply(tenantMsg, result));
                break;
            case ACTION_ADD:
                if (!isValidRequestPayload(body)) {
                    log.debug("tenant request contains invalid structure");
                    reply(tenantMsg, TenantResult.from(HttpURLConnection.HTTP_BAD_REQUEST));
                    break;
                }
                payload = getRequestPayload(body);
                log.debug("creating tenant [{}] with data {}", tenantId, payload != null ? payload.encode() : null);
                add(tenantId, payload, result -> reply(tenantMsg, result));
                break;
            case ACTION_UPDATE:
                payload = getRequestPayload(body);
                log.debug("updating tenant [{}] with data {}", tenantId, payload != null ? payload.encode() : null);
                update(tenantId, payload, result -> reply(tenantMsg, result));
                break;
            case ACTION_REMOVE:
                log.debug("deleting tenant [{}]", tenantId);
                remove(tenantId, result -> reply(tenantMsg, result));
                break;
            default:
                break;
            }
        } catch (final ClassCastException e) {
            log.debug("malformed request message [{}]", e.getMessage());
            reply(tenantMsg, TenantResult.from(HTTP_BAD_REQUEST));
        }
    }

    private void reply(final Message<JsonObject> request, final AsyncResult<TenantResult> result) {
        if (result.succeeded()) {
            this.reply(request, result.result());
        } else {
            request.fail(HTTP_INTERNAL_ERROR, "cannot process tenant management request");
        }
    }

    /**
     * Sends a response to a tenant request over the Vertx event bus.
     *
     * @param request The message to respond to.
     * @param result The tenant result that should be conveyed in the response.
     */
    protected final void reply(final Message<JsonObject> request, final TenantResult result) {
        final JsonObject body = request.body();
        final String tenantId = body.getString(RequestResponseApiConstants.FIELD_TENANT_ID);
        request.reply(TenantConstants.getServiceReplyAsJson(tenantId, result));
    }

    private JsonObject getRequestPayload(final JsonObject request) {
        final JsonObject payload = request.getJsonObject(TenantConstants.FIELD_PAYLOAD, new JsonObject());
        addNotPresentFieldsWithDefaultValuesForTenant(payload);
        return payload;
    }

    private boolean isValidRequestPayload(final JsonObject request) {
        final JsonObject payload = request.getJsonObject(TenantConstants.FIELD_PAYLOAD, new JsonObject());
        final JsonArray adapters = payload.getJsonArray(TenantConstants.FIELD_ADAPTERS);
        if (adapters != null) {
            if (adapters.size() == 0) {
                return false;
            }
            for (int i = 0; i < adapters.size(); i++) {
                final JsonObject adapterDetails = adapters.getJsonObject(i);
                if (adapterDetails == null) {
                    return false;
                }
                // check for mandatory field adapter type
                if (adapterDetails.getString(FIELD_ADAPTERS_TYPE) == null) {
                    return false;
                }
            }
        }
        return true;
    }

    private void addNotPresentFieldsWithDefaultValuesForTenant(final JsonObject payload) {
        if(!payload.containsKey(TenantConstants.FIELD_ENABLED)) {
            log.debug("adding 'enabled' key to payload");
            payload.put(TenantConstants.FIELD_ENABLED, Boolean.TRUE);
        }

        final JsonArray adapters = payload.getJsonArray(TenantConstants.FIELD_ADAPTERS);
        if (adapters != null) {
            for (int i = 0; i < adapters.size(); i++) {
                final JsonObject adapterDetails = adapters.getJsonObject(i);
                addNotPresentFieldsWithDefaultValuesForAdapter(adapterDetails);
            }
        }
    }
    
    private void addNotPresentFieldsWithDefaultValuesForAdapter(final JsonObject adapter) {
        if(!adapter.containsKey(TenantConstants.FIELD_ENABLED)) {
            log.debug("adding 'enabled' key to payload");
            adapter.put(TenantConstants.FIELD_ENABLED, Boolean.TRUE);
        }
        
        if(!adapter.containsKey(TenantConstants.FIELD_ADAPTERS_DEVICE_AUTHENTICATION_REQUIRED)) {
            log.debug("adding 'device-authentication-required' key to adapter payload");
            adapter.put(TenantConstants.FIELD_ADAPTERS_DEVICE_AUTHENTICATION_REQUIRED, Boolean.TRUE);
        }
    }

    /**
     * Wraps a given tenant ID, it's properties data and it's adapter configuration data into a JSON structure suitable
     * to be returned to clients as the result of a tenant API operation.
     *
     * @param tenantId The tenant ID.
     * @param data The tenant properties data.
     * @param adapterConfigurations The adapter configurations data for the tenant as JsonArray.
     * @return The JSON structure.
     */
    protected final static JsonObject getResultPayload(final String tenantId, final JsonObject data,
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
    public void get(String tenantId, Handler<AsyncResult<TenantResult>> resultHandler) {
        handleUnimplementedOperation(resultHandler);
    }

    /**
     * {@inheritDoc}
     *
     * This default implementation simply returns an empty result with status code 501 (Not Implemented).
     * Subclasses should override this method in order to provide a reasonable implementation.
     */
    public void add(String tenantId, JsonObject tenantObj, Handler<AsyncResult<TenantResult>> resultHandler) {
        handleUnimplementedOperation(resultHandler);
    }

    /**
     * {@inheritDoc}
     *
     * This default implementation simply returns an empty result with status code 501 (Not Implemented).
     * Subclasses should override this method in order to provide a reasonable implementation.
     */
    public void update(String tenantId, JsonObject tenantObj, Handler<AsyncResult<TenantResult>> resultHandler) {
        handleUnimplementedOperation(resultHandler);
    }

    /**
     * {@inheritDoc}
     *
     * This default implementation simply returns an empty result with status code 501 (Not Implemented).
     * Subclasses should override this method in order to provide a reasonable implementation.
     */
    public void remove(String tenantId, Handler<AsyncResult<TenantResult>> resultHandler) {
        handleUnimplementedOperation(resultHandler);
    }

    private void handleUnimplementedOperation(final Handler<AsyncResult<TenantResult>> resultHandler) {
        resultHandler.handle(Future.succeededFuture(TenantResult.from(HttpURLConnection.HTTP_NOT_IMPLEMENTED)));
    }

}
