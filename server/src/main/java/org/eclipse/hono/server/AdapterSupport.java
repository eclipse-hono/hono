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

import static org.eclipse.hono.server.EndpointHelper.*;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.hono.util.AbstractInstanceNumberAwareVerticle;
import org.eclipse.hono.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;

/**
 * Base class for implementing adapters that process downstream messages received from clients
 * connecting to a Hono {@code Endpoint}.
 */
public abstract class AdapterSupport extends AbstractInstanceNumberAwareVerticle {

    protected static final int          DEFAULT_CREDIT             = 10;
    protected final Logger              LOG                        = LoggerFactory.getLogger(getClass());
    private final Map<String, String>   flowControlAddressRegistry = new HashMap<>();
    private MessageConsumer<JsonObject> linkControlConsumer;
    private MessageConsumer<String>     connectionClosedListener;

    protected AdapterSupport(final int instanceNo, final int totalNoOfInstances) {
        super(instanceNo, totalNoOfInstances);
    }

    /**
     * Registers Vert.x event consumers for receiving link control messages
     * and then invokes {@link #doStart(Future)}.
     * 
     * @param startFuture The handler to invoke once start up is complete.
     */
    @Override
    public final void start(final Future<Void> startFuture) {
        registerLinkControlConsumer();
        registerConnectionClosedListener();
        doStart(startFuture);
    }

    /**
     * Subclasses should override this method to perform any work required on start-up of this verticle.
     * <p>
     * This method is invoked by {@link #start()} as part of the verticle deployment process.
     * </p>
     * 
     * @param startFuture The handler to invoke once start up is complete.
     */
    protected void doStart(final Future<Void> startFuture) {
        startFuture.complete();
    }

    /**
     * Gets the name of the {@code Endpoint} this adapter works with.
     * 
     * @return The endpoint name.
     */
    protected abstract String getEndpointName();

    private void registerLinkControlConsumer() {
        String address = EndpointHelper.getLinkControlAddress(getEndpointName(), instanceNo);
        linkControlConsumer = vertx.eventBus().consumer(address);
        linkControlConsumer.handler(this::processLinkControlMessage);
        LOG.info("listening on event bus [address: {}] for downstream link control messages", address);
    }

    private void registerConnectionClosedListener() {
        connectionClosedListener = vertx.eventBus().consumer(
                Constants.EVENT_BUS_ADDRESS_CONNECTION_CLOSED,
                this::processConnectionClosedEvent);
    }

    /**
     * Unregisters the consumers from the Vert.x event bus and then invokes {@link #doStop(Future)}.
     * 
     * @param stopFuture    the handler to invoke once shutdown is complete.
     */
    @Override
    public final void stop(final Future<Void> stopFuture) {
        linkControlConsumer.unregister();
        LOG.info("unregistered link control consumer from event bus");
        connectionClosedListener.unregister();
        LOG.info("unregistered connection close listener from event bus");
        doStop(stopFuture);
    }

    /**
     * Subclasses should override this method to perform any work required before shutting down this verticle.
     * <p>
     * This method is invoked by {@link #stop()} as part of the verticle deployment process.
     * </p>
     * 
     * @param stopFuture    the handler to invoke once shutdown is complete.
     */
    protected void doStop(final Future<Void> stopFuture) {
        stopFuture.complete();
    }

    private void processConnectionClosedEvent(final Message<String> msg) {
        onUpstreamConnectionClosed(msg.body());
    }

    /**
     * Invoked when an upstream client closes its connection with Hono.
     * <p>
     * Subclasses should override this method to release any resources created/acquired
     * in the context of the connection, e.g. a connection and links to a downstream container.
     * 
     * @param connectionId The ID of the upstream connection that has been closed.
     */
    protected void onUpstreamConnectionClosed(final String connectionId) {
        // do nothing
    }

    public final void processLinkControlMessage(final Message<JsonObject> msg) {

        JsonObject body = msg.body();
        String event = body.getString(FIELD_NAME_EVENT);
        String linkId = body.getString(FIELD_NAME_LINK_ID);

        if (LOG.isTraceEnabled()) {
            LOG.trace("received link control msg: {}", body.encodePrettily());
        }

        if (EVENT_ATTACHED.equalsIgnoreCase(event)) {
            processLinkAttachedMessage(
                    body.getString(FIELD_NAME_CONNECTION_ID),
                    linkId,
                    body.getString(FIELD_NAME_TARGET_ADDRESS),
                    getReplyToAddress(msg.headers()));
        } else if (EVENT_DETACHED.equalsIgnoreCase(event)) {
            processLinkDetachedMessage(linkId);
        } else {
            LOG.warn("discarding unsupported link control command [{}]", event);
        }
    }

    public final void processLinkAttachedMessage(
            final String connectionId,
            final String linkId,
            final String targetAddress,
            final String replyToAddress) {

        if (replyToAddress == null) {
            LOG.warn("discarding link [{}] control message lacking reply-to address", linkId);
        } else {
            flowControlAddressRegistry.put(linkId, replyToAddress);
            onLinkAttached(connectionId, linkId, targetAddress);
        }
    }

    public final void processLinkDetachedMessage(final String linkId) {
        onLinkDetached(linkId);
        flowControlAddressRegistry.remove(linkId);
    }

    /**
     * Invoked when a client wants to establish a link with the Hono server for sending
     * messages for a given target address downstream.
     * <p>
     * Subclasses should use this method for allocating any resources required
     * for processing messages sent by the client, e.g. connect to a downstream container.
     * <p>
     * In order to signal the client to start sending messages the
     * {@link #sendFlowControlMessage(String, int, Handler)} method must be invoked with
     * some <em>credit</em>.
     * 
     * @param connectionId The unique ID of the AMQP 1.0 connection with the client.
     * @param linkId The unique ID of the link used by the client for uploading data.
     * @param targetAddress The target address to upload data to.
     */
    protected abstract void onLinkAttached(final String connectionId, final String linkId, final String targetAddress);

    /**
     * Invoked when a client closes a link with the Hono server.
     * <p>
     * Subclasses should release any resources allocated as part of the invocation of the
     * {@link #onLinkAttached(String, String, String)} method.
     * 
     * @param linkId the unique ID of the link being closed.
     */
    protected abstract void onLinkDetached(final String linkId);

    /**
     * Sends a flow control message upstream.
     * 
     * @param linkId The ID of the link the flow control information applies to.
     * @param credit The number of credits to replenish the client with.
     * @param replyHandler If not {@code null} the flow control message will have its <em>drain</em> flag set
     *                     and this handler will be notified about the result of the request to drain the client.
     */
    protected final void sendFlowControlMessage(final String linkId, final int credit, final Handler<AsyncResult<Message<Boolean>>> replyHandler) {
        DeliveryOptions options = new DeliveryOptions();
        options.addHeader(HEADER_NAME_TYPE, MSG_TYPE_FLOW_CONTROL);
        sendUpstreamMessage(linkId, options, getFlowControlMsg(linkId, credit, replyHandler != null), replyHandler);
    }

    /**
     * Sends an error message upstream.
     * 
     * @param linkId the ID of the link the error affects.
     * @param closeLink {@code true} if the error is unrecoverable and the receiver of the message
     *        should therefore close the link with the client.
     */
    protected final void sendErrorMessage(final String linkId, final boolean closeLink) {
        DeliveryOptions options = new DeliveryOptions();
        options.addHeader(HEADER_NAME_TYPE, MSG_TYPE_ERROR);
        sendUpstreamMessage(linkId, options, getErrorMessage(linkId, closeLink), null);
    }

    private <T> void sendUpstreamMessage(final String linkId, final DeliveryOptions options, final JsonObject msg, final Handler<AsyncResult<Message<T>>> replyHandler) {

        String address = flowControlAddressRegistry.get(linkId);
        if (address != null) {
            LOG.trace("sending upstream message for link [{}] to address [{}]: {}", linkId, address, msg.encodePrettily());
            if (replyHandler != null) {
                vertx.eventBus().send(address, msg, options, replyHandler);
            } else {
                vertx.eventBus().send(address, msg, options);
            }
        } else {
            LOG.warn("cannot send upstream message for link [{}], no event bus address registered", linkId);
        }
    }
}
