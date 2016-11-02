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

import static org.eclipse.hono.server.EndpointHelper.FIELD_NAME_LINK_ID;
import static org.eclipse.hono.server.EndpointHelper.FIELD_NAME_MSG_UUID;

import org.eclipse.hono.AmqpMessage;
import org.eclipse.hono.server.AdapterSupport;
import org.eclipse.hono.telemetry.TelemetryAdapter;
import org.eclipse.hono.telemetry.TelemetryConstants;

import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;

/**
 * Base class for implementing {@code TelemetryAdapter}s.
 * <p>
 * {@code BaseTelemetryAdapter} can be notified about {@code ProtonMessage}s containing telemetry data
 * to be processed by means of sending events via the Vert.x event bus using address
 * {@link TelemetryConstants#EVENT_BUS_ADDRESS_TELEMETRY_IN}.
 * <p> 
 * A separate event is expected for each telemetry message to process. The events must be JSON formatted strings containing
 * the following information:
 * <pre>
 *   { "client-id": ${clientId},
 *     "uuid"     : ${msgId}
 *   }
 * </pre>
 * <p>
 * The events do not contain the AMQP messages but instead only contain a unique ID (string) in the uuid field which is used
 * to look up the {@code ProtonMessage} from a Vert.x shared map with name {@link TelemetryConstants#EVENT_BUS_ADDRESS_TELEMETRY_IN}.
 * The sender of the event is thus required to put the message to that map before sending the event.
 * <p>
 * The <em>clientId</em> is the unique ID (string) of the sender of the message and is used to associate the message(s)
 * with a particular AMQP link.
 * <p>
 * For each event {@code BaseTelemetryAdapter} retrieves (and removes) the corresponding telemetry message from the shared map and
 * then invokes {@link TelemetryAdapter#processTelemetryData(org.apache.qpid.proton.message.Message, String)}.
 */
public abstract class BaseTelemetryAdapter extends AdapterSupport implements TelemetryAdapter {

    protected static final int          DEFAULT_CREDIT             = 10;
    private final String                dataAddress;
    private MessageConsumer<JsonObject> telemetryDataConsumer;

    protected BaseTelemetryAdapter() {
        this(0, 1);
    }

    protected BaseTelemetryAdapter(final int instanceNo, final int totalNoOfInstances) {
        super(instanceNo, totalNoOfInstances);
        dataAddress = TelemetryConstants.getDownstreamMessageAddress(instanceNo);
    }

    @Override
    protected String getEndpointName() {
        return TelemetryConstants.TELEMETRY_ENDPOINT;
    }

    /**
     * Registers a Vert.x event consumer for address {@link TelemetryConstants#EVENT_BUS_ADDRESS_TELEMETRY_IN}
     * and then invokes {@link #doStart(Future)}.
     * 
     * @param startFuture   the handler to invoke once start up is complete.
     */
    @Override
    protected final void doStart(final Future<Void> startFuture) {
        registerTelemetryDataConsumer();
        setUpResources(startFuture);
    }

    protected void setUpResources(final Future<Void> startFuture) {
        startFuture.complete();
    }

    private void registerTelemetryDataConsumer() {
        telemetryDataConsumer = vertx.eventBus().consumer(dataAddress);
        telemetryDataConsumer.handler(this::processMessage);
        LOG.info("listening on event bus [address: {}] for downstream telemetry messages", dataAddress);
    }

    /**
     * Unregisters the consumers from the Vert.x event bus and then invokes {@link #doStop(Future)}.
     * 
     * @param stopFuture    the handler to invoke once shutdown is complete.
     */
    protected final void doStop(final Future<Void> stopFuture) {
        telemetryDataConsumer.unregister();
        LOG.info("unregistered telemetry data consumer from event bus");
        shutDownResources(stopFuture);
    }

    protected void shutDownResources(final Future<Void> stopFuture) {
        stopFuture.complete();
    }

    public final void processMessage(final Message<JsonObject> message) {
        JsonObject body = message.body();
        String linkId = body.getString(FIELD_NAME_LINK_ID);
        String msgId = body.getString(FIELD_NAME_MSG_UUID);
        Object obj = vertx.sharedData().getLocalMap(dataAddress).remove(msgId);
        if (obj instanceof AmqpMessage) {
            AmqpMessage telemetryMsg = (AmqpMessage) obj;
            processTelemetryData(telemetryMsg.getMessage(), linkId);
        } else {
            LOG.warn("expected {} in shared local map {} but found {}", AmqpMessage.class.getName(),
                    dataAddress, obj.getClass().getName());
        }
    }
}
