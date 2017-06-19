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
     * <p>
     * In particular, the following conditions need to be met in order for the message to pass:
     * <ol>
     * <li>The message contains an application property {@link MessageHelper#APP_PROPERTY_DEVICE_ID}.</li>
     * <li>If the given link target contains a device id in its path, it must match the id from the property.</li>
     * </ol>
     * <p>
     * After successful verification the following properties are added to the message's <em>annotations</em>:
     * <ul>
     * <li>{@link MessageHelper#APP_PROPERTY_DEVICE_ID} - the ID of the device that reported the data.</li>
     * <li>{@link MessageHelper#APP_PROPERTY_TENANT_ID} - the ID of the tenant as indicated by the link target's second segment.</li>
     * <li>{@link MessageHelper#APP_PROPERTY_RESOURCE} - the full resource path including the endpoint, the tenant and the device ID.</li>
     * </ul>
     * 
     * @param linkTarget The resource path to check the message's properties against for consistency.
     * @param msg The AMQP 1.0 message to perform the checks on.
     * @return {@code true} if the message passes all checks.
     */
     protected static final boolean verifyStandardProperties(final ResourceIdentifier linkTarget, final Message msg) {

         final String deviceIdProperty = MessageHelper.getDeviceId(msg);

         if (deviceIdProperty == null) {
             LOG.trace("message [{}] contains no {} application property", msg.getMessageId(), MessageHelper.APP_PROPERTY_DEVICE_ID);
             return false;
         } else if (linkTarget.getResourceId() != null && !deviceIdProperty.equals(linkTarget.getResourceId())) {
             LOG.trace("message property contains invalid device ID [expected: {}, but was: {}]",
                     linkTarget.getResourceId(), deviceIdProperty);
             return false;
         } else {
             final ResourceIdentifier targetResource = ResourceIdentifier
                     .from(linkTarget.getEndpoint(), linkTarget.getTenantId(), deviceIdProperty);
             MessageHelper.annotate(msg, targetResource);
             return true;
         }
    }
}
