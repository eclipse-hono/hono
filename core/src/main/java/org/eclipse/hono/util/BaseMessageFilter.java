/**
 * Copyright (c) 2016, 2018 Bosch Software Innovations GmbH.
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

    protected BaseMessageFilter () {
    }

    /**
     * Checks whether an AMQP message contains required standard properties.
     * <p>
     * This method checks if the message contains a valid device identifier using
     * the {@link #hasValidDeviceId(ResourceIdentifier, Message)} method.
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

         if (!hasValidDeviceId(linkTarget, msg)) {
             return false;
         } else {
             final ResourceIdentifier targetResource = ResourceIdentifier
                     .from(linkTarget.getEndpoint(), linkTarget.getTenantId(), MessageHelper.getDeviceId(msg));
             MessageHelper.annotate(msg, targetResource);
             return true;
         }
    }

     /**
      * Checks if an AMQP message contains a valid device identifier.
      * 
      * @param linkTarget The resource path to check the message's properties against for consistency.
      * @param msg The AMQP 1.0 message to perform the checks on.
      * @return {@code true} if the following conditions are met:
      *                      <ul>
      *                      <li>The message contains an application property {@link MessageHelper#APP_PROPERTY_DEVICE_ID}.</li>
      *                      <li>If the link target contains a resource ID, it matches the ID from the property.</li>
      *                      </ul>
      */
     protected static final boolean hasValidDeviceId(final ResourceIdentifier linkTarget, final Message msg) {

         final String deviceIdProperty = MessageHelper.getDeviceId(msg);

         if (deviceIdProperty == null) {
             LOG.trace("message [{}] contains no {} application property", msg.getMessageId(), MessageHelper.APP_PROPERTY_DEVICE_ID);
             return false;
         } else if (linkTarget.getResourceId() != null && !deviceIdProperty.equals(linkTarget.getResourceId())) {
             LOG.trace("message's {} property contains invalid device ID [expected: {}, but was: {}]",
                     MessageHelper.APP_PROPERTY_DEVICE_ID, linkTarget.getResourceId(), deviceIdProperty);
             return false;
         } else {
             return true;
         }
    }

     /**
      * Checks if an AMQP message contains either a message ID or a correlation ID.
      * 
      * @param msg The message.
      * @return {@code true} if the message has an ID that can be used for correlation.
      */
     protected static final boolean hasCorrelationId(final Message msg) {

         if (msg.getMessageId() == null && msg.getCorrelationId() == null) {
             LOG.trace("message has neither a message-id nor correlation-id");
             return false;
         } else {
             return true;
         }
     }
}
