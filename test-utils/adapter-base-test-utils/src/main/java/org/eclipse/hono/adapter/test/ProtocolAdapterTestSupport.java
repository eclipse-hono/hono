/**
 * Copyright (c) 2020, 2021 Contributors to the Eclipse Foundation
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


package org.eclipse.hono.adapter.test;

import org.eclipse.hono.adapter.AbstractProtocolAdapterBase;
import org.eclipse.hono.adapter.MessagingClients;
import org.eclipse.hono.adapter.client.command.CommandResponseSender;
import org.eclipse.hono.adapter.client.telemetry.EventSender;
import org.eclipse.hono.adapter.client.telemetry.TelemetrySender;
import org.eclipse.hono.client.util.MessagingClient;
import org.eclipse.hono.config.ProtocolAdapterProperties;
import org.eclipse.hono.util.MessagingType;

/**
 * A base class for implementing tests for protocol adapters that extend
 * {@link AbstractProtocolAdapterBase}.
 *
 * @param <T> The type of protocol adapter to test.
 * @param <C> The type of configuration properties the adapter uses.
 */
public abstract class ProtocolAdapterTestSupport<C extends ProtocolAdapterProperties, T extends AbstractProtocolAdapterBase<C>> extends ProtocolAdapterMockSupport {

    protected C properties;
    protected T adapter;

    /**
     * Creates default configuration properties for the adapter.
     *
     * @return The configuration properties.
     */
    protected abstract C givenDefaultConfigurationProperties();

    /**
     * Creates messaging clients from the downstream senders.
     *
     * @return The clients
     */
    protected MessagingClients createMessagingClients() {

        return new MessagingClients(
                new MessagingClient<TelemetrySender>().setClient(MessagingType.amqp, telemetrySender),
                new MessagingClient<EventSender>().setClient(MessagingType.amqp, eventSender),
                new MessagingClient<CommandResponseSender>().setClient(MessagingType.amqp, commandResponseSender));
    }

    /**
     * Sets the (mock) service clients on an adapter.
     *
     * @param adapter The adapter.
     */
    protected void setServiceClients(final T adapter) {
        adapter.setCommandConsumerFactory(commandConsumerFactory);
        adapter.setCredentialsClient(credentialsClient);
        adapter.setCommandRouterClient(commandRouterClient);
        adapter.setRegistrationClient(registrationClient);
        adapter.setTenantClient(tenantClient);
        adapter.setMessagingClients(createMessagingClients());
    }
}
