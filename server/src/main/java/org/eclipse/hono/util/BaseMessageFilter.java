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
package org.eclipse.hono.util;

import org.apache.qpid.proton.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A basic filter for checking existence and correctness of mandatory message properties.
 *
 */
public class BaseMessageFilter {

    private static final Logger LOG = LoggerFactory.getLogger(BaseMessageFilter.class);

    /**
     * Checks whether a given registration message contains required standard properties.
     */
     protected static boolean verifyStandardProperties(final ResourceIdentifier linkTarget, final Message msg) {

         final String deviceIdProperty = MessageHelper.getDeviceId(msg);

         if (deviceIdProperty == null) {
             LOG.trace("message [{}] contains no {} application property", msg.getMessageId(), MessageHelper.APP_PROPERTY_DEVICE_ID);
             return false;
         } else if (linkTarget.getDeviceId() != null && !deviceIdProperty.equals(linkTarget.getDeviceId())) {
             LOG.trace("message property contains invalid device ID [expected: {}, but was: {}]",
                     linkTarget.getDeviceId(), deviceIdProperty);
             return false;
         } else {
             final ResourceIdentifier targetResource = ResourceIdentifier
                     .from(linkTarget.getEndpoint(), linkTarget.getTenantId(), deviceIdProperty);
             MessageHelper.annotate(msg, targetResource);
             return true;
         }
    }
}
