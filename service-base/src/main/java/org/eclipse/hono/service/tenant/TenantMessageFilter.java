/**
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 1.0 which is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * SPDX-License-Identifier: EPL-1.0
 */

package org.eclipse.hono.service.tenant;

import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.util.BaseMessageFilter;
import org.eclipse.hono.util.ResourceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A filter for verifying the format of <em>Tenant API</em> request messages.
 */
public final class TenantMessageFilter extends BaseMessageFilter {

    private static final Logger LOG = LoggerFactory.getLogger(TenantMessageFilter.class);

    private TenantMessageFilter() {
        // prevent instantiation
    }

    /**
     * Checks whether a given tenant message contains all required properties.
     * 
     * @param linkTarget The resource path to check the message's properties against for consistency.
     * @param msg The AMQP 1.0 message to perform the checks on.
     * @return {@code true} if the message passes all checks.
     */
    public static boolean verify(final ResourceIdentifier linkTarget, final Message msg) {

        if (msg.getMessageId() == null && msg.getCorrelationId() == null) {
            LOG.trace("message has neither a message-id nor correlation-id");
            return false;
        } else if (msg.getSubject() == null) {
            LOG.trace("message [{}] does not contain subject", msg.getMessageId());
            return false;
        } else if (msg.getReplyTo() == null) {
            LOG.trace("message [{}] contains no reply-to address", msg.getMessageId());
            return false;
        } else if (msg.getBody() != null) {
            if (!(msg.getBody() instanceof AmqpValue)) {
                LOG.trace("message [{}] contains non-AmqpValue section payload", msg.getMessageId());
                return false;
            } else {
                return true;
            }
        } else {
            return true;
        }
    }
}
