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
package org.eclipse.hono.telemetry.impl;

import static org.eclipse.hono.server.MessageHelper.getDeviceId;
import static org.eclipse.hono.server.MessageHelper.getMessageId;
import static org.eclipse.hono.server.MessageHelper.getTenantId;

import org.apache.qpid.proton.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * A telemetry adapter that simply logs and discards all messages.
 *
 */
@Service
@Profile("testing")
public final class MessageDiscardingTelemetryAdapter extends BaseTelemetryAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(MessageDiscardingTelemetryAdapter.class);

    @Override
    public boolean processTelemetryData(final Message data) {
        LOG.debug("processing telemetry data [id: {}, device-id: {}, tenant-id: {}]", getMessageId(data),
                getDeviceId(data), getTenantId(data));
        return true;
    }

}
