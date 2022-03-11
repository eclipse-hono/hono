/*******************************************************************************
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.hono.service.registration;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.service.BaseMessageFilter;
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

         final Object correlationId = MessageHelper.getCorrelationId(msg);

         if (!hasValidDeviceId(linkTarget, msg)) {
             return false;
         } else if (correlationId == null) {
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
