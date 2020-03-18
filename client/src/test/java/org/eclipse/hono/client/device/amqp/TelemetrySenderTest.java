/*******************************************************************************
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.hono.client.device.amqp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.eclipse.hono.client.AbstractAmqpAdapterClientDownstreamSenderTestBase;
import org.eclipse.hono.client.device.amqp.internal.AmqpAdapterClientTelemetrySenderImpl;
import org.junit.jupiter.api.Test;

import io.vertx.core.Future;
import io.vertx.proton.ProtonDelivery;

/**
 * Verifies behavior of {@link TelemetrySender}.
 *
 */
public class TelemetrySenderTest extends AbstractAmqpAdapterClientDownstreamSenderTestBase {

    /**
     * Verifies that the message produced by {@link TelemetrySender#send(String, byte[], String, Map)} conforms to the
     * expectations of the AMQP adapter.
     */
    @Test
    public void testSendProducesValidMessage() {

        // GIVEN a TelemetrySender instance
        final TelemetrySender telemetrySender = AmqpAdapterClientTelemetrySenderImpl
                .createWithAnonymousLinkAddress(connection, tenantId, s -> {
                }).result();

        // WHEN sending a message using the API
        final Future<ProtonDelivery> deliveryFuture = telemetrySender.send(deviceId, payload, contentType,
                applicationProperties);

        // THEN the AMQP message produces by the client conforms to the expectations of the AMQP protocol adapter
        assertThat(deliveryFuture.succeeded());

        assertMessageConformsAmqpAdapterSpec("telemetry" + "/" + tenantId + "/" + deviceId);

    }

    /**
     * Verifies that the message produced by {@link TelemetrySender#sendAndWaitForOutcome(String, byte[], String, Map)}
     * conforms to the expectations of the AMQP adapter.
     */
    @Test
    public void testSendAndWaitForOutcomeProducesValidMessage() {

        // GIVEN a TelemetrySender instance
        final TelemetrySender telemetrySender = AmqpAdapterClientTelemetrySenderImpl
                .createWithAnonymousLinkAddress(connection, tenantId, s -> {
                }).result();

        // WHEN sending a message using the API
        final Future<ProtonDelivery> deliveryFuture = telemetrySender.sendAndWaitForOutcome(deviceId, payload,
                contentType,
                applicationProperties);

        // THEN the AMQP message produces by the client conforms to the expectations of the AMQP protocol adapter
        assertThat(deliveryFuture.succeeded());

        assertMessageConformsAmqpAdapterSpec("telemetry" + "/" + tenantId + "/" + deviceId);

    }

}
