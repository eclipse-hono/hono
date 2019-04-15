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

package org.eclipse.hono.tests;

import org.eclipse.hono.client.ApplicationClientFactory;
import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.MessageSender;

import io.vertx.core.Future;

/**
 * A IntegrationTestApplicationClientFactory.
 *
 */
public interface IntegrationTestApplicationClientFactory extends ApplicationClientFactory {

    /**
     * Creates a new factory for a connection.
     * 
     * @param connection The connection to Hono.
     * @return the factory.
     */
    static IntegrationTestApplicationClientFactory create(final HonoConnection connection) {
        return new IntegrationTestApplicationClientFactoryImpl(connection);
    }

    /**
     * Creates a new sender on this client's connection and context.
     * <p>
     * Note that this method returns a newly created sender on each invocation.
     * 
     * @param targetAddress The target address to create the sender for.
     * @return The sender.
     */
    Future<MessageSender> createGenericMessageSender(String targetAddress);

}
