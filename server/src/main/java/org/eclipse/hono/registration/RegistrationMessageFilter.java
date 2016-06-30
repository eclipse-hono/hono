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

import static org.eclipse.hono.registration.RegistrationConstants.APP_PROPERTY_ACTION;

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.ResourceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A filter for verifying the format of <em>Registration</em> messages.
 */
public final class RegistrationMessageFilter {

    private static final Logger LOG = LoggerFactory.getLogger(RegistrationMessageFilter.class);

    private RegistrationMessageFilter() {
        // prevent instantiation
    }

    /**
     * Checks whether a given registration message contains all required properties.
     */
     public static boolean verify(final ResourceIdentifier linkTarget, final Message msg) {
         final String deviceIdProperty = MessageHelper.getDeviceId(msg);
         final String tenantIdProperty = MessageHelper.getTenantId(msg);
         final String actionProperty = MessageHelper.getApplicationProperty(msg.getApplicationProperties(),
                 APP_PROPERTY_ACTION, String.class);

         if (tenantIdProperty != null && !linkTarget.getTenantId().equals(tenantIdProperty)) {
             LOG.trace("message property contains invalid tenant ID [expected: {}, but was: {}]",
                     linkTarget.getTenantId(), tenantIdProperty);
             return false;
         } else if (deviceIdProperty == null) {
             LOG.trace("message [{}] contains no valid device ID", msg.getMessageId());
             return false;
         } else if (linkTarget.getDeviceId() != null && !deviceIdProperty.equals(linkTarget.getDeviceId())) {
             LOG.trace("message property contains invalid device ID [expected: {}, but was: {}]",
                     linkTarget.getDeviceId(), deviceIdProperty);
             return false;
         } else if (actionProperty == null) {
             LOG.trace("message [{}] contains no valid action.", msg.getMessageId());
             return false;
         } else if (msg.getMessageId() == null) {
             LOG.trace("message [{}] contains no valid message id.", msg.getMessageId());
             return false;
         } else if (msg.getReplyTo() == null) {
             LOG.trace("message [{}] contains no valid reply-to address.", msg.getMessageId());
             return false;
         } else {
             final ResourceIdentifier targetResource = ResourceIdentifier
                     .from(linkTarget.getEndpoint(), linkTarget.getTenantId(), deviceIdProperty);
             MessageHelper.annotate(msg, targetResource);
             return true;
         }
    }


}
