/**
 * Copyright (c) 2016, 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.service.tenant;

import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A filter for verifying the format of <em>Tenant</em> messages.
 */
public final class TenantMessageFilter extends BaseMessageFilter {

    private static final Logger LOG = LoggerFactory.getLogger(TenantMessageFilter.class);

    private TenantMessageFilter() {
        // prevent instantiation
    }

    // TODO discuss the properties that should be required
    /**
     * Checks whether a given tenant message contains all required properties.
     * 
     * @param linkTarget The resource path to check the message's properties against for consistency.
     * @param msg The AMQP 1.0 message to perform the checks on.
     * @return {@code true} if the message passes all checks.
     */
    public static boolean verify(final ResourceIdentifier linkTarget, final Message msg) {

        if (MessageHelper.getTenantId(msg) == null) {
            LOG.trace("message [{}] does not contain a tenant-id", msg.getMessageId());
            return false;
        } else if (msg.getMessageId() == null && msg.getCorrelationId() == null) {
            LOG.trace("message has neither a message-id nor correlation-id");
            return false;
        } else if (!TenantConstants.isValidAction(msg.getSubject())) {
            LOG.trace("message [{}] does not contain valid action property", msg.getMessageId());
            return false;
        } else if (msg.getReplyTo() == null) {
            LOG.trace("message [{}] contains no reply-to address", msg.getMessageId());
            return false;
        } else if (msg.getBody() != null) {
            if (!(msg.getBody() instanceof AmqpValue)) {
                LOG.trace("message [{}] contains non-AmqpValue section payload", msg.getMessageId());
                return false;
            } else {
                annotate(linkTarget, msg);
                return true;
            }
        } else {
            annotate(linkTarget, msg);
            return true;
        }
    }

    private static void annotate(final ResourceIdentifier linkTarget, final Message msg) {
        final ResourceIdentifier targetResource = ResourceIdentifier
                .from(linkTarget.getEndpoint(), linkTarget.getTenantId(), null);
        MessageHelper.annotate(msg, targetResource);
    }
}
