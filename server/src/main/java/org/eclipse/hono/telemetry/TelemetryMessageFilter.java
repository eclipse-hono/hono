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

import java.util.HashMap;

import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.MessageAnnotations;
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
     * 
     * @param linkAddressTenant the ID of the tenant to upload telemetry data for as determined from the target address
     *            of the link established by the client.
     * @param msg the message to verify.
     * @return {@code true} if the given message complies with the <em>Telemetry</em> API specification, {@code false}
     *         otherwise.
     */
    public static boolean verify(final String linkAddressTenant, final Message msg) {
        return verify(linkAddressTenant, msg, false);
    }

    /**
     * Checks whether a given telemetry message contains all required properties.
     * 
     * @param linkAddressTenant the ID of the tenant to upload telemetry data for as determined from the target address
     *            of the link established by the client.
     * @param msg the message to verify.
     * @param assumeDefaultTenant if{@code true} assume the default tenant as the tenant when parsing the message's
     *            address field.
     * @return {@code true} if the given message complies with the <em>Telemetry</em> API specification, {@code false}
     *         otherwise.
     */
    public static boolean verify(final String linkAddressTenant, final Message msg, final boolean assumeDefaultTenant) {
        if (msg.getAddress() == null
                || !msg.getAddress().startsWith(TelemetryConstants.NODE_ADDRESS_TELEMETRY_PREFIX)) {
            LOG.trace("message [id: {}] has no telemetry endpoint address [to: {}]", msg.getMessageId(),
                    msg.getAddress());
            return false;
        } else {
            return hasValidAddress(linkAddressTenant, msg, assumeDefaultTenant);
        }
    }

    private static boolean hasValidAddress(final String linkAddressTenant, final Message msg,
            final boolean assumeDefaultTenant) {
        try {
            if (assumeDefaultTenant) {
                return hasValidAddress(linkAddressTenant,
                        ResourceIdentifier.fromStringAssumingDefaultTenant(msg.getAddress()), msg);
            } else {
                return hasValidAddress(linkAddressTenant, ResourceIdentifier.fromString(msg.getAddress()), msg);
            }
        } catch (IllegalArgumentException e) {
            LOG.trace("message has invalid address", e);
            return false;
        }
    }

    private static boolean hasValidAddress(final String linkAddressTenant, final ResourceIdentifier resourceId,
            final Message msg) {
        if (linkAddressTenant.equals(resourceId.getTenantId())) {
            MessageAnnotations annotations = msg.getMessageAnnotations();
            if (annotations == null) {
                annotations = new MessageAnnotations(new HashMap<>());
                msg.setMessageAnnotations(annotations);
            }
            annotations.getValue().put(Symbol.valueOf(MessageHelper.APP_PROPERTY_TENANT_ID), resourceId.getTenantId());
            annotations.getValue().put(Symbol.valueOf(MessageHelper.APP_PROPERTY_DEVICE_ID), resourceId.getDeviceId());
            return true;
        } else {
            LOG.trace("message address contains invalid tenant ID [expected: {}, but was: {}]", linkAddressTenant,
                    resourceId.getTenantId());
            return false;
        }
    }
}
