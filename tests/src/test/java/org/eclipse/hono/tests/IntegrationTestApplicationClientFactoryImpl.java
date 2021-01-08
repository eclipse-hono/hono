/**
 * Copyright (c) 2018, 2021 Contributors to the Eclipse Foundation
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

import org.eclipse.hono.client.HonoConnection;
import org.eclipse.hono.client.SendMessageSampler;
import org.eclipse.hono.client.amqp.GenericSenderLink;
import org.eclipse.hono.client.impl.ApplicationClientFactoryImpl;

import io.vertx.core.Future;


/**
 * A Hono client that also allows to create generic links to a peer.
 *
 */
public class IntegrationTestApplicationClientFactoryImpl extends ApplicationClientFactoryImpl implements IntegrationTestApplicationClientFactory {

    /**
     * Creates a new client.
     *
     * @param connection The connection to Hono.
     */
    public IntegrationTestApplicationClientFactoryImpl(final HonoConnection connection) {
        super(connection, SendMessageSampler.Factory.noop());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<GenericSenderLink> createGenericMessageSender(
            final String endpointName,
            final String tenantId) {

        Objects.requireNonNull(endpointName);
        Objects.requireNonNull(tenantId);

        return GenericSenderLink.create(
                connection,
                endpointName,
                tenantId,
                SendMessageSampler.noop(),
                s -> {});
    }

}
