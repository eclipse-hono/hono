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
import org.eclipse.hono.telemetry.TelemetryAdapter;
import org.eclipse.hono.telemetry.TelemetryConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
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
    public final void start(final Future<Void> startFuture) throws Exception {
        telemetryDataConsumer = vertx.eventBus().consumer(TelemetryConstants.EVENT_BUS_ADDRESS_TELEMETRY_IN);
        telemetryDataConsumer.handler(this::processMessage);
        LOG.info("listening on event bus [address: {}] for incoming telemetry messages",
                TelemetryConstants.EVENT_BUS_ADDRESS_TELEMETRY_IN);
        doStart(startFuture);
    }

    protected void doStart(final Future<Void> startFuture) throws Exception {
        // should be overridden by subclasses
        startFuture.complete();
    }

    /*
     * (non-Javadoc)
     * 
     * @see io.vertx.core.AbstractVerticle#stop()
     */
    @Override
    public final void stop(final Future<Void> stopFuture) {
        telemetryDataConsumer.unregister(unregistered -> {
            if (unregistered.succeeded()) {
                LOG.info("unregistered message consumer from event bus");
                doStop(stopFuture);
            } else {
                stopFuture.fail(unregistered.cause());
            }
        });
    }

    protected void doStop(final Future<Void> stopFuture) {
        // to be overridden by subclasses
        stopFuture.complete();
    }

    private void processMessage(final Message<String> message) {
        Object obj = vertx.sharedData().getLocalMap(TelemetryConstants.EVENT_BUS_ADDRESS_TELEMETRY_IN)
                .remove(message.body());
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
                    TelemetryConstants.EVENT_BUS_ADDRESS_TELEMETRY_IN, obj.getClass().getName());
        }
    }
}
