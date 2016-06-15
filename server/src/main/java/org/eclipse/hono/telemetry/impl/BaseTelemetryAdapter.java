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
package org.eclipse.hono.telemetry.impl;

import static org.eclipse.hono.telemetry.TelemetryConstants.EVENT_ATTACHED;
import static org.eclipse.hono.telemetry.TelemetryConstants.EVENT_BUS_ADDRESS_TELEMETRY_LINK_CONTROL;
import static org.eclipse.hono.telemetry.TelemetryConstants.EVENT_DETACHED;
import static org.eclipse.hono.telemetry.TelemetryConstants.FIELD_NAME_EVENT;
import static org.eclipse.hono.telemetry.TelemetryConstants.FIELD_NAME_LINK_ID;
import static org.eclipse.hono.telemetry.TelemetryConstants.FIELD_NAME_TARGET_ADDRESS;
import static org.eclipse.hono.telemetry.TelemetryConstants.getErrorMessage;
import static org.eclipse.hono.telemetry.TelemetryConstants.getFlowControlMsg;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.hono.AmqpMessage;
import org.eclipse.hono.telemetry.TelemetryAdapter;
import org.eclipse.hono.telemetry.TelemetryConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;

/**
 * Base class for implementing {@code TelemetryAdapter}s.
 * <p>
 * {@code BaseTelemetryAdapter} can be notified about {@code ProtonMessage}s containing telemetry data to be processed by means of
 * sending events via the vert.x event bus using address {@link TelemetryConstants#EVENT_BUS_ADDRESS_TELEMETRY_IN}. 
 * A separate event is expected for each telemetry message to process. The events must be JSON formatted strings containing
 * the following information:
 * </p>
 * <pre>
 *   { "client-id": ${clientId},
 *     "uuid"     : ${msgId}
 *   }
 * </pre>
 * <p>
 * The events do not contain the AMQP messages but instead only contain a unique ID (string) in the uuid field which is used to look up the
 * {@code ProtonMessage} from a Vert.x shared map with name {@link TelemetryConstants#EVENT_BUS_ADDRESS_TELEMETRY_IN}. The sender of
 * the event is thus required to put the message to that map before sending the event.
 * The <em>clientId</em> is the unique ID (string) of the sender of the message and is used to associate the message(s) with a particular
 * AMQP link.
 * </p>
 * <p>
 * For each event {@code BaseTelemetryAdapter} retrieves the corresponding telemetry message from the shared map and then invokes
 * {@link TelemetryAdapter#processTelemetryData(org.apache.qpid.proton.message.Message, String, io.vertx.core.Handler)}.
 * </p>
 */
public abstract class BaseTelemetryAdapter extends AbstractVerticle implements TelemetryAdapter {

    protected final int                 instanceNo;
    protected final int                 totalNoOfInstances;
    private static final Logger         LOG                        = LoggerFactory.getLogger(BaseTelemetryAdapter.class);
    private final Map<String, String>   flowControlAddressRegistry = new HashMap<>();
    private MessageConsumer<JsonObject> telemetryDataConsumer;
    private MessageConsumer<JsonObject> linkControlConsumer;
    private String                      dataAddress;

    protected BaseTelemetryAdapter() {
        this(0, 1);
    }

    protected BaseTelemetryAdapter(final int instanceNo, final int totalNoOfInstances) {
        this.instanceNo = instanceNo;
        this.totalNoOfInstances = totalNoOfInstances;
    }

    /**
     * Registers a Vert.x event consumer for address {@link TelemetryConstants#EVENT_BUS_ADDRESS_TELEMETRY_IN}
     * and then invokes {@link #doStart(Future)}.
     * 
     * @param the handler to invoke once start up is complete.
     */
    @Override
    public final void start(final Future<Void> startFuture) throws Exception {
        registerLinkControlConsumer();
        registerTelemetryDataConsumer();
        doStart(startFuture);
    }

    /**
     * Subclasses should override this method to perform any work required on start-up of this verticle.
     * <p>
     * This method is invoked by {@link #start()} as part of the verticle deployment process.
     * </p>
     * 
     * @param the handler to invoke once start up is complete.
     * @throws Exception if start-up fails
     */
    protected void doStart(final Future<Void> startFuture) throws Exception {
        // should be overridden by subclasses
        startFuture.complete();
    }

    private void registerTelemetryDataConsumer() {
        dataAddress = getAddressWithId(TelemetryConstants.EVENT_BUS_ADDRESS_TELEMETRY_IN);
        telemetryDataConsumer = vertx.eventBus().consumer(dataAddress);
        telemetryDataConsumer.handler(this::processMessage);
        LOG.info("listening on event bus [address: {}] for downstream telemetry messages",
                dataAddress);
    }

    private void registerLinkControlConsumer() {
        String address = getAddressWithId(EVENT_BUS_ADDRESS_TELEMETRY_LINK_CONTROL);
        linkControlConsumer = vertx.eventBus().consumer(address);
        linkControlConsumer.handler(this::processLinkControlMessage);
        LOG.info("listening on event bus [address: {}] for downstream link control messages", address);
    }

    private String getAddressWithId(final String baseAddress) {
        StringBuilder b = new StringBuilder(baseAddress);
        if (instanceNo > 0) {
            b.append(".").append(instanceNo);
        }
        return b.toString();
    }

    /**
     * Unregisters the telemetry data consumer from the Vert.x event bus and then invokes {@link #doStop(Future)}.
     * 
     * @param the handler to invoke once shutdown is complete.
     */
    @Override
    public final void stop(final Future<Void> stopFuture) {
        telemetryDataConsumer.unregister();
        LOG.info("unregistered telemetry data consumer from event bus");
        linkControlConsumer.unregister();
        LOG.info("unregistered link control consumer from event bus");
        doStop(stopFuture);
    }

    /**
     * Subclasses should override this method to perform any work required before shutting down this verticle.
     * <p>
     * This method is invoked by {@link #stop()} as part of the verticle deployment process.
     * </p>
     * 
     * @param the handler to invoke once shutdown is complete.
     */
    protected void doStop(final Future<Void> stopFuture) {
        // to be overridden by subclasses
        stopFuture.complete();
    }

    private void processLinkControlMessage(final Message<JsonObject> msg) {
        JsonObject body = msg.body();
        String event = body.getString(FIELD_NAME_EVENT);
        String linkId = body.getString(FIELD_NAME_LINK_ID);
        LOG.trace("received link [{}] control msg: {}", linkId, body.encode());
        if (EVENT_ATTACHED.equalsIgnoreCase(event)) {
            processLinkAttachedMessage(
                    linkId,
                    body.getString(FIELD_NAME_TARGET_ADDRESS),
                    msg.headers().get(TelemetryConstants.HEADER_NAME_REPLY_TO));
        } else if (EVENT_DETACHED.equalsIgnoreCase(event)) {
            linkDetached(linkId);
            unregisterReplyToAddress(linkId);
        } else {
            LOG.warn("discarding unsupported link control command [{}]", event);
        }
    }

    final void processLinkAttachedMessage(final String linkId, final String targetAddress, final String replyToAddress) {
        if (replyToAddress != null) {
            flowControlAddressRegistry.put(linkId, replyToAddress);
            linkAttached(linkId, targetAddress);
        } else {
            LOG.warn("discarding link [{}] control message lacking required header [{}]", linkId, TelemetryConstants.HEADER_NAME_REPLY_TO);
        }
    }

    private void unregisterReplyToAddress(final String linkId) {
        flowControlAddressRegistry.remove(linkId);
    }

    /**
     * Invoked when a client wants to establish a link with the Hono server for uploading
     * telemetry data for a given target address.
     * <p>
     * Subclasses should override this method in order to allocate any resources necessary
     * for processing telemetry messages sent later by the client. In order to signal
     * the client to start sending telemetry messages the sendFlowControl method must be
     * invoked which is what this method does by default.
     * </p>
     * 
     * @param linkId the unique ID of the link used by the client for uploading data.
     * @param targetAddress the target address to upload data to.
     */
    protected void linkAttached(final String linkId, final String targetAddress) {
        // by default resume link so that the client can start to send messages
        sendFlowControlMessage(linkId, false);
    }

    /**
     * Invoked when a client closes a link with the Hono server.
     * <p>
     * Subclasses should override this method in order to release any resources allocated as
     * part of the invocation of the clientAttached method.
     * </p>
     * <p>
     * This method does nothing by default.
     * </p>
     * 
     * @param linkId the unique ID of the link to be closed.
     */
    protected void linkDetached(final String linkId) {
        // do nothing
    }

    private void processMessage(final Message<JsonObject> message) {
        JsonObject body = message.body();
        String linkId = body.getString(TelemetryConstants.FIELD_NAME_LINK_ID);
        String msgId = body.getString(TelemetryConstants.FIELD_NAME_MSG_UUID);
        Object obj = vertx.sharedData().getLocalMap(dataAddress).remove(msgId);
        if (obj instanceof AmqpMessage) {
            AmqpMessage telemetryMsg = (AmqpMessage) obj;
            processTelemetryData(telemetryMsg.getMessage(), linkId);
        } else {
            LOG.warn("expected {} in shared local map {} but found {}", AmqpMessage.class.getName(),
                    dataAddress, obj.getClass().getName());
        }
    }

    protected final void sendFlowControlMessage(final String linkId, final boolean suspend) {
        sendMessage(linkId, getFlowControlMsg(linkId, suspend));
    }

    protected final void sendErrorMessage(final String linkId, final boolean closeLink) {
        sendMessage(linkId, getErrorMessage(linkId, closeLink));
    }

    private void sendMessage(final String linkId, final JsonObject msg) {
        String address = flowControlAddressRegistry.get(linkId);
        if (address != null) {
            LOG.trace("sending flow control message for link [{}] to address [{}]: {}", linkId, address, msg.encodePrettily());
            vertx.eventBus().send(address, msg);
        } else {
            LOG.warn("cannot send flow control message upstream for link [{}], no event bus address registered", linkId);
        }
    }
}
