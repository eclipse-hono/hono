/*
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
 */

package org.eclipse.hono.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.Map;

import org.assertj.core.api.Assertions;
import org.eclipse.hono.adapter.client.command.CommandResponseSender;
import org.eclipse.hono.adapter.client.telemetry.EventSender;
import org.eclipse.hono.adapter.client.telemetry.TelemetrySender;
import org.eclipse.hono.util.MessagingType;
import org.eclipse.hono.util.TenantConstants;
import org.eclipse.hono.util.TenantObject;
import org.junit.jupiter.api.Test;

/**
 * Tests verifying behavior of {@link MessagingClientSet}.
 *
 */
public class MessagingClientSetTest {

    private final MessagingClientSet underTest = new MessagingClientSet();
    private final String tenant = "tenant";
    private final MessagingClient amqpClient = new MessagingClient(MessagingType.amqp, mock(EventSender.class),
            mock(TelemetrySender.class), mock(CommandResponseSender.class));
    private final MessagingClient kafkaClient = new MessagingClient(MessagingType.kafka, mock(EventSender.class),
            mock(TelemetrySender.class), mock(CommandResponseSender.class));
    private final Map<String, String> extensionFieldKafka = Map.of(TenantConstants.FIELD_EXT_MESSAGING_TYPE,
            MessagingType.kafka.name());
    private final Map<String, String> extensionFieldAmqp = Map.of(TenantConstants.FIELD_EXT_MESSAGING_TYPE,
            MessagingType.amqp.name());

    /**
     * Verifies that {@link MessagingClientSet#isUnconfigured()} returns {@code true} if no client is set and
     * {@code false} otherwise.
     */
    @Test
    public void isUnconfigured() {
        assertThat(underTest.isUnconfigured()).isTrue();

        underTest.addClient(amqpClient);
        assertThat(underTest.isUnconfigured()).isFalse();
    }

    /**
     * Verifies that when the messaging to be used is configured for a tenant, then this is returned.
     */
    @Test
    public void testGetClientConfiguredOnTenant() {

        underTest.addClient(kafkaClient);
        underTest.addClient(amqpClient);

        assertThat(underTest.getClientForTenant(TenantObject.from(tenant, true).setProperty(
                TenantConstants.FIELD_EXT, extensionFieldKafka))).isEqualTo(kafkaClient);

        assertThat(underTest.getClientForTenant(TenantObject.from(tenant, true).setProperty(
                TenantConstants.FIELD_EXT, extensionFieldAmqp))).isEqualTo(amqpClient);

    }

    /**
     * Verifies that when no messaging type is configured for a tenant and only the Kafka client is set, then this one
     * is used.
     */
    @Test
    public void testGetClientOnlyKafkaClientSet() {
        underTest.addClient(kafkaClient);

        assertThat(underTest.getClientForTenant(TenantObject.from(tenant, true))).isEqualTo(kafkaClient);
    }

    /**
     * Verifies that when no messaging type is configured for a tenant and only the AMQP client is set, then this one is
     * used.
     */
    @Test
    public void testGetClientOnlyAmqpClientSet() {
        underTest.addClient(amqpClient);

        assertThat(underTest.getClientForTenant(TenantObject.from(tenant, true))).isEqualTo(amqpClient);
    }

    /**
     * Verifies that when no messaging type is configured for a tenant and both clients are set, then the AMQP client is
     * used.
     */
    @Test
    public void testGetClientDefault() {
        underTest.addClient(kafkaClient);
        underTest.addClient(amqpClient);

        assertThat(underTest.getClientForTenant(TenantObject.from(tenant, true))).isEqualTo(amqpClient);
    }

    /**
     * Verifies that the invocation of {@link MessagingClientSet#getClientForTenant(TenantObject)} throws an
     * {@link IllegalArgumentException} if no client has been set.
     */
    @Test
    public void testGetClientThrowsIfNoClientSet() {
        Assertions.assertThatIllegalStateException()
                .isThrownBy(() -> underTest.getClientForTenant(TenantObject.from(tenant, true)));
    }
}
