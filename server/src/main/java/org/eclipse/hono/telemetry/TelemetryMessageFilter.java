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
import org.eclipse.hono.util.Constants;
import org.eclipse.hono.util.MessageHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A filter for verifying the format of <em>Telemetry</em> messages.
 *
 */
public final class TelemetryMessageFilter {

    private static final Logger LOG = LoggerFactory.getLogger(TelemetryMessageFilter.class);

    private TelemetryMessageFilter() {
    }

    /**
     * Checks whether a given telemetry message contains all required properties.
     * 
     * @param linkAddressTenant the ID of the tenant to upload telemetry data for as determined from the target address
     *            of the link established by the client.
     * @param msg the message to verify.
     * @return {@code true} if the given message complies with the <em>Telemetry</em> API specification, {@code false}
     *         otherwise.
     */
    public static boolean verify(final String linkAddressTenant, final Message msg) {
        return hasConsistentToField(linkAddressTenant, msg);
    }

    static boolean hasConsistentToField(final String linkAddressTenant, final Message msg) {
        if (msg.getAddress() == null
                || !msg.getAddress().startsWith(TelemetryConstants.NODE_ADDRESS_TELEMETRY_PREFIX)) {
            LOG.trace("message [id: {}] has no valid address [to: {}]", msg.getMessageId(), msg.getAddress());
            return false;
        } else {
            return hasConsistentDeviceIdProperty(msg) && hasConsistentTenantIdProperty(linkAddressTenant, msg);
        }
    }

    private static boolean hasConsistentDeviceIdProperty(final Message msg) {
        Object deviceId = MessageHelper.getDeviceId(msg);
        final String deviceIdFromAddress = msg.getAddress()
                .substring(TelemetryConstants.NODE_ADDRESS_TELEMETRY_PREFIX.length());
        boolean consistent = deviceIdFromAddress.equals(deviceId);
        if (!consistent) {
            LOG.trace(
                    "message's [id: {}] application-property [device-id: {}] is inconsistent with message address [to: {}]",
                    msg.getMessageId(), deviceId, msg.getAddress());
        }
        return consistent;
    }

    private static boolean hasConsistentTenantIdProperty(final String linkAddressTenant, final Message msg) {
        Object tenantId = MessageHelper.getTenantId(msg);
        if (tenantId == null && Constants.isDefaultTenant(linkAddressTenant)) {
            // add DEFAULT_TENANT to message
            tenantId = Constants.DEFAULT_TENANT;
            MessageHelper.addTenantId(msg, Constants.DEFAULT_TENANT);
        }
        boolean consistent = linkAddressTenant.equals(tenantId);
        if (!consistent) {
            LOG.trace(
                    "message's [id: {}] application-property [tenant-id: {}] is inconsistent with link target address [tenant: {}]",
                    msg.getMessageId(), tenantId, linkAddressTenant);
        }
        return consistent;
    }
}
