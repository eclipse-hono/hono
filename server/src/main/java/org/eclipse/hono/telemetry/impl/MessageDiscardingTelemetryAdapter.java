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

import org.apache.qpid.proton.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import io.vertx.core.Handler;

/**
 * A telemetry adapter that simply logs and discards all messages.
 *
 */
@Service
@Profile("testing")
public final class MessageDiscardingTelemetryAdapter extends BaseTelemetryAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(MessageDiscardingTelemetryAdapter.class);

    @Override
    public void processTelemetryData(final Message data, final String tenantId, final Handler<Boolean> resultHandler) {
        LOG.debug("processing telemetry data [id: {}, to: {}]", data.getMessageId(), data.getAddress());
        resultHandler.handle(Boolean.TRUE);
    }
}
