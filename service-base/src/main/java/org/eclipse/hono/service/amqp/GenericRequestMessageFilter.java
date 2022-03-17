/*******************************************************************************
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.service.amqp;

import java.util.Optional;

import org.apache.qpid.proton.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A generic filter for checking existence and correctness of mandatory request message properties.
 *
 */
public class GenericRequestMessageFilter {

    private static final Logger LOG = LoggerFactory.getLogger(GenericRequestMessageFilter.class);

    /**
     * Empty default constructor.
     */
    protected GenericRequestMessageFilter() {
    }

    /**
     * Checks if a given AMQP 1.0 request message contains properties required for responding to it.
     *
     * @param msg The AMQP 1.0 message to perform the checks on.
     * @return {@code true} if the message's subject and reply-to properties have a non-empty value and the
     *         message's correlation-id or message-id property has a non-empty value.
     */
    public static boolean isValidRequestMessage(final Message msg) {

        final var correlationId = Optional.ofNullable(msg.getCorrelationId()).orElseGet(msg::getMessageId);

        if (correlationId == null) {
            LOG.trace("message has neither a message-id nor correlation-id");
            return false;
        } else if (msg.getSubject() == null) {
            LOG.trace("message [correlation ID: {}] does not contain a subject", correlationId);
            return false;
        } else if (msg.getReplyTo() == null) {
            LOG.trace("message [correlation ID: {}] contains no reply-to address", correlationId);
            return false;
        } else {
            return true;
        }
    }
}
