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
package org.eclipse.hono.server;

import static java.net.HttpURLConnection.HTTP_OK;
import static org.eclipse.hono.util.RegistrationConstants.EVENT_BUS_ADDRESS_REGISTRATION_IN;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.apache.qpid.proton.amqp.transport.AmqpError;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.service.amqp.Endpoint;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonReceiver;
import io.vertx.proton.ProtonSender;

/**
 * Base class for Hono endpoints.
 */
public abstract class BaseEndpoint implements Endpoint {

    protected Vertx                             vertx;
    protected final Logger                      logger               = LoggerFactory.getLogger(getClass());
    protected ServiceConfigProperties              honoConfig           = new ServiceConfigProperties();
    private static final String                 STATUS_OK            = String.valueOf(HTTP_OK);
    private Map<String, UpstreamReceiverImpl>   activeClients        = new HashMap<>();

    /**
     * 
     * @param vertx the Vertx instance to use for accessing the event bus.
     * @throws NullPointerException if vertx is {@code null};
     */
    protected BaseEndpoint(final Vertx vertx) {
        this.vertx = Objects.requireNonNull(vertx);
    }

    /**
     * Sets the global Hono configuration properties.
     * 
     * @param props The properties.
     * @throws NullPointerException if props is {@code null}.
     */
    @Autowired(required = false)
    public final void setHonoConfiguration(final ServiceConfigProperties props) {
        this.honoConfig = Objects.requireNonNull(props);
    }

    @Override
    public final void start(final Future<Void> startFuture) {
        if (vertx == null) {
            startFuture.fail("Vert.x instance must be set");
        } else {
            doStart(startFuture);
        }
    }

    /**
     * Subclasses should override this method to create required resources
     * during startup.
     * <p>
     * This implementation always completes the start future.
     * 
     * @param startFuture Completes if startup succeeded.
     */
    protected void doStart(final Future<Void> startFuture) {
        startFuture.complete();
    }

    @Override
    public final void stop(final Future<Void> stopFuture) {
        doStop(stopFuture);
    }

    /**
     * Subclasses should override this method to release resources
     * during shutdown.
     * <p>
     * This implementation always completes the stop future.
     * 
     * @param stopFuture Completes if shutdown succeeded.
     */
    protected void doStop(final Future<Void> stopFuture) {
        stopFuture.complete();
    }

    /**
     * Closes the link to an upstream client and removes all state kept for it.
     * 
     * @param client The client to detach.
     */
    protected final void onLinkDetach(final UpstreamReceiver client) {
        onLinkDetach(client, null);
    }

    /**
     * Closes the link to an upstream client and removes all state kept for it.
     * 
     * @param client The client to detach.
     * @param error The error condition to convey to the client when closing the link.
     */
    protected final void onLinkDetach(final UpstreamReceiver client, final ErrorCondition error) {
        if (error == null) {
            logger.debug("closing receiver for client [{}]", client.getLinkId());
        } else {
            logger.debug("closing receiver for client [{}]: {}", client.getLinkId(), error.getDescription());
        }
        client.close(error);
        removeClientLink(client.getLinkId());
    }

    /**
     * Registers a link with an upstream client.
     * 
     * @param link The link to register.
     */
    protected final void registerClientLink(final UpstreamReceiverImpl link) {
        activeClients.put(link.getLinkId(), link);
    }

    /**
     * Looks up a link with an upstream client based on its identifier.
     * 
     * @param linkId The identifier of the client.
     * @return The link object representing the client or {@code null} if no link with the given identifier exists.
     * @throws NullPointerException if the link id is {@code null}.
     */
    protected final UpstreamReceiver getClientLink(final String linkId) {
        return activeClients.get(Objects.requireNonNull(linkId));
    }

    /**
     * Deregisters a link with an upstream client.
     * 
     * @param linkId The identifier of the link to deregister.
     */
    protected final void removeClientLink(final String linkId) {
        activeClients.remove(linkId);
    }

    @Override
    public void onLinkAttach(final ProtonReceiver receiver, final ResourceIdentifier targetResource) {
        logger.info("Endpoint [{}] does not support data upload, closing link.", getName());
        receiver.setCondition(ProtonHelper.condition(AmqpError.NOT_IMPLEMENTED, "resource cannot be written to"));
        receiver.close();
    }

    @Override
    public void onLinkAttach(final ProtonSender sender, final ResourceIdentifier targetResource) {
        logger.info("Endpoint [{}] does not support data retrieval, closing link.", getName());
        sender.setCondition(ProtonHelper.condition(AmqpError.NOT_IMPLEMENTED, "resource cannot be read from"));
        sender.close();
    }

    /**
     * Checks with the registration service whether a device is registered and enabled.
     * 
     * @param resource The resource identifier containing the tenant and device identifier to check.
     * @param resultHandler The result handler will be invoked with the value of the device's <em>enabled</em>
     *                      property if the device exists, otherwise the handler will be invoked with {@code false}.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    protected final void checkDeviceEnabled(final ResourceIdentifier resource, final Handler<AsyncResult<Boolean>> resultHandler) {

        Objects.requireNonNull(resource);
        Objects.requireNonNull(resultHandler);

        final JsonObject registrationJson = RegistrationConstants
                .getRegistrationJson(RegistrationConstants.ACTION_ENABLED, resource.getTenantId(), resource.getResourceId());

        vertx.eventBus().send(EVENT_BUS_ADDRESS_REGISTRATION_IN, registrationJson, response -> {

            if (response.succeeded()) {
                final io.vertx.core.eventbus.Message<Object> message = response.result();
                if (message.body() instanceof JsonObject) {
                    JsonObject result = (JsonObject) message.body();
                    resultHandler.handle(Future.succeededFuture(getEnabled(result)));
                } else {
                    logger.error("received malformed response from registration service [type: {}]", response.result().getClass().getName());
                    resultHandler.handle(Future.failedFuture("internal error"));
                }
            } else {
                logger.error("could not retrieve device information from registration service", response.cause());
                resultHandler.handle(Future.failedFuture(response.cause()));
            }
        });
    }

    private boolean getEnabled(final JsonObject registrationResponse) {
        JsonObject payload = registrationResponse.getJsonObject(RegistrationConstants.FIELD_PAYLOAD);
        if (payload == null) {
            return false;
        } else {
            return payload.getBoolean(RegistrationConstants.FIELD_ENABLED, Boolean.FALSE);
        }
    }
}
