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

import org.apache.qpid.proton.message.Message;
import org.eclipse.hono.util.MessageHelper;
import org.eclipse.hono.util.ResourceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A filter for verifying the format of <em>Telemetry</em> messages.
 */
public final class TelemetryMessageFilter {

    private static final Logger LOG = LoggerFactory.getLogger(TelemetryMessageFilter.class);

    private TelemetryMessageFilter() {
        // prevent instantiation
    }

    /**
     * Checks whether a given telemetry message contains all required properties.
     * <p>
     * If verification is successful, the following two properties are added to the message's
     * <em>annotations</em>:
     * </p>
     * <ul>
     * <li><em>device_id</em> - the ID of the device that reported the data.</li>
     * <li><em>tenant_id</em> - the ID of the tenant the device belongs to.</li>
     * </ul>
     * 
     * @param linkTarget the link target address to match the telemetry message's address against.
     * @param msg the message to verify.
     * @return {@code true} if the given message complies with the <em>Telemetry</em> API specification, {@code false}
     *         otherwise.
     */
     public static boolean verify(final ResourceIdentifier linkTarget, final Message msg) {
         final String deviceIdProperty = MessageHelper.getDeviceId(msg);
         final String tenantIdProperty = MessageHelper.getTenantId(msg);

         if (tenantIdProperty != null && !linkTarget.getTenantId().equals(tenantIdProperty)) {
             LOG.trace("message property contains invalid tenant ID [expected: {}, but was: {}]",
                     linkTarget.getTenantId(), tenantIdProperty);
             return false;
         } else if (deviceIdProperty == null) {
             LOG.trace("message [{}] contains no valid device ID", msg.getMessageId());
             return false;
         } else {
             final ResourceIdentifier targetResource = ResourceIdentifier
                     .from(linkTarget.getEndpoint(), linkTarget.getTenantId(), deviceIdProperty);
             MessageHelper.annotate(msg, targetResource);
             return true;
         }
    }
}
