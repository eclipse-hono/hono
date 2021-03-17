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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.Map;

import org.eclipse.hono.adapter.client.command.CommandResponseSender;
import org.eclipse.hono.adapter.client.telemetry.EventSender;
import org.eclipse.hono.adapter.client.telemetry.TelemetrySender;
import org.eclipse.hono.client.util.MessagingClient;
import org.eclipse.hono.util.MessagingType;
import org.eclipse.hono.util.TenantConstants;
import org.eclipse.hono.util.TenantObject;
import org.junit.jupiter.api.Test;

/**
 * Tests verifying behavior of {@link MessagingClients}.
 *
 */
public class MessagingClientsTest {

    private static final String TENANT = "tenant";

    private final TelemetrySender kafkaTelemetrySender = mock(TelemetrySender.class);
    private final EventSender amqpEventSender = mock(EventSender.class);

    private final MessagingClient<TelemetrySender> telemetrySenders = new MessagingClient<TelemetrySender>()
            .setClient(MessagingType.amqp, mock(TelemetrySender.class))
            .setClient(MessagingType.kafka, kafkaTelemetrySender);

    private final MessagingClient<EventSender> eventSenders = new MessagingClient<EventSender>()
            .setClient(MessagingType.amqp, amqpEventSender)
            .setClient(MessagingType.kafka, mock(EventSender.class));
    private final MessagingClient<CommandResponseSender> commandResponseSenders = new MessagingClient<CommandResponseSender>()
            .setClient(MessagingType.amqp, mock(CommandResponseSender.class))
            .setClient(MessagingType.kafka, mock(CommandResponseSender.class));

    private final Map<String, String> extensionFieldKafka = Map.of(TenantConstants.FIELD_EXT_MESSAGING_TYPE,
            MessagingType.kafka.name());
    private final Map<String, String> extensionFieldAmqp = Map.of(TenantConstants.FIELD_EXT_MESSAGING_TYPE,
            MessagingType.amqp.name());

    /**
     * Verifies that when the messaging to be used is configured for a tenant, then this is returned.
     */
    @Test
    public void testGetClientConfiguredOnTenant() {

        final MessagingClients underTest = new MessagingClients(telemetrySenders, eventSenders, commandResponseSenders);

        assertThat(underTest.getTelemetrySender(TenantObject.from(TENANT, true).setProperty(
                TenantConstants.FIELD_EXT, extensionFieldKafka))).isEqualTo(kafkaTelemetrySender);

        assertThat(underTest.getEventSender(TenantObject.from(TENANT, true).setProperty(
                TenantConstants.FIELD_EXT, extensionFieldAmqp))).isEqualTo(amqpEventSender);
    }
}
