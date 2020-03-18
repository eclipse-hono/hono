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

import io.opentracing.SpanContext;
import io.vertx.core.Future;
import io.vertx.proton.ProtonDelivery;

/**
 * Verifies behavior of {@link TraceableTelemetrySender}.
 *
 */
public class TraceableTelemetrySenderTest extends AbstractAmqpAdapterClientDownstreamSenderTestBase {

    /**
     * Verifies that the message produced by
     * {@link TraceableTelemetrySender#send(String, byte[], String, Map, SpanContext)} conforms to the expectations of
     * the AMQP adapter.
     */
    @Test
    public void testSendProducesValidMessage() {

        // GIVEN a TelemetrySender instance
        final TraceableTelemetrySender telemetrySender = ((TraceableTelemetrySender) AmqpAdapterClientTelemetrySenderImpl
                .createWithAnonymousLinkAddress(connection, tenantId, s -> {
                }).result());

        // WHEN sending a message using the API
        final Future<ProtonDelivery> deliveryFuture = telemetrySender.send(deviceId, payload, contentType,
                applicationProperties, null);

        // THEN the AMQP message produces by the client conforms to the expectations of the AMQP protocol adapter
        assertThat(deliveryFuture.succeeded());

        assertMessageConformsAmqpAdapterSpec("telemetry" + "/" + tenantId + "/" + deviceId);

    }

    /**
     * Verifies that the message produced by
     * {@link TraceableTelemetrySender#sendAndWaitForOutcome(String, byte[], String, Map, SpanContext)} conforms to the
     * expectations of the AMQP adapter.
     */
    @Test
    public void testSendAndWaitForOutcomeProducesValidMessage() {

        // GIVEN a TelemetrySender instance
        final TraceableTelemetrySender telemetrySender = ((TraceableTelemetrySender) AmqpAdapterClientTelemetrySenderImpl
                .createWithAnonymousLinkAddress(connection, tenantId, s -> {
                }).result());

        // WHEN sending a message using the API
        final Future<ProtonDelivery> deliveryFuture = telemetrySender.sendAndWaitForOutcome(deviceId, payload,
                contentType,
                applicationProperties, null);

        // THEN the AMQP message produces by the client conforms to the expectations of the AMQP protocol adapter
        assertThat(deliveryFuture.succeeded());

        assertMessageConformsAmqpAdapterSpec("telemetry" + "/" + tenantId + "/" + deviceId);

    }

}
