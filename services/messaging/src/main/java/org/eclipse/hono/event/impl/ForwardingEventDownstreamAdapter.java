/**
 * Copyright (c) 2016, 2018 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 *    Red Hat Inc
 *
 */
package org.eclipse.hono.event.impl;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.messaging.ForwardingDownstreamAdapter;
import org.eclipse.hono.messaging.SenderFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import io.vertx.core.Vertx;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonQoS;
import io.vertx.proton.ProtonSender;

/**
 * A event adapter that forwards uploaded messages to another AMQP 1.0 container.
 *
 */
@Component
@Scope("prototype")
@Qualifier("event")
public final class ForwardingEventDownstreamAdapter extends ForwardingDownstreamAdapter {

    /**
     * Creates a new adapter instance for a sender factory.
     *
     * @param vertx The Vert.x instance to run on.
     * @param senderFactory The factory to use for creating new senders for downstream event messages.
     * @throws NullPointerException if any of the parameters is {@code null}.
     */
    @Autowired
    public ForwardingEventDownstreamAdapter(final Vertx vertx, final SenderFactory senderFactory) {
        super(vertx, senderFactory);
    }

    protected void forwardMessage(final ProtonSender sender, final Message msg, final ProtonDelivery delivery) {
        sender.send(msg, updatedDelivery -> delivery.disposition(updatedDelivery.getRemoteState(), updatedDelivery.remotelySettled()));
    }

    @Override
    protected ProtonQoS getDownstreamQos() {
        return ProtonQoS.AT_LEAST_ONCE;
    }
}
