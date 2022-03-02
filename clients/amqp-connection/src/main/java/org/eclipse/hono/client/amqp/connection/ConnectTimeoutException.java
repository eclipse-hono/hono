/*******************************************************************************
 * Copyright (c) 2016, 2022 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client.amqp.connection;

/**
 * An exception thrown when a connection attempt has timed out.
 *
 */
public class ConnectTimeoutException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a {@code ConnectTimeoutException} with no specified detail
     * message.
     */
    public ConnectTimeoutException() {
        super();
    }

    /**
     * Constructs a {@code ConnectTimeoutException} with the specified detail
     * message.
     *
     * @param message the detail message
     */
    public ConnectTimeoutException(final String message) {
        super(message);
    }
}
