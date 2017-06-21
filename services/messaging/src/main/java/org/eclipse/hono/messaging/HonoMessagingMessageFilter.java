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
package org.eclipse.hono.messaging;

import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.util.BaseMessageFilter;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.ResourceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A filter for verifying the format of <em>Telemetry</em> messages.
 */
public final class HonoMessagingMessageFilter extends BaseMessageFilter {

    private static final Logger LOG = LoggerFactory.getLogger(HonoMessagingMessageFilter.class);

    private HonoMessagingMessageFilter() {
        // prevent instantiation
    }

    /**
     * Checks whether a given message meets all formal requirements for processing.
     * <p>
     * For successful verification, the message must meet the following conditions
     * <ul>
     * <li>All conditions defined by {@link #verifyStandardProperties(ResourceIdentifier, Message)}</li>
     * <li>The message must contain a {@link MessageHelper#APP_PROPERTY_REGISTRATION_ASSERTION} application property.</li>
     * <li>The message must have its {@code content-type} property set.</li>
     * <li>The message must have a body of type AMQP {@code Data}.</li>
     * </ul>
     * <p>
     * After successful verification the following properties are added to the message's <em>annotations</em>:
     * <ul>
     * <li>{@link MessageHelper#APP_PROPERTY_DEVICE_ID} - the ID of the device that reported the data.</li>
     * <li>{@link MessageHelper#APP_PROPERTY_TENANT_ID} - the ID of the tenant as indicated by the link target's second segment.</li>
     * <li>{@link MessageHelper#APP_PROPERTY_RESOURCE} - the full resource path including the endpoint, the tenant and the device ID.</li>
     * </ul>
     * 
     * @param linkTarget the link target address to match the telemetry message's properties against.
     * @param msg the message to verify.
     * @return {@code true} if the given message meets all requirements, {@code false}
     *         otherwise.
     */
     public static boolean verify(final ResourceIdentifier linkTarget, final Message msg) {

         if (MessageHelper.getRegistrationAssertion(msg) == null) {
             LOG.trace("message [{}] contains no {} application property", msg.getMessageId(), MessageHelper.APP_PROPERTY_REGISTRATION_ASSERTION);
             return false;
         } else if (msg.getContentType() == null) {
             LOG.trace("message [{}] has no content type", msg.getMessageId());
             return false;
         } else if (msg.getBody() == null || !(msg.getBody() instanceof Data)) {
             LOG.trace("message [{}] has no body of type AMQP Data", msg.getMessageId());
             return false;
         } else {
             return verifyStandardProperties(linkTarget, msg);
         }
    }
}
