/**
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

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
