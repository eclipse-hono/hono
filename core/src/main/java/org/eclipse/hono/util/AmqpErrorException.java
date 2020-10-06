/*******************************************************************************
 * Copyright (c) 2016, 2020 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package org.eclipse.hono.util;

import java.util.Objects;
import java.util.Optional;

import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.amqp.transport.DeliveryState;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;

import io.vertx.proton.ProtonHelper;

/**
 * An exception wrapping an AMQP 1.0 error.
 *
 */
public final class AmqpErrorException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    private final Symbol error;

    /**
     * Creates a new exception for an error and description.
     *
     * @param error The AMQP error to convey in this exception.
     * @param description A textual description of the context the error occurred in.
     * @throws NullPointerException if error is {@code null}.
     */
    public AmqpErrorException(final Symbol error, final String description) {
        super(description);
        this.error = Objects.requireNonNull(error);
    }

    /**
     * Creates a new exception for an error and description.
     *
     * @param error The AMQP error to convey in this exception.
     * @param description A textual description of the context the error occurred in.
     * @throws NullPointerException if error is {@code null}.
     */
    public AmqpErrorException(final String error, final String description) {
        super(description);
        this.error = Symbol.getSymbol(Objects.requireNonNull(error));
    }

    /**
     * Creates an exception for an AMQP delivery state.
     *
     * @param deliveryState The delivery state.
     * @return The exception.
     * @throws NullPointerException if delivery state is {@code null}.
     */
    public static AmqpErrorException from(final DeliveryState deliveryState) {
        switch (deliveryState.getType()) {
        case Rejected:
            final Rejected rejected = (Rejected) deliveryState;
            return Optional.ofNullable(rejected.getError())
                    .map(ec -> new AmqpErrorException(ec.getCondition(), ec.getDescription()))
                    .orElseGet(() -> new AmqpErrorException(deliveryState.getType().toString(), null));
        default:
            return new AmqpErrorException(deliveryState.getType().toString(), null);
        }
    }

    /**
     * Gets the AMQP error conveyed in this exception.
     *
     * @return The error.
     */
    public Symbol getError() {
        return error;
    }

    /**
     * Gets an AMQP {@link ErrorCondition} based on this exception's error and description.
     *
     * @return The condition.
     */
    public ErrorCondition asErrorCondition() {
        return ProtonHelper.condition(error.toString(), getMessage());
    }
}
