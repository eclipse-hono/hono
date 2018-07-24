/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
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

import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.transport.AmqpError;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;

import io.vertx.proton.ProtonHelper;

/**
 * An exception wrapping an {@link AmqpError}.
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
     */
    public AmqpErrorException(final Symbol error, final String description) {
        super(Objects.requireNonNull(description));
        this.error = Objects.requireNonNull(error);
    }

    /**
     * Creates a new exception for an error and description.
     * 
     * @param error The AMQP error to convey in this exception.
     * @param description A textual description of the context the error occurred in.
     */
    public AmqpErrorException(final String error, final String description) {
        super(Objects.requireNonNull(description));
        this.error = Symbol.getSymbol(Objects.requireNonNull(error));
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
