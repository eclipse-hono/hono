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
package org.eclipse.hono;

import java.util.Objects;

import org.apache.qpid.proton.message.Message;

import io.vertx.core.shareddata.Shareable;
import io.vertx.proton.ProtonDelivery;
import io.vertx.proton.ProtonHelper;

/**
 * A wrapper around a <em>Proton</em> {@code Message} and {@code ProtonDelivery}.
 *
 */
public final class AmqpMessage implements Shareable {

    private final Message        message;
    private final ProtonDelivery delivery;

    /**
     * @param message
     * @param delivery
     */
    private AmqpMessage(final Message message, final ProtonDelivery delivery) {
        this.message = Objects.requireNonNull(message);
        this.delivery = Objects.requireNonNull(delivery);
    }

    public static AmqpMessage of(final Message message, final ProtonDelivery delivery) {
        return new AmqpMessage(message, delivery);
    }

    /**
     * @return the message
     */
    public Message getMessage() {
        return message;
    }

    /**
     * @return the delivery
     */
    public ProtonDelivery getDelivery() {
        return delivery;
    }

    public void accepted(final boolean settle) {
        ProtonHelper.accepted(delivery, settle);
    }

    public void rejected(final boolean settle) {
        ProtonHelper.rejected(delivery, settle);
    }

    public void released(final boolean settle) {
        ProtonHelper.released(delivery, settle);
    }
}
