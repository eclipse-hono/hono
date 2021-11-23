/**
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
package org.eclipse.hono.tests;

import static com.google.common.truth.Truth.assertWithMessage;

import org.eclipse.hono.application.client.DownstreamMessage;
import org.eclipse.hono.application.client.MessageContext;
import org.eclipse.hono.application.client.amqp.AmqpMessageContext;


/**
 * A utility class encapsulating assertions on {@link DownstreamMessage}s.
 */
public final class DownstreamMessageAssertions {

    private DownstreamMessageAssertions() {
        // prevent instantiation
    }

    /**
     * Verifies that an AMQP message is durable.
     *
     * @param msg The message to be verified.
     * @throws AssertionError if the given message is an AMQP message but is not durable.
     */
    public static void assertMessageIsDurable(final DownstreamMessage<? extends MessageContext> msg) {
        if (msg.getMessageContext() instanceof AmqpMessageContext) {
            final AmqpMessageContext amqpMessageContext = (AmqpMessageContext) msg.getMessageContext();
            assertWithMessage("message is durable").that(amqpMessageContext.getRawMessage().isDurable()).isTrue();
        }
    }

    /**
     * Verifies that a downstream message contains a creation-time.
     *
     * @param msg The message to check.
     * @throws AssertionError if any of the checks fails.
     */
    public static void assertMessageContainsCreationTime(final DownstreamMessage<? extends MessageContext> msg) {
        assertWithMessage("message contains creation time").that(msg.getCreationTime()).isNotNull();
    }

    /**
     * Verifies that a telemetry message that has been received by a downstream consumer contains
     * all properties that are required by the north bound Telemetry API.
     *
     * @param msg The message to check.
     * @param expectedTenantId The identifier of the tenant that the origin device is expected to belong to.
     * @throws AssertionError if any of the checks fail.
     */
    public static void assertTelemetryMessageProperties(
            final DownstreamMessage<? extends MessageContext> msg,
            final String expectedTenantId) {

        assertWithMessage("message has expected tenant ID").that(msg.getTenantId()).isEqualTo(expectedTenantId);
        assertWithMessage("message has device ID").that(msg.getDeviceId()).isNotNull();
        final var ttdValue = msg.getTimeTillDisconnect();
        if (ttdValue != null) {
            assertWithMessage("ttd property value").that(ttdValue).isAtLeast(-1);
            assertWithMessage("message creation time").that(msg.getCreationTime()).isNotNull();
        }
    }
}
