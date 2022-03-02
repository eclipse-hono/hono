/**
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.client.amqp.connection;


/**
 * A listener to be notified when a connection is re-established after
 * it has been lost unexpectedly.
 *
 * @param <T> The type of connection that this is a listener for.
 */
@FunctionalInterface
public interface ReconnectListener<T> {

    /**
     * Invoked after the connection to a Hono service has been re-established
     * after it had been lost unexpectedly.
     *
     * @param client The client representing the (re-established) connection to the service.
     */
    void onReconnect(T client);
}
