/**
 * Copyright (c) 2016 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial API and implementation and initial documentation
 */
package org.eclipse.hono.dispatcher.amqp;

/**
 * Exception thrown if sth went wrong using AMQP connection.
 */
public final class AmqpException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new AmqpException with {@code null} message and {@code null} cause.
     */
    public AmqpException() {
    }

    /**
     * Constructs a new AmqpException with {@code null} message and {@code null} cause.
     *
     * @param cause what caused the exception
     */
    public AmqpException(final Throwable cause) {
        super(cause);
    }

    /**
     * Constructs a new AmqpException with {@code null} cause.
     *
     * @param message detail message describing what happened
     */
    public AmqpException(final String message) {
        super(message);
    }

    /**
     * Constructs a new AmqpException with message and cause.
     *
     * @param message detail message describing what happened
     * @param cause what caused the exception
     */
    public AmqpException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
