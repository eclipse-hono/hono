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

import org.eclipse.hono.AmqpMessage;
import org.eclipse.hono.server.HonoServer;
import org.eclipse.hono.telemetry.TelemetryAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;

/**
 * Base class for implementing {@code TelemetryAdapter}s.
 * <p>
 * Provides support for receiving telemetry messages via Vert.x event bus.
 * </p>
 */
public abstract class BaseTelemetryAdapter extends AbstractVerticle implements TelemetryAdapter {

    private static final Logger     LOG = LoggerFactory.getLogger(BaseTelemetryAdapter.class);
    private MessageConsumer<String> telemetryDataConsumer;

    /*
     * (non-Javadoc)
     * 
     * @see io.vertx.core.AbstractVerticle#start()
     */
    @Override
    public final void start() throws Exception {
        telemetryDataConsumer = vertx.eventBus().consumer(HonoServer.EVENT_BUS_ADDRESS_TELEMETRY_IN);
        telemetryDataConsumer.handler(this::processMessage);
        doStart();
    }

    protected void doStart() throws Exception {
        // should be overridden by subclasses
    }

    /*
     * (non-Javadoc)
     * 
     * @see io.vertx.core.AbstractVerticle#stop()
     */
    @Override
    public final void stop() throws Exception {
        telemetryDataConsumer.unregister();
        doStop();
    }

    protected void doStop() throws Exception {
        // to be overridden by subclasses
    }

    private void processMessage(final Message<String> message) {
        Object obj = vertx.sharedData().getLocalMap(HonoServer.EVENT_BUS_ADDRESS_TELEMETRY_IN).remove(message.body());
        if (obj instanceof AmqpMessage) {
            AmqpMessage telemetryMsg = (AmqpMessage) obj;
            if (processTelemetryData(telemetryMsg.getMessage())) {
                message.reply("accepted");
            } else {
                LOG.warn("cannot process telemetry message [uuid: {}]", message.body());
                message.reply("error");
            }
        } else {
            LOG.warn("expected {} in shared local map {} but found {}", AmqpMessage.class.getName(),
                    HonoServer.EVENT_BUS_ADDRESS_TELEMETRY_IN, obj.getClass().getName());
        }
    }
}
