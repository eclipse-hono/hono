/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.hono.application.client.amqp;

import java.util.Objects;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.application.client.MessageContext;

import io.vertx.proton.ProtonDelivery;

/**
 * The context that an AMQP message has been received in.
 * <p>
 * Provides access to the raw message and the delivery that it is associated with.
 */
public class AmqpMessageContext implements MessageContext {

    private final ProtonDelivery delivery;
    private final Message message;
    /**
     * Creates a context.
     *
     * @param delivery The delivery of the message.
     * @param message The raw AMQP message.
     * @throws NullPointerException any of the parameters are {@code null}.
     */
    public AmqpMessageContext(final ProtonDelivery delivery, final Message message) {
        Objects.requireNonNull(delivery);
        Objects.requireNonNull(message);
        this.delivery = delivery;
        this.message = message;
    }

    /**
     * Gets the proton delivery of the message.
     * <p>
     * The delivery can be used to manually settle messages with a particular outcome.
     *
     * @return The delivery.
     */
    public final ProtonDelivery getDelivery() {
        return delivery;
    }

    /**
     * Gets the AMQP message.
     *
     * @return The raw message.
     */
    public final Message getRawMessage() {
        return message;
    }
}
