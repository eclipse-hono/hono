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

import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.util.BaseMessageFilter;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.RegistrationConstants;
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

         final String action = getAction(msg);
         if (!verifyProperties(action,linkTarget,msg)) {
             return false;
         } else if (msg.getMessageId() == null && msg.getCorrelationId() == null) {
             LOG.trace("message has neither a message-id nor correlation-id");
             return false;
         } else if (!RegistrationConstants.isValidAction(action)) {
             LOG.trace("message [{}] does not contain valid action property", msg.getMessageId());
             return false;
         } else if (msg.getReplyTo() == null) {
             LOG.trace("message [{}] contains no reply-to address", msg.getMessageId());
             return false;
         } else if (msg.getBody() != null) {
             if (msg.getContentType() == null || !msg.getContentType().startsWith("application/json")) {
                 LOG.trace("message [{}] content type is not JSON", msg.getMessageId());
                 return false;
             } else if (!(msg.getBody() instanceof AmqpValue)) {
                 LOG.trace("message [{}] contains non-AmqpValue section payload", msg.getMessageId());
                 return false;
             } else {
                 return true;
             }
         } else {
             return true;
         }
     }

     private static String getAction(final Message msg) {
         return MessageHelper.getApplicationProperty(
                 msg.getApplicationProperties(),
                 RegistrationConstants.APP_PROPERTY_ACTION,
                 String.class);
     }

    private static boolean verifyProperties(final String action, final ResourceIdentifier linkTarget, final Message msg) {
        if(!RegistrationConstants.ACTION_FIND.equals(action)) {
            return verifyStandardProperties(linkTarget,msg);
        }
        else {
            final String valueProperty = MessageHelper.getApplicationProperty(msg.getApplicationProperties(),
                    RegistrationConstants.APP_PROPERTY_VALUE, String.class);
            if (valueProperty == null) {
                LOG.trace("message [{}] contains no {} application property", msg.getMessageId(), RegistrationConstants.APP_PROPERTY_VALUE);
                return false;
            } else {
                final ResourceIdentifier targetResource = ResourceIdentifier
                        .from(linkTarget.getEndpoint(), linkTarget.getTenantId(), null);
                MessageHelper.annotate(msg, targetResource);
                return true;
            }
        }
    }


}
