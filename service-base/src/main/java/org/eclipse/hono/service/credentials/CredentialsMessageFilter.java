/**
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */

package org.eclipse.hono.service.credentials;

import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.util.BaseMessageFilter;
import org.eclipse.hono.util.CredentialsConstants;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.ResourceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A filter for verifying the format of <em>Credentials</em> messages.
 */
public final class CredentialsMessageFilter  extends BaseMessageFilter {
    private static final Logger LOG = LoggerFactory.getLogger(CredentialsMessageFilter.class);

    private CredentialsMessageFilter() {
        // prevent instantiation
    }

    /**
     * Checks whether a given credentials message contains all required properties.
     *
     * @param linkTarget The resource path to check the message's properties against for consistency.
     * @param msg The AMQP 1.0 message to perform the checks on.
     * @return {@code true} if the message passes all checks.
     */
    public static boolean verify(final ResourceIdentifier linkTarget, final Message msg) {
        if (!hasValidSubject(msg)) {
            LOG.trace("message [{}] does not contain valid subject property", msg.getMessageId());
            return false;
        } else if (msg.getMessageId() == null && msg.getCorrelationId() == null) {
            LOG.trace("message has neither a message-id nor correlation-id");
            return false;
        } else if (!verifySingleAmqpValueMessage(linkTarget,msg)) {
            return false;
        } else {
            // message is annotated with parts of the endpoint (e.g. the tenant) to retrieve it later for further evaluations
            final ResourceIdentifier targetResource = ResourceIdentifier
                    .from(linkTarget.getEndpoint(), linkTarget.getTenantId(), null);
            MessageHelper.annotate(msg, targetResource);
            return true;
        }
    }

    protected static final boolean verifySingleAmqpValueMessage(final ResourceIdentifier linkTarget, final Message msg) {
        if (!(msg.getBody() instanceof AmqpValue)) {
            LOG.trace("message [{}] contains non-AmqpValue section payload", msg.getMessageId());
            return false;
        }
        return true;
    }

    private static boolean hasValidSubject(final Message msg) {
        String subject = msg.getSubject();
        return CredentialsConstants.isValidSubject(subject);
    }
}
