/**
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
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

import java.util.Objects;

import org.eclipse.hono.client.impl.HonoClientImpl;
import org.eclipse.hono.config.ClientConfigProperties;

import io.vertx.core.Future;
import io.vertx.core.Vertx;


/**
 * A Hono client that also allows to create generic links to a peer.
 *
 */
public class IntegrationTestHonoClient extends HonoClientImpl {

    /**
     * Creates a new client.
     * 
     * @param vertx The vert.x instance to use.
     * @param clientConfigProperties The configuration properties.
     */
    public IntegrationTestHonoClient(final Vertx vertx, final ClientConfigProperties clientConfigProperties) {
        super(vertx, clientConfigProperties);
    }

    /**
     * Creates a new sender on this client's connection and context.
     * <p>
     * Note that this method returns a newly created sender on each invocation.
     * 
     * @param targetAddress The target address to create the sender for.
     * @return The sender.
     */
    public Future<GenericMessageSender> createGenericMessageSender(final String targetAddress) {

        Objects.requireNonNull(targetAddress);
        return GenericMessageSender.create(
                context,
                clientConfigProperties,
                connection,
                targetAddress,
                s -> {});
    }
}
