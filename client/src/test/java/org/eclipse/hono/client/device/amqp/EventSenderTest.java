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

import org.eclipse.hono.client.AbstractAmqpAdapterClientDownstreamSenderTestBase;
import org.eclipse.hono.client.device.amqp.internal.AmqpAdapterClientEventSenderImpl;
import org.junit.jupiter.api.Test;

import io.vertx.core.Future;
import io.vertx.proton.ProtonDelivery;

/**
 * Verifies behavior of {@link EventSender}.
 *
 */
public class EventSenderTest extends AbstractAmqpAdapterClientDownstreamSenderTestBase {

    /**
     * Verifies that the message produced conforms to the expectations of the AMQP adapter.
     */
    @Test
    public void testMessageIsValid() {

        // GIVEN a EventSender instance
        final EventSender eventSender = AmqpAdapterClientEventSenderImpl
                .createWithAnonymousLinkAddress(connection, tenantId, s -> {
                }).result();

        // WHEN sending a message using the API
        final Future<ProtonDelivery> deliveryFuture = eventSender.send(deviceId, payload, contentType,
                applicationProperties);

        // THEN the AMQP message produces by the client conforms to the expectations of the AMQP protocol adapter
        assertThat(deliveryFuture.succeeded());

        assertMessageConformsAmqpAdapterSpec("event" + "/" + tenantId + "/" + deviceId);

    }

}
