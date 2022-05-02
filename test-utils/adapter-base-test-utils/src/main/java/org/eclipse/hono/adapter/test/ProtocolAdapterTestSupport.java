/**
 * Copyright (c) 2020, 2022 Contributors to the Eclipse Foundation
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
import org.eclipse.hono.adapter.MessagingClientProviders;
import org.eclipse.hono.adapter.ProtocolAdapterProperties;
import org.eclipse.hono.client.command.CommandResponseSender;
import org.eclipse.hono.client.telemetry.EventSender;
import org.eclipse.hono.client.telemetry.TelemetrySender;
import org.eclipse.hono.client.util.MessagingClientProvider;

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
     * Creates messaging client providers from the downstream senders.
     *
     * @return The client providers.
     */
    protected MessagingClientProviders createMessagingClientProviders() {

        return new MessagingClientProviders(
                new MessagingClientProvider<TelemetrySender>().setClient(telemetrySender),
                new MessagingClientProvider<EventSender>().setClient(eventSender),
                new MessagingClientProvider<CommandResponseSender>().setClient(commandResponseSender));
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
        adapter.setMessagingClientProviders(createMessagingClientProviders());
    }
}
