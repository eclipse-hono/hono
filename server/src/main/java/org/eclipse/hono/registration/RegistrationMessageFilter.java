/**
 * Copyright (c) 2016 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial creation
 */
package org.eclipse.hono.registration;

import static org.eclipse.hono.util.RegistrationConstants.APP_PROPERTY_ACTION;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.util.BaseMessageFilter;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.ResourceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A filter for verifying the format of <em>Registration</em> messages.
 */
public final class RegistrationMessageFilter extends BaseMessageFilter {

    private static final Logger LOG = LoggerFactory.getLogger(RegistrationMessageFilter.class);

    private RegistrationMessageFilter() {
        // prevent instantiation
    }

    /**
     * Checks whether a given registration message contains all required properties.
     * 
     * @param linkTarget The resource path to check the message's properties against for consistency.
     * @param msg The AMQP 1.0 message to perform the checks on.
     * @return {@code true} if the message passes all checks.
     */
     public static boolean verify(final ResourceIdentifier linkTarget, final Message msg) {

         final String actionProperty = MessageHelper.getApplicationProperty(msg.getApplicationProperties(),
                 APP_PROPERTY_ACTION, String.class);

         if (!verifyStandardProperties(linkTarget, msg)) {
             return false;
         } else if (msg.getMessageId() == null && msg.getCorrelationId() == null) {
             LOG.trace("message has neither a message-id nor correlation-id");
             return false;
         } else if (actionProperty == null) {
             LOG.trace("message [{}] contains no action property", msg.getMessageId());
             return false;
         } else if (msg.getReplyTo() == null) {
             LOG.trace("message [{}] contains no reply-to address", msg.getMessageId());
             return false;
         } else {
             return true;
         }
    }


}
