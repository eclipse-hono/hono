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
     public static boolean verify(final ResourceIdentifier linkTarget, final ResourceIdentifier messageAddress, final Message msg) {
        if (messageAddress == null
                || !RegistrationConstants.REGISTRATION_ENDPOINT.equals(messageAddress.getEndpoint())) {
            LOG.trace("message [id: {}] has no registration endpoint address [to: {}]", msg.getMessageId(),
                    messageAddress);
            return false;
        } else {
            return hasValidAddress(linkTarget, messageAddress, msg) && hasValidProperties(msg);
        }
    }

    private static boolean hasValidProperties(final Message msg) {
        return msg.getApplicationProperties() != null
                && msg.getApplicationProperties().getValue().containsKey(MessageHelper.APP_PROPERTY_TENANT_ID)
                && msg.getApplicationProperties().getValue().containsKey(MessageHelper.APP_PROPERTY_DEVICE_ID);
    }

    private static boolean hasValidAddress(final ResourceIdentifier linkTarget, final ResourceIdentifier messageAddress,
            final Message msg) {
        if (linkTarget.getTenantId().equals(messageAddress.getTenantId())) {
            MessageHelper.addAnnotation(msg, MessageHelper.APP_PROPERTY_TENANT_ID, messageAddress.getTenantId());
            MessageHelper.addAnnotation(msg, MessageHelper.APP_PROPERTY_DEVICE_ID, messageAddress.getDeviceId());
            return true;
        } else {
            LOG.trace("message address contains invalid tenant ID [expected: {}, but was: {}]", linkTarget.getTenantId(),
                    messageAddress.getTenantId());
            return false;
        }
    }

}
