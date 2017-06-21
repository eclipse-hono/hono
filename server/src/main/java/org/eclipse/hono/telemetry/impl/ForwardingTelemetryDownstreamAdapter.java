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

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.server.ForwardingDownstreamAdapter;
import org.eclipse.hono.server.SenderFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import io.vertx.core.Vertx;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonSender;

/**
 * A telemetry adapter that forwards uploaded messages to another AMQP 1.0 container.
 *
 */
@Component
@Scope("prototype")
@Qualifier("telemetry")
public final class ForwardingTelemetryDownstreamAdapter extends ForwardingDownstreamAdapter {

    /**
     * Creates a new adapter instance for a sender factory.
     * 
     * @param vertx The Vert.x instance to run on.
     * @param senderFactory The factory to use for creating new senders for downstream telemetry data.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    @Autowired
    public ForwardingTelemetryDownstreamAdapter(final Vertx vertx, final SenderFactory senderFactory) {
        super(vertx, senderFactory);
    }

    protected void forwardMessage(final ProtonSender sender, final Message msg, final ProtonDelivery upstreamDelivery) {
        ProtonHelper.accepted(upstreamDelivery, true);
        sender.send(msg);
    }

    @Override
    protected ProtonQoS getDownstreamQos() {
        return ProtonQoS.AT_MOST_ONCE;
    }
}
