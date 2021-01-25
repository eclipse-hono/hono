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

import org.eclipse.hono.application.client.MessageContext;

import io.vertx.proton.ProtonDelivery;

/**
 * The context of a AMQP message.
 * <p>
 * It provides access to the {@link ProtonDelivery} object of the message.
 */
public class AmqpMessageContext implements MessageContext {

    private final ProtonDelivery delivery;

    /**
     * Creates a context.
     *
     * @param delivery The delivery of the message.
     * @throws NullPointerException if delivery is {@code null}.
     */
    public AmqpMessageContext(final ProtonDelivery delivery) {
        Objects.requireNonNull(delivery);
        this.delivery = delivery;
    }

    /**
     * Gets the proton delivery of the message.
     *
     * @return The delivery.
     */
    public final ProtonDelivery getDelivery() {
        return delivery;
    }
}
