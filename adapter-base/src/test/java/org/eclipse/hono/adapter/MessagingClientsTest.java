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
 * Tests verifying behavior of {@link MessagingClients}.
 *
 */
public class MessagingClientsTest {

    private final MessagingClients underTest = new MessagingClients();
    private final String tenant = "tenant";
    private final MessagingClientSet amqpClientSet = new MessagingClientSet(MessagingType.amqp, mock(EventSender.class),
            mock(TelemetrySender.class), mock(CommandResponseSender.class));
    private final MessagingClientSet kafkaClientSet = new MessagingClientSet(MessagingType.kafka,
            mock(EventSender.class), mock(TelemetrySender.class), mock(CommandResponseSender.class));
    private final Map<String, String> extensionFieldKafka = Map.of(TenantConstants.FIELD_EXT_MESSAGING_TYPE,
            MessagingType.kafka.name());
    private final Map<String, String> extensionFieldAmqp = Map.of(TenantConstants.FIELD_EXT_MESSAGING_TYPE,
            MessagingType.amqp.name());

    /**
     * Verifies that {@link MessagingClients#isUnconfigured()} returns {@code true} if no client set has been added and
     * {@code false} otherwise.
     */
    @Test
    public void isUnconfigured() {
        assertThat(underTest.isUnconfigured()).isTrue();

        underTest.addClientSet(amqpClientSet);
        assertThat(underTest.isUnconfigured()).isFalse();
    }

    /**
     * Verifies that when the messaging to be used is configured for a tenant, then this is returned.
     */
    @Test
    public void testGetClientConfiguredOnTenant() {

        underTest.addClientSet(kafkaClientSet);
        underTest.addClientSet(amqpClientSet);

        assertThat(underTest.getClientSetForTenant(TenantObject.from(tenant, true).setProperty(
                TenantConstants.FIELD_EXT, extensionFieldKafka))).isEqualTo(kafkaClientSet);

        assertThat(underTest.getClientSetForTenant(TenantObject.from(tenant, true).setProperty(
                TenantConstants.FIELD_EXT, extensionFieldAmqp))).isEqualTo(amqpClientSet);

    }

    /**
     * Verifies that when no messaging type is configured for a tenant and only the Kafka client set is present, then
     * this one is used.
     */
    @Test
    public void testGetClientOnlyKafkaClientSet() {
        underTest.addClientSet(kafkaClientSet);

        assertThat(underTest.getClientSetForTenant(TenantObject.from(tenant, true))).isEqualTo(kafkaClientSet);
    }

    /**
     * Verifies that when no messaging type is configured for a tenant and only the AMQP client set is present, then
     * this one is used.
     */
    @Test
    public void testGetClientOnlyAmqpClientSet() {
        underTest.addClientSet(amqpClientSet);

        assertThat(underTest.getClientSetForTenant(TenantObject.from(tenant, true))).isEqualTo(amqpClientSet);
    }

    /**
     * Verifies that when no messaging type is configured for a tenant and multiple client sets are present, then the
     * default (i.e. the AMQP) client set is used.
     */
    @Test
    public void testGetClientDefault() {
        underTest.addClientSet(kafkaClientSet);
        underTest.addClientSet(amqpClientSet);

        assertThat(underTest.getClientSetForTenant(TenantObject.from(tenant, true))).isEqualTo(amqpClientSet);
    }

    /**
     * Verifies that the invocation of {@link MessagingClients#getClientSetForTenant(TenantObject)} throws an
     * {@link IllegalArgumentException} if no client set has been added.
     */
    @Test
    public void testGetClientSetThrowsIfNoClientSetPresent() {
        Assertions.assertThatIllegalStateException()
                .isThrownBy(() -> underTest.getClientSetForTenant(TenantObject.from(tenant, true)));
    }
}
