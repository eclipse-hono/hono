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


package org.eclipse.hono.client;


/**
 * A listener to be notified when a connection is lost unexpectedly.
 *
 */
@FunctionalInterface
public interface DisconnectListener {

    /**
     * Invoked when the connection to a Hono service is lost unexpectedly.
     * 
     * @param client The client representing the connection to the service.
     */
    void onDisconnect(HonoClient client);
}
