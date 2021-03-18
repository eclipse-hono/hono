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
package org.eclipse.hono.tests;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.hono.application.client.DownstreamMessage;
import org.eclipse.hono.application.client.MessageContext;
import org.eclipse.hono.application.client.amqp.AmqpMessageContext;


/**
 * A utility class encapsulating verifications which shall only be made on {@link AmqpMessageContext} based
 * {@link DownstreamMessage}s.
 */
public final class AmqpMessageContextConditionalVerifier {

    private AmqpMessageContextConditionalVerifier() {
    }

    /**
     * Verify that the raw AMQP message of the given message's context is durable.
     *
     * @param msg The message to be verified.
     */
    public static void assertMessageIsDurable(final DownstreamMessage<? extends MessageContext> msg) {
        if (msg.getMessageContext() instanceof AmqpMessageContext) {
            final AmqpMessageContext amqpMessageContext = (AmqpMessageContext) msg.getMessageContext();
            assertThat(amqpMessageContext.getRawMessage().isDurable()).isTrue();
        }
    }

}
