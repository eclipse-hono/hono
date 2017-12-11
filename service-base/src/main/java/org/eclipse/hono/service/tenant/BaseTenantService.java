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

package org.eclipse.hono.service.tenant;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.eclipse.hono.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.net.HttpURLConnection;
import java.util.Objects;

import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;
import static org.eclipse.hono.util.TenantConstants.*;

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
    private TenantAssertionHelper assertionFactory;

    // TODO how do we want to do this?
    /**
     * Sets the factory to use for creating tokens asserting a tenant's status.
     *
     * @param assertionFactory The factory.
     * @throws NullPointerException if factory is {@code null}.
     */
    @Autowired
    @Qualifier("signing")
    public final void setRegistrationAssertionFactory(final TenantAssertionHelper assertionFactory) {
        this.assertionFactory = Objects.requireNonNull(assertionFactory);
    }

    /**
     * Starts up this service.
     * <ol>
     * <li>Checks if <em>tenantAssertionFactory</em>is set. If not, startup fails.</li>
     * <li>Registers an event bus consumer for address {@link TenantConstants#EVENT_BUS_ADDRESS_TENANT_IN} listening for
     * tenant CRUD requests.</li>
     * <li>Invokes {@link #doStart(Future)}.</li>
     * </ol>
     *
     * @param startFuture The future to complete on successful startup.
     */
    public final void start(final Future<Void> startFuture) {
        this.registerConsumer();
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

    private void registerConsumer() {
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
            final String tenantId = body.getJsonObject("payload").getString(RequestResponseApiConstants.FIELD_TENANT_ID);
            final String operation = body.getString(MessageHelper.SYS_PROPERTY_SUBJECT);

            if (tenantId == null) {
                log.debug("tenant request does not contain mandatory property [{}]",
                        CredentialsConstants.FIELD_TENANT_ID);
                reply(tenantMsg, TenantResult.from(HttpURLConnection.HTTP_BAD_REQUEST));
                return;
            }

            switch (operation) {
            case ACTION_GET:
                log.debug("retrieving tenant [{}]", tenantId);
                get(tenantId, result -> reply(tenantMsg, result));
                break;
            case ACTION_ADD:
                JsonObject payload = getRequestPayload(body);
                log.debug("creating tenant [{}] with data {}", tenantId, payload != null ? payload.encode() : null);
                add(payload, result -> reply(tenantMsg, result));
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
            log.debug("malformed request message");
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
     * Sends a response to a registration request over the Vertx event bus.
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
        JsonObject payload = request.getJsonObject(TenantConstants.FIELD_PAYLOAD, new JsonObject());
        addNotPresentFieldsWithDefaultValuesForTenant(payload);
        return payload;
    }
    
    private void addNotPresentFieldsWithDefaultValuesForTenant(JsonObject payload) {
        if(!payload.containsKey(TenantConstants.FIELD_ENABLED)) {
            log.debug("adding 'enabled' key to payload because it's not present");
            payload.put(TenantConstants.FIELD_ENABLED, Boolean.TRUE);
        }
        
        JsonArray adapters = payload.getJsonArray(TenantConstants.FIELD_ADAPTERS, new JsonArray());
 
        for (int i = 0; i < adapters.size(); i++) {
            JsonObject current = adapters.getJsonObject(i);
            addNotPresentFieldsWithDefaultValuesForAdapter(current);
        }
    }
    
    private void addNotPresentFieldsWithDefaultValuesForAdapter(JsonObject adapter) {
        if(!adapter.containsKey(TenantConstants.FIELD_ENABLED)) {
            log.debug("adding 'enabled' key to adapter payload because it's not present");
            adapter.put(TenantConstants.FIELD_ENABLED, Boolean.TRUE);
        }
        
        if(!adapter.containsKey(TenantConstants.FIELD_ADAPTERS_DEVICE_AUTHENTICATION_REQUIRED)) {
            log.debug("adding 'device-authentication-required' key to adapter payload because it's not present");
            adapter.put(TenantConstants.FIELD_ADAPTERS_DEVICE_AUTHENTICATION_REQUIRED, Boolean.TRUE);
        }
    }
}
