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
package org.eclipse.hono.server;

import static java.net.HttpURLConnection.HTTP_OK;
import static org.eclipse.hono.server.EndpointHelper.*;
import static org.eclipse.hono.util.RegistrationConstants.EVENT_BUS_ADDRESS_REGISTRATION_IN;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.apache.qpid.proton.amqp.transport.AmqpError;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.RegistrationConstants;
import org.eclipse.hono.util.ResourceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonSender;

/**
 * Base class for Hono endpoints.
 */
public abstract class BaseEndpoint implements Endpoint {

    protected final boolean                 singleTenant;
    protected final Vertx                   vertx;
    protected final int                     instanceNo;
    protected final Logger                  logger               = LoggerFactory.getLogger(getClass());
    protected final String                  linkControlAddress;
    protected final String                  flowControlAddress;
    private static final String             STATUS_OK            = String.valueOf(HTTP_OK);
    private Map<String, UpstreamReceiver>   activeClients        = new HashMap<>();
    private MessageConsumer<JsonObject>     flowControlConsumer;

    /**
     * 
     * @param vertx the Vertx instance to use for accessing the event bus.
     */
    protected BaseEndpoint(final Vertx vertx) {
        this(vertx, false, 0);
    }

    protected BaseEndpoint(final Vertx vertx, final boolean singleTenant, final int instanceNo) {
        this.vertx = Objects.requireNonNull(vertx);
        this.singleTenant = singleTenant;
        this.instanceNo = instanceNo;
        this.linkControlAddress = EndpointHelper.getLinkControlAddress(getName(), instanceNo);
        logger.info("publishing downstream link control messages on event bus [address: {}]", linkControlAddress);
        this.flowControlAddress = EndpointHelper.getFlowControlAddress(getName(), instanceNo);
    }

    @Override
    public final boolean start() {
        registerFlowControlConsumer();
        return doStart();
    }

    /**
     * Subclasses should override this method to create required resources
     * during startup.
     * <p>
     * This implementation always returns {@code true}.
     * </p>
     * @return {@code true} if startup succeeded.
     */
    protected boolean doStart() {
        return true;
    }

    @Override
    public final boolean stop() {
        if (flowControlConsumer != null) {
            flowControlConsumer.unregister();
        }
        return doStop();
    }

    /**
     * Subclasses should override this method to release resources
     * during shutdown.
     * <p>
     * This implementation always returns {@code true}.
     * </p>
     * @return {@code true} if shutdown succeeded.
     */
    protected boolean doStop() {
        return true;
    }

    private void registerFlowControlConsumer() {

        flowControlConsumer = this.vertx.eventBus().consumer(flowControlAddress, this::handleUpstreamMsg);
        logger.info("listening on event bus [address: {}] for upstream messages", flowControlAddress);
    }

    private void handleUpstreamMsg(final io.vertx.core.eventbus.Message<JsonObject> msg) {

        if (msg.body() == null) {
            logger.warn("received empty upstream message, is this a bug?");
        } else {
            if (logger.isTraceEnabled()) {
                logger.trace("received upstream message: {}", msg.body().encodePrettily());
            }
            if (isFlowControlMessage(msg.headers())) {
                handleFlowControlMsg(msg.body(), drainResult -> {
                    msg.reply(drainResult);
                });
            } else if (isErrorMessage(msg.headers())) {
                handleErrorMessage(msg.body());
            } else if (logger.isInfoEnabled()) {
                logger.info("discarding unsupported upstream message");
            }
        }
    }

    private void handleFlowControlMsg(final JsonObject msg, final Handler<Boolean> drainResultHandler) {

        String linkId = msg.getString(FIELD_NAME_LINK_ID);
        UpstreamReceiver client = getClientLink(linkId);

        if (client == null) {
            logger.warn("discarding flow control message for non-existing link {}", linkId);
        } else if (msg.getBoolean(FIELD_NAME_DRAIN)) {
            client.drain(10000, drainAttempt -> {
                if (drainAttempt.failed()) {
                    logger.info("request to drain client {} failed", client.getLinkId());
                    drainResultHandler.handle(false);
                    onLinkDetach(client, ErrorConditions.ERROR_MISSING_DRAIN_RESPONSE);
                } else {
                    logger.debug("request to drain client {} succeeded", client.getLinkId());
                    drainResultHandler.handle(true);
                }
            });
        } else {
            int credits = msg.getInteger(FIELD_NAME_CREDIT, 0);
            client.replenish(credits);
        }
    }

    private void handleErrorMessage(final JsonObject msg) {

        String linkId = msg.getString(FIELD_NAME_LINK_ID);
        UpstreamReceiver client = getClientLink(linkId);

        if (client == null) {
            logger.warn("discarding flow control message for non-existing link {}", linkId);
        } else if (msg.getBoolean(FIELD_NAME_CLOSE_LINK, false)) {
            onLinkDetach(client, ErrorConditions.ERROR_NO_DOWNSTREAM_CONSUMER);
        }
    }

    protected final void onLinkDetach(final UpstreamReceiver client) {
        onLinkDetach(client, null);
    }

    protected final void onLinkDetach(final UpstreamReceiver client, final ErrorCondition error) {
        if (error == null) {
            logger.debug("closing receiver for client [{}]", client.getLinkId());
        } else {
            logger.debug("closing receiver for client [{}]: {}", client.getLinkId(), error.getDescription());
        }
        removeClientLink(client.getLinkId());
        sendLinkDetachMessage(client);
        client.close(error);
    }

    protected final void sendLinkAttachMessage(final UpstreamReceiver link, final ResourceIdentifier targetAddress) {
        JsonObject msg = getLinkAttachedMsg(link.getConnectionId(), link.getLinkId(), targetAddress);
        DeliveryOptions headers = addReplyToHeader(new DeliveryOptions(), flowControlAddress);
        vertx.eventBus().send(linkControlAddress, msg, headers);
    }

    protected final void sendLinkDetachMessage(final UpstreamReceiver link) {
        JsonObject msg = getLinkDetachedMsg(link.getLinkId());
        DeliveryOptions headers = addReplyToHeader(new DeliveryOptions(), flowControlAddress);
        vertx.eventBus().send(linkControlAddress, msg, headers);
    }

    /**
     * Checks if Hono runs in single-tenant mode.
     * <p>
     * In single-tenant mode Hono will accept target addresses in {@code ATTACH} messages
     * that do not contain a tenant ID and will assume {@link Constants#DEFAULT_TENANT} instead.
     * </p>
     * <p>
     * The default value of this property is {@code false}.
     * </p>
     *
     * @return {@code true} if Hono runs in single-tenant mode.
     */
    public final boolean isSingleTenant() {
        return singleTenant;
    }

    /**
     * Registers a link with an upstream client.
     * 
     * @param link The link to register.
     */
    protected final void registerClientLink(final UpstreamReceiver link) {
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
    public void onLinkAttach(final ProtonSender sender, final ResourceIdentifier targetResource) {
        logger.info("Endpoint [{}] does not support data retrieval, closing link.", getName());
        sender.setCondition(ProtonHelper.condition(AmqpError.NOT_IMPLEMENTED, "resource does not support sending"));
        sender.close();
    }

    /**
     * Checks with the registration service whether a device is registered.
     * 
     * @param resource The resource identifier containing the tenant and device identifier to check.
     * @param resultHandler The result handler will be invoked with {@code true} if the device is registered,
     *                      otherwise the handler will be invoked with {@code false}.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    protected final void checkDeviceExists(final ResourceIdentifier resource, final Handler<Boolean> resultHandler) {

        Objects.requireNonNull(resource);
        Objects.requireNonNull(resultHandler);

        final JsonObject registrationJson = RegistrationConstants
                .getRegistrationJson(RegistrationConstants.ACTION_GET, resource.getTenantId(), resource.getResourceId());

        vertx.eventBus().send(EVENT_BUS_ADDRESS_REGISTRATION_IN, registrationJson, response -> {

            if (response.succeeded()) {
                final io.vertx.core.eventbus.Message<Object> message = response.result();
                if (message.body() instanceof JsonObject) {
                    final JsonObject body = (JsonObject) message.body();
                    final String status = body.getString(RegistrationConstants.APP_PROPERTY_STATUS);
                    resultHandler.handle(STATUS_OK.equals(status));
                } else {
                    logger.error("received malformed response from registration service [type: {}]", response.result().getClass().getName());
                }
            } else {
                logger.error("could not retrieve device information from registration service", response.cause());
                resultHandler.handle(false);
            }
        });
    }
}
