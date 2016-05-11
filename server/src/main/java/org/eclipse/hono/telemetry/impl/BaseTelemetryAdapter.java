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

import static org.eclipse.hono.telemetry.TelemetryConstants.*;

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

    private static final Logger         LOG                    = LoggerFactory.getLogger(BaseTelemetryAdapter.class);
    private MessageConsumer<JsonObject> telemetryDataConsumer;
    private MessageConsumer<JsonObject> linkControlConsumer;

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
        telemetryDataConsumer = vertx.eventBus().consumer(TelemetryConstants.EVENT_BUS_ADDRESS_TELEMETRY_IN);
        telemetryDataConsumer.handler(this::processMessage);
        LOG.info("listening on event bus [address: {}] for incoming telemetry messages",
                TelemetryConstants.EVENT_BUS_ADDRESS_TELEMETRY_IN);
    }

    private void registerLinkControlConsumer() {
        linkControlConsumer = vertx.eventBus().consumer(EVENT_BUS_ADDRESS_TELEMETRY_LINK_CONTROL);
        linkControlConsumer.handler(this::processLinkControlMessage);
        LOG.info("listening on event bus [address: {}] for link control messages",
                EVENT_BUS_ADDRESS_TELEMETRY_LINK_CONTROL);
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
        String clientId = body.getString(FIELD_NAME_LINK_ID);
        LOG.trace("received link control msg from client [{}]: {}", clientId, body.encode());
        if (EVENT_ATTACHED.equalsIgnoreCase(event)) {
            linkAttached(clientId, body.getString(FIELD_NAME_TARGET_ADDRESS));
        } else if (EVENT_DETACHED.equalsIgnoreCase(event)) {
            linkDetached(clientId);
        } else {
            LOG.warn("discarding unsupported link control command [{}]", event);
        }
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
        String clientId = body.getString(TelemetryConstants.FIELD_NAME_LINK_ID);
        String msgId = body.getString(TelemetryConstants.FIELD_NAME_MSG_UUID);
        Object obj = vertx.sharedData().getLocalMap(TelemetryConstants.EVENT_BUS_ADDRESS_TELEMETRY_IN).remove(msgId);
        if (obj instanceof AmqpMessage) {
            AmqpMessage telemetryMsg = (AmqpMessage) obj;
            processTelemetryData(telemetryMsg.getMessage(), clientId);
        } else {
            LOG.warn("expected {} in shared local map {} but found {}", AmqpMessage.class.getName(),
                    TelemetryConstants.EVENT_BUS_ADDRESS_TELEMETRY_IN, obj.getClass().getName());
        }
    }

    protected final void sendFlowControlMessage(final String senderId, final boolean suspend) {
        vertx.eventBus().send(EVENT_BUS_ADDRESS_TELEMETRY_FLOW_CONTROL, getFlowControlMsg(senderId, suspend));
    }

    protected final void sendErrorMessage(final String senderId, final boolean closeLink) {
        vertx.eventBus().send(EVENT_BUS_ADDRESS_TELEMETRY_FLOW_CONTROL, getErrorMessage(senderId, closeLink));
    }


}
