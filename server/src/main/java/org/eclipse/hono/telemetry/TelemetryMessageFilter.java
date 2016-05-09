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
     * <li><em>device-id</em> - the ID of the device that reported the data.</li>
     * <li><em>tenant-id</em> - the ID of the tenant the device belongs to.</li>
     * </ul>
     * 
     * @param linkTarget the link target address to match the telemetry message's address against.
     * @param messageAddress the resource identifier representing the message's address as taken from its
     *                       <em>to</em> property.
     * @param msg the message to verify.
     * @return {@code true} if the given message complies with the <em>Telemetry</em> API specification, {@code false}
     *         otherwise.
     */
     public static boolean verify(final ResourceIdentifier linkTarget, final ResourceIdentifier messageAddress, final Message msg) {
        if (messageAddress == null
                || !TelemetryConstants.TELEMETRY_ENDPOINT.equals(messageAddress.getEndpoint())) {
            LOG.trace("message [id: {}] has no telemetry endpoint address [to: {}]", msg.getMessageId(),
                    messageAddress);
            return false;
        } else {
            return hasValidAddress(linkTarget, messageAddress, msg);
        }
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
