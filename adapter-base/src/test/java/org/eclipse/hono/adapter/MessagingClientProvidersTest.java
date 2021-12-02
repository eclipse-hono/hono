/*******************************************************************************
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.adapter;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static com.google.common.truth.Truth.assertThat;

import java.util.Map;

import org.eclipse.hono.client.command.CommandResponseSender;
import org.eclipse.hono.client.telemetry.EventSender;
import org.eclipse.hono.client.telemetry.TelemetrySender;
import org.eclipse.hono.client.util.MessagingClientProvider;
import org.eclipse.hono.util.MessagingClient;
import org.eclipse.hono.util.MessagingType;
import org.eclipse.hono.util.TenantConstants;
import org.eclipse.hono.util.TenantObject;
import org.junit.jupiter.api.Test;

/**
 * Tests verifying behavior of {@link MessagingClientProviders}.
 *
 */
public class MessagingClientProvidersTest {

    private static final String TENANT = "tenant";

    private final TelemetrySender kafkaTelemetrySender = mockMessagingClient(TelemetrySender.class, MessagingType.kafka);
    private final EventSender amqpEventSender = mockMessagingClient(EventSender.class, MessagingType.amqp);

    private final MessagingClientProvider<TelemetrySender> telemetrySenderProvider = new MessagingClientProvider<TelemetrySender>()
            .setClient(mockMessagingClient(TelemetrySender.class, MessagingType.amqp))
            .setClient(kafkaTelemetrySender);

    private final MessagingClientProvider<EventSender> eventSenderProvider = new MessagingClientProvider<EventSender>()
            .setClient(amqpEventSender)
            .setClient(mockMessagingClient(EventSender.class, MessagingType.kafka));
    private final MessagingClientProvider<CommandResponseSender> commandResponseSenderProvider = new MessagingClientProvider<CommandResponseSender>()
            .setClient(mockMessagingClient(CommandResponseSender.class, MessagingType.amqp))
            .setClient(mockMessagingClient(CommandResponseSender.class, MessagingType.kafka));

    private final Map<String, String> extensionFieldKafka = Map.of(TenantConstants.FIELD_EXT_MESSAGING_TYPE,
            MessagingType.kafka.name());
    private final Map<String, String> extensionFieldAmqp = Map.of(TenantConstants.FIELD_EXT_MESSAGING_TYPE,
            MessagingType.amqp.name());

    /**
     * Verifies that when the messaging to be used is configured for a tenant, then this is returned.
     */
    @Test
    public void testGetClientConfiguredOnTenant() {

        final MessagingClientProviders underTest = new MessagingClientProviders(telemetrySenderProvider,
                eventSenderProvider, commandResponseSenderProvider);

        assertThat(underTest.getTelemetrySender(TenantObject.from(TENANT, true).setProperty(
                TenantConstants.FIELD_EXT, extensionFieldKafka))).isEqualTo(kafkaTelemetrySender);

        assertThat(underTest.getEventSender(TenantObject.from(TENANT, true).setProperty(
                TenantConstants.FIELD_EXT, extensionFieldAmqp))).isEqualTo(amqpEventSender);
    }

    private <T extends MessagingClient> T mockMessagingClient(final Class<T> messagingClientClass,
            final MessagingType messagingType) {
        final T mock = mock(messagingClientClass);
        when(mock.getMessagingType()).thenReturn(messagingType);
        return mock;
    }
}
