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
package org.eclipse.hono.telemetry;

import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.util.BaseMessageFilter;
import org.eclipse.hono.util.ResourceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A filter for verifying the format of <em>Telemetry</em> messages.
 */
public final class TelemetryMessageFilter extends BaseMessageFilter {

    private static final Logger LOG = LoggerFactory.getLogger(TelemetryMessageFilter.class);

    private TelemetryMessageFilter() {
        // prevent instantiation
    }

    /**
     * Checks whether a given telemetry message contains all required properties.
     * <p>
     * For successful verification, the message must meet the following conditions
     * <ul>
     * <li>All conditions defined by {@link #verifyStandardProperties(ResourceIdentifier, Message)}</li>
     * <li>The message must have its {@code content-type} property set.</li>
     * <li>The message must have an AMQP {@code Data} typed body.</li>
     * </ul>
     * 
     * @param linkTarget the link target address to match the telemetry message's properties against.
     * @param msg the message to verify.
     * @return {@code true} if the given message complies with the <em>Telemetry</em> API specification, {@code false}
     *         otherwise.
     */
     public static boolean verify(final ResourceIdentifier linkTarget, final Message msg) {
         if (!verifyStandardProperties(linkTarget, msg)) {
             return false;
         } else if (msg.getContentType() == null) {
             LOG.trace("message [{}] has no content type", msg.getMessageId());
             return false;
         } else if (msg.getBody() == null || !(msg.getBody() instanceof Data)) {
             LOG.trace("message [{}] has no body of type AMQP Data", msg.getMessageId());
             return false;
         } else {
             return true;
         }
    }
}
