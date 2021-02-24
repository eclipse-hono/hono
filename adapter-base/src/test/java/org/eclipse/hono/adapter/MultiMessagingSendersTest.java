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
import org.eclipse.hono.adapter.client.telemetry.EventSender;
import org.eclipse.hono.util.TenantConstants;
import org.eclipse.hono.util.TenantObject;
import org.junit.jupiter.api.Test;

/**
 * Tests verifying behavior of {@link MultiMessagingSenders}.
 *
 */
public class MultiMessagingSendersTest {

    private final String tenant = "tenant";
    private final Map<String, String> extensionFieldKafka = Map.of(TenantConstants.FIELD_EXT_MESSAGING_TYPE,
            TenantConstants.MESSAGING_TYPE_KAFKA);
    private final Map<String, String> extensionFieldAmqp = Map.of(TenantConstants.FIELD_EXT_MESSAGING_TYPE,
            TenantConstants.MESSAGING_TYPE_AMQP);
    private final EventSender amqpSender = mock(EventSender.class);
    private final EventSender kafkaSender = mock(EventSender.class);
    private final MultiMessagingSenders<EventSender> underTest = new MultiMessagingSenders<>();

    /**
     * Verifies that {@link MultiMessagingSenders#isUnconfigured()} returns {@code true} if no sender is set and
     * {@code false} otherwise.
     */
    @Test
    public void isUnconfigured() {
        assertThat(underTest.isUnconfigured()).isTrue();

        underTest.setAmqpSender(amqpSender);
        assertThat(underTest.isUnconfigured()).isFalse();
    }

    /**
     * Verifies that when the messaging to be used is configured for a tenant, then this is used.
     */
    @Test
    public void testGetSenderConfiguredOnTenant() {

        underTest.setKafkaSender(kafkaSender);
        underTest.setAmqpSender(amqpSender);

        assertThat(underTest.getSenderForTenantOrDefault(TenantObject.from(tenant, true).setProperty(
                TenantConstants.FIELD_EXT, extensionFieldKafka))).isEqualTo(kafkaSender);

        assertThat(underTest.getSenderForTenantOrDefault(TenantObject.from(tenant, true).setProperty(
                TenantConstants.FIELD_EXT, extensionFieldAmqp))).isEqualTo(amqpSender);

    }

    /**
     * Verifies that when no messaging type is configured for a tenant and only the Kafka sender is set, then this one
     * is used.
     */
    @Test
    public void testGetSenderOnlyKafkaSenderSet() {
        underTest.setKafkaSender(kafkaSender);

        assertThat(underTest.getSenderForTenantOrDefault(TenantObject.from(tenant, true))).isEqualTo(kafkaSender);
    }

    /**
     * Verifies that when no messaging type is configured for a tenant and only the AMQP sender is set, then this one is
     * used.
     */
    @Test
    public void testGetSenderOnlyAmqpSenderSet() {
        underTest.setAmqpSender(amqpSender);

        assertThat(underTest.getSenderForTenantOrDefault(TenantObject.from(tenant, true))).isEqualTo(amqpSender);
    }

    /**
     * Verifies that when no messaging type is configured for a tenant and both senders are set, then the AMQP sender is
     * used.
     */
    @Test
    public void testGetSenderDefault() {
        underTest.setKafkaSender(kafkaSender);
        underTest.setAmqpSender(amqpSender);

        assertThat(underTest.getSenderForTenantOrDefault(TenantObject.from(tenant, true))).isEqualTo(amqpSender);
    }

    /**
     * Verifies that the invocation of {@link MultiMessagingSenders#getSenderForTenantOrDefault(TenantObject)} throws an
     * {@link IllegalArgumentException} if no sender has been set.
     */
    @Test
    public void testGetSenderThrowsIfNoSenderSet() {
        Assertions.assertThatIllegalStateException()
                .isThrownBy(() -> underTest.getSenderForTenantOrDefault(TenantObject.from(tenant, true)));
    }

}
