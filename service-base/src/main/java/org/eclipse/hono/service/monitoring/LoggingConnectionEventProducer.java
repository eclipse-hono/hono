/*******************************************************************************
 * Copyright (c) 2016, 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.hono.service.monitoring;

import org.eclipse.hono.service.auth.device.Device;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * A logging only implementation.
 * <p>
 * This implementation only create log messages as <em>events</em>. It will log everything on {@code INFO} level.
 */
public class LoggingConnectionEventProducer implements ConnectionEventProducer {

    private static final Logger logger = LoggerFactory.getLogger(LoggingConnectionEventProducer.class);

    @Override
    public Future<?> connected(final String remoteId, final String protocolAdapter, final Device authenticatedDevice,
            final JsonObject data) {
        logger.info("   Connected - ID: {}, Protocol Adapter: {}, Device: {}, Data: {}", remoteId, protocolAdapter,
                authenticatedDevice, data);
        return Future.succeededFuture();
    }

    @Override
    public Future<?> disconnected(final String remoteId, final String protocolAdapter, final Device authenticatedDevice,
            final JsonObject data) {
        logger.info("Disconnected - ID: {}, Protocol Adapter: {}, Device: {}, Data: {}", remoteId, protocolAdapter,
                authenticatedDevice, data);
        return Future.succeededFuture();
    }

}
