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

import static org.eclipse.hono.server.MessageHelper.APP_PROPERTY_DEVICE_ID;
import static org.eclipse.hono.server.MessageHelper.APP_PROPERTY_TENANT_ID;

import org.apache.qpid.proton.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A filter for verifying the format of <em>Telemetry</em> messages.
 *
 */
public final class TelemetryMessageFilter {

    private static final String DEFAULT_TENANT_ID = "default";

    private static final Logger LOG = LoggerFactory.getLogger(TelemetryMessageFilter.class);

    /**
     * Checks whether a given telemetry message contains all required properties.
     * 
     * @param msg the message to verify.
     * @return {@code true} if the given message complies with the <em>Telemetry</em> API, {@code false} otherwise.
     */
    @SuppressWarnings("unchecked")
    public static boolean verify(final Message msg) {
        String deviceId = (String) msg.getApplicationProperties().getValue().get(APP_PROPERTY_DEVICE_ID);
        if (deviceId == null) {
            LOG.trace("dropping message [id: {}] due to missing device ID", msg.getMessageId());
            return false;
        }
        String tenant = (String) msg.getApplicationProperties().getValue().get(APP_PROPERTY_TENANT_ID);
        if (tenant == null) {
            LOG.trace("no tenant-id found in telemetry message [id: {}], using default [{}]", msg.getMessageId(),
                    DEFAULT_TENANT_ID);
            msg.getApplicationProperties().getValue().put(APP_PROPERTY_TENANT_ID, DEFAULT_TENANT_ID);
        } else {
            LOG.trace("telemetry message passed [id: {}, tenant: {}]", msg.getMessageId(), tenant);
        }
        return true;
    }
}
